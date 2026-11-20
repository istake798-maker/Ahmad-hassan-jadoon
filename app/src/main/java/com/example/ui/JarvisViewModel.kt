package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRole
import com.example.data.model.ClientLead
import com.example.data.model.ClientReplyAnalysis
import com.example.data.model.ClientStatus
import com.example.data.model.DailyStat
import com.example.data.model.HuntedLeadCandidate
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.data.model.UiState
import com.example.data.model.WorkType
import com.example.data.repository.ChatStreamEvent
import com.example.data.repository.JarvisAiRepository
import com.example.data.repository.JarvisFirestoreRepository
import com.example.data.repository.SettingsPreferencesRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JarvisViewModel(
    private val firestoreRepo: JarvisFirestoreRepository,
    private val aiRepo: JarvisAiRepository,
    private val prefsRepo: SettingsPreferencesRepository,
    val currentUserId: String
) : ViewModel() {

    val clientsState: StateFlow<UiState<List<ClientLead>>> =
        firestoreRepo.observeClients(currentUserId)
            .toUiStateFlow("Failed to load clients")

    val memoriesState: StateFlow<UiState<List<MemoryItem>>> =
        firestoreRepo.observeMemories(currentUserId)
            .toUiStateFlow("Failed to load Second Brain memories")

    val messagesState: StateFlow<UiState<List<ChatMessage>>> =
        firestoreRepo.observeMessages(currentUserId)
            .toUiStateFlow("Failed to load chat history")

    val dailyStatState: StateFlow<UiState<DailyStat>> =
        firestoreRepo.observeDailyStat(currentUserId)
            .toUiStateFlow("Failed to load daily report")

    val voiceReplyEnabled: StateFlow<Boolean> = prefsRepo.voiceReplyEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val groqApiKey: StateFlow<String> = prefsRepo.groqApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    private val _activeBrainLabel = MutableStateFlow(JarvisAiRepository.LABEL_BRAIN_1)
    val activeBrainLabel: StateFlow<String> = _activeBrainLabel.asStateFlow()

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _isChatBusy = MutableStateFlow(false)
    val isChatBusy: StateFlow<Boolean> = _isChatBusy.asStateFlow()

    private val _huntState = MutableStateFlow<UiState<List<HuntedLeadCandidate>>>(UiState.Success(emptyList()))
    val huntState: StateFlow<UiState<List<HuntedLeadCandidate>>> = _huntState.asStateFlow()

    private val _handleAnalysisState = MutableStateFlow<UiState<ClientReplyAnalysis>?>(null)
    val handleAnalysisState: StateFlow<UiState<ClientReplyAnalysis>?> = _handleAnalysisState.asStateFlow()

    private val _speakEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val speakEvents: SharedFlow<String> = _speakEvents.asSharedFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    fun sendChatMessage(rawPrompt: String, fromVoice: Boolean = false) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() || _isChatBusy.value) return

        viewModelScope.launch {
            _isChatBusy.value = true
            _streamingReply.value = ""

            val saveUserResult = firestoreRepo.addMessage(
                role = ChatRole.USER,
                text = prompt,
                brainUsed = "",
                voiceInput = fromVoice
            )
            if (saveUserResult.isFailure) {
                _toastEvents.tryEmit(saveUserResult.exceptionOrNull()?.localizedMessage ?: "Message save nahi ho saka.")
            }

            try {
                val history = (messagesState.value as? UiState.Success)?.data.orEmpty()
                val memories = (memoriesState.value as? UiState.Success)?.data.orEmpty()
                val clients = (clientsState.value as? UiState.Success)?.data.orEmpty()
                val stat = (dailyStatState.value as? UiState.Success)?.data ?: DailyStat()

                val pendingApprovals = clients.filter { it.statusEnum() == ClientStatus.AWAITING_APPROVAL }
                val activeCount = clients.count {
                    it.statusEnum() != ClientStatus.COMPLETED && it.statusEnum() != ClientStatus.HUNTED
                }
                val dailySummary =
                    "Aaj ${stat.huntedCount}/10 clients hunt hue, ${stat.repliesHandled} replies handle hue, $activeCount active/pending kaam hain, ${stat.dealsClosed} deals aaj close huin."

                val groqKey = prefsRepo.groqApiKey.first()
                var finalResponseText = ""
                var usedBrain = JarvisAiRepository.LABEL_BRAIN_1

                aiRepo.streamJarvisChat(
                    userPrompt = prompt,
                    history = history,
                    memories = memories,
                    dailySummary = dailySummary,
                    pendingApprovals = pendingApprovals,
                    groqApiKey = groqKey
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Chunk -> {
                            _activeBrainLabel.value = event.brainName
                            _streamingReply.value = event.accumulatedText
                        }
                        is ChatStreamEvent.Completed -> {
                            _activeBrainLabel.value = event.brainName
                            usedBrain = event.brainName
                            finalResponseText = event.fullText
                            event.extractedMemory?.let { (title, content, cat) ->
                                firestoreRepo.addMemory(title, content, cat)
                            }
                        }
                    }
                }

                _streamingReply.value = null
                if (finalResponseText.isNotBlank()) {
                    firestoreRepo.addMessage(
                        role = ChatRole.ASSISTANT,
                        text = finalResponseText,
                        brainUsed = usedBrain,
                        voiceInput = false
                    )
                    if (fromVoice || voiceReplyEnabled.value) {
                        _speakEvents.tryEmit(finalResponseText)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                _streamingReply.value = null
                val errText = "Boss, network ya AI service me rukawat aayi: ${e.localizedMessage ?: "Unknown error"}. Dobara try karein, main backup brain ready rakh raha hoon."
                firestoreRepo.addMessage(
                    role = ChatRole.ASSISTANT,
                    text = errText,
                    brainUsed = _activeBrainLabel.value,
                    voiceInput = false
                )
            } finally {
                _isChatBusy.value = false
                _streamingReply.value = null
            }
        }
    }

    fun huntClients(platform: String, niche: String, workType: WorkType, requestedCount: Int) {
        val cleanNiche = niche.trim().ifEmpty { "Small business owners & creators" }
        val stat = (dailyStatState.value as? UiState.Success)?.data ?: DailyStat()
        val remainingToday = (10 - stat.huntedCount.toInt()).coerceAtLeast(0)
        if (remainingToday <= 0) {
            _huntState.value = UiState.Error(
                "Boss, aaj ke 10/10 targeted clients hunt ho chuke hain! Spam se bachne ke liye daily limit 10 hai — kal naya batch shuru karenge."
            )
            return
        }

        viewModelScope.launch {
            _huntState.value = UiState.Loading
            try {
                val countToHunt = requestedCount.coerceAtMost(remainingToday).coerceIn(1, 5)
                val (leads, brain) = aiRepo.huntClients(platform, cleanNiche, workType, countToHunt)
                _activeBrainLabel.value = brain
                _huntState.value = UiState.Success(leads)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Hunt clients failed", e)
                _huntState.value = UiState.Error(
                    e.localizedMessage ?: "Client hunt abhi complete nahi ho saka. Dobara try karein."
                )
            }
        }
    }

    fun saveHuntedLeadToCrm(candidate: HuntedLeadCandidate, markAsPitched: Boolean) {
        viewModelScope.launch {
            val status = if (markAsPitched) ClientStatus.PITCHED else ClientStatus.HUNTED
            val result = firestoreRepo.addClientLead(
                name = candidate.clientName,
                workType = candidate.workType,
                priceUsd = candidate.priceUsd,
                platform = candidate.platform,
                contactHandle = candidate.contactHandle,
                status = status,
                outreachMessage = candidate.personalizedEnglishPitch,
                notes = candidate.whyGoodFit,
                countTowardsDailyHunt = true
            )
            if (result.isSuccess) {
                val current = (_huntState.value as? UiState.Success)?.data.orEmpty()
                _huntState.value = UiState.Success(current.filterNot { it == candidate })
                _toastEvents.tryEmit("${candidate.clientName} CRM me save ho gaya Boss!")
            } else {
                _toastEvents.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Client save nahi ho saka.")
            }
        }
    }

    fun addManualClient(
        name: String,
        workType: WorkType,
        priceUsd: Int,
        platform: String,
        contactHandle: String,
        notes: String
    ) {
        viewModelScope.launch {
            val result = firestoreRepo.addClientLead(
                name = name,
                workType = workType,
                priceUsd = priceUsd,
                platform = platform,
                contactHandle = contactHandle,
                status = ClientStatus.IN_DISCUSSION,
                notes = notes,
                countTowardsDailyHunt = false
            )
            if (result.isSuccess) {
                _toastEvents.tryEmit("$name CRM me add ho gaya!")
            } else {
                _toastEvents.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Client add fail hua.")
            }
        }
    }

    fun handleClientMessage(client: ClientLead, incomingMessage: String) {
        val msg = incomingMessage.trim()
        if (msg.isEmpty()) return

        viewModelScope.launch {
            _handleAnalysisState.value = UiState.Loading
            try {
                val memories = (memoriesState.value as? UiState.Success)?.data.orEmpty()
                val (analysis, brain) = aiRepo.handleClientMessage(client, msg, memories)
                _activeBrainLabel.value = brain
                val targetStatus = if (analysis.clientAgreed) {
                    ClientStatus.AWAITING_APPROVAL
                } else {
                    ClientStatus.IN_DISCUSSION
                }

                val updateRes = firestoreRepo.updateClientHandled(
                    clientId = client.id,
                    workType = analysis.detectedWorkType,
                    priceUsd = analysis.quotedPriceUsd,
                    lastClientMessage = msg,
                    draftedReply = analysis.englishReply,
                    newStatus = targetStatus
                )
                if (updateRes.isSuccess) {
                    _handleAnalysisState.value = UiState.Success(analysis)
                } else {
                    _handleAnalysisState.value = UiState.Error(
                        updateRes.exceptionOrNull()?.localizedMessage ?: "CRM update nahi ho saka."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Handle client failed", e)
                _handleAnalysisState.value = UiState.Error(
                    e.localizedMessage ?: "Client message analysis fail ho gayi."
                )
            }
        }
    }

    fun clearHandleAnalysis() {
        _handleAnalysisState.value = null
    }

    fun approveDealClose(client: ClientLead) {
        viewModelScope.launch {
            val res = firestoreRepo.approveClientDeal(client.id)
            if (res.isSuccess) {
                _toastEvents.tryEmit("Deal Final! ${client.name} ($${client.priceUsd}) closed.")
            } else {
                _toastEvents.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Deal approve nahi ho saki.")
            }
        }
    }

    fun completeDeal(client: ClientLead) {
        viewModelScope.launch {
            val res = firestoreRepo.completeClientDeal(client.id)
            if (res.isSuccess) {
                _toastEvents.tryEmit("${client.name} ka kaam Completed mark ho gaya!")
            } else {
                _toastEvents.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Update fail hua.")
            }
        }
    }

    fun markPitched(client: ClientLead) {
        viewModelScope.launch {
            val res = firestoreRepo.updateClientStatus(client.id, ClientStatus.PITCHED)
            if (res.isSuccess) {
                _toastEvents.tryEmit("${client.name} Pitched mark ho gaya.")
            } else {
                _toastEvents.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Update fail hua.")
            }
        }
    }

    fun deleteClient(clientId: String) {
        viewModelScope.launch {
            val res = firestoreRepo.deleteClient(clientId)
            if (res.isFailure) {
                _toastEvents.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Delete fail hua.")
            }
        }
    }

    fun addMemory(title: String, content: String, category: MemoryCategory) {
        viewModelScope.launch {
            val res = firestoreRepo.addMemory(title, content, category)
            if (res.isSuccess) {
                _toastEvents.tryEmit("Yaad rakh liya Boss! Second Brain updated.")
            } else {
                _toastEvents.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Memory save nahi ho saki.")
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteMemory(memoryId)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            val res = firestoreRepo.clearChatHistory()
            if (res.isSuccess) {
                _toastEvents.tryEmit("Chat history clear ho gayi.")
            }
        }
    }

    fun setVoiceReplyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setVoiceReplyEnabled(enabled)
        }
    }

    fun setGroqApiKey(key: String) {
        viewModelScope.launch {
            prefsRepo.setGroqApiKey(key)
            _toastEvents.tryEmit("Brain 3 (Groq Llama 3) configuration saved!")
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(fallbackMessage: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackMessage, rootError)
                emit(UiState.Error(rootError.message ?: fallbackMessage))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private companion object {
        const val TAG = "JarvisViewModel"
    }
}
