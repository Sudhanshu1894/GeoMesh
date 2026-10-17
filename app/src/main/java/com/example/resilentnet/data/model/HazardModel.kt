package com.example.resilentnet.data.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Hazard classification types transmitted over the mesh network.
 * Each type has a short wire code to minimize UDP packet size.
 */
enum class HazardType(val code: String, val label: String) {
    DEMOLISHED_BUILDING("BLDG", "Collapsed Building"),
    FLOODED_ROAD("FLOOD", "Flooded Route"),
    OBSTRUCTED_PATH("PATH", "Blocked Path");

    companion object {
        fun fromCode(code: String): HazardType? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}

/**
 * Spatial hazard marker produced by parsing @HAZARD| messages.
 * [points] contains 1 coordinate for point hazards, or multiple for route/path hazards.
 */
data class HazardMarker(
    val id: String,
    val senderId: String,
    val type: HazardType,
    val points: List<Pair<Double, Double>>,
    val description: String,
    val timestamp: Long
) {
    /** Primary coordinate (first point) for distance calculations. */
    val latitude: Double get() = points.firstOrNull()?.first ?: 0.0
    val longitude: Double get() = points.firstOrNull()?.second ?: 0.0

    /** Whether this hazard is a route/path (multiple points) vs a single point marker. */
    val isRoute: Boolean get() = points.size > 1

    /**
     * Approximate distance in meters from this hazard to a given coordinate.
     */
    fun distanceTo(lat: Double, lon: Double): Double {
        val dy = (latitude - lat) * 111320.0
        val dx = (longitude - lon) * 111320.0 * cos(Math.toRadians(lat))
        return sqrt(dx * dx + dy * dy)
    }
}

/**
 * Zero-dependency spatial codec for hazard wire format.
 *
 * Wire format: `@HAZARD|<TYPE_CODE>|<MODE:P or L>|<ENCODED_GEO>|<DESCRIPTION>`
 * - MODE=P uses Geohash (single point)
 * - MODE=L uses Google Encoded Polyline (multiple points)
 */
object GeoCodec {
    private const val HAZARD_PREFIX = "@HAZARD|"
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    // ── Geohash ──────────────────────────────────────────────────

    /**
     * Encode a latitude/longitude into a standard Base32 Geohash string.
     */
    fun encodeGeohash(lat: Double, lon: Double, precision: Int = 9): String {
        var latMin = -90.0; var latMax = 90.0
        var lonMin = -180.0; var lonMax = 180.0
        var isEven = true
        var bit = 0
        var ch = 0
        val sb = StringBuilder()

        while (sb.length < precision) {
            if (isEven) {
                val mid = (lonMin + lonMax) / 2.0
                if (lon >= mid) { ch = ch or (1 shl (4 - bit)); lonMin = mid } else { lonMax = mid }
            } else {
                val mid = (latMin + latMax) / 2.0
                if (lat >= mid) { ch = ch or (1 shl (4 - bit)); latMin = mid } else { latMax = mid }
            }
            isEven = !isEven
            if (bit < 4) {
                bit++
            } else {
                sb.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return sb.toString()
    }

    /**
     * Decode a Base32 Geohash back to approximate (lat, lon) center coordinates.
     */
    fun decodeGeohash(hash: String): Pair<Double, Double> {
        var latMin = -90.0; var latMax = 90.0
        var lonMin = -180.0; var lonMax = 180.0
        var isEven = true

        for (c in hash) {
            val cd = BASE32.indexOf(c.lowercaseChar())
            if (cd < 0) continue
            for (mask in intArrayOf(16, 8, 4, 2, 1)) {
                if (isEven) {
                    val mid = (lonMin + lonMax) / 2.0
                    if (cd and mask != 0) lonMin = mid else lonMax = mid
                } else {
                    val mid = (latMin + latMax) / 2.0
                    if (cd and mask != 0) latMin = mid else latMax = mid
                }
                isEven = !isEven
            }
        }
        return Pair((latMin + latMax) / 2.0, (lonMin + lonMax) / 2.0)
    }

    // ── Google Encoded Polyline ──────────────────────────────────

    /**
     * Encode a list of (lat, lon) pairs into a Google Encoded Polyline string.
     * Uses standard 1e5 multiplier and ASCII bit-shifting.
     */
    fun encodePolyline(points: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        var prevLat = 0
        var prevLon = 0

        for ((lat, lon) in points) {
            val iLat = Math.round(lat * 1e5).toInt()
            val iLon = Math.round(lon * 1e5).toInt()
            encodeValue(iLat - prevLat, sb)
            encodeValue(iLon - prevLon, sb)
            prevLat = iLat
            prevLon = iLon
        }
        return sb.toString()
    }

    private fun encodeValue(value: Int, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else (value shl 1)
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1F)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }

    /**
     * Decode a Google Encoded Polyline string back to (lat, lon) pairs.
     */
    fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lon = 0

        while (index < encoded.length) {
            var shift = 0; var component = 0
            while (index < encoded.length) {
                val b = encoded[index++].code - 63
                component = component or ((b and 0x1F) shl shift)
                shift += 5
                if (b < 0x20) break
            }
            lat += if (component and 1 != 0) (component shr 1).inv() else (component shr 1)

            shift = 0; component = 0
            while (index < encoded.length) {
                val b = encoded[index++].code - 63
                component = component or ((b and 0x1F) shl shift)
                shift += 5
                if (b < 0x20) break
            }
            lon += if (component and 1 != 0) (component shr 1).inv() else (component shr 1)

            result.add(Pair(lat / 1e5, lon / 1e5))
        }
        return result
    }

    // ── Ramer-Douglas-Peucker Path Simplification ────────────────

    /**
     * Simplify a list of points using the Ramer-Douglas-Peucker algorithm.
     * [epsilon] is the perpendicular distance threshold in degrees (~0.0001 ≈ 11m).
     */
    fun simplifyPath(
        points: List<Pair<Double, Double>>,
        epsilon: Double = 0.0001
    ): List<Pair<Double, Double>> {
        if (points.size <= 2) return points

        var dMax = 0.0
        var index = 0
        val end = points.size - 1

        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points[0], points[end])
            if (d > dMax) { dMax = d; index = i }
        }

        return if (dMax > epsilon) {
            val left = simplifyPath(points.subList(0, index + 1), epsilon)
            val right = simplifyPath(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(points[0], points[end])
        }
    }

    private fun perpendicularDistance(
        point: Pair<Double, Double>,
        lineStart: Pair<Double, Double>,
        lineEnd: Pair<Double, Double>
    ): Double {
        val dx = lineEnd.first - lineStart.first
        val dy = lineEnd.second - lineStart.second
        val mag = sqrt(dx * dx + dy * dy)
        if (mag == 0.0) return sqrt(
            (point.first - lineStart.first).let { it * it } +
                    (point.second - lineStart.second).let { it * it }
        )
        val u = ((point.first - lineStart.first) * dx + (point.second - lineStart.second) * dy) / (mag * mag)
        val closestX = lineStart.first + u * dx
        val closestY = lineStart.second + u * dy
        return sqrt((point.first - closestX).let { it * it } + (point.second - closestY).let { it * it })
    }

    // ── Wire Format Serialization ────────────────────────────────

    /**
     * Serialize hazard data into a @HAZARD| prefixed string for Message.content.
     *
     * Format: `@HAZARD|<TYPE_CODE>|<P or L>|<ENCODED_GEO>|<DESCRIPTION>`
     */
    fun serializeHazard(
        type: HazardType,
        points: List<Pair<Double, Double>>,
        desc: String
    ): String {
        val sanitizedDesc = desc.replace("|", "/")
        return if (points.size <= 1) {
            val (lat, lon) = points.firstOrNull() ?: Pair(0.0, 0.0)
            val geohash = encodeGeohash(lat, lon)
            "$HAZARD_PREFIX${type.code}|P|$geohash|$sanitizedDesc"
        } else {
            val simplified = simplifyPath(points)
            val polyline = encodePolyline(simplified)
            "$HAZARD_PREFIX${type.code}|L|$polyline|$sanitizedDesc"
        }
    }

    /**
     * Parse a Message into a HazardMarker if its content starts with @HAZARD|.
     * Returns null for non-hazard messages.
     */
    fun parseHazard(message: Message): HazardMarker? {
        val content = message.content
        if (!content.startsWith(HAZARD_PREFIX)) return null

        return try {
            // Remove prefix, then split: TYPE_CODE | MODE | ENCODED_GEO | DESCRIPTION
            val body = content.removePrefix(HAZARD_PREFIX)
            val parts = body.split("|", limit = 4)
            if (parts.size < 4) return null

            val typeCode = parts[0]
            val mode = parts[1]
            val encodedGeo = parts[2]
            val description = parts[3]

            val hazardType = HazardType.fromCode(typeCode) ?: return null

            val points = when (mode.uppercase()) {
                "P" -> {
                    val (lat, lon) = decodeGeohash(encodedGeo)
                    listOf(Pair(lat, lon))
                }
                "L" -> decodePolyline(encodedGeo)
                else -> return null
            }

            HazardMarker(
                id = message.id,
                senderId = message.senderId,
                type = hazardType,
                points = points,
                description = description,
                timestamp = message.timestamp
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a message content string is a hazard wire message.
     */
    fun isHazardMessage(content: String): Boolean = content.startsWith(HAZARD_PREFIX)
}
