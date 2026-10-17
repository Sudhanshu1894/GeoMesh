package com.example.resilentnet.domain

import android.content.Context
import com.example.resilentnet.data.model.GeoCodec
import com.example.resilentnet.data.model.HazardMarker
import com.example.resilentnet.data.model.HazardType
import com.example.resilentnet.data.model.Message
import com.example.resilentnet.data.model.MessagePriority
import com.example.resilentnet.data.model.PeerNode
import com.example.resilentnet.data.network.MeshSocketManager
import com.example.resilentnet.data.network.NetworkDiagnostics
import com.example.resilentnet.data.storage.MessageStore
import com.example.resilentnet.data.storage.NodeIdentityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Repository layer exposing clean StateFlows for Jetpack Compose UI.
 * Coordinates persistence, network state, and battery status.
 */
class MeshRepository(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val identityStore = NodeIdentityStore(context)
    val messageStore = MessageStore(context)
    val socketManager = MeshSocketManager(context, identityStore, messageStore)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // Chat messages: excludes @HAZARD| wire messages so raw codes don't clutter chat
    private val _chatMessages = MutableStateFlow<List<Message>>(emptyList())
    val chatMessages: StateFlow<List<Message>> = _chatMessages.asStateFlow()

    // Parsed hazard markers derived from @HAZARD| messages
    private val _hazards = MutableStateFlow<List<HazardMarker>>(emptyList())
    val hazards: StateFlow<List<HazardMarker>> = _hazards.asStateFlow()

    private val _peers = MutableStateFlow<List<PeerNode>>(emptyList())
    val peers: StateFlow<List<PeerNode>> = _peers.asStateFlow()

    private val _systemAlerts = MutableSharedFlow<String>(replay = 1)
    val systemAlerts: SharedFlow<String> = _systemAlerts.asSharedFlow()

    val diagnostics: StateFlow<NetworkDiagnostics> = socketManager.diagnostics

    init {
        // Load initial messages from persistent disk cache
        _messages.value = messageStore.getAllMessages()

        // Wire socket callbacks
        socketManager.onMessageReceived = { msg ->
            refreshMessages()
        }

        socketManager.onPeerDiscovered = { peer ->
            refreshPeers()
        }

        socketManager.onPeerLost = { peerId ->
            refreshPeers()
        }

        socketManager.onDeliveryAck = { messageId, peerId ->
            refreshMessages()
        }

        socketManager.onSystemNotification = { text ->
            scope.launch {
                _systemAlerts.emit(text)
            }
        }

        // Keep peer relative timers and state synchronized
        scope.launch {
            while (isActive) {
                delay(3000)
                refreshPeers()
            }
        }
    }

    private fun refreshMessages() {
        val allMessages = messageStore.getAllMessages()
        _messages.value = allMessages
        _chatMessages.value = allMessages.filter { !GeoCodec.isHazardMessage(it.content) }
        _hazards.value = allMessages.mapNotNull { GeoCodec.parseHazard(it) }
    }

    private fun refreshPeers() {
        _peers.value = socketManager.knownPeers.values.toList().sortedByDescending { it.isOnline }
    }

    fun startMesh(): Boolean {
        val started = socketManager.start()
        refreshPeers()
        return started
    }

    fun stopMesh() {
        socketManager.stop()
        refreshPeers()
    }

    suspend fun sendMessage(
        content: String,
        targetPeerId: String? = null,
        priority: MessagePriority = MessagePriority.NORMAL
    ): Message {
        val msg = socketManager.sendMessage(content, targetPeerId, priority)
        refreshMessages()
        return msg
    }

    suspend fun sendEmergencySos(additionalInfo: String? = null): Message {
        val sosText = if (!additionalInfo.isNullOrBlank()) {
            "SOS EMERGENCY: $additionalInfo (Node ${identityStore.nodeId})"
        } else {
            "SOS EMERGENCY: Need immediate assistance at Node ${identityStore.nodeId}!"
        }
        return sendMessage(
            content = sosText,
            targetPeerId = null, // Broadcast to entire mesh
            priority = MessagePriority.CRITICAL
        )
    }

    fun toggleSignal() {
        socketManager.toggleSignal()
    }

    /**
     * Broadcast a spatial hazard report to the entire mesh.
     * Piggybacks on existing UDP broadcast + Bloom filter sync pipeline.
     */
    suspend fun broadcastHazard(
        type: HazardType,
        points: List<Pair<Double, Double>>,
        description: String
    ): Message {
        val content = GeoCodec.serializeHazard(type, points, description)
        return sendMessage(
            content = content,
            targetPeerId = null, // Broadcast to entire mesh
            priority = MessagePriority.HIGH
        )
    }

    fun updateDisplayName(name: String) {
        identityStore.displayName = name
    }

    fun updateBattery(level: Int) {
        socketManager.updateBattery(level)
    }

    fun clearChatHistory() {
        messageStore.clearAll()
        refreshMessages()
    }
}
