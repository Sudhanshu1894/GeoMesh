package com.example.resilentnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resilentnet.ui.components.MetricCard
import com.example.resilentnet.ui.components.StatusBadge
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

@Composable
fun DashboardScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val diagnostics by viewModel.diagnostics.collectAsState()
    val displayName by viewModel.displayName.collectAsState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showSosDialog by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }
    var sosReason by remember { mutableStateOf("") }

    val isRunning = diagnostics.engineState == "RUNNING" || diagnostics.engineState == "PAUSED"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ResilientMainBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Clean App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⬡",
                        fontSize = 20.sp,
                        color = ResilientPrimaryGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ResilientNet",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResilientTextMain,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    text = "Resilient peer-to-peer communication",
                    fontSize = 12.sp,
                    color = ResilientTextSecondary
                )
            }

            // Diagnostics Button + Engine State Badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showDiagnostics = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ResilientTextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "📊", fontSize = 14.sp)
                }
                // Real Engine State Badge
                StatusBadge(state = diagnostics.engineState)
            }
        }

        // Section 2: Device Identity Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "THIS DEVICE IDENTITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ResilientTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    OutlinedButton(
                        onClick = {
                            tempName = displayName
                            showEditNameDialog = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ResilientPrimaryGreen),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(text = "Edit Name", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

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
                            text = "Node ID:",
                            fontSize = 13.sp,
                            color = ResilientTextSecondary
                        )
                        Text(
                            text = viewModel.myNodeId,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ResilientTextMain
                        )
                    }
                    Text(
                        text = if (isRunning) "Port: ${diagnostics.listeningPort}" else "Port: 9876",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRunning) ResilientPrimaryGreen else ResilientTextSecondary
                    )
                }

                Text(
                    text = "Display Name: $displayName",
                    fontSize = 13.sp,
                    color = ResilientTextMain
                )
            }
        }

        // Section 3: Network Operations Control Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "NETWORK CONTROLS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ResilientTextSecondary,
                    letterSpacing = 0.5.sp
                )

                Button(
                    onClick = {
                        if (isRunning) {
                            viewModel.stopNetworking(context)
                        } else {
                            viewModel.startNetworking(context)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) ResilientInputBg else ResilientPrimaryGreen
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = if (isRunning) androidx.compose.foundation.BorderStroke(1.dp, ResilientErrorRed.copy(alpha = 0.5f)) else null
                ) {
                    Text(
                        text = if (isRunning) "STOP MESH NETWORK" else "START MESH NETWORK",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isRunning) ResilientErrorRed else Color.White
                    )
                }

                // Error feedback if startup fails
                if (diagnostics.lastError != null) {
                    Text(
                        text = "Error: ${diagnostics.lastError}",
                        fontSize = 12.sp,
                        color = ResilientErrorRed
                    )
                }

                // Signal Simulation testing switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ResilientInputBg, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Simulate Signal Drop (Offline Test)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ResilientTextMain
                        )
                        Text(
                            text = if (diagnostics.isSignalEnabled)
                                "Normal — Broadcasting and receiving datagrams"
                            else
                                "Paused — Simulates isolation; outgoing traffic buffers",
                            fontSize = 11.sp,
                            color = if (diagnostics.isSignalEnabled) ResilientPrimaryGreen else ResilientWarningAmber,
                            lineHeight = 15.sp
                        )
                    }

                    Switch(
                        checked = diagnostics.isSignalEnabled,
                        onCheckedChange = { viewModel.toggleSignal() },
                        enabled = isRunning,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ResilientPrimaryGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = ResilientCardBorder
                        )
                    )
                }
            }
        }

        // Section 4: 2x2 Summary Metric Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "ACTIVE PEERS",
                value = if (isRunning) "${diagnostics.activePeerCount}" else "0",
                subtitle = if (isRunning) "${diagnostics.totalKnownPeersCount} total discovered" else "Network stopped",
                accentColor = if (diagnostics.activePeerCount > 0) ResilientPrimaryGreen else ResilientTextMain,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "PENDING MESSAGES",
                value = "${diagnostics.pendingBufferCount}",
                subtitle = if (diagnostics.pendingBufferCount > 0) "Awaiting delivery" else "Buffer empty",
                accentColor = if (diagnostics.pendingBufferCount > 0) ResilientWarningAmber else ResilientTextMain,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "DEVICE BATTERY",
                value = "${diagnostics.batteryLevel}%",
                subtitle = if (diagnostics.batteryLevel < 15) "Throttle active (<15%)" else "Normal operation",
                accentColor = if (diagnostics.batteryLevel < 15) ResilientErrorRed else ResilientTextMain,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "LOCAL IP & PORT",
                value = if (isRunning) ":${diagnostics.listeningPort}" else "Standby",
                subtitle = if (isRunning) diagnostics.localIpAddress else "Not connected",
                accentColor = ResilientTextMain,
                modifier = Modifier.weight(1f)
            )
        }

        // Section 5: Primary Action (Emergency SOS)
        Button(
            onClick = { showSosDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ResilientErrorRed),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "🚨 BROADCAST EMERGENCY SOS",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
    }

    // Edit Display Name Dialog
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(text = "Change Display Name", color = ResilientTextMain, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ResilientCardBg,
                        unfocusedContainerColor = ResilientInputBg,
                        focusedBorderColor = ResilientPrimaryGreen,
                        unfocusedBorderColor = ResilientCardBorder
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDisplayName(tempName)
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ResilientPrimaryGreen)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = ResilientTextSecondary)
                }
            },
            containerColor = ResilientCardBg,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Emergency SOS Confirmation Dialog
    if (showSosDialog) {
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            title = {
                Text(
                    text = "Confirm Emergency Broadcast?",
                    color = ResilientErrorRed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This will broadcast a high-priority CRITICAL alert to all reachable nodes on the local mesh. Please use only for genuine emergencies.",
                        color = ResilientTextMain,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = sosReason,
                        onValueChange = { sosReason = it },
                        label = { Text("Details / Location (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ResilientCardBg,
                            unfocusedContainerColor = ResilientInputBg,
                            focusedBorderColor = ResilientErrorRed,
                            unfocusedBorderColor = ResilientCardBorder
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendEmergencySos(sosReason.ifBlank { null })
                        showSosDialog = false
                        sosReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ResilientErrorRed)
                ) {
                    Text("BROADCAST SOS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosDialog = false }) {
                    Text("Cancel", color = ResilientTextSecondary)
                }
            },
            containerColor = ResilientCardBg,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Diagnostics Full-Screen Dialog
    if (showDiagnostics) {
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\uD83D\uDCCA Network Diagnostics",
                        color = ResilientTextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                DiagnosticsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.height(480.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDiagnostics = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ResilientPrimaryGreen)
                ) {
                    Text("Close", color = Color.White)
                }
            },
            containerColor = ResilientCardBg,
            shape = RoundedCornerShape(12.dp)
        )
    }
}
