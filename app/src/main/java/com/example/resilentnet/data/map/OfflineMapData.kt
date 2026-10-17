package com.example.resilentnet.data.map

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Core Data Models for Offline Vector Mapping and Navigation Graph.
 */

data class MapBuilding(
    val id: String,
    val name: String,
    val category: String, // e.g. "ACADEMIC", "ADMIN", "SHELTER", "HOSTEL", "FACILITY"
    val polygon: List<Pair<Double, Double>>, // Closed ring (lat, lon)
    val center: Pair<Double, Double>
)

data class MapNode(
    val id: String,
    val lat: Double,
    val lon: Double,
    val name: String? = null
)

data class MapRoadEdge(
    val id: String,
    val name: String,
    val fromNodeId: String,
    val toNodeId: String,
    val pathPoints: List<Pair<Double, Double>>,
    val lengthMeters: Double,
    val isMainRoad: Boolean = true
)

data class OfflineCampusMap(
    val anchorLat: Double,
    val anchorLon: Double,
    val buildings: List<MapBuilding>,
    val nodes: Map<String, MapNode>,
    val roads: List<MapRoadEdge>
)

/**
 * Bundled offline vector dataset and spatial query engine.
 * Provides a rich campus vector map centered around default coordinate (12.8406, 80.1534),
 * with dynamic re-anchoring to translate map geometry wherever the user moves or anchors.
 */
object OfflineMapData {
    const val DEFAULT_ANCHOR_LAT = 12.8406
    const val DEFAULT_ANCHOR_LON = 80.1534

    // Metric conversion factors near Chennai/Kelambakkam latitude (~12.84°)
    private const val METERS_PER_DEG_LAT = 111320.0
    private fun metersPerDegLon(lat: Double): Double = METERS_PER_DEG_LAT * cos(Math.toRadians(lat))

    // Helper to generate lat/lon offset in meters from base anchor
    private fun offsetCoord(baseLat: Double, baseLon: Double, dyMeters: Double, dxMeters: Double): Pair<Double, Double> {
        val lat = baseLat + dyMeters / METERS_PER_DEG_LAT
        val lon = baseLon + dxMeters / metersPerDegLon(baseLat)
        return Pair(lat, lon)
    }

    /**
     * Builds a rich, connected reference campus map around the given anchor coordinate.
     * Contains 13 named buildings with polygonal footprints and 22 interconnected road segments
     * forming multiple loops, cross-junctions, and perimeter avenues.
     */
    fun getMapForAnchor(anchorLat: Double = DEFAULT_ANCHOR_LAT, anchorLon: Double = DEFAULT_ANCHOR_LON): OfflineCampusMap {
        val lat = anchorLat
        val lon = anchorLon

        // ─────────────────────────────────────────────────────────────
        // 1. Road Network Graph Nodes (Intersections & Landmarks)
        // ─────────────────────────────────────────────────────────────
        // Layout span: ~600m x ~600m
        // Y: North (+) / South (-)
        // X: East (+) / West (-)
        val nodeDefs = listOf(
            // North Gate & North Perimeter
            Triple("N_GATE", 260.0, 0.0),
            Triple("N_WEST", 250.0, -220.0),
            Triple("N_EAST", 250.0, 220.0),

            // Academic Quad Junctions (Upper Middle)
            Triple("ACAD_NW", 130.0, -110.0),
            Triple("ACAD_NE", 130.0, 110.0),
            Triple("ACAD_NORTH", 140.0, 0.0),

            // Central Campus Plaza & Crossroad (Center)
            Triple("CENTER", 0.0, 0.0),
            Triple("WEST_MID", 0.0, -220.0),
            Triple("EAST_MID", 0.0, 220.0),
            Triple("LIB_WEST", 10.0, -110.0),
            Triple("ADMIN_EAST", 10.0, 110.0),

            // Southern Quad Junctions (Lower Middle)
            Triple("SHELTER_CROSS", -130.0, -110.0),
            Triple("SPORTS_CROSS", -130.0, 110.0),
            Triple("SOUTH_MID", -140.0, 0.0),

            // South Gate & South Perimeter
            Triple("S_GATE", -260.0, 0.0),
            Triple("S_WEST", -250.0, -220.0),
            Triple("S_EAST", -250.0, 220.0),

            // Specialized branches
            Triple("SUBSTATION_NODE", 180.0, 210.0),
            Triple("DEPOT_NODE", -180.0, -210.0)
        )

        val nodesMap = mutableMapOf<String, MapNode>()
        for ((id, dy, dx) in nodeDefs) {
            val (nLat, nLon) = offsetCoord(lat, lon, dy, dx)
            nodesMap[id] = MapNode(id = id, lat = nLat, lon = nLon, name = id.replace("_", " "))
        }

        // ─────────────────────────────────────────────────────────────
        // 2. Road Edges (Bidirectional street segments with realistic paths)
        // ─────────────────────────────────────────────────────────────
        val edgeDefs = listOf(
            // North Perimeter & Gate
            EdgeDef("R_N_PERIM_W", "North Avenue West", "N_WEST", "N_GATE"),
            EdgeDef("R_N_PERIM_E", "North Avenue East", "N_GATE", "N_EAST"),
            EdgeDef("R_N_GATE_ENTRY", "Main Gate Blvd", "N_GATE", "ACAD_NORTH"),

            // West Corridor (North to South)
            EdgeDef("R_WEST_1", "West Campus Way", "N_WEST", "WEST_MID"),
            EdgeDef("R_WEST_2", "West Campus Way", "WEST_MID", "S_WEST"),

            // East Boulevard (North to South)
            EdgeDef("R_EAST_1", "East Boulevard", "N_EAST", "SUBSTATION_NODE"),
            EdgeDef("R_EAST_1B", "East Boulevard", "SUBSTATION_NODE", "EAST_MID"),
            EdgeDef("R_EAST_2", "East Boulevard", "EAST_MID", "S_EAST"),

            // Academic Loop & Quad Roads
            EdgeDef("R_ACAD_NORTH_W", "Academic Drive North", "ACAD_NW", "ACAD_NORTH"),
            EdgeDef("R_ACAD_NORTH_E", "Academic Drive North", "ACAD_NORTH", "ACAD_NE"),
            EdgeDef("R_ACAD_WEST", "Library Walk", "ACAD_NW", "LIB_WEST"),
            EdgeDef("R_ACAD_EAST", "Admin Walkway", "ACAD_NE", "ADMIN_EAST"),
            EdgeDef("R_ACAD_MID_W", "Central Quad", "LIB_WEST", "CENTER"),
            EdgeDef("R_ACAD_MID_E", "Central Quad", "CENTER", "ADMIN_EAST"),

            // Central Crossways
            EdgeDef("R_CENTRAL_WEST", "Main Spine West", "WEST_MID", "LIB_WEST"),
            EdgeDef("R_CENTRAL_EAST", "Main Spine East", "ADMIN_EAST", "EAST_MID"),
            EdgeDef("R_SPINE_NORTH", "University Spine", "ACAD_NORTH", "CENTER"),
            EdgeDef("R_SPINE_SOUTH", "University Spine", "CENTER", "SOUTH_MID"),

            // Southern Quad Roads (Shelter & Sports Access)
            EdgeDef("R_SHELTER_ROAD", "Shelter Access Rd", "LIB_WEST", "SHELTER_CROSS"),
            EdgeDef("R_SPORTS_ROAD", "Pavilion Drive", "ADMIN_EAST", "SPORTS_CROSS"),
            EdgeDef("R_SOUTH_MID_W", "South Ring West", "SHELTER_CROSS", "SOUTH_MID"),
            EdgeDef("R_SOUTH_MID_E", "South Ring East", "SOUTH_MID", "SPORTS_CROSS"),

            // South Perimeter & Gate
            EdgeDef("R_S_GATE_ENTRY", "South Gate Way", "SOUTH_MID", "S_GATE"),
            EdgeDef("R_S_PERIM_W", "South Perimeter West", "S_WEST", "S_GATE"),
            EdgeDef("R_S_PERIM_E", "South Perimeter East", "S_GATE", "S_EAST"),

            // Branches
            EdgeDef("R_DEPOT_SPUR", "Emergency Depot Spur", "SHELTER_CROSS", "DEPOT_NODE"),
            EdgeDef("R_WEST_CONNECTOR", "Campus Link West", "N_WEST", "ACAD_NW"),
            EdgeDef("R_EAST_CONNECTOR", "Campus Link East", "N_EAST", "ACAD_NE")
        )

        val roadsList = edgeDefs.map { def ->
            val fromNode = nodesMap[def.from]!!
            val toNode = nodesMap[def.to]!!
            val path = listOf(Pair(fromNode.lat, fromNode.lon), Pair(toNode.lat, toNode.lon))
            val dist = calculateDistanceMeters(fromNode.lat, fromNode.lon, toNode.lat, toNode.lon)
            MapRoadEdge(
                id = def.id,
                name = def.name,
                fromNodeId = def.from,
                toNodeId = def.to,
                pathPoints = path,
                lengthMeters = dist
            )
        }

        // ─────────────────────────────────────────────────────────────
        // 3. Named Building Footprints (Realistic Polygons)
        // ─────────────────────────────────────────────────────────────
        // Helper to construct rectangular/polygonal building around center (dy, dx)
        fun makeBuilding(
            id: String,
            name: String,
            category: String,
            centerDy: Double,
            centerDx: Double,
            heightMeters: Double,
            widthMeters: Double
        ): MapBuilding {
            val halfH = heightMeters / 2.0
            val halfW = widthMeters / 2.0
            val polyOffsets = listOf(
                Pair(centerDy + halfH, centerDx - halfW), // Top-Left
                Pair(centerDy + halfH, centerDx + halfW), // Top-Right
                Pair(centerDy - halfH, centerDx + halfW), // Bottom-Right
                Pair(centerDy - halfH, centerDx - halfW), // Bottom-Left
                Pair(centerDy + halfH, centerDx - halfW)  // Close ring
            )
            val polyCoords = polyOffsets.map { (dy, dx) -> offsetCoord(lat, lon, dy, dx) }
            val centerCoord = offsetCoord(lat, lon, centerDy, centerDx)
            return MapBuilding(
                id = id,
                name = name,
                category = category,
                polygon = polyCoords,
                center = centerCoord
            )
        }

        val buildingsList = listOf(
            makeBuilding("BLD_ACAD_A", "Academic Block A", "ACAD", 190.0, -80.0, 50.0, 70.0),
            makeBuilding("BLD_ACAD_B", "Lab & Research Complex", "ACAD", 190.0, 80.0, 50.0, 75.0),
            makeBuilding("BLD_ADMIN", "Main Admin Block", "ADMIN", 70.0, 60.0, 55.0, 65.0),
            makeBuilding("BLD_LIB", "Central Library", "ACAD", 70.0, -60.0, 55.0, 65.0),
            makeBuilding("BLD_CAFE", "Central Cafeteria", "FACILITY", -10.0, -70.0, 40.0, 50.0),
            makeBuilding("BLD_SHELTER", "Medical Center / Shelter", "SHELTER", -80.0, -170.0, 55.0, 65.0),
            makeBuilding("BLD_HOSTEL_1", "Hostel Block 1 (North)", "HOSTEL", 120.0, -175.0, 65.0, 45.0),
            makeBuilding("BLD_HOSTEL_2", "Hostel Block 2 (South)", "HOSTEL", -170.0, -175.0, 65.0, 45.0),
            makeBuilding("BLD_SPORTS", "Sports Arena & Pavilion", "FACILITY", -80.0, 170.0, 60.0, 75.0),
            makeBuilding("BLD_SUBSTATION", "Power Substation", "FACILITY", 200.0, 160.0, 35.0, 45.0),
            makeBuilding("BLD_DEPOT", "Emergency Supply Depot", "SHELTER", -200.0, -130.0, 40.0, 50.0),
            makeBuilding("BLD_N_GATE", "North Gate Security", "ADMIN", 280.0, 0.0, 20.0, 35.0),
            makeBuilding("BLD_S_GATE", "South Gate Security", "ADMIN", -280.0, 0.0, 20.0, 35.0)
        )

        return OfflineCampusMap(
            anchorLat = lat,
            anchorLon = lon,
            buildings = buildingsList,
            nodes = nodesMap,
            roads = roadsList
        )
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Spatial Hit-Testing & Nearest Feature Queries
    // ─────────────────────────────────────────────────────────────

    /**
     * Find nearest building to a coordinate within maxDistMeters.
     */
    fun findNearestBuilding(
        lat: Double,
        lon: Double,
        map: OfflineCampusMap,
        maxDistMeters: Double = 40.0
    ): MapBuilding? {
        var closest: MapBuilding? = null
        var minDist = maxDistMeters

        for (b in map.buildings) {
            // Check distance to center
            val distCenter = calculateDistanceMeters(lat, lon, b.center.first, b.center.second)
            if (distCenter < minDist) {
                minDist = distCenter
                closest = b
                continue
            }
            // Check if point is inside polygon or near edge
            if (isPointInPolygon(lat, lon, b.polygon)) {
                return b
            }
        }
        return closest
    }

    /**
     * Find nearest road edge segment to a coordinate within maxDistMeters.
     */
    fun findNearestRoadSegment(
        lat: Double,
        lon: Double,
        map: OfflineCampusMap,
        maxDistMeters: Double = 30.0
    ): MapRoadEdge? {
        var closest: MapRoadEdge? = null
        var minDist = maxDistMeters

        for (road in map.roads) {
            val dist = distanceToSegment(lat, lon, road.pathPoints[0], road.pathPoints[1])
            if (dist < minDist) {
                minDist = dist
                closest = road
            }
        }
        return closest
    }

    /**
     * Calculate Euclidean/Great-circle distance in meters between two lat/lon points.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dy = (lat2 - lat1) * METERS_PER_DEG_LAT
        val dx = (lon2 - lon1) * metersPerDegLon((lat1 + lat2) / 2.0)
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Shortest perpendicular distance in meters from point to line segment.
     */
    fun distanceToSegment(
        pLat: Double,
        pLon: Double,
        a: Pair<Double, Double>,
        b: Pair<Double, Double>
    ): Double {
        val meanLat = (a.first + b.first + pLat) / 3.0
        val cosM = cos(Math.toRadians(meanLat))

        val px = pLon * METERS_PER_DEG_LAT * cosM
        val py = pLat * METERS_PER_DEG_LAT

        val ax = a.second * METERS_PER_DEG_LAT * cosM
        val ay = a.first * METERS_PER_DEG_LAT

        val bx = b.second * METERS_PER_DEG_LAT * cosM
        val by = b.first * METERS_PER_DEG_LAT

        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))

        val t = ((px - ax) * dx + (py - ay) * dy) / lenSq
        val clampedT = t.coerceIn(0.0, 1.0)
        val projX = ax + clampedT * dx
        val projY = ay + clampedT * dy

        return sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY))
    }

    /**
     * Ray-casting algorithm to test if point is inside a polygon.
     */
    fun isPointInPolygon(lat: Double, lon: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val (yi, xi) = polygon[i]
            val (yj, xj) = polygon[j]
            val intersect = ((yi > lat) != (yj > lat)) &&
                    (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private data class EdgeDef(val id: String, val name: String, val from: String, val to: String)
}
