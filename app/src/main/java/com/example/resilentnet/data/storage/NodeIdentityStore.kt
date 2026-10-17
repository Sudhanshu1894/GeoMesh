package com.example.resilentnet.data.storage

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Manages persistent identity of this local node across app restarts.
 * Conforms to desktop ResilientNet's 6-character ID generation pattern.
 */
class NodeIdentityStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("resilientnet_identity_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_NODE_ID = "key_node_id"
        private const val KEY_DISPLAY_NAME = "key_display_name"
        private const val KEY_NODE_ROLE = "key_node_role"
    }

    val nodeId: String
        get() {
            var id = prefs.getString(KEY_NODE_ID, null)
            if (id.isNullOrBlank()) {
                // Generate a 6-character alphanumeric ID matching desktop logic
                id = UUID.randomUUID().toString().replace("-", "").substring(0, 6).uppercase()
                prefs.edit().putString(KEY_NODE_ID, id).apply()
            }
            return id
        }

    var displayName: String
        get() {
            val saved = prefs.getString(KEY_DISPLAY_NAME, null)
            return if (!saved.isNullOrBlank()) saved else "Android-$nodeId"
        }
        set(value) {
            prefs.edit().putString(KEY_DISPLAY_NAME, value.trim()).apply()
        }

    var role: String
        get() = prefs.getString(KEY_NODE_ROLE, "USER") ?: "USER"
        set(value) {
            prefs.edit().putString(KEY_NODE_ROLE, value).apply()
        }

    val isAdmin: Boolean
        get() = role.equals("ADMIN", ignoreCase = true)
}
