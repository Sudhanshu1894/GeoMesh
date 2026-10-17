package com.example.resilentnet.data.model

import com.google.gson.annotations.SerializedName

/**
 * Top-level JSON network packet envelope.
 * Exactly mirrors NetworkPacket in ResilientNet desktop MeshNode.java.
 */
data class NetworkPacket(
    @SerializedName("type")
    var type: PacketType = PacketType.HELLO,

    @SerializedName("senderId")
    var senderId: String = "",

    @SerializedName("payload")
    var payload: Message? = null,

    // Optional extension fields for display name and protocol sync
    // Ignored gracefully by desktop Gson parser
    @SerializedName("displayName")
    var displayName: String? = null,

    @SerializedName("bloomFilter")
    var bloomFilter: String? = null
)
