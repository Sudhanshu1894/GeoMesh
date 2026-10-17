package com.example.resilentnet.data.model

/**
 * Message delivery lifecycle tracking states.
 */
enum class DeliveryStatus {
    QUEUED,        // Stored in offline buffer awaiting active neighbors
    SENT,          // Datagram transmitted via UDP socket (awaiting ACK)
    DELIVERED,     // Direct ACK received from destination/neighbor
    RELAYED,       // Forwarded through intermediate mesh nodes
    FAILED         // Retries exhausted without ACK
}
