package com.example.resilentnet.data.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Manages WifiManager.MulticastLock for UDP broadcast/multicast reception on Android.
 * Essential on Android to prevent Wi-Fi chipsets from dropping broadcast datagrams.
 */
class MulticastLockHelper(context: Context) {
    private val tag = "MulticastLockHelper"
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null

    val isHeld: Boolean
        get() = multicastLock?.isHeld == true

    fun acquire() {
        if (wifiManager == null) {
            Log.w(tag, "WifiManager unavailable; cannot acquire MulticastLock.")
            return
        }
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("ResilientNet_MulticastLock").apply {
                    setReferenceCounted(true)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
                Log.d(tag, "MulticastLock acquired successfully.")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to acquire MulticastLock: ${e.message}", e)
        }
    }

    fun release() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                Log.d(tag, "MulticastLock released.")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to release MulticastLock: ${e.message}", e)
        }
    }
}
