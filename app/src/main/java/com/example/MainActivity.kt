package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.signOut
import com.example.data.model.ClientStatus
import com.example.data.model.UiState
import com.example.data.repository.JarvisAiRepository
import com.example.data.repository.JarvisFirestoreRepository
import com.example.data.repository.SettingsPreferencesRepository
import com.example.ui.JarvisViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BrainSettingsScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ClientsCrmScreen
import com.example.ui.screens.HuntScreen
import com.example.ui.theme.JarvisUltraTheme
import com.example.util.VoiceController
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            JarvisUltraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

private enum class JarvisTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CHAT("Chat", Icons.Filled.Chat, Icons.Outlined.Chat, "tab_command"),
    HUNT("Hunt", Icons.Filled.TravelExplore, Icons.Outlined.TravelExplore, "tab_hunt"),
    CLIENTS("Clients", Icons.Filled.BusinessCenter, Icons.Outlined.BusinessCenter, "tab_clients"),
    BRAIN("Brain", Icons.Filled.Psychology, Icons.Outlined.Psychology, "tab_brain")
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            JarvisWorkspace(
                currentUserId = user.uid,
                userEmail = user.email
            )
        }
    }
}

@Composable
private fun JarvisWorkspace(
    currentUserId: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: JarvisViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                JarvisViewModel(
                    firestoreRepo = JarvisFirestoreRepository(db),
                    aiRepo = JarvisAiRepository(),
                    prefsRepo = SettingsPreferencesRepository(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val voiceController = remember(context) { VoiceController(context) }
    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    val clientsState by viewModel.clientsState.collectAsStateWithLifecycle()
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val dailyStatState by viewModel.dailyStatState.collectAsStateWithLifecycle()
    val voiceReplyEnabled by viewModel.voiceReplyEnabled.collectAsStateWithLifecycle()
    val groqApiKey by viewModel.groqApiKey.collectAsStateWithLifecycle()
    val activeBrainLabel by viewModel.activeBrainLabel.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val isChatBusy by viewModel.isChatBusy.collectAsStateWithLifecycle()
    val huntState by viewModel.huntState.collectAsStateWithLifecycle()
    val handleAnalysisState by viewModel.handleAnalysisState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.speakEvents.collect { text ->
            voiceController.speak(text)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(JarvisTab.CHAT) }

    if (currentTab != JarvisTab.CHAT) {
        BackHandler {
            currentTab = JarvisTab.CHAT
        }
    }

    val pendingApprovalCount = remember(clientsState) {
        (clientsState as? UiState.Success)?.data?.count {
            it.statusEnum() == ClientStatus.AWAITING_APPROVAL
        } ?: 0
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    FloatingMobileBottomDock(
                        currentTab = currentTab,
                        pendingApprovalCount = pendingApprovalCount,
                        onSelectTab = { currentTab = it }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        JarvisTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                modifier = Modifier.testTag(tab.testTag),
                                icon = {
                                    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
                                    if (tab == JarvisTab.CLIENTS && pendingApprovalCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                                    Text("$pendingApprovalCount")
                                                }
                                            }
                                        ) {
                                            Icon(icon, contentDescription = tab.label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentTab) {
                        JarvisTab.CHAT -> ChatScreen(
                            messagesState = messagesState,
                            clientsState = clientsState,
                            dailyStatState = dailyStatState,
                            streamingReply = streamingReply,
                            isChatBusy = isChatBusy,
                            activeBrainLabel = activeBrainLabel,
                            voiceReplyEnabled = voiceReplyEnabled,
                            voiceController = voiceController,
                            onToggleVoiceReply = viewModel::setVoiceReplyEnabled,
                            onSendMessage = viewModel::sendChatMessage,
                            onApproveDeal = viewModel::approveDealClose,
                            onNavigateToHunt = { currentTab = JarvisTab.HUNT },
                            onNavigateToClients = { currentTab = JarvisTab.CLIENTS },
                            onShowToast = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )

                        JarvisTab.HUNT -> HuntScreen(
                            huntState = huntState,
                            dailyStatState = dailyStatState,
                            onHuntClients = viewModel::huntClients,
                            onSaveLead = viewModel::saveHuntedLeadToCrm
                        )

                        JarvisTab.CLIENTS -> ClientsCrmScreen(
                            clientsState = clientsState,
                            handleAnalysisState = handleAnalysisState,
                            onAddManualClient = viewModel::addManualClient,
                            onAnalyzeClientReply = viewModel::handleClientMessage,
                            onClearHandleAnalysis = viewModel::clearHandleAnalysis,
                            onApproveDeal = viewModel::approveDealClose,
                            onCompleteDeal = viewModel::completeDeal,
                            onMarkPitched = viewModel::markPitched,
                            onDeleteClient = viewModel::deleteClient
                        )

                        JarvisTab.BRAIN -> BrainSettingsScreen(
                            userEmail = userEmail,
                            activeBrainLabel = activeBrainLabel,
                            voiceReplyEnabled = voiceReplyEnabled,
                            groqApiKey = groqApiKey,
                            memoriesState = memoriesState,
                            onToggleVoiceReply = viewModel::setVoiceReplyEnabled,
                            onSaveGroqKey = viewModel::setGroqApiKey,
                            onAddMemory = viewModel::addMemory,
                            onDeleteMemory = viewModel::deleteMemory,
                            onClearChat = viewModel::clearChat,
                            onSignOut = {
                                voiceController.stopSpeaking()
                                signOut(credentialManager, scope)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingMobileBottomDock(
    currentTab: JarvisTab,
    pendingApprovalCount: Int,
    onSelectTab: (JarvisTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                JarvisTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            )
                            .clickable { onSelectTab(tab) }
                            .padding(vertical = 8.dp)
                            .testTag(tab.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (tab == JarvisTab.CLIENTS && pendingApprovalCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                            Text("$pendingApprovalCount")
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = tab.label,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
