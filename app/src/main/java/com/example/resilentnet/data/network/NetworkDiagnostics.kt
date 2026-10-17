package com.example.resilentnet.data.network

/**
 * Real-time network telemetry and diagnostics data model.
 */
data class NetworkDiagnostics(
    val engineState: String = "STOPPED", // STOPPED, STARTING, RUNNING, PAUSED, ERROR
    val localIpAddress: String = "Unknown",
    val listeningPort: Int = 9876,
    val isMulticastLockHeld: Boolean = false,
    val isSignalEnabled: Boolean = true,
    val activePeerCount: Int = 0,
    val totalKnownPeersCount: Int = 0,
    val packetsSentTotal: Long = 0,
    val packetsReceivedTotal: Long = 0,
    val helloPacketsCount: Long = 0,
    val dataPacketsCount: Long = 0,
    val ackPacketsCount: Long = 0,
    val syncPacketsCount: Long = 0,
    val reqPacketsCount: Long = 0,
    val lastPacketTimestamp: Long = 0,
    val lastError: String? = null,
    val pendingBufferCount: Int = 0,
    val batteryLevel: Int = 100
)
