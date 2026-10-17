package com.example.resilentnet.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.resilentnet.ResilientNetApp
import com.example.resilentnet.data.model.HazardMarker
import com.example.resilentnet.data.model.HazardType
import com.example.resilentnet.data.model.Message
import com.example.resilentnet.data.model.MessagePriority
import com.example.resilentnet.data.model.PeerNode
import com.example.resilentnet.data.network.NetworkDiagnostics
import com.example.resilentnet.domain.MeshRepository
import com.example.resilentnet.service.MeshForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Device location snapshot with accuracy, provider source, and fix verification.
 */
data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val isRealFix: Boolean = false,
    val provider: String = "default",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ViewModel managing UI state and user interactions for ResilientNet.
 */
class MeshViewModel(
    private val repository: MeshRepository = ResilientNetApp.instance.repository
) : ViewModel() {

    val myNodeId: String = repository.identityStore.nodeId
    val isMyNodeAdmin: Boolean = repository.identityStore.isAdmin

    private val _displayName = MutableStateFlow(repository.identityStore.displayName)
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _selectedPeerId = MutableStateFlow<String?>(null)
    val selectedPeerId: StateFlow<String?> = _selectedPeerId.asStateFlow()

    val messages: StateFlow<List<Message>> = repository.messages
    val chatMessages: StateFlow<List<Message>> = repository.chatMessages
    val hazards: StateFlow<List<HazardMarker>> = repository.hazards
    val peers: StateFlow<List<PeerNode>> = repository.peers
    val diagnostics: StateFlow<NetworkDiagnostics> = repository.diagnostics
    val systemAlerts: SharedFlow<String> = repository.systemAlerts

    // Current GPS location state with accuracy, provider, and fix status
    private val _locationState = MutableStateFlow(
        DeviceLocation(latitude = 12.8406, longitude = 80.1534, isRealFix = false, provider = "default")
    )
    val locationState: StateFlow<DeviceLocation> = _locationState.asStateFlow()

    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(Pair(12.8406, 80.1534))
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

    val isNetworkingActive: Boolean
        get() = diagnostics.value.engineState == "RUNNING" || diagnostics.value.engineState == "PAUSED"

    fun startNetworking(context: Context) {
        MeshForegroundService.startService(context)
    }

    fun stopNetworking(context: Context) {
        repository.stopMesh()
        MeshForegroundService.stopService(context)
    }

    fun toggleSignal() {
        repository.toggleSignal()
    }

    fun updateDisplayName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotBlank()) {
            repository.updateDisplayName(trimmed)
            _displayName.value = trimmed
        }
    }

    fun selectPeer(peerId: String?) {
        _selectedPeerId.value = peerId
    }

    fun sendMessage(
        content: String,
        priority: MessagePriority = MessagePriority.NORMAL,
        targetPeerId: String? = _selectedPeerId.value
    ) {
        if (content.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(
                content = content.trim(),
                targetPeerId = targetPeerId,
                priority = priority
            )
        }
    }

    fun sendEmergencySos(additionalDetails: String? = null) {
        viewModelScope.launch {
            repository.sendEmergencySos(additionalDetails)
        }
    }

    fun clearHistory() {
        repository.clearChatHistory()
    }

    fun updateLocation(
        lat: Double,
        lon: Double,
        accuracy: Float? = null,
        provider: String = "gps"
    ) {
        _locationState.value = DeviceLocation(
            latitude = lat,
            longitude = lon,
            accuracyMeters = accuracy,
            isRealFix = true,
            provider = provider,
            timestamp = System.currentTimeMillis()
        )
        _currentLocation.value = Pair(lat, lon)
    }

    fun setAnchorCoordinates(lat: Double, lon: Double, sourceName: String = "manual") {
        _locationState.value = DeviceLocation(
            latitude = lat,
            longitude = lon,
            accuracyMeters = null,
            isRealFix = true,
            provider = sourceName,
            timestamp = System.currentTimeMillis()
        )
        _currentLocation.value = Pair(lat, lon)
    }

    fun reportHazard(
        type: HazardType,
        points: List<Pair<Double, Double>>,
        description: String
    ) {
        viewModelScope.launch {
            repository.broadcastHazard(type, points, description)
        }
    }
}
