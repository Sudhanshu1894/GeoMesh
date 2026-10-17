package com.example.resilentnet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resilentnet.data.network.NetworkDiagnostics
import com.example.resilentnet.ui.theme.ResilientCardBg
import com.example.resilentnet.ui.theme.ResilientCardBorder
import com.example.resilentnet.ui.theme.ResilientErrorRed
import com.example.resilentnet.ui.theme.ResilientInfoBlue
import com.example.resilentnet.ui.theme.ResilientMainBg
import com.example.resilentnet.ui.theme.ResilientPrimaryGreen
import com.example.resilentnet.ui.theme.ResilientTextMain
import com.example.resilentnet.ui.theme.ResilientTextSecondary
import com.example.resilentnet.ui.theme.ResilientWarningAmber
import com.example.resilentnet.ui.viewmodel.MeshViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val diagnostics by viewModel.diagnostics.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ResilientMainBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Column {
            Text(
                text = "Network Diagnostics",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ResilientTextMain
            )
            Text(
                text = "Real-time socket and protocol telemetry",
                fontSize = 12.sp,
                color = ResilientTextSecondary
            )
        }

        // Section 1: Socket & Interface Diagnostics
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SOCKET & SYSTEM STATE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ResilientTextSecondary,
                    letterSpacing = 0.5.sp
                )

                DiagRow(
                    label = "Engine State",
                    value = diagnostics.engineState,
                    valueColor = when (diagnostics.engineState) {
                        "RUNNING" -> ResilientPrimaryGreen
                        "PAUSED" -> ResilientWarningAmber
                        "ERROR" -> ResilientErrorRed
                        else -> ResilientTextSecondary
                    }
                )
                DiagRow(
                    label = "Local IPv4 Endpoint",
                    value = if (diagnostics.engineState == "RUNNING" || diagnostics.engineState == "PAUSED")
                        "${diagnostics.localIpAddress}:${diagnostics.listeningPort}"
                    else "Standby"
                )
                DiagRow(
                    label = "MulticastLock Status",
                    value = if (diagnostics.isMulticastLockHeld) "ACTIVE (Held)" else "INACTIVE",
                    valueColor = if (diagnostics.isMulticastLockHeld) ResilientPrimaryGreen else ResilientTextSecondary
                )
                DiagRow(
                    label = "Signal Simulation Mode",
                    value = if (diagnostics.isSignalEnabled) "NORMAL (Transmitting)" else "SIMULATED DROP (Paused)",
                    valueColor = if (diagnostics.isSignalEnabled) ResilientPrimaryGreen else ResilientWarningAmber
                )
                DiagRow(
                    label = "Device Battery Level",
                    value = "${diagnostics.batteryLevel}%",
                    valueColor = if (diagnostics.batteryLevel < 15) ResilientErrorRed else ResilientTextMain
                )
                if (diagnostics.lastError != null) {
                    DiagRow("Last Error", diagnostics.lastError!!, ResilientErrorRed)
                }
            }
        }

        // Section 2: Protocol Packet Counters
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PROTOCOL TELEMETRY (UDP PORT 9876)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ResilientTextSecondary,
                    letterSpacing = 0.5.sp
                )

                DiagRow("Total Packets Sent", "${diagnostics.packetsSentTotal}")
                DiagRow("Total Packets Received", "${diagnostics.packetsReceivedTotal}")
                HorizontalDivider(color = ResilientCardBorder, thickness = 0.5.dp)
                DiagRow("HELLO Discovery Beacons", "${diagnostics.helloPacketsCount}")
                DiagRow("DATA Message Packets", "${diagnostics.dataPacketsCount}")
                DiagRow("ACK Confirmation Packets", "${diagnostics.ackPacketsCount}")
                DiagRow("SYNC Bloom-Filter Packets", "${diagnostics.syncPacketsCount}")
                DiagRow("REQ History Requests", "${diagnostics.reqPacketsCount}")

                val lastPacketDate = if (diagnostics.lastPacketTimestamp > 0) {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(diagnostics.lastPacketTimestamp))
                } else "None yet"
                DiagRow("Last Datagram Timestamp", lastPacketDate)
            }
        }

        // Section 3: Mesh Routing & Queue
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TOPOLOGY & QUEUE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ResilientTextSecondary,
                    letterSpacing = 0.5.sp
                )

                DiagRow("Active Reachable Peers", "${diagnostics.activePeerCount}", ResilientPrimaryGreen)
                DiagRow("Total Discovered Nodes", "${diagnostics.totalKnownPeersCount}")
                DiagRow(
                    label = "Pending Offline Queue",
                    value = "${diagnostics.pendingBufferCount}",
                    valueColor = if (diagnostics.pendingBufferCount > 0) ResilientWarningAmber else ResilientTextMain
                )
            }
        }

        // Action: Copy Diagnostics to Clipboard
        Button(
            onClick = {
                val report = generateDiagnosticsReport(diagnostics, viewModel.myNodeId)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ResilientNet Diagnostics", report))
                Toast.makeText(context, "Diagnostics report copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ResilientPrimaryGreen),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Copy Diagnostics Report", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DiagRow(
    label: String,
    value: String,
    valueColor: Color = ResilientTextMain
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = ResilientTextSecondary
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

private fun generateDiagnosticsReport(diag: NetworkDiagnostics, nodeId: String): String {
    return """
        === RESILIENTNET DIAGNOSTICS REPORT ===
        Local Node ID: $nodeId
        Engine State: ${diag.engineState}
        Endpoint: ${diag.localIpAddress}:${diag.listeningPort}
        MulticastLock: ${diag.isMulticastLockHeld}
        Battery Level: ${diag.batteryLevel}%
        Active Peers: ${diag.activePeerCount} / ${diag.totalKnownPeersCount}
        Packets Sent: ${diag.packetsSentTotal}
        Packets Received: ${diag.packetsReceivedTotal}
        HELLO: ${diag.helloPacketsCount} | DATA: ${diag.dataPacketsCount} | ACK: ${diag.ackPacketsCount}
        SYNC: ${diag.syncPacketsCount} | REQ: ${diag.reqPacketsCount}
        Pending Buffer Queue: ${diag.pendingBufferCount}
        Last Error: ${diag.lastError ?: "None"}
        Timestamp: ${Date()}
    """.trimIndent()
}
