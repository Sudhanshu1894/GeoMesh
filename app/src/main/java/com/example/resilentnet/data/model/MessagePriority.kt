package com.example.resilentnet.data.model

/**
 * Message priority matching desktop Message.Priority.
 */
enum class MessagePriority {
    LOW,      // Routine info
    NORMAL,   // Standard message
    HIGH,     // Important, needs attention
    CRITICAL  // Urgent/emergency message (e.g. SOS, HELP)
}
