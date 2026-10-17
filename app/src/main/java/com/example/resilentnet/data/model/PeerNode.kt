package com.example.resilentnet.data.model

/**
 * Representation of an active or previously discovered peer on the mesh.
 */
data class PeerNode(
    val id: String,
    val displayName: String = "Node-$id",
    val ipAddress: String,
    val port: Int = 9876,
    var lastSeenTimestamp: Long = System.currentTimeMillis(),
    var isOnline: Boolean = true,
    var role: String = "USER",
    var priority: String = "NORMAL",
    var batteryLevel: Int = 100
) {
    val endpointDescription: String
        get() = "$ipAddress:$port"
}
