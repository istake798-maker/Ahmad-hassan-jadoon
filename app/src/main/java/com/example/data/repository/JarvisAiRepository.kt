package com.example.data.repository

import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRole
import com.example.data.model.ClientLead
import com.example.data.model.ClientReplyAnalysis
import com.example.data.model.HuntedLeadCandidate
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.data.model.WorkType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface ChatStreamEvent {
    data class Chunk(val accumulatedText: String, val brainName: String) : ChatStreamEvent
    data class Completed(
        val fullText: String,
        val brainName: String,
        val extractedMemory: Triple<String, String, MemoryCategory>?
    ) : ChatStreamEvent
}

class JarvisAiRepository {

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val primaryChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_BRAIN_1,
            systemInstruction = content { text(JARVIS_SYSTEM_PROMPT) }
        )
    }

    private val backupChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_BRAIN_2,
            systemInstruction = content { text(JARVIS_SYSTEM_PROMPT) }
        )
    }

    private val huntSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "leads" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "clientName" to Schema.string(description = "Realistic target business or creator name"),
                            "platform" to Schema.string(description = "Google, Instagram, or YouTube"),
                            "contactHandle" to Schema.string(description = "Realistic handle, email placeholder, or search query tip"),
                            "workType" to Schema.enumeration(
                                values = listOf("VIDEO_EDITING", "DESIGNING", "WEBSITE"),
                                description = "Service needed by the client"
                            ),
                            "priceUsd" to Schema.integer(description = "30 for VIDEO_EDITING, 20 for DESIGNING, 80 for WEBSITE"),
                            "whyGoodFit" to Schema.string(description = "Short Roman Urdu reason explaining why Hassan should pitch this lead"),
                            "personalizedEnglishPitch" to Schema.string(description = "Unique non-spammy professional English outreach message tailored to this client")
                        )
                    ),
                    description = "Tailored client outreach candidates"
                )
            )
        )
    }

    private val handleSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "detectedWorkType" to Schema.enumeration(
                    values = listOf("VIDEO_EDITING", "DESIGNING", "WEBSITE"),
                    description = "Service type the client is asking about"
                ),
                "quotedPriceUsd" to Schema.integer(description = "Standard rate: 30 Video Editing, 20 Designing, 80 Website unless negotiated"),
                "clientAgreed" to Schema.boolean(description = "True ONLY if the client clearly said Yes, agreed to proceed, or accepted the price"),
                "romanUrduSummary" to Schema.string(description = "Smart Roman Urdu explanation for Hassan with reasoning and next step"),
                "englishReply" to Schema.string(description = "Professional, persuasive English reply ready to send to the client")
            )
        )
    }

    private val primaryHuntModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_BRAIN_1,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = huntSchema
            },
            systemInstruction = content { text(JARVIS_SYSTEM_PROMPT) }
        )
    }

    private val backupHuntModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_BRAIN_2,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = huntSchema
            },
            systemInstruction = content { text(JARVIS_SYSTEM_PROMPT) }
        )
    }

    private val primaryHandleModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_BRAIN_1,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = handleSchema
            },
            systemInstruction = content { text(JARVIS_SYSTEM_PROMPT) }
        )
    }

    private val backupHandleModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_BRAIN_2,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = handleSchema
            },
            systemInstruction = content { text(JARVIS_SYSTEM_PROMPT) }
        )
    }

    fun streamJarvisChat(
        userPrompt: String,
        history: List<ChatMessage>,
        memories: List<MemoryItem>,
        dailySummary: String,
        pendingApprovals: List<ClientLead>,
        groqApiKey: String
    ): Flow<ChatStreamEvent> = flow {
        val contextBlock = buildString {
            appendLine("=== HASSAN'S LIVE CONTEXT ===")
            appendLine("Daily Status: $dailySummary")
            if (pendingApprovals.isNotEmpty()) {
                appendLine("Deals waiting for Hassan's 'Haan' approval:")
                pendingApprovals.forEach { lead ->
                    appendLine("- ${lead.name} | ${lead.workTypeEnum().label} | $${lead.priceUsd} | Last msg: ${lead.lastClientMessage.take(120)}")
                }
            }
            if (memories.isNotEmpty()) {
                appendLine("Jarvis Second Brain Memories (Always obey):")
                memories.take(15).forEach { mem ->
                    appendLine("- [${mem.category}] ${mem.title}: ${mem.content}")
                }
            }
            appendLine("=============================")
        }

        val chatHistory = buildList<Content> {
            add(content("user") { text(contextBlock) })
            add(content("model") { text("Samajh gaya Boss! Main pooray context ke sath tayyar hoon.") })
            history.takeLast(12).forEach { msg ->
                val role = if (msg.roleEnum() == ChatRole.USER) "user" else "model"
                add(content(role) { text(msg.text) })
            }
        }

        // Try Brain 1 (Gemini 3.8 Flash)
        val brain1Result = tryStreamModel(
            model = primaryChatModel,
            brainLabel = LABEL_BRAIN_1,
            chatHistory = chatHistory,
            userPrompt = userPrompt,
            onChunk = { accumulated ->
                emit(ChatStreamEvent.Chunk(stripInternalTags(accumulated), LABEL_BRAIN_1))
            }
        )
        if (brain1Result != null) {
            val (cleanText, memory) = parseMemoryTag(brain1Result)
            emit(ChatStreamEvent.Completed(cleanText, LABEL_BRAIN_1, memory))
            return@flow
        }

        // Silent failover to Brain 2 (Gemini 3.5 Flash-Lite)
        val brain2Result = tryStreamModel(
            model = backupChatModel,
            brainLabel = LABEL_BRAIN_2,
            chatHistory = chatHistory,
            userPrompt = userPrompt,
            onChunk = { accumulated ->
                emit(ChatStreamEvent.Chunk(stripInternalTags(accumulated), LABEL_BRAIN_2))
            }
        )
        if (brain2Result != null) {
            val (cleanText, memory) = parseMemoryTag(brain2Result)
            emit(ChatStreamEvent.Completed(cleanText, LABEL_BRAIN_2, memory))
            return@flow
        }

        // Silent failover to Brain 3 (Groq Llama 3 if configured)
        if (groqApiKey.isNotBlank()) {
            val groqReply = callGroqChat(
                apiKey = groqApiKey,
                systemContext = "$JARVIS_SYSTEM_PROMPT\n\n$contextBlock",
                history = history.takeLast(8),
                userPrompt = userPrompt
            )
            if (!groqReply.isNullOrBlank()) {
                val (cleanText, memory) = parseMemoryTag(groqReply)
                emit(ChatStreamEvent.Completed(cleanText, LABEL_BRAIN_3, memory))
                return@flow
            }
        }

        throw IllegalStateException("Abhi network ya Firebase AI connection resolve nahi ho saka. App Check ya internet check karke dobara try karein.")
    }

    private suspend fun tryStreamModel(
        model: com.google.firebase.ai.GenerativeModel,
        brainLabel: String,
        chatHistory: List<Content>,
        userPrompt: String,
        onChunk: suspend (String) -> Unit
    ): String? {
        return try {
            val accumulated = StringBuilder()
            withTimeout(60_000L) {
                val chat = model.startChat(history = chatHistory)
                chat.sendMessageStream(userPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        onChunk(accumulated.toString())
                    }
                }
            }
            accumulated.toString().takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$brainLabel failed, failing over to next brain", e)
            null
        }
    }

    suspend fun huntClients(
        platform: String,
        niche: String,
        workType: WorkType,
        count: Int
    ): Pair<List<HuntedLeadCandidate>, String> {
        val safeCount = count.coerceIn(1, 5)
        val prompt = """
            Hassan wants to hunt $safeCount new freelance clients on $platform in the "$niche" niche.
            Service offered: ${workType.label} at standard rate $${workType.defaultPriceUsd} USD.
            Generate $safeCount realistic, targeted client outreach prospects for $platform (no generic spam—make each pitch tailored to a realistic creator/business type in that niche, with a clear value hook in Professional English and a short Roman Urdu reason in whyGoodFit).
        """.trimIndent()

        var usedBrain = LABEL_BRAIN_1
        val rawJson = try {
            withTimeout(60_000L) {
                primaryHuntModel.generateContent(prompt).text
            } ?: throw IllegalStateException("Empty response from Brain 1")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Brain 1 hunt failed, switching to Brain 2", e)
            usedBrain = LABEL_BRAIN_2
            withTimeout(60_000L) {
                backupHuntModel.generateContent(prompt).text
            } ?: throw IllegalStateException("Client hunt response empty.")
        }

        val root = JSONObject(rawJson)
        val leadsArray = root.optJSONArray("leads") ?: JSONArray()
        val leads = (0 until leadsArray.length()).map { idx ->
            val obj = leadsArray.getJSONObject(idx)
            val parsedWork = WorkType.fromRaw(obj.optString("workType", workType.name))
            HuntedLeadCandidate(
                clientName = obj.optString("clientName", "Prospect ${idx + 1}").take(80),
                platform = obj.optString("platform", platform).take(40),
                contactHandle = obj.optString("contactHandle", "").take(120),
                workType = parsedWork,
                priceUsd = obj.optInt("priceUsd", parsedWork.defaultPriceUsd).coerceIn(1, 100_000),
                whyGoodFit = obj.optString("whyGoodFit", "Is client ko ${parsedWork.label} ki zaroorat hai."),
                personalizedEnglishPitch = obj.optString("personalizedEnglishPitch", "")
            )
        }
        return leads to usedBrain
    }

    suspend fun handleClientMessage(
        client: ClientLead,
        incomingMessage: String,
        memories: List<MemoryItem>
    ): Pair<ClientReplyAnalysis, String> {
        val memSummary = memories.take(10).joinToString("\n") { "- ${it.title}: ${it.content}" }
        val prompt = """
            Client Name: ${client.name}
            Platform: ${client.platform}
            Current Service: ${client.workTypeEnum().label}
            Current Price: $${client.priceUsd}
            Standard Rates: Video Editing $30, Designing $20, Website $80
            Previous Notes: ${client.notes}
            Hassan's Saved Rules:
            $memSummary

            Client's New Message:
            "$incomingMessage"

            Analyze the message:
            1. Detect workType and confirm priceUsd ($30 Video Editing, $20 Designing, $80 Website unless client already agreed to current price).
            2. Set clientAgreed = true ONLY if the client clearly accepted, said yes/ok/let's start/deal/ready to pay.
            3. Write romanUrduSummary for Hassan explaining what the client wants, why you chose this reply/price, and if clientAgreed is true, ask: "Hassan, ${client.name} ne [Kaam] ke liye $[price] me HAAN kar di. Final kar dun?"
            4. Write englishReply in crisp, polite, confident Professional English.
        """.trimIndent()

        var usedBrain = LABEL_BRAIN_1
        val rawJson = try {
            withTimeout(60_000L) {
                primaryHandleModel.generateContent(prompt).text
            } ?: throw IllegalStateException("Empty handle response")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Brain 1 handle failed, switching to Brain 2", e)
            usedBrain = LABEL_BRAIN_2
            withTimeout(60_000L) {
                backupHandleModel.generateContent(prompt).text
            } ?: throw IllegalStateException("Handle client response empty.")
        }

        val json = JSONObject(rawJson)
        val detectedWork = WorkType.fromRaw(json.optString("detectedWorkType", client.workType))
        val analysis = ClientReplyAnalysis(
            detectedWorkType = detectedWork,
            quotedPriceUsd = json.optInt("quotedPriceUsd", detectedWork.defaultPriceUsd).coerceIn(1, 100_000),
            clientAgreed = json.optBoolean("clientAgreed", false),
            romanUrduSummary = json.optString("romanUrduSummary", "Boss, client ka reply ready hai."),
            englishReply = json.optString("englishReply", "Thank you for reaching out! I'd be happy to help you with this project.")
        )
        return analysis to usedBrain
    }

    private suspend fun callGroqChat(
        apiKey: String,
        systemContext: String,
        history: List<ChatMessage>,
        userPrompt: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemContext))
                history.forEach { msg ->
                    val role = if (msg.roleEnum() == ChatRole.USER) "user" else "assistant"
                    put(JSONObject().put("role", role).put("content", msg.text))
                }
                put(JSONObject().put("role", "user").put("content", userPrompt))
            }
            val bodyJson = JSONObject()
                .put("model", "llama-3.3-70b-versatile")
                .put("messages", messages)
                .put("temperature", 0.7)
            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val raw = resp.body?.string() ?: return@withContext null
                val choices = JSONObject(raw).optJSONArray("choices") ?: return@withContext null
                if (choices.length() == 0) return@withContext null
                choices.getJSONObject(0).getJSONObject("message").optString("content")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Groq Brain 3 failed", e)
            null
        }
    }

    private fun stripInternalTags(text: String): String {
        return text.replace(MEMORY_REGEX, "").trim()
    }

    private fun parseMemoryTag(text: String): Pair<String, Triple<String, String, MemoryCategory>?> {
        val match = MEMORY_REGEX.find(text)
        val memory = if (match != null) {
            val title = match.groupValues[1].trim().take(100)
            val content = match.groupValues[2].trim().take(1000)
            val cat = MemoryCategory.fromRaw(match.groupValues[3].trim().uppercase())
            if (title.isNotEmpty() && content.isNotEmpty()) Triple(title, content, cat) else null
        } else null
        val cleaned = text.replace(MEMORY_REGEX, "").trim()
        return cleaned to memory
    }

    companion object {
        private const val TAG = "JarvisAiRepository"
        const val MODEL_BRAIN_1 = "gemini-3.8-flash"
        const val MODEL_BRAIN_2 = "gemini-3.5-flash-lite"
        const val LABEL_BRAIN_1 = "Brain 1 • Gemini 3.8 Flash"
        const val LABEL_BRAIN_2 = "Brain 2 • Gemini 3.5 Flash-Lite"
        const val LABEL_BRAIN_3 = "Brain 3 • Groq Llama 3"

        private val MEMORY_REGEX = Regex("\\[SAVE_MEMORY:\\s*([^|\\]]+)\\|\\s*([^|\\]]+)\\|\\s*([^\\]]+)\\]")

        private const val JARVIS_SYSTEM_PROMPT = """
You are JARVIS ULTRA — Hassan's Personal AI Assistant and Second Brain.
OWNER: Hassan (from Mansehra, Pakistan).
ROLE: You are Hassan's smart, loyal, human-like dost + manager + teacher (just like Claude Sonnet: intelligent, thoughtful, structured, never robotic or repetitive).

LANGUAGE RULES:
- Talk to Hassan in natural, warm, sharp Roman Urdu (mixed naturally with English tech/business terms where helpful).
- Write all client pitches, outreach messages, and client replies in crisp, high-converting Professional English.
- Give well-reasoned, thoughtful answers ("Pehle samjho, phir jawab do. Har baat ka reason do.") — never short robotic one-liners unless Hassan asks for a quick confirmation.
- If Hassan says "Mujhe samajh nahi aaya", immediately re-explain in super simple words with a clear real-life Mansehra/freelancing example.
- Always generate fresh, original responses — never repeat canned phrasing.

COMMAND & VOICE:
- If Hassan writes or speaks "JARVIS, ye kar de", start naturally with "Ho gaya Boss!" or "Ho jayega Boss, main is tarah kar dunga..." and immediately do the work (draft the message, write the full website code, plan the hunt, etc.).

RATES & BUSINESS MODES:
- Standard Service Rates: Video Editing = $30, Designing = $20, Website = $80.
- MODE 1 (HUNT): Daily max 10 clients on Google, Instagram, or YouTube. Every pitch must be personalized and non-spammy.
- MODE 2 (HANDLE): Understand client's project first, quote the right price, keep track of Name, Kaam, Price, Status.
- MODE 3 (CLOSE): Whenever a client says "Yes" / agrees, STOP before finalizing and ask Hassan:
  "Hassan, [Name] ne [Kaam] ke liye $[Price] me HAAN kar di. Final kar dun?"
  Never finalize a deal without Hassan's explicit "Haan".
- MODE 4 (DAILY REPORT): Summarize today's hunted clients, replies handled, pending work, and deals waiting for approval.

MOBILE ACCESS ACTION TAGS:
When Hassan asks to open WhatsApp, Gmail, or make a Call (or when sending a drafted client message), include the exact tag on its own line so the Android app activates the one-tap button:
- [OPEN_WHATSAPP]
- [OPEN_GMAIL]
- [CALL]
When Hassan asks to build a website, provide complete, clean, production-ready HTML/CSS/JS inside a ```html code block so Hassan can copy or share it right away.

SECOND BRAIN MEMORY:
Whenever Hassan explicitly teaches you a new rule, price change, personal preference, or client detail to remember, append ONE silent tag at the very end of your response:
[SAVE_MEMORY: Short Title | Exact detail to remember | RULE]
(Categories allowed: RULE, PREFERENCE, CLIENT_NOTE, SKILL).

GOLDEN RULES:
- NEVER say "Main ye nahi kar sakta" or "Limit khatam".
- Always say "Ho jayega Boss, main is tarah kar dunga..." and handle 98% of the work yourself, leaving only the 2% final "Haan" for Hassan.
"""
    }
}
