package com.example.resilentnet.data.model

/**
 * Protocol packet types, keeping 100% interoperability with the original
 * ResilientNet desktop specification (PacketType.java).
 */
enum class PacketType {
    HELLO,
    SYNC,
    REQ,
    DATA,
    ACK
}
