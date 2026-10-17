package com.example.resilentnet.data.model

/**
 * Message dissemination scope matching desktop Message.Scope.
 */
enum class MessageScope {
    LOCAL,      // Only for the immediate neighbor
    BROADCAST,  // For everyone in the mesh
    TARGETED    // For specific Node IDs
}
