package com.example.ui.screens

import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyStat
import com.example.data.model.HuntedLeadCandidate
import com.example.data.model.UiState
import com.example.data.model.WorkType
import com.example.util.MobileAccessHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HuntScreen(
    huntState: UiState<List<HuntedLeadCandidate>>,
    dailyStatState: UiState<DailyStat>,
    onHuntClients: (String, String, WorkType, Int) -> Unit,
    onSaveLead: (HuntedLeadCandidate, Boolean) -> Unit
) {
    val context = LocalContext.current
    val stat = (dailyStatState as? UiState.Success)?.data ?: DailyStat()
    val huntedToday = stat.huntedCount.toInt().coerceIn(0, 10)
    val remaining = (10 - huntedToday).coerceAtLeast(0)

    var selectedPlatform by remember { mutableStateOf("Instagram") }
    var selectedWorkType by remember { mutableStateOf(WorkType.VIDEO_EDITING) }
    var nicheInput by remember { mutableStateOf("Fitness Coaches & Real Estate Creators") }
    var batchCount by remember { mutableIntStateOf(3) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "hunt_header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "MODE 1 • CLIENT HUNT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Targeted Clients Pakrein (Max 10/Day)",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Har client ke liye alag personal message — zero spam.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "quota_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Aaj ka Daily Quota",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "$huntedToday / 10 Clients",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (remaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { huntedToday / 10f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (remaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        item(key = "hunt_controls") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Platform Chunein", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Google", "Instagram", "YouTube").forEach { platform ->
                            FilterChip(
                                selected = selectedPlatform == platform,
                                onClick = { selectedPlatform = platform },
                                label = { Text(platform) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    Text("Service & Rate", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorkType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedWorkType == type,
                                onClick = { selectedWorkType = type },
                                label = { Text("${type.label} ($${type.defaultPriceUsd})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = nicheInput,
                        onValueChange = { nicheInput = it },
                        label = { Text("Target Niche / Industry") },
                        placeholder = { Text("e.g. Podcasts, E-commerce Brands, Restaurants") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Batch Size", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 3, 5).forEach { count ->
                                FilterChip(
                                    selected = batchCount == count,
                                    onClick = { batchCount = count },
                                    label = { Text("$count") }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onHuntClients(selectedPlatform, nicheInput, selectedWorkType, batchCount)
                        },
                        enabled = remaining > 0 && huntState !is UiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("hunt_clients_button")
                    ) {
                        if (huntState is UiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Clients Dhoond Raha Hoon...")
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (remaining > 0) "Client Dhoondo & Pitch Banao" else "Aaj ka Quota Full (10/10)")
                        }
                    }
                }
            }
        }

        when (huntState) {
            is UiState.Loading -> {}
            is UiState.Error -> {
                item(key = "hunt_error") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = huntState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                if (huntState.data.isEmpty()) {
                    item(key = "hunt_empty") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Boss, niche aur platform select karke \"Client Dhoondo\" dabayein — main har prospect ke liye tailored English pitch tayyar kar dunga.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(huntState.data, key = { "${it.clientName}_${it.platform}" }) { candidate ->
                        HuntedLeadCard(
                            candidate = candidate,
                            onSendWhatsApp = {
                                MobileAccessHelper.openWhatsApp(
                                    context,
                                    candidate.personalizedEnglishPitch,
                                    candidate.contactHandle
                                )
                                onSaveLead(candidate, true)
                            },
                            onSendGmail = {
                                MobileAccessHelper.openGmail(
                                    context,
                                    recipientEmail = if (candidate.contactHandle.contains("@")) candidate.contactHandle else "",
                                    subject = "${candidate.workType.label} Proposal for ${candidate.clientName}",
                                    body = candidate.personalizedEnglishPitch
                                )
                                onSaveLead(candidate, true)
                            },
                            onCopyPitch = {
                                MobileAccessHelper.copyToClipboard(context, "Client Pitch", candidate.personalizedEnglishPitch)
                            },
                            onSaveOnly = { onSaveLead(candidate, false) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HuntedLeadCard(
    candidate: HuntedLeadCandidate,
    onSendWhatsApp: () -> Unit,
    onSendGmail: () -> Unit,
    onCopyPitch: () -> Unit,
    onSaveOnly: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
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
                        text = candidate.clientName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${candidate.platform} • ${candidate.contactHandle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${candidate.workType.label} • $${candidate.priceUsd}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Kyun fit hai: ${candidate.whyGoodFit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PERSONALIZED ENGLISH PITCH",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = onCopyPitch, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy pitch", modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        text = candidate.personalizedEnglishPitch,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onSendWhatsApp) {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp + Save")
                }
                OutlinedButton(onClick = onSendGmail) {
                    Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gmail + Save")
                }
                OutlinedButton(onClick = onSaveOnly) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Lead")
                }
            }
        }
    }
}
