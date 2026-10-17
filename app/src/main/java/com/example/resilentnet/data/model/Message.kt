package com.example.resilentnet.data.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Message payload data model.
 * Fully compatible with ResilientNet desktop Message JSON serialization.
 */
data class Message(
    @SerializedName("id")
    var id: String = UUID.randomUUID().toString(),

    @SerializedName("senderId")
    var senderId: String = "",

    @SerializedName("senderRole")
    var senderRole: String = "USER",

    @SerializedName("content")
    var content: String = "",

    @SerializedName("urgent")
    var urgent: Boolean = false,

    @SerializedName("timestamp")
    var timestamp: Long = System.currentTimeMillis(),

    @SerializedName("scope")
    var scope: MessageScope = MessageScope.BROADCAST,

    @SerializedName("targets")
    var targets: MutableSet<String> = mutableSetOf(),

    @SerializedName("ttl")
    var ttl: Int = 3,

    @SerializedName("priority")
    var priority: MessagePriority = MessagePriority.NORMAL,

    // Local device tracking fields (not transmitted or ignored over the wire)
    @Transient
    var deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,

    @Transient
    var isOutgoing: Boolean = false,

    @Transient
    var retryCount: Int = 0
) {
    init {
        evaluateEmergencyStatus()
    }

    /**
     * Preserves desktop keyword-based auto-escalation rules.
     * Escalates to CRITICAL and sets urgent = true if SOS or HELP is present.
     */
    fun evaluateEmergencyStatus() {
        val upper = content.uppercase()
        val hasEmergencyKeyword = upper.contains("HELP") || upper.contains("SOS")
        if (hasEmergencyKeyword || priority == MessagePriority.CRITICAL) {
            urgent = true
            priority = MessagePriority.CRITICAL
        }
    }

    companion object {
        fun createOutgoing(
            content: String,
            senderId: String,
            senderRole: String = "USER",
            scope: MessageScope = MessageScope.BROADCAST,
            targets: Set<String> = emptySet(),
            priority: MessagePriority = MessagePriority.NORMAL
        ): Message {
            val msg = Message(
                id = UUID.randomUUID().toString(),
                senderId = senderId,
                senderRole = senderRole,
                content = content,
                scope = scope,
                targets = targets.toMutableSet(),
                timestamp = System.currentTimeMillis(),
                priority = priority,
                ttl = 3,
                deliveryStatus = DeliveryStatus.QUEUED,
                isOutgoing = true
            )
            msg.evaluateEmergencyStatus()
            return msg
        }
    }
}
