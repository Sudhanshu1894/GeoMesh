package com.example.resilentnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resilentnet.data.model.DeliveryStatus
import com.example.resilentnet.data.model.Message
import com.example.resilentnet.data.model.MessagePriority
import com.example.resilentnet.data.model.MessageScope
import com.example.resilentnet.ui.components.EmptyState
import com.example.resilentnet.ui.theme.ResilientCardBg
import com.example.resilentnet.ui.theme.ResilientCardBorder
import com.example.resilentnet.ui.theme.ResilientErrorRed
import com.example.resilentnet.ui.theme.ResilientInfoBlue
import com.example.resilentnet.ui.theme.ResilientInputBg
import com.example.resilentnet.ui.theme.ResilientMainBg
import com.example.resilentnet.ui.theme.ResilientPrimaryGreen
import com.example.resilentnet.ui.theme.ResilientSoftGreen
import com.example.resilentnet.ui.theme.ResilientTextMain
import com.example.resilentnet.ui.theme.ResilientTextSecondary
import com.example.resilentnet.ui.theme.ResilientWarningAmber
import com.example.resilentnet.ui.viewmodel.MeshViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.chatMessages.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val selectedPeerId by viewModel.selectedPeerId.collectAsState()

    var inputContent by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(MessagePriority.NORMAL) }
    var showTargetMenu by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Filter messages for current recipient or broadcast
    val filteredMessages = remember(messages, selectedPeerId) {
        if (selectedPeerId == null) {
            messages.filter { it.scope == MessageScope.BROADCAST }
        } else {
            messages.filter {
                (it.senderId == selectedPeerId && (it.scope == MessageScope.TARGETED || it.scope == MessageScope.LOCAL)) ||
                        (it.senderId == viewModel.myNodeId && it.targets.contains(selectedPeerId))
            }
        }
    }

    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ResilientMainBg)
            .imePadding()
    ) {
        // Chat Header: Recipient Selector & Clear History Action
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
            shape = RoundedCornerShape(0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .clickable { showTargetMenu = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CONVERSATION TARGET",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ResilientTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            val targetLabel = if (selectedPeerId == null) {
                                "⬡ Mesh Broadcast (All Nodes)"
                            } else {
                                val peer = peers.find { it.id == selectedPeerId }
                                "Private: ${peer?.displayName ?: selectedPeerId}"
                            }
                            Text(
                                text = "$targetLabel ▼",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResilientPrimaryGreen
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showTargetMenu,
                        onDismissRequest = { showTargetMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("⬡ Mesh Broadcast (All Nodes)", color = ResilientTextMain) },
                            onClick = {
                                viewModel.selectPeer(null)
                                showTargetMenu = false
                            }
                        )
                        peers.forEach { peer ->
                            DropdownMenuItem(
                                text = { Text("${peer.displayName} (${peer.id})", color = ResilientTextMain) },
                                onClick = {
                                    viewModel.selectPeer(peer.id)
                                    showTargetMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showClearConfirmDialog = true },
                    border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Clear", fontSize = 11.sp, color = ResilientTextSecondary)
                }
            }
        }

        // Message Feed
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            if (filteredMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = "💬",
                        title = "No messages in this channel",
                        description = "Send a message below. Messages are transmitted peer-to-peer over UDP without internet."
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
                ) {
                    items(filteredMessages, key = { it.id }) { msg ->
                        MessageBubbleItem(
                            message = msg,
                            isOutgoing = msg.senderId == viewModel.myNodeId
                        )
                    }
                }
            }
        }

        // Bottom Input and Priority Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
            shape = RoundedCornerShape(0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Priority Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Priority:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = ResilientTextSecondary
                    )
                    MessagePriority.values().forEach { priority ->
                        val isSelected = selectedPriority == priority
                        val (chipBg, chipText) = when (priority) {
                            MessagePriority.CRITICAL -> Pair(
                                if (isSelected) Color(0xFFFDE8E9) else ResilientInputBg,
                                ResilientErrorRed
                            )
                            MessagePriority.HIGH -> Pair(
                                if (isSelected) Color(0xFFFFF7E6) else ResilientInputBg,
                                ResilientWarningAmber
                            )
                            MessagePriority.NORMAL -> Pair(
                                if (isSelected) ResilientSoftGreen else ResilientInputBg,
                                ResilientPrimaryGreen
                            )
                            MessagePriority.LOW -> Pair(
                                if (isSelected) ResilientCardBorder else ResilientInputBg,
                                ResilientTextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(chipBg, RoundedCornerShape(6.dp))
                                .border(
                                    1.dp,
                                    if (isSelected) chipText else ResilientCardBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedPriority = priority }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = priority.name,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = chipText
                            )
                        }
                    }
                }

                // Text Input Field and Send Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputContent,
                        onValueChange = { inputContent = it },
                        placeholder = { Text("Type mesh message...", fontSize = 13.sp, color = ResilientTextSecondary) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ResilientCardBg,
                            unfocusedContainerColor = ResilientInputBg,
                            focusedBorderColor = ResilientPrimaryGreen,
                            unfocusedBorderColor = ResilientCardBorder
                        ),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 3
                    )

                    Button(
                        onClick = {
                            if (inputContent.isNotBlank()) {
                                viewModel.sendMessage(
                                    content = inputContent,
                                    priority = selectedPriority,
                                    targetPeerId = selectedPeerId
                                )
                                inputContent = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ResilientPrimaryGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Send", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Clear History Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Message History?", color = ResilientTextMain, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all stored local messages. This action cannot be undone.", color = ResilientTextSecondary, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ResilientErrorRed)
                ) {
                    Text("Clear All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = ResilientTextSecondary)
                }
            },
            containerColor = ResilientCardBg,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun MessageBubbleItem(
    message: Message,
    isOutgoing: Boolean
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = timeFormat.format(Date(message.timestamp))

    val isEmergency = message.urgent || message.priority == MessagePriority.CRITICAL

    val containerBg = when {
        isEmergency -> Color(0xFFFDE8E9) // Soft red
        isOutgoing -> ResilientSoftGreen  // Soft green
        else -> ResilientInputBg          // Neutral light gray
    }

    val borderColor = when {
        isEmergency -> ResilientErrorRed
        isOutgoing -> ResilientPrimaryGreen.copy(alpha = 0.35f)
        else -> ResilientCardBorder
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            colors = CardDefaults.cardColors(containerColor = containerBg),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Header: Sender + Priority Badge + Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isOutgoing) "You (${message.senderId})" else "Node ${message.senderId}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOutgoing) ResilientPrimaryGreen else ResilientInfoBlue
                        )
                        if (message.priority != MessagePriority.NORMAL) {
                            Text(
                                text = "[${message.priority.name}]",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isEmergency) ResilientErrorRed else ResilientWarningAmber
                            )
                        }
                    }

                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = ResilientTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Content
                Text(
                    text = message.content,
                    fontSize = 13.sp,
                    color = ResilientTextMain,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Footer: TTL and Delivery Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TTL: ${message.ttl}",
                        fontSize = 10.sp,
                        color = ResilientTextSecondary
                    )

                    if (isOutgoing) {
                        val (statusText, statusColor) = when (message.deliveryStatus) {
                            DeliveryStatus.QUEUED -> "⏳ Queued (Offline)" to ResilientWarningAmber
                            DeliveryStatus.SENT -> "✓ Sent" to ResilientTextSecondary
                            DeliveryStatus.DELIVERED -> "✓✓ Delivered" to ResilientPrimaryGreen
                            DeliveryStatus.RELAYED -> "↻ Relayed" to ResilientInfoBlue
                            DeliveryStatus.FAILED -> "✕ Failed" to ResilientErrorRed
                        }

                        Text(
                            text = statusText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}
