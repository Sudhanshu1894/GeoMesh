package com.example.resilentnet.data.map

import com.example.resilentnet.data.model.HazardMarker
import com.example.resilentnet.data.model.HazardType
import java.util.PriorityQueue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Route planning result containing safe path, turn-by-turn guidance, and detour telemetry.
 */
data class RouteResult(
    val pathCoordinates: List<Pair<Double, Double>>,
    val totalDistanceMeters: Int,
    val estimatedWalkMinutes: Int,
    val isDetour: Boolean,
    val isBlocked: Boolean,
    val avoidedHazards: List<String>,
    val turnByTurnSteps: List<String>,
    val blockedRoadEdges: List<MapRoadEdge>
)

/**
 * Offline A* (A-Star) Pathfinding and Safe-Routing Engine.
 * Dynamically avoids floods, debris, and collapsed buildings reported over the mesh.
 */
object RoutePlanner {

    private const val AVERAGE_WALKING_SPEED_MPS = 1.35 // ~4.8 km/h walking pace

    /**
     * Compute safe route avoiding all active hazards, plus detect detours vs standard direct paths.
     */
    fun calculateSafeRoute(
        start: Pair<Double, Double>,
        destination: Pair<Double, Double>,
        map: OfflineCampusMap,
        activeHazards: List<HazardMarker>
    ): RouteResult {
        // 1. Identify which road segments are compromised by active hazards
        val (blockedRoads, avoidedHazardDescriptions) = evaluateBlockedRoads(map.roads, activeHazards)

        // 2. Snap start & destination to nearest graph nodes
        val startNode = findNearestNode(start.first, start.second, map.nodes.values)
        val endNode = findNearestNode(destination.first, destination.second, map.nodes.values)

        if (startNode == null || endNode == null) {
            return RouteResult(
                pathCoordinates = listOf(start, destination),
                totalDistanceMeters = OfflineMapData.calculateDistanceMeters(start.first, start.second, destination.first, destination.second).roundToInt(),
                estimatedWalkMinutes = 1,
                isDetour = false,
                isBlocked = false,
                avoidedHazards = emptyList(),
                turnByTurnSteps = listOf("Head directly towards destination"),
                blockedRoadEdges = blockedRoads
            )
        }

        // 3. Build adjacency graphs
        // Graph A: Safe Graph (omits blocked roads)
        val blockedIds = blockedRoads.map { it.id }.toSet()
        val safeEdges = map.roads.filter { !blockedIds.contains(it.id) }
        val safeGraph = buildAdjacencyList(safeEdges)

        // Graph B: Raw Graph (all roads, for detour detection)
        val allGraph = buildAdjacencyList(map.roads)

        // 4. Run A* search
        val safePathNodes = runAStar(startNode.id, endNode.id, safeGraph, map.nodes)
        val directPathNodes = runAStar(startNode.id, endNode.id, allGraph, map.nodes)

        // If safe route is completely blocked:
        if (safePathNodes.isEmpty()) {
            return RouteResult(
                pathCoordinates = emptyList(),
                totalDistanceMeters = 0,
                estimatedWalkMinutes = 0,
                isDetour = false,
                isBlocked = true,
                avoidedHazards = avoidedHazardDescriptions,
                turnByTurnSteps = listOf(
                    "⚠️ NO SAFE PATH AVAILABLE",
                    "All connecting routes to this destination are blocked by hazards.",
                    "Wait for alternative route updates over mesh or proceed with extreme caution."
                ),
                blockedRoadEdges = blockedRoads
            )
        }

        // 5. Construct path coordinates (including exact start and destination points)
        val pathCoords = mutableListOf<Pair<Double, Double>>()
        pathCoords.add(start)
        for (nodeId in safePathNodes) {
            map.nodes[nodeId]?.let { pathCoords.add(Pair(it.lat, it.lon)) }
        }
        pathCoords.add(destination)

        // 6. Calculate total route distance
        var totalDist = 0.0
        for (i in 0 until pathCoords.size - 1) {
            val (lat1, lon1) = pathCoords[i]
            val (lat2, lon2) = pathCoords[i + 1]
            totalDist += OfflineMapData.calculateDistanceMeters(lat1, lon1, lat2, lon2)
        }
        val distMeters = totalDist.roundToInt()
        val walkMinutes = (distMeters / (AVERAGE_WALKING_SPEED_MPS * 60.0)).roundToInt().coerceAtLeast(1)

        // 7. Check if safe path is a detour vs direct path
        val isDetour = directPathNodes.isNotEmpty() &&
                (safePathNodes != directPathNodes || avoidedHazardDescriptions.isNotEmpty())

        // 8. Generate Turn-by-Turn instructions
        val steps = generateTurnByTurnSteps(safePathNodes, map, isDetour, avoidedHazardDescriptions)

        return RouteResult(
            pathCoordinates = pathCoords,
            totalDistanceMeters = distMeters,
            estimatedWalkMinutes = walkMinutes,
            isDetour = isDetour,
            isBlocked = false,
            avoidedHazards = avoidedHazardDescriptions,
            turnByTurnSteps = steps,
            blockedRoadEdges = blockedRoads
        )
    }

    /**
     * Inspect all roads against active hazards to identify blocked edges and debris risks.
     */
    private fun evaluateBlockedRoads(
        roads: List<MapRoadEdge>,
        hazards: List<HazardMarker>
    ): Pair<List<MapRoadEdge>, List<String>> {
        val blocked = mutableListOf<MapRoadEdge>()
        val avoidedReasons = mutableSetOf<String>()

        for (road in roads) {
            for (hazard in hazards) {
                val isNearby = when (hazard.type) {
                    HazardType.FLOODED_ROAD, HazardType.OBSTRUCTED_PATH -> {
                        // 20 meter threshold for flooded / blocked paths
                        if (hazard.isRoute) {
                            hazard.points.any { (hLat, hLon) ->
                                OfflineMapData.distanceToSegment(hLat, hLon, road.pathPoints[0], road.pathPoints[1]) <= 22.0
                            }
                        } else {
                            OfflineMapData.distanceToSegment(hazard.latitude, hazard.longitude, road.pathPoints[0], road.pathPoints[1]) <= 22.0
                        }
                    }
                    HazardType.DEMOLISHED_BUILDING -> {
                        // 26 meter debris zone for collapsed buildings
                        OfflineMapData.distanceToSegment(hazard.latitude, hazard.longitude, road.pathPoints[0], road.pathPoints[1]) <= 26.0
                    }
                }

                if (isNearby) {
                    blocked.add(road)
                    avoidedReasons.add("${road.name} (${hazard.type.label}: ${hazard.description})")
                    break
                }
            }
        }
        return Pair(blocked, avoidedReasons.toList())
    }

    /**
     * Core A* Graph Traversal using PriorityQueue and Euclidean/Haversine heuristic.
     */
    private fun runAStar(
        startId: String,
        targetId: String,
        adjList: Map<String, List<EdgeRef>>,
        nodes: Map<String, MapNode>
    ): List<String> {
        if (startId == targetId) return listOf(startId)

        val targetNode = nodes[targetId] ?: return emptyList()

        val gScore = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        gScore[startId] = 0.0

        val fScore = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        fScore[startId] = heuristic(nodes[startId], targetNode)

        val cameFrom = mutableMapOf<String, String>()

        // Priority queue sorted by fScore
        val openSet = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        openSet.add(Pair(startId, fScore[startId]!!))
        val closedSet = mutableSetOf<String>()

        while (openSet.isNotEmpty()) {
            val (currentId, _) = openSet.poll()!!

            if (currentId == targetId) {
                // Reconstruct path
                val path = mutableListOf(targetId)
                var curr = targetId
                while (cameFrom.containsKey(curr)) {
                    curr = cameFrom[curr]!!
                    path.add(0, curr)
                }
                return path
            }

            if (!closedSet.add(currentId)) continue

            val neighbors = adjList[currentId] ?: continue
            for (edge in neighbors) {
                val neighborId = edge.targetNodeId
                if (closedSet.contains(neighborId)) continue

                val tentativeG = gScore[currentId]!! + edge.lengthMeters
                if (tentativeG < gScore.getValue(neighborId)) {
                    cameFrom[neighborId] = currentId
                    gScore[neighborId] = tentativeG
                    val neighborNode = nodes[neighborId]
                    val h = heuristic(neighborNode, targetNode)
                    val newF = tentativeG + h
                    fScore[neighborId] = newF
                    openSet.add(Pair(neighborId, newF))
                }
            }
        }

        return emptyList()
    }

    private fun heuristic(a: MapNode?, b: MapNode?): Double {
        if (a == null || b == null) return 0.0
        return OfflineMapData.calculateDistanceMeters(a.lat, a.lon, b.lat, b.lon)
    }

    private fun findNearestNode(lat: Double, lon: Double, nodes: Collection<MapNode>): MapNode? {
        var closest: MapNode? = null
        var minDist = Double.MAX_VALUE
        for (node in nodes) {
            val dist = OfflineMapData.calculateDistanceMeters(lat, lon, node.lat, node.lon)
            if (dist < minDist) {
                minDist = dist
                closest = node
            }
        }
        return closest
    }

    private fun buildAdjacencyList(roads: List<MapRoadEdge>): Map<String, List<EdgeRef>> {
        val map = mutableMapOf<String, MutableList<EdgeRef>>()
        for (road in roads) {
            map.computeIfAbsent(road.fromNodeId) { mutableListOf() }
                .add(EdgeRef(road.toNodeId, road.lengthMeters, road.name))
            // Bidirectional streets
            map.computeIfAbsent(road.toNodeId) { mutableListOf() }
                .add(EdgeRef(road.fromNodeId, road.lengthMeters, road.name))
        }
        return map
    }

    private data class EdgeRef(val targetNodeId: String, val lengthMeters: Double, val roadName: String)

    /**
     * Generate clear, human-readable turn-by-turn guidance from node sequence.
     */
    private fun generateTurnByTurnSteps(
        nodeIds: List<String>,
        map: OfflineCampusMap,
        isDetour: Boolean,
        avoidedReasons: List<String>
    ): List<String> {
        val steps = mutableListOf<String>()

        if (isDetour && avoidedReasons.isNotEmpty()) {
            steps.add("⚠️ Safe Detour Active: Bypassing ${avoidedReasons.first().take(36)}...")
        }

        if (nodeIds.size <= 1) {
            steps.add("Proceed directly to destination")
            return steps
        }

        for (i in 0 until nodeIds.size - 1) {
            val fromId = nodeIds[i]
            val toId = nodeIds[i + 1]

            val fromNode = map.nodes[fromId]
            val toNode = map.nodes[toId]

            if (fromNode != null && toNode != null) {
                val dist = OfflineMapData.calculateDistanceMeters(fromNode.lat, fromNode.lon, toNode.lat, toNode.lon).roundToInt()
                val road = map.roads.firstOrNull {
                    (it.fromNodeId == fromId && it.toNodeId == toId) ||
                            (it.fromNodeId == toId && it.toNodeId == fromId)
                }
                val roadName = road?.name ?: "Campus Path"

                if (i == 0) {
                    val bearing = computeBearing(fromNode.lat, fromNode.lon, toNode.lat, toNode.lon)
                    val dir = bearingToCompassDir(bearing)
                    steps.add("Start heading $dir along $roadName ($dist m)")
                } else {
                    val prevNode = map.nodes[nodeIds[i - 1]]
                    val turn = if (prevNode != null) {
                        computeTurnInstruction(prevNode, fromNode, toNode)
                    } else "Continue onto"
                    steps.add("$turn $roadName ($dist m)")
                }
            }
        }

        steps.add("🏁 Arrive at destination safely")
        return steps
    }

    private fun computeTurnInstruction(prev: MapNode, curr: MapNode, next: MapNode): String {
        val b1 = computeBearing(prev.lat, prev.lon, curr.lat, curr.lon)
        val b2 = computeBearing(curr.lat, curr.lon, next.lat, next.lon)
        var diff = b2 - b1
        while (diff < -180) diff += 360
        while (diff > 180) diff -= 360

        return when {
            diff in -45.0..45.0 -> "Continue straight on"
            diff in 45.0..135.0 -> "Turn Right onto"
            diff > 135.0 -> "Sharp Right onto"
            diff in -135.0..-45.0 -> "Turn Left onto"
            else -> "Sharp Left onto"
        }
    }

    private fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val y = kotlin.math.sin(dLon) * kotlin.math.cos(rLat2)
        val x = kotlin.math.cos(rLat1) * kotlin.math.sin(rLat2) -
                kotlin.math.sin(rLat1) * kotlin.math.cos(rLat2) * kotlin.math.cos(dLon)

        val deg = Math.toDegrees(atan2(y, x))
        return (deg + 360.0) % 360.0
    }

    private fun bearingToCompassDir(bearing: Double): String {
        return when (((bearing % 360.0) + 360.0) % 360.0) {
            in 337.5..360.0, in 0.0..22.5 -> "North"
            in 22.5..67.5 -> "North-East"
            in 67.5..112.5 -> "East"
            in 112.5..157.5 -> "South-East"
            in 157.5..202.5 -> "South"
            in 202.5..247.5 -> "South-West"
            in 247.5..292.5 -> "West"
            in 292.5..337.5 -> "North-West"
            else -> "North"
        }
    }
}
