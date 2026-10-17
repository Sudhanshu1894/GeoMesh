package com.example.resilentnet.data.network

import android.content.Context
import android.util.Log
import com.example.resilentnet.data.model.DeliveryStatus
import com.example.resilentnet.data.model.Message
import com.example.resilentnet.data.model.MessagePriority
import com.example.resilentnet.data.model.MessageScope
import com.example.resilentnet.data.model.NetworkPacket
import com.example.resilentnet.data.model.PacketType
import com.example.resilentnet.data.model.PeerNode
import com.example.resilentnet.data.protocol.BloomFilter
import com.example.resilentnet.data.storage.MessageStore
import com.example.resilentnet.data.storage.NodeIdentityStore
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Core UDP Socket Engine for ResilientNet running on Port 9876.
 * Provides peer discovery, reliable messaging, epidemic Bloom-filter sync,
 * TTL-bounded relaying, and store-and-forward buffering.
 */
class MeshSocketManager(
    private val context: Context,
    private val identityStore: NodeIdentityStore,
    private val messageStore: MessageStore
) {
    private val tag = "MeshSocketManager"
    val port = 9876
    private val gson = Gson()

    private val multicastLockHelper = MulticastLockHelper(context)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var socket: DatagramSocket? = null
    private var listeningJob: Job? = null
    private var heartbeatJob: Job? = null
    private var timeoutJob: Job? = null
    private var retryJob: Job? = null

    // Node & Mesh State
    private val localIpAddresses = ConcurrentHashMap.newKeySet<String>()
    val knownPeers = ConcurrentHashMap<String, PeerNode>()
    private val activeNeighborEndPoints = ConcurrentHashMap<String, InetSocketAddress>()
    private val lastSeenTimestamps = ConcurrentHashMap<String, Long>()

    // Unacknowledged outgoing messages awaiting ACK: messageId -> Message
    private val pendingAcks = ConcurrentHashMap<String, Message>()

    // Simulation / testing control: allows user to simulate network drop / pause
    var isSignalEnabled: Boolean = true
        private set

    var batteryLevel: Int = 100
        private set

    // Telemetry state flow
    private val _diagnostics = MutableStateFlow(NetworkDiagnostics())
    val diagnostics: StateFlow<NetworkDiagnostics> = _diagnostics.asStateFlow()

    // Observable event listeners
    var onMessageReceived: ((Message) -> Unit)? = null
    var onPeerDiscovered: ((PeerNode) -> Unit)? = null
    var onPeerLost: ((String) -> Unit)? = null
    var onDeliveryAck: ((messageId: String, peerId: String) -> Unit)? = null
    var onSystemNotification: ((String) -> Unit)? = null

    @Synchronized
    fun start(): Boolean {
        if (socket != null && !socket!!.isClosed) {
            Log.d(tag, "Socket already running on port $port")
            return true
        }

        try {
            _diagnostics.update { it.copy(engineState = "STARTING", lastError = null) }
            multicastLockHelper.acquire()
            refreshLocalIps()

            socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(port))
            }

            _diagnostics.update {
                it.copy(
                    engineState = "RUNNING",
                    localIpAddress = getPrimaryLocalIp(),
                    listeningPort = port,
                    isMulticastLockHeld = multicastLockHelper.isHeld,
                    isSignalEnabled = isSignalEnabled
                )
            }

            startListening()
            startHeartbeat()
            startTimeoutChecker()
            startRetryWorker()

            onSystemNotification?.invoke("ResilientNet online on port $port")
            Log.i(tag, "ResilientNet node online. ID=${identityStore.nodeId}, Port=$port")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start socket on port $port: ${e.message}", e)
            _diagnostics.update {
                it.copy(
                    engineState = "ERROR",
                    lastError = "Socket bind failure: ${e.message}"
                )
            }
            onSystemNotification?.invoke("Socket error: ${e.message}")
            stop()
            return false
        }
    }

    @Synchronized
    fun stop() {
        listeningJob?.cancel()
        heartbeatJob?.cancel()
        timeoutJob?.cancel()
        retryJob?.cancel()

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null

        multicastLockHelper.release()

        _diagnostics.update {
            it.copy(
                engineState = "STOPPED",
                isMulticastLockHeld = false,
                activePeerCount = 0
            )
        }
        Log.i(tag, "ResilientNet node stopped.")
    }

    fun updateBattery(level: Int) {
        this.batteryLevel = level.coerceIn(0, 100)
        _diagnostics.update { it.copy(batteryLevel = this.batteryLevel) }
    }

    /**
     * Testing control: Pauses or resumes the network engine locally to test
     * offline store-and-forward buffering and recovery without modifying Wi-Fi settings.
     */
    fun toggleSignal() {
        isSignalEnabled = !isSignalEnabled
        _diagnostics.update {
            it.copy(
                isSignalEnabled = isSignalEnabled,
                engineState = if (isSignalEnabled) "RUNNING" else "PAUSED"
            )
        }
        onSystemNotification?.invoke(
            if (isSignalEnabled) "Signal RESTORED. Flushing offline queue..."
            else "Signal PAUSED. Outgoing messages will be buffered."
        )

        if (isSignalEnabled) {
            coroutineScope.launch {
                flushOfflineQueue()
            }
        }
    }

    // ───────────────────────────────────────────────────────────
    // Incoming Datagram Listener
    // ───────────────────────────────────────────────────────────
    private fun startListening() {
        listeningJob = coroutineScope.launch {
            val buffer = ByteArray(16384) // 16KB UDP packet buffer
            while (isActive && socket != null && !socket!!.isClosed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    withContext(Dispatchers.IO) {
                        socket?.receive(packet)
                    }

                    // Skip self-packets
                    if (isSelfPacket(packet)) continue

                    val senderAddress = InetSocketAddress(packet.address, packet.port)
                    val rawJson = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)

                    val netPacket = try {
                        gson.fromJson(rawJson, NetworkPacket::class.java)
                    } catch (e: Exception) {
                        Log.w(tag, "Discarding malformed JSON packet: ${e.message}")
                        null
                    } ?: continue

                    _diagnostics.update {
                        it.copy(
                            packetsReceivedTotal = it.packetsReceivedTotal + 1,
                            lastPacketTimestamp = System.currentTimeMillis()
                        )
                    }

                    handleIncomingPacket(netPacket, senderAddress)

                } catch (e: Exception) {
                    if (socket != null && !socket!!.isClosed) {
                        Log.w(tag, "Socket receive error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun handleIncomingPacket(netPacket: NetworkPacket, sender: InetSocketAddress) {
        val senderId = netPacket.senderId
        if (senderId.isBlank() || senderId == identityStore.nodeId) return

        when (netPacket.type) {
            PacketType.HELLO -> {
                _diagnostics.update { it.copy(helloPacketsCount = it.helloPacketsCount + 1) }
                handlePeerHello(senderId, netPacket.displayName, sender)
            }
            PacketType.DATA -> {
                _diagnostics.update { it.copy(dataPacketsCount = it.dataPacketsCount + 1) }
                handlePeerHello(senderId, netPacket.displayName, sender)
                val msg = netPacket.payload
                if (msg != null) {
                    // Send ACK back to direct sender
                    coroutineScope.launch {
                        sendAck(msg.id, sender)
                    }
                    handleIncomingDataMessage(msg)
                }
            }
            PacketType.ACK -> {
                _diagnostics.update { it.copy(ackPacketsCount = it.ackPacketsCount + 1) }
                handlePeerHello(senderId, netPacket.displayName, sender)
                val ackedMsgId = netPacket.payload?.id
                if (!ackedMsgId.isNullOrBlank()) {
                    handleAckReceived(ackedMsgId, senderId)
                } else {
                    // Desktop ResilientNet-main sends ACK with payload = null
                    // Match the most recent pending unacknowledged message sent to this peer or broadcast
                    val matchedPending = pendingAcks.values
                        .filter { it.targets.contains(senderId) || it.scope == MessageScope.BROADCAST }
                        .maxByOrNull { it.timestamp }
                    if (matchedPending != null) {
                        handleAckReceived(matchedPending.id, senderId)
                    }
                }
            }
            PacketType.SYNC -> {
                _diagnostics.update { it.copy(syncPacketsCount = it.syncPacketsCount + 1) }
                handlePeerHello(senderId, netPacket.displayName, sender)
                handleSyncRequest(netPacket.bloomFilter, sender)
            }
            PacketType.REQ -> {
                _diagnostics.update { it.copy(reqPacketsCount = it.reqPacketsCount + 1) }
                handlePeerHello(senderId, netPacket.displayName, sender)
                val requestedId = netPacket.payload?.id
                if (!requestedId.isNullOrBlank()) {
                    val msg = messageStore.getMessage(requestedId)
                    if (msg != null) {
                        coroutineScope.launch {
                            sendPacketDirect(PacketType.DATA, msg, sender)
                        }
                    }
                }
            }
        }
    }

    private fun handlePeerHello(peerId: String, displayName: String?, sender: InetSocketAddress) {
        val now = System.currentTimeMillis()
        val isNew = !knownPeers.containsKey(peerId)
        val wasOffline = knownPeers[peerId]?.isOnline == false

        val peer = knownPeers.compute(peerId) { _, existing ->
            existing?.copy(
                displayName = displayName ?: existing.displayName,
                ipAddress = sender.address.hostAddress ?: existing.ipAddress,
                port = sender.port,
                lastSeenTimestamp = now,
                isOnline = true
            ) ?: PeerNode(
                id = peerId,
                displayName = displayName ?: "Node-$peerId",
                ipAddress = sender.address.hostAddress ?: "Unknown",
                port = sender.port,
                lastSeenTimestamp = now,
                isOnline = true
            )
        }!!

        activeNeighborEndPoints[peerId] = sender
        lastSeenTimestamps[peerId] = now

        updatePeerCounts()

        if (isNew || wasOffline) {
            onPeerDiscovered?.invoke(peer)
            onSystemNotification?.invoke("Peer discovered: ${peer.displayName} (${peer.id})")
            Log.d(tag, "Peer active: ${peer.displayName} @ ${sender.address.hostAddress}:${sender.port}")

            // Send unicast HELLO reply to ensure symmetric discovery
            coroutineScope.launch {
                sendPacketDirect(PacketType.HELLO, null, sender)
                // Also trigger epidemic sync summary exchange
                sendSyncBloom(sender)
                // Attempt to deliver any buffered messages for this peer or broadcast
                flushOfflineQueue()
            }
        }
    }

    private fun handleIncomingDataMessage(msg: Message) {
        // Deduplication: if message ID already in store, drop it
        if (messageStore.hasMessage(msg.id)) {
            Log.d(tag, "Dropping duplicate message: ${msg.id}")
            return
        }

        msg.deliveryStatus = DeliveryStatus.DELIVERED
        msg.isOutgoing = false
        messageStore.saveOrUpdate(msg)

        val isTargetedToMe = msg.scope == MessageScope.TARGETED &&
                (msg.targets.contains(identityStore.nodeId) || identityStore.isAdmin)
        val isBroadcast = msg.scope == MessageScope.BROADCAST
        val isForMe = isBroadcast || isTargetedToMe

        if (isForMe) {
            onMessageReceived?.invoke(msg)
            if (msg.urgent || msg.priority == MessagePriority.CRITICAL) {
                onSystemNotification?.invoke("[EMERGENCY SOS] From ${msg.senderId}: ${msg.content}")
            }
        }

        // Multi-hop relay forwarding
        if (msg.ttl > 0 && msg.scope != MessageScope.LOCAL) {
            val relayMsg = msg.copy(ttl = msg.ttl - 1)
            coroutineScope.launch {
                forwardRelayMessage(relayMsg)
            }
        }
    }

    private suspend fun forwardRelayMessage(msg: Message) {
        // Broadcast relay to all active neighbors except the original sender
        for ((peerId, endpoint) in activeNeighborEndPoints) {
            if (peerId != msg.senderId) {
                try {
                    sendPacketDirect(PacketType.DATA, msg, endpoint)
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleAckReceived(messageId: String, senderId: String) {
        pendingAcks.remove(messageId)
        messageStore.updateStatus(messageId, DeliveryStatus.DELIVERED)
        onDeliveryAck?.invoke(messageId, senderId)
        Log.d(tag, "Received delivery ACK for message $messageId from $senderId")
    }

    private fun handleSyncRequest(remoteBloomBase64: String?, sender: InetSocketAddress) {
        if (remoteBloomBase64.isNullOrBlank()) return
        val remoteBloom = BloomFilter.fromBase64(remoteBloomBase64)
        val allLocalMessages = messageStore.getAllMessages()

        // Find messages remote peer might not have
        coroutineScope.launch {
            for (msg in allLocalMessages) {
                if (!remoteBloom.mightContain(msg.id) && msg.scope != MessageScope.LOCAL) {
                    sendPacketDirect(PacketType.DATA, msg, sender)
                    delay(30) // slight pacing to avoid UDP buffer saturation
                }
            }
        }
    }

    // ───────────────────────────────────────────────────────────
    // Outgoing Message Dispatch & Queue Flushing
    // ───────────────────────────────────────────────────────────
    suspend fun sendMessage(
        content: String,
        targetPeerId: String? = null,
        priority: MessagePriority = MessagePriority.NORMAL
    ): Message = withContext(Dispatchers.IO) {
        val scope = if (targetPeerId.isNullOrBlank()) MessageScope.BROADCAST else MessageScope.TARGETED
        val targets = if (targetPeerId.isNullOrBlank()) emptySet() else setOf(targetPeerId)

        val msg = Message.createOutgoing(
            content = content,
            senderId = identityStore.nodeId,
            senderRole = identityStore.role,
            scope = scope,
            targets = targets,
            priority = priority
        )

        // Battery conservation check: below 15%, regular traffic is throttled
        if (batteryLevel < 15 && msg.priority != MessagePriority.CRITICAL) {
            msg.deliveryStatus = DeliveryStatus.QUEUED
            messageStore.saveOrUpdate(msg)
            onSystemNotification?.invoke("Battery low (<15%). Message buffered.")
            updateQueueCount()
            return@withContext msg
        }

        // Offline / testing pause check
        if (!isSignalEnabled || activeNeighborEndPoints.isEmpty()) {
            msg.deliveryStatus = DeliveryStatus.QUEUED
            messageStore.saveOrUpdate(msg)
            onSystemNotification?.invoke("No active peers. Message queued.")
            updateQueueCount()
            return@withContext msg
        }

        // Active dispatch
        dispatchMessage(msg, targetPeerId)
        return@withContext msg
    }

    private suspend fun dispatchMessage(msg: Message, targetPeerId: String?) {
        msg.deliveryStatus = DeliveryStatus.SENT
        messageStore.saveOrUpdate(msg)
        pendingAcks[msg.id] = msg

        if (!targetPeerId.isNullOrBlank()) {
            // Targeted dispatch
            val endpoint = activeNeighborEndPoints[targetPeerId]
            if (endpoint != null) {
                sendPacketDirect(PacketType.DATA, msg, endpoint)
            } else {
                // Relay through available neighbors
                for (ep in activeNeighborEndPoints.values) {
                    sendPacketDirect(PacketType.DATA, msg, ep)
                }
            }
        } else {
            // Broadcast dispatch
            for (ep in activeNeighborEndPoints.values) {
                sendPacketDirect(PacketType.DATA, msg, ep)
            }
            sendSubnetBroadcast(PacketType.DATA, msg)
        }

        updateQueueCount()
    }

    suspend fun flushOfflineQueue() = withContext(Dispatchers.IO) {
        if (!isSignalEnabled || activeNeighborEndPoints.isEmpty()) return@withContext
        val queue = messageStore.getPendingQueue()
        if (queue.isEmpty()) return@withContext

        Log.d(tag, "Flushing ${queue.size} buffered offline messages...")
        for (msg in queue) {
            val target = msg.targets.firstOrNull()
            dispatchMessage(msg, target)
            delay(50) // packet rate control
        }
        updateQueueCount()
    }

    private fun startRetryWorker() {
        retryJob = coroutineScope.launch {
            while (isActive) {
                delay(3000)
                if (!isSignalEnabled || activeNeighborEndPoints.isEmpty()) continue

                val now = System.currentTimeMillis()
                for ((id, msg) in pendingAcks) {
                    if (now - msg.timestamp > 3000) {
                        if (msg.retryCount < 2) {
                            msg.retryCount++
                            Log.d(tag, "Retrying unacked message $id (attempt ${msg.retryCount})")
                            val target = msg.targets.firstOrNull()
                            dispatchMessage(msg, target)
                        } else {
                            // Retries exhausted
                            pendingAcks.remove(id)
                            messageStore.updateStatus(id, DeliveryStatus.FAILED)
                            Log.w(tag, "Message $id marked FAILED after retries.")
                        }
                    }
                }
            }
        }
    }

    // ───────────────────────────────────────────────────────────
    // Low-Level UDP Transmission Helpers
    // ───────────────────────────────────────────────────────────
    private suspend fun sendAck(messageId: String, target: InetSocketAddress) {
        val ackMsg = Message(id = messageId, senderId = identityStore.nodeId)
        sendPacketDirect(PacketType.ACK, ackMsg, target)
    }

    private suspend fun sendSyncBloom(target: InetSocketAddress) {
        val bloom = BloomFilter()
        for (msg in messageStore.getAllMessages()) {
            bloom.add(msg.id)
        }
        val pkt = NetworkPacket(
            type = PacketType.SYNC,
            senderId = identityStore.nodeId,
            displayName = identityStore.displayName,
            bloomFilter = bloom.toBase64()
        )
        sendRawPacket(pkt, target)
    }

    private suspend fun sendPacketDirect(
        type: PacketType,
        payload: Message?,
        target: InetSocketAddress
    ) {
        val pkt = NetworkPacket(
            type = type,
            senderId = identityStore.nodeId,
            displayName = identityStore.displayName,
            payload = payload
        )
        sendRawPacket(pkt, target)
    }

    private suspend fun sendRawPacket(pkt: NetworkPacket, target: InetSocketAddress) =
        withContext(Dispatchers.IO) {
            val sock = socket ?: return@withContext
            if (sock.isClosed) return@withContext
            try {
                val json = gson.toJson(pkt)
                val bytes = json.toByteArray(StandardCharsets.UTF_8)
                val dgram = DatagramPacket(bytes, bytes.size, target.address, target.port)
                sock.send(dgram)

                _diagnostics.update {
                    it.copy(packetsSentTotal = it.packetsSentTotal + 1)
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to send packet to $target: ${e.message}")
            }
        }

    // ───────────────────────────────────────────────────────────
    // Discovery Beacons & Subnet Broadcast
    // ───────────────────────────────────────────────────────────
    private fun startHeartbeat() {
        heartbeatJob = coroutineScope.launch {
            while (isActive) {
                if (isSignalEnabled) {
                    try {
                        refreshLocalIps()
                        val helloPkt = NetworkPacket(
                            type = PacketType.HELLO,
                            senderId = identityStore.nodeId,
                            displayName = identityStore.displayName
                        )
                        sendSubnetBroadcast(helloPkt)
                    } catch (e: Exception) {
                        Log.w(tag, "Heartbeat broadcast failed: ${e.message}")
                    }
                }
                delay(5000)
            }
        }
    }

    private suspend fun sendSubnetBroadcast(pkt: NetworkPacket) = withContext(Dispatchers.IO) {
        val sock = socket ?: return@withContext
        if (sock.isClosed) return@withContext

        val json = gson.toJson(pkt)
        val bytes = json.toByteArray(StandardCharsets.UTF_8)

        // 1. General limited broadcast
        try {
            val dgram = DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), port)
            sock.send(dgram)
        } catch (_: Exception) {}

        // 2. Directed subnet broadcast on each active IPv4 interface
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@withContext
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || !iface.supportsMulticast()) continue
                for (ia in iface.interfaceAddresses) {
                    val bcast = ia.broadcast ?: continue
                    if (bcast.hostAddress == "255.255.255.255") continue
                    try {
                        val dgram = DatagramPacket(bytes, bytes.size, bcast, port)
                        sock.send(dgram)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun sendSubnetBroadcast(type: PacketType, payload: Message) {
        val pkt = NetworkPacket(
            type = type,
            senderId = identityStore.nodeId,
            displayName = identityStore.displayName,
            payload = payload
        )
        sendSubnetBroadcast(pkt)
    }

    // ───────────────────────────────────────────────────────────
    // Timeout Checker
    // ───────────────────────────────────────────────────────────
    private fun startTimeoutChecker() {
        timeoutJob = coroutineScope.launch {
            while (isActive) {
                delay(5000)
                val now = System.currentTimeMillis()
                for ((peerId, lastSeen) in lastSeenTimestamps) {
                    if (now - lastSeen > 20000) { // 20s timeout
                        lastSeenTimestamps.remove(peerId)
                        activeNeighborEndPoints.remove(peerId)
                        val peer = knownPeers[peerId]
                        if (peer != null && peer.isOnline) {
                            peer.isOnline = false
                            onPeerLost?.invoke(peerId)
                            onSystemNotification?.invoke("Peer offline: ${peer.displayName}")
                            Log.d(tag, "Peer offline: ${peer.displayName} ($peerId)")
                        }
                    }
                }
                updatePeerCounts()
            }
        }
    }

    // ───────────────────────────────────────────────────────────
    // IP Filtering & Helpers
    // ───────────────────────────────────────────────────────────
    private fun refreshLocalIps() {
        localIpAddresses.clear()
        localIpAddresses.add("127.0.0.1")
        localIpAddresses.add("0.0.0.0")
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address) {
                        addr.hostAddress?.let { localIpAddresses.add(it) }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun isSelfPacket(packet: DatagramPacket): Boolean {
        val addr = packet.address?.hostAddress ?: return false
        return localIpAddresses.contains(addr) && packet.port == port
    }

    fun getPrimaryLocalIp(): String {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun updatePeerCounts() {
        _diagnostics.update {
            it.copy(
                activePeerCount = activeNeighborEndPoints.size,
                totalKnownPeersCount = knownPeers.size
            )
        }
    }

    private fun updateQueueCount() {
        val count = messageStore.getPendingQueue().size
        _diagnostics.update { it.copy(pendingBufferCount = count) }
    }
}
