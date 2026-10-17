package com.example.resilentnet.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Robust zero-dependency Android Location Manager helper.
 * Obtains immediate last-known fixes from GPS, Network, and Passive providers,
 * and maintains continuous updates with 0-meter distance threshold for stationary/indoor testing.
 */
object LocationProviderHelper {
    private const val TAG = "LocationProviderHelper"

    /**
     * Inspect all system location providers and return the most accurate and recent location available.
     */
    fun getBestLastKnownLocation(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val candidateProviders = mutableListOf<String>()
        if (hasFine) {
            candidateProviders.add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                candidateProviders.add(LocationManager.FUSED_PROVIDER)
            }
        }
        candidateProviders.add(LocationManager.NETWORK_PROVIDER)
        candidateProviders.add(LocationManager.PASSIVE_PROVIDER)

        var bestLocation: Location? = null
        val now = System.currentTimeMillis()

        for (provider in candidateProviders) {
            try {
                if (!locationManager.isProviderEnabled(provider)) continue
                val loc = locationManager.getLastKnownLocation(provider) ?: continue

                if (bestLocation == null) {
                    bestLocation = loc
                    continue
                }

                // If loc is fresh (< 2 minutes old) and more accurate, pick it
                val isSignificantlyNewer = loc.time - bestLocation.time > 120_000
                val isSignificantlyOlder = bestLocation.time - loc.time > 120_000
                val isMoreAccurate = loc.hasAccuracy() && (!bestLocation.hasAccuracy() || loc.accuracy < bestLocation.accuracy)

                if (isSignificantlyNewer) {
                    bestLocation = loc
                } else if (!isSignificantlyOlder && isMoreAccurate) {
                    bestLocation = loc
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException querying $provider: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Error querying $provider: ${e.message}")
            }
        }

        return bestLocation
    }

    /**
     * Start continuous location updates across all available providers.
     * Uses minDistance = 0f so that stationary users (e.g. testing at a desk) still receive fixes.
     */
    fun registerLocationUpdates(
        context: Context,
        minTimeMs: Long = 1000L,
        onLocationChanged: (Location) -> Unit
    ): Pair<LocationManager, LocationListener>? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocationChanged(location)
            }
            @Deprecated("Deprecated in API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val providers = mutableListOf<String>()
        if (hasFine) {
            providers.add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                providers.add(LocationManager.FUSED_PROVIDER)
            }
        }
        providers.add(LocationManager.NETWORK_PROVIDER)
        providers.add(LocationManager.PASSIVE_PROVIDER)

        var registeredAny = false
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, minTimeMs, 0f, listener)
                    registeredAny = true
                    Log.d(TAG, "Registered location updates on $provider")
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException requesting updates for $provider: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed requesting updates for $provider: ${e.message}")
            }
        }

        return if (registeredAny) Pair(locationManager, listener) else null
    }

    /**
     * Remove registered location updates.
     */
    fun unregisterLocationUpdates(pair: Pair<LocationManager, LocationListener>?) {
        pair?.let { (mgr, listener) ->
            try {
                mgr.removeUpdates(listener)
                Log.d(TAG, "Unregistered location listener")
            } catch (_: Exception) {}
        }
    }
}
