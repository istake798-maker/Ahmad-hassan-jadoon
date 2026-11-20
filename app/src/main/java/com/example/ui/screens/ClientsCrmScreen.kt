package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ClientLead
import com.example.data.model.ClientReplyAnalysis
import com.example.data.model.ClientStatus
import com.example.data.model.UiState
import com.example.data.model.WorkType
import com.example.util.MobileAccessHelper

private enum class ClientFilter(val label: String) {
    ALL("Sab Clients"),
    APPROVAL("Haan Chahiye"),
    ACTIVE("Active"),
    CLOSED("Closed / Done")
}

@Composable
fun ClientsCrmScreen(
    clientsState: UiState<List<ClientLead>>,
    handleAnalysisState: UiState<ClientReplyAnalysis>?,
    onAddManualClient: (String, WorkType, Int, String, String, String) -> Unit,
    onAnalyzeClientReply: (ClientLead, String) -> Unit,
    onClearHandleAnalysis: () -> Unit,
    onApproveDeal: (ClientLead) -> Unit,
    onCompleteDeal: (ClientLead) -> Unit,
    onMarkPitched: (ClientLead) -> Unit,
    onDeleteClient: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(ClientFilter.ALL) }
    var showAddDialog by remember { mutableStateOf(false) }
    var activeHandleClient by remember { mutableStateOf<ClientLead?>(null) }

    val clients = (clientsState as? UiState.Success)?.data.orEmpty()
    val filteredClients = remember(clients, selectedFilter) {
        when (selectedFilter) {
            ClientFilter.ALL -> clients
            ClientFilter.APPROVAL -> clients.filter { it.statusEnum() == ClientStatus.AWAITING_APPROVAL }
            ClientFilter.ACTIVE -> clients.filter {
                it.statusEnum() in listOf(
                    ClientStatus.HUNTED,
                    ClientStatus.PITCHED,
                    ClientStatus.IN_DISCUSSION
                )
            }
            ClientFilter.CLOSED -> clients.filter {
                it.statusEnum() == ClientStatus.CLOSED_WON || it.statusEnum() == ClientStatus.COMPLETED
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_client_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add new client")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "crm_header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "MODE 2 & 3 • HANDLE + CLOSE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Client CRM & Deal Approval",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Video Editing $30 • Designing $20 • Website $80",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            item(key = "crm_filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClientFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }

            when (clientsState) {
                is UiState.Loading -> {
                    item(key = "crm_loading") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item(key = "crm_error") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = clientsState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (filteredClients.isEmpty()) {
                        item(key = "crm_empty") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Abhi is filter me koi client nahi hai. Hunt tab se client pakrein ya '+' daba ke manually add karein.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredClients, key = { it.id }) { client ->
                            ClientCrmCard(
                                client = client,
                                onHandleClick = {
                                    onClearHandleAnalysis()
                                    activeHandleClient = client
                                },
                                onApproveDeal = { onApproveDeal(client) },
                                onCompleteDeal = { onCompleteDeal(client) },
                                onMarkPitched = { onMarkPitched(client) },
                                onOpenWhatsApp = {
                                    val msg = client.draftedReply.ifBlank { client.outreachMessage }
                                    MobileAccessHelper.openWhatsApp(context, msg, client.contactHandle)
                                },
                                onOpenGmail = {
                                    val msg = client.draftedReply.ifBlank { client.outreachMessage }
                                    MobileAccessHelper.openGmail(
                                        context,
                                        recipientEmail = if (client.contactHandle.contains("@")) client.contactHandle else "",
                                        subject = "${client.workTypeEnum().label} Project",
                                        body = msg
                                    )
                                },
                                onCall = {
                                    MobileAccessHelper.openDialer(context, client.contactHandle)
                                },
                                onDelete = { onDeleteClient(client.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddClientDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, workType, price, platform, handle, notes ->
                onAddManualClient(name, workType, price, platform, handle, notes)
                showAddDialog = false
            }
        )
    }

    activeHandleClient?.let { targetClient ->
        HandleClientReplyDialog(
            client = targetClient,
            analysisState = handleAnalysisState,
            onDismiss = {
                onClearHandleAnalysis()
                activeHandleClient = null
            },
            onAnalyze = { msg -> onAnalyzeClientReply(targetClient, msg) },
            onApproveDeal = {
                onApproveDeal(targetClient)
                onClearHandleAnalysis()
                activeHandleClient = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClientCrmCard(
    client: ClientLead,
    onHandleClick: () -> Unit,
    onApproveDeal: () -> Unit,
    onCompleteDeal: () -> Unit,
    onMarkPitched: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenGmail: () -> Unit,
    onCall: () -> Unit,
    onDelete: () -> Unit
) {
    val status = client.statusEnum()
    val statusColor = when (status) {
        ClientStatus.AWAITING_APPROVAL -> MaterialTheme.colorScheme.secondary
        ClientStatus.CLOSED_WON, ClientStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (status == ClientStatus.AWAITING_APPROVAL) 1.5.dp else 1.dp,
                color = if (status == ClientStatus.AWAITING_APPROVAL) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.large
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${client.workTypeEnum().label} • $${client.priceUsd} • ${client.platform}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (client.contactHandle.isNotBlank()) {
                        Text(
                            text = client.contactHandle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = statusColor.copy(alpha = 0.16f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = status.romanLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete client",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (client.lastClientMessage.isNotBlank()) {
                Text(
                    text = "Client: \"${client.lastClientMessage}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (client.draftedReply.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Jarvis Draft: ${client.draftedReply}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (status == ClientStatus.AWAITING_APPROVAL) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Hassan, ${client.name} ne ${client.workTypeEnum().label} ke liye $${client.priceUsd} me HAAN kar di. Final kar dun?",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Button(
                            onClick = onApproveDeal,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.testTag("approve_deal_button_${client.id}")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Haan, Deal Final Karo")
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (status != ClientStatus.COMPLETED) {
                    Button(
                        onClick = onHandleClick,
                        modifier = Modifier.testTag("handle_client_button_${client.id}")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Handle Message")
                    }
                }
                if (status == ClientStatus.HUNTED) {
                    OutlinedButton(onClick = onMarkPitched) {
                        Text("Mark Pitched")
                    }
                }
                if (status == ClientStatus.CLOSED_WON) {
                    OutlinedButton(onClick = onCompleteDeal) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mark Completed")
                    }
                }
                IconButton(onClick = onOpenWhatsApp) {
                    Icon(Icons.Filled.Send, contentDescription = "Send on WhatsApp", tint = MaterialTheme.colorScheme.tertiary)
                }
                IconButton(onClick = onOpenGmail) {
                    Icon(Icons.Filled.Email, contentDescription = "Send on Gmail", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onCall) {
                    Icon(Icons.Filled.Call, contentDescription = "Call client", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddClientDialog(
    onDismiss: () -> Unit,
    onSave: (String, WorkType, Int, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var workType by remember { mutableStateOf(WorkType.WEBSITE) }
    var priceText by remember { mutableStateOf(WorkType.WEBSITE.defaultPriceUsd.toString()) }
    var platform by remember { mutableStateOf("WhatsApp") }
    var handle by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Naya Client Record Add Karein") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Client Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WorkType.entries.forEach { type ->
                        FilterChip(
                            selected = workType == type,
                            onClick = {
                                workType = type
                                priceText = type.defaultPriceUsd.toString()
                            },
                            label = { Text("${type.label} $${type.defaultPriceUsd}") }
                        )
                    }
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Price (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = platform,
                    onValueChange = { platform = it },
                    label = { Text("Platform (WhatsApp / Gmail / Instagram)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("Phone / Email / Handle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceText.toIntOrNull()?.coerceAtLeast(1) ?: workType.defaultPriceUsd
                    onSave(name, workType, price, platform, handle, notes)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save Client")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun HandleClientReplyDialog(
    client: ClientLead,
    analysisState: UiState<ClientReplyAnalysis>?,
    onDismiss: () -> Unit,
    onAnalyze: (String) -> Unit,
    onApproveDeal: () -> Unit
) {
    val context = LocalContext.current
    var incomingMessage by remember { mutableStateOf(client.lastClientMessage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mode 2 • ${client.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Client ka message paste karein — Jarvis kaam samajh ke Professional English reply banayega aur agar client ne HAAN ki to aapse poochayga.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = incomingMessage,
                    onValueChange = { incomingMessage = it },
                    label = { Text("Client ka message") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                when (analysisState) {
                    is UiState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Samajh raha hoon Boss...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is UiState.Error -> {
                        Text(
                            text = analysisState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is UiState.Success -> {
                        val res = analysisState.data
                        Surface(
                            color = if (res.clientAgreed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = res.romanUrduSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "English Reply:\n${res.englishReply}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = {
                                        MobileAccessHelper.copyToClipboard(context, "Reply", res.englishReply)
                                    }) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Copy")
                                    }
                                    if (!res.clientAgreed) {
                                        TextButton(onClick = {
                                            MobileAccessHelper.openWhatsApp(context, res.englishReply, client.contactHandle)
                                        }) {
                                            Text("WhatsApp")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    null -> {}
                }
            }
        },
        confirmButton = {
            val agreed = (analysisState as? UiState.Success)?.data?.clientAgreed == true
            if (agreed) {
                Button(
                    onClick = onApproveDeal,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text("Haan, Deal Final Karo")
                }
            } else {
                Button(
                    onClick = { onAnalyze(incomingMessage) },
                    enabled = incomingMessage.isNotBlank() && analysisState !is UiState.Loading
                ) {
                    Text("Draft English Reply")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
