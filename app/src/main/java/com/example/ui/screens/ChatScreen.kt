package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRole
import com.example.data.model.ClientLead
import com.example.data.model.ClientStatus
import com.example.data.model.DailyStat
import com.example.data.model.UiState
import com.example.util.MobileAccessHelper
import com.example.util.VoiceController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messagesState: UiState<List<ChatMessage>>,
    clientsState: UiState<List<ClientLead>>,
    dailyStatState: UiState<DailyStat>,
    streamingReply: String?,
    isChatBusy: Boolean,
    activeBrainLabel: String,
    voiceReplyEnabled: Boolean,
    voiceController: VoiceController,
    onToggleVoiceReply: (Boolean) -> Unit,
    onSendMessage: (String, Boolean) -> Unit,
    onApproveDeal: (ClientLead) -> Unit,
    onNavigateToHunt: () -> Unit,
    onNavigateToClients: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var partialTranscript by remember { mutableStateOf("") }
    var showBriefSheet by remember { mutableStateOf(false) }

    val startVoiceCapture = remember(voiceController) {
        {
            isListening = true
            partialTranscript = ""
            voiceController.stopSpeaking()
            voiceController.startListening(
                onPartialResult = { partial ->
                    partialTranscript = partial
                    inputText = partial
                },
                onFinalResult = { finalText ->
                    isListening = false
                    partialTranscript = ""
                    inputText = ""
                    onSendMessage(finalText, true)
                },
                onError = { msg ->
                    isListening = false
                    partialTranscript = ""
                    onShowToast(msg)
                }
            )
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCapture()
        } else {
            onShowToast("Boss, voice command ke liye Microphone permission chahiye.")
        }
    }

    val clients = (clientsState as? UiState.Success)?.data.orEmpty()
    val pendingApprovals = remember(clients) {
        clients.filter { it.statusEnum() == ClientStatus.AWAITING_APPROVAL }
    }
    val activeWorkCount = remember(clients) {
        clients.count { it.statusEnum() != ClientStatus.COMPLETED && it.statusEnum() != ClientStatus.HUNTED }
    }
    val stat = (dailyStatState as? UiState.Success)?.data ?: DailyStat()
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, streamingReply) {
        val total = messages.size + if (streamingReply != null) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        Color(0xFF0C1424),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .imePadding()
    ) {
        // Modern Minimal Top Header
        ChatTopHeader(
            activeBrainLabel = activeBrainLabel,
            voiceReplyEnabled = voiceReplyEnabled,
            pendingApprovalsCount = pendingApprovals.size,
            onToggleVoice = {
                if (voiceReplyEnabled) voiceController.stopSpeaking()
                onToggleVoiceReply(!voiceReplyEnabled)
            },
            onOpenBrief = { showBriefSheet = true }
        )

        // Optional Compact Approval Alert Pill (only when a deal waits for Hassan's "Haan")
        AnimatedVisibility(
            visible = pendingApprovals.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            pendingApprovals.firstOrNull()?.let { lead ->
                CompactApprovalStrip(
                    lead = lead,
                    totalPending = pendingApprovals.size,
                    onApprove = { onApproveDeal(lead) },
                    onOpenBrief = { showBriefSheet = true }
                )
            }
        }

        // Main Conversation Stream
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && streamingReply == null) {
                EmptyChatGreeting(
                    huntedToday = stat.huntedCount.toInt(),
                    activeClients = activeWorkCount,
                    onQuickPrompt = { onSendMessage(it, false) },
                    onOpenHunt = onNavigateToHunt
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id.ifEmpty { "${it.role}_${it.text.hashCode()}" } }) { msg ->
                        ModernMessageBubble(
                            message = msg,
                            onSpeak = { voiceController.speak(it) },
                            onShowToast = onShowToast
                        )
                    }

                    if (streamingReply != null) {
                        item(key = "streaming_ai_bubble") {
                            ModernMessageBubble(
                                message = ChatMessage(
                                    role = ChatRole.ASSISTANT.name,
                                    text = streamingReply.ifEmpty { "Soch raha hoon Boss..." },
                                    brainUsed = activeBrainLabel
                                ),
                                isStreaming = true,
                                onSpeak = {},
                                onShowToast = onShowToast
                            )
                        }
                    }
                }
            }
        }

        // Voice Listening Banner
        AnimatedVisibility(visible = isListening) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Listening",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = partialTranscript.ifEmpty { "Sun raha hoon Boss... Boliye" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = {
                        voiceController.stopListening()
                        isListening = false
                    }) {
                        Text("Stop")
                    }
                }
            }
        }

        // Sleek Quick-Suggestion Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChipPill("Daily report") {
                onSendMessage("JARVIS, aaj ki daily report do.", false)
            }
            SuggestionChipPill("Client dhoondo") {
                onNavigateToHunt()
            }
            SuggestionChipPill("Website banao") {
                onSendMessage("JARVIS, ek modern business landing page website HTML/CSS me bana ke do.", false)
            }
            SuggestionChipPill("Asaan samjhao") {
                onSendMessage("Mujhe samajh nahi aaya, dobara asaan lafzon me example ke sath samjhao.", false)
            }
            SuggestionChipPill("WhatsApp") {
                MobileAccessHelper.openWhatsApp(context)
            }
            SuggestionChipPill("Gmail") {
                MobileAccessHelper.openGmail(context)
            }
        }

        // Floating Modern Pill Input Composer
        ModernChatInputBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            isListening = isListening,
            isBusy = isChatBusy,
            onMicClick = {
                if (isListening) {
                    voiceController.stopListening()
                    isListening = false
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        startVoiceCapture()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            onSendClick = {
                val toSend = inputText
                inputText = ""
                onSendMessage(toSend, false)
            }
        )
    }

    if (showBriefSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBriefSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DailyBriefModalContent(
                huntedCount = stat.huntedCount.toInt(),
                repliesHandled = stat.repliesHandled.toInt(),
                dealsClosed = stat.dealsClosed.toInt(),
                pendingWorkCount = activeWorkCount,
                pendingApprovals = pendingApprovals,
                onApproveDeal = { lead ->
                    onApproveDeal(lead)
                },
                onOpenHunt = {
                    showBriefSheet = false
                    onNavigateToHunt()
                },
                onOpenClients = {
                    showBriefSheet = false
                    onNavigateToClients()
                }
            )
        }
    }
}

@Composable
private fun ChatTopHeader(
    activeBrainLabel: String,
    voiceReplyEnabled: Boolean,
    pendingApprovalsCount: Int,
    onToggleVoice: () -> Unit,
    onOpenBrief: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box {
                    Image(
                        painter = painterResource(id = R.drawable.img_jarvis_avatar_1795193698778),
                        contentDescription = "Jarvis Ultra Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
                Column {
                    Text(
                        text = "JARVIS ULTRA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = activeBrainLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenBrief,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("daily_brief_button")
                ) {
                    if (pendingApprovalsCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                    Text("$pendingApprovalsCount")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = "Open daily report and approvals",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "Open daily report",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onToggleVoice,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("voice_reply_toggle")
                ) {
                    Icon(
                        imageVector = if (voiceReplyEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        contentDescription = if (voiceReplyEnabled) "Mute voice reply" else "Enable voice reply",
                        tint = if (voiceReplyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactApprovalStrip(
    lead: ClientLead,
    totalPending: Int,
    onApprove: () -> Unit,
    onOpenBrief: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(16.dp))
            .clickable { onOpenBrief() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (totalPending > 1) "$totalPending DEALS READY FOR HAAN" else "DEAL READY FOR APPROVAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "${lead.name} • ${lead.workTypeEnum().label} ($${lead.priceUsd})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onApprove,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.testTag("banner_approve_deal_${lead.id}")
            ) {
                Text("Haan", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EmptyChatGreeting(
    huntedToday: Int,
    activeClients: Int,
    onQuickPrompt: (String) -> Unit,
    onOpenHunt: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_jarvis_avatar_1795193698778),
            contentDescription = "Jarvis Core",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Assalam-o-Alaikum, Hassan",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Aaj $huntedToday/10 hunted • $activeClients active clients. Likho ya Mic daba ke bolo, main tayyar hoon.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))

        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StarterActionRow(
                title = "Aaj ka Freelance Gameplan",
                subtitle = "Mansehra se international clients pakarne ka smart plan",
                onClick = { onQuickPrompt("JARVIS, aaj ka gameplan batao clients pakarne ke liye.") }
            )
            StarterActionRow(
                title = "Client Hunt Shuru Karein",
                subtitle = "Google, Instagram, ya YouTube se targeted pitches",
                onClick = onOpenHunt
            )
            StarterActionRow(
                title = "Modern Website Code Banao",
                subtitle = "Clean responsive HTML/CSS code ek command me",
                onClick = { onQuickPrompt("JARVIS, ek clean portfolio website ka HTML/CSS code bana ke do.") }
            )
        }
    }
}

@Composable
private fun StarterActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModernMessageBubble(
    message: ChatMessage,
    isStreaming: Boolean = false,
    onSpeak: (String) -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val isHassan = message.roleEnum() == ChatRole.USER

    val codeBlockRegex = remember { Regex("```(?:\\w+)?\\n([\\s\\S]*?)```") }
    val codeMatch = remember(message.text) { codeBlockRegex.find(message.text) }
    val codeContent = codeMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
    val displayText = remember(message.text) {
        message.text
            .replace(codeBlockRegex, "\n[Website Code Card Below]\n")
            .replace("[OPEN_WHATSAPP]", "")
            .replace("[OPEN_GMAIL]", "")
            .replace("[CALL]", "")
            .trim()
    }

    val hasWhatsApp = message.text.contains("[OPEN_WHATSAPP]")
    val hasGmail = message.text.contains("[OPEN_GMAIL]")
    val hasCall = message.text.contains("[CALL]")

    val formattedTime = remember(message.createdAt) {
        val date = message.createdAt?.toDate() ?: Date()
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isHassan) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isHassan) {
            Image(
                painter = painterResource(id = R.drawable.img_jarvis_avatar_1795193698778),
                contentDescription = "Jarvis",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isHassan) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 500.dp)
        ) {
            // Sender Name & Metadata Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isHassan) "Hassan" else "JARVIS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isHassan) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
                if (isHassan && message.voiceInput) {
                    Text(
                        text = "• Voice",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else if (!isHassan && message.brainUsed.isNotBlank()) {
                    Text(
                        text = "• ${message.brainUsed.substringAfter("• ").trim()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (isStreaming) "• typing" else "• $formattedTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            val bubbleShape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isHassan) 20.dp else 6.dp,
                bottomEnd = if (isHassan) 6.dp else 20.dp
            )

            Surface(
                color = if (isHassan) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = bubbleShape,
                tonalElevation = if (isHassan) 0.dp else 2.dp,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = if (isHassan) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = bubbleShape
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (displayText.isNotEmpty()) {
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isHassan) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    if (codeContent.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Code,
                                            contentDescription = "Website code",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "WEBSITE CODE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            MobileAccessHelper.copyToClipboard(context, "Website Code", codeContent)
                                        }) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = {
                                            MobileAccessHelper.shareText(context, codeContent, "Share Website Code")
                                        }) {
                                            Icon(Icons.Filled.Share, contentDescription = "Share code", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                Text(
                                    text = codeContent.take(360) + if (codeContent.length > 360) "\n..." else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!isHassan && (hasWhatsApp || hasGmail || hasCall)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (hasWhatsApp) {
                                AssistChip(
                                    onClick = { MobileAccessHelper.openWhatsApp(context, displayText) },
                                    label = { Text("Open WhatsApp") },
                                    leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                )
                            }
                            if (hasGmail) {
                                AssistChip(
                                    onClick = { MobileAccessHelper.openGmail(context, body = displayText) },
                                    label = { Text("Open Gmail") },
                                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                            if (hasCall) {
                                AssistChip(
                                    onClick = { MobileAccessHelper.openDialer(context) },
                                    label = { Text("Open Dialer") },
                                    leadingIcon = { Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }

                    if (!isHassan && !isStreaming && displayText.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onSpeak(displayText) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Filled.VolumeUp, contentDescription = "Read aloud", modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Speak", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(
                                onClick = {
                                    MobileAccessHelper.copyToClipboard(context, "Jarvis Reply", displayText)
                                    onShowToast("Copied!")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy reply", modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChipPill(label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50.dp),
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun ModernChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isListening: Boolean,
    isBusy: Boolean,
    onMicClick: () -> Unit,
    onSendClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .border(
                width = 1.dp,
                color = if (inputText.isNotBlank()) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "Message JARVIS...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_input_field")
                )
            }

            FilledIconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("voice_command_button"),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isListening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Voice command"
                )
            }

            FilledIconButton(
                onClick = onSendClick,
                enabled = inputText.isNotBlank() && !isBusy,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_chat_button"),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message"
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyBriefModalContent(
    huntedCount: Int,
    repliesHandled: Int,
    dealsClosed: Int,
    pendingWorkCount: Int,
    pendingApprovals: List<ClientLead>,
    onApproveDeal: (ClientLead) -> Unit,
    onOpenHunt: () -> Unit,
    onOpenClients: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "24/7 DAILY REPORT • MANSEHRA HQ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Aaj $huntedCount/10 clients hunt hue, $repliesHandled replies handle hue, $pendingWorkCount kaam pending hain, aur $dealsClosed deals final huin.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricPillCard("Hunted", "$huntedCount/10", Modifier.weight(1f))
            MetricPillCard("Replies", "$repliesHandled", Modifier.weight(1f))
            MetricPillCard("Closed", "$dealsClosed", Modifier.weight(1f))
        }

        if (pendingApprovals.isNotEmpty()) {
            Text(
                text = "Mode 3 • Deals Waiting for Your 'Haan'",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            pendingApprovals.forEach { lead ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Hassan, ${lead.name} ne ${lead.workTypeEnum().label} ke liye $${lead.priceUsd} me HAAN kar di. Final kar dun?",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Button(
                            onClick = { onApproveDeal(lead) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("Haan, Final Karo")
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onOpenHunt, modifier = Modifier.weight(1f)) {
                Text("Client Hunt")
            }
            OutlinedButton(onClick = onOpenClients, modifier = Modifier.weight(1f)) {
                Text("Client CRM")
            }
        }
    }
}

@Composable
private fun MetricPillCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
