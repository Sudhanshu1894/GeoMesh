package com.example.resilentnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resilentnet.data.model.PeerNode
import com.example.resilentnet.ui.components.EmptyState
import com.example.resilentnet.ui.theme.ResilientCardBg
import com.example.resilentnet.ui.theme.ResilientCardBorder
import com.example.resilentnet.ui.theme.ResilientMainBg
import com.example.resilentnet.ui.theme.ResilientPrimaryGreen
import com.example.resilentnet.ui.theme.ResilientSoftGreen
import com.example.resilentnet.ui.theme.ResilientTextMain
import com.example.resilentnet.ui.theme.ResilientTextSecondary
import com.example.resilentnet.ui.viewmodel.MeshViewModel

@Composable
fun PeersScreen(
    viewModel: MeshViewModel,
    onNavigateToChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val peers by viewModel.peers.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val isRunning = diagnostics.engineState == "RUNNING" || diagnostics.engineState == "PAUSED"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ResilientMainBg)
            .padding(16.dp)
    ) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Nearby Peers",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResilientTextMain
                )
                Text(
                    text = "Discovered nodes on UDP Port 9876",
                    fontSize = 12.sp,
                    color = ResilientTextSecondary
                )
            }

            if (isRunning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .background(ResilientSoftGreen, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 2.dp,
                        color = ResilientPrimaryGreen
                    )
                    Text(
                        text = "Listening",
                        fontSize = 11.sp,
                        color = ResilientPrimaryGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (peers.isEmpty()) {
            EmptyState(
                icon = "👥",
                title = "No nearby devices found",
                description = if (!isRunning)
                    "Mesh networking is currently stopped. Go to the Dashboard and start the network to begin discovery."
                else
                    "Start networking on another device and ensure both devices can communicate over the same local network.",
                troubleshootingHints = listOf(
                    "Ensure both devices are connected to the same Wi-Fi network or mobile hotspot.",
                    "Verify the companion application or laptop is running and listening on UDP port 9876.",
                    "Check if 'Client Isolation' or 'AP Isolation' is enabled in your router settings (mobile hotspot bypasses this)."
                )
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(peers, key = { it.id }) { peer ->
                    PeerCardItem(
                        peer = peer,
                        onChatClick = {
                            viewModel.selectPeer(peer.id)
                            onNavigateToChat()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PeerCardItem(
    peer: PeerNode,
    onChatClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Online/Offline status dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (peer.isOnline) ResilientPrimaryGreen else ResilientTextSecondary.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )

                    Text(
                        text = peer.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResilientTextMain
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "ID: ${peer.id}  ·  ${peer.endpointDescription}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ResilientTextSecondary
                )

                val secondsAgo = (System.currentTimeMillis() - peer.lastSeenTimestamp) / 1000
                Text(
                    text = if (peer.isOnline) "Active (seen ${secondsAgo}s ago)" else "Offline (${secondsAgo}s ago)",
                    fontSize = 11.sp,
                    color = if (peer.isOnline) ResilientPrimaryGreen else ResilientTextSecondary
                )
            }

            Button(
                onClick = onChatClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ResilientSoftGreen,
                    contentColor = ResilientPrimaryGreen
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "\uD83D\uDCAC 1-on-1",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
