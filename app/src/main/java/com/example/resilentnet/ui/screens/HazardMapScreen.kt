package com.example.resilentnet.ui.screens

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resilentnet.data.location.CompassHelper
import com.example.resilentnet.data.location.LocationProviderHelper
import com.example.resilentnet.data.location.MapOrientationMode
import com.example.resilentnet.data.map.MapBuilding
import com.example.resilentnet.data.map.MapRoadEdge
import com.example.resilentnet.data.map.OfflineCampusMap
import com.example.resilentnet.data.map.OfflineMapData
import com.example.resilentnet.data.map.RoutePlanner
import com.example.resilentnet.data.map.RouteResult
import com.example.resilentnet.data.model.HazardMarker
import com.example.resilentnet.data.model.HazardType
import com.example.resilentnet.data.model.PeerMapState
import com.example.resilentnet.ui.theme.ResilientCardBg
import com.example.resilentnet.ui.theme.ResilientCardBorder
import com.example.resilentnet.ui.theme.ResilientErrorRed
import com.example.resilentnet.ui.theme.ResilientInfoBlue
import com.example.resilentnet.ui.theme.ResilientInputBg
import com.example.resilentnet.ui.theme.ResilientMainBg
import com.example.resilentnet.ui.theme.ResilientPrimaryGreen
import com.example.resilentnet.ui.theme.ResilientSoftGreen
import com.example.resilentnet.ui.theme.ResilientTextMain
import com.example.resilentnet.ui.theme.ResilientTextSecondary
import com.example.resilentnet.ui.theme.ResilientWarningAmber
import com.example.resilentnet.ui.viewmodel.MeshViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline Tactical Vector Map & Safe-Route Navigator.
 * Features:
 * - 100% Offline 2D Vector Map Canvas (Roads, Buildings, Footprints, Labels)
 * - Hardware Compass Sensor (Heading-Up vs North-Up rotation)
 * - Live Peer Tracking with role badges (@LOC| wire piggybacking)
 * - Offline A* Safe-Route Navigation with Hazard Detour Detection
 * - Smart Snap-to-Building & Snap-to-Road hazard marking
 * - Full backward compatibility with location anchor and refresh controls
 */
@Composable
fun HazardMapScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hazards by viewModel.hazards.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val locationState by viewModel.locationState.collectAsState()
    val peerLocations by viewModel.peerLocations.collectAsState()
    val peers by viewModel.peers.collectAsState()

    // Map Center coordinates
    val centerLat = currentLocation?.first ?: OfflineMapData.DEFAULT_ANCHOR_LAT
    val centerLon = currentLocation?.second ?: OfflineMapData.DEFAULT_ANCHOR_LON

    // Dynamic campus vector map anchored to active coordinate
    val campusMap = remember(centerLat, centerLon) {
        OfflineMapData.getMapForAnchor(centerLat, centerLon)
    }

    // Compass Sensor Helper
    val compassHelper = remember { CompassHelper(context) }
    val azimuthDegrees by compassHelper.azimuthDegrees.collectAsState()

    DisposableEffect(Unit) {
        compassHelper.start()
        onDispose { compassHelper.stop() }
    }

    // Interactive Map Canvas State (Pan, Zoom, Orientation)
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var mapRadiusMeters by remember { mutableDoubleStateOf(350.0) }
    var orientationMode by remember { mutableStateOf(MapOrientationMode.NORTH_UP) }

    // Mode toggles
    var isSimulationMode by remember { mutableStateOf(false) }
    var isRouteTraceMode by remember { mutableStateOf(false) }
    var selectedHazardType by remember { mutableStateOf(HazardType.DEMOLISHED_BUILDING) }
    var showTypeMenu by remember { mutableStateOf(false) }

    // Navigation & A* State
    var targetDestinationCoord by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var targetDestinationName by remember { mutableStateOf<String?>(null) }
    var activeRouteResult by remember { mutableStateOf<RouteResult?>(null) }
    var showRouteDialog by remember { mutableStateOf(false) }
    var showDirectionsSheet by remember { mutableStateOf(false) }

    // Dialog & UI states
    var showReportDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var selectedPeerForAction by remember { mutableStateOf<PeerMapState?>(null) }
    var showPeerDialog by remember { mutableStateOf(false) }

    var manualLatText by remember { mutableStateOf("") }
    var manualLonText by remember { mutableStateOf("") }
    var reportDescription by remember { mutableStateOf("") }
    val tracedPoints = remember { mutableStateListOf<Pair<Double, Double>>() }

    // Recalculate A* safe route whenever hazards or destination changes
    LaunchedEffect(targetDestinationCoord, hazards, centerLat, centerLon) {
        val dest = targetDestinationCoord
        if (dest != null) {
            val res = RoutePlanner.calculateSafeRoute(
                start = Pair(centerLat, centerLon),
                destination = dest,
                map = campusMap,
                activeHazards = hazards
            )
            activeRouteResult = res
        } else {
            activeRouteResult = null
        }
    }

    fun refreshGps() {
        val best = LocationProviderHelper.getBestLastKnownLocation(context)
        if (best != null) {
            viewModel.updateLocation(
                best.latitude,
                best.longitude,
                if (best.hasAccuracy()) best.accuracy else null,
                best.provider ?: "gps"
            )
            viewModel.broadcastCurrentLocation(azimuthDegrees)
            val accStr = if (best.hasAccuracy()) " (±${best.accuracy.toInt()}m)" else ""
            Toast.makeText(
                context,
                "GPS: ${"%.4f".format(best.latitude)}, ${"%.4f".format(best.longitude)}$accStr",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(context, "Scanning for GPS / Network fix...", Toast.LENGTH_SHORT).show()
        }
    }

    // Continuous location listener across all available providers
    DisposableEffect(Unit) {
        LocationProviderHelper.getBestLastKnownLocation(context)?.let { loc ->
            viewModel.updateLocation(
                loc.latitude,
                loc.longitude,
                if (loc.hasAccuracy()) loc.accuracy else null,
                loc.provider ?: "cache"
            )
        }

        val pair = LocationProviderHelper.registerLocationUpdates(context, 1000L) { loc ->
            viewModel.updateLocation(
                loc.latitude,
                loc.longitude,
                if (loc.hasAccuracy()) loc.accuracy else null,
                loc.provider ?: "gps"
            )
        }

        onDispose {
            LocationProviderHelper.unregisterLocationUpdates(pair)
        }
    }

    val consolidatedPeers = remember(peerLocations, peers, hazards) {
        viewModel.getConsolidatedPeers()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ResilientMainBg)
    ) {
        // ── Header Bar ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        manualLatText = "%.6f".format(centerLat)
                        manualLonText = "%.6f".format(centerLon)
                        showLocationDialog = true
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🗺 Tactical Vector Map",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResilientTextMain
                    )
                    // Status Badge
                    val acc = locationState.accuracyMeters
                    val badgeColor = when {
                        locationState.isRealFix && acc != null -> ResilientPrimaryGreen
                        locationState.provider.startsWith("peer") || locationState.provider.startsWith("hazard") || locationState.provider == "manual" -> ResilientInfoBlue
                        else -> ResilientWarningAmber
                    }
                    val badgeText = when {
                        locationState.isRealFix && acc != null -> "GPS ±${acc.toInt()}m"
                        locationState.provider.startsWith("peer") || locationState.provider.startsWith("hazard") -> "Anchored"
                        locationState.provider == "manual" -> "Custom"
                        else -> "No Fix"
                    }
                    Box(
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
                Text(
                    text = "📍 ${"%.4f".format(centerLat)}, ${"%.4f".format(centerLon)} (Tap to edit/anchor)",
                    fontSize = 11.sp,
                    color = ResilientTextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // Refresh GPS button
                OutlinedButton(
                    onClick = { refreshGps() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ResilientPrimaryGreen),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "🔄 GPS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                // Safe Route Dialog Button
                Button(
                    onClick = { showRouteDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (targetDestinationCoord != null) ResilientInfoBlue else ResilientPrimaryGreen
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (targetDestinationCoord != null) "🧭 Navigating" else "🧭 Route",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // ── Map Controls & Action Bar ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hazard type dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showTypeMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ResilientTextMain),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${hazardTypeEmoji(selectedHazardType)} ${selectedHazardType.label}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = showTypeMenu,
                    onDismissRequest = { showTypeMenu = false }
                ) {
                    HazardType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text("${hazardTypeEmoji(type)} ${type.label}", fontSize = 12.sp) },
                            onClick = {
                                selectedHazardType = type
                                showTypeMenu = false
                            }
                        )
                    }
                }
            }

            // Route Mode vs Point Mode
            OutlinedButton(
                onClick = {
                    isRouteTraceMode = !isRouteTraceMode
                    if (!isRouteTraceMode) tracedPoints.clear()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isRouteTraceMode) ResilientInfoBlue else ResilientTextSecondary
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isRouteTraceMode) ResilientInfoBlue else ResilientCardBorder
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isRouteTraceMode) "🛤 Route (${tracedPoints.size})" else "📌 Point",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Test Mode Toggle
            OutlinedButton(
                onClick = { isSimulationMode = !isSimulationMode },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isSimulationMode) ResilientWarningAmber else ResilientTextSecondary
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSimulationMode) ResilientWarningAmber else ResilientCardBorder
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isSimulationMode) "🧪 Test: ON" else "🧪 Test: OFF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── 2D Interactive Vector Map Canvas ─────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(310.dp),
            colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // Pan & Pinch-to-zoom gestures
                            detectTransformGestures { _, pan, zoom, _ ->
                                panOffsetX += pan.x
                                panOffsetY += pan.y
                                zoomScale = (zoomScale * zoom).coerceIn(0.4f, 4.0f)
                            }
                        }
                        .pointerInput(isSimulationMode, isRouteTraceMode, mapRadiusMeters, zoomScale, panOffsetX, panOffsetY) {
                            // Tap handling for reporting hazards or selecting nodes
                            detectTapGestures { tapOffset ->
                                val canvasW = size.width.toFloat()
                                val canvasH = size.height.toFloat()
                                val cx = canvasW / 2f + panOffsetX
                                val cy = canvasH / 2f + panOffsetY
                                val maxR = min(canvasW, canvasH) / 2f - 10f
                                val scale = (maxR / mapRadiusMeters.toFloat()) * zoomScale

                                // Invert pan and scale back to lat/lon
                                val dxMeters = (tapOffset.x - cx) / scale
                                val dyMeters = -(tapOffset.y - cy) / scale
                                val cosLat = cos(Math.toRadians(centerLat))
                                val tapLat = centerLat + dyMeters / 111320.0
                                val tapLon = centerLon + dxMeters / (111320.0 * cosLat)

                                if (isSimulationMode) {
                                    // Smart Snap to nearest building or road
                                    val nearBuilding = OfflineMapData.findNearestBuilding(tapLat, tapLon, campusMap, 35.0)
                                    val nearRoad = OfflineMapData.findNearestRoadSegment(tapLat, tapLon, campusMap, 25.0)

                                    if (isRouteTraceMode) {
                                        if (tracedPoints.size < 12) {
                                            tracedPoints.add(Pair(tapLat, tapLon))
                                        }
                                    } else {
                                        tracedPoints.clear()
                                        if (selectedHazardType == HazardType.DEMOLISHED_BUILDING && nearBuilding != null) {
                                            tracedPoints.add(nearBuilding.center)
                                            reportDescription = "Collapsed: ${nearBuilding.name}"
                                        } else if (nearRoad != null) {
                                            tracedPoints.add(Pair(tapLat, tapLon))
                                            reportDescription = "Blocked: ${nearRoad.name}"
                                        } else {
                                            tracedPoints.add(Pair(tapLat, tapLon))
                                            reportDescription = selectedHazardType.label
                                        }
                                        showReportDialog = true
                                    }
                                }
                            }
                        }
                ) {
                    drawVectorMap(
                        centerLat = centerLat,
                        centerLon = centerLon,
                        radiusMeters = mapRadiusMeters,
                        zoomScale = zoomScale,
                        panOffsetX = panOffsetX,
                        panOffsetY = panOffsetY,
                        orientationMode = orientationMode,
                        azimuth = azimuthDegrees,
                        map = campusMap,
                        hazards = hazards,
                        peers = consolidatedPeers,
                        activeRoute = activeRouteResult,
                        tracedPoints = tracedPoints.toList(),
                        isRouteMode = isRouteTraceMode
                    )
                }

                // ── Compass HUD Overlay (Top-Right) ──────────────────
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clickable {
                            orientationMode = if (orientationMode == MapOrientationMode.NORTH_UP)
                                MapOrientationMode.HEADING_UP else MapOrientationMode.NORTH_UP
                        },
                    colors = CardDefaults.cardColors(containerColor = ResilientCardBg.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = CompassHelper.formatHeading(azimuthDegrees),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ResilientTextMain
                        )
                        Box(
                            modifier = Modifier
                                .background(ResilientSoftGreen, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = orientationMode.label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ResilientPrimaryGreen
                            )
                        }
                    }
                }

                // ── Map Floating Controls (Recenter & Zoom) ───────────
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (panOffsetX != 0f || panOffsetY != 0f || zoomScale != 1f) {
                        Button(
                            onClick = {
                                panOffsetX = 0f
                                panOffsetY = 0f
                                zoomScale = 1f
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ResilientCardBg.copy(alpha = 0.9f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🎯 Recenter", fontSize = 10.sp, color = ResilientTextMain, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Scale Bar text
                    Box(
                        modifier = Modifier
                            .background(ResilientCardBg.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        val currentSpanMeters = (mapRadiusMeters / zoomScale).roundToInt()
                        Text(
                            text = "${currentSpanMeters}m",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ResilientTextSecondary
                        )
                    }
                }
            }
        }

        // ── Active Navigation Summary Card ───────────────────────────
        if (activeRouteResult != null) {
            val route = activeRouteResult!!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = if (route.isBlocked) ResilientErrorRed.copy(alpha = 0.1f) else ResilientInfoBlue.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (route.isBlocked) ResilientErrorRed else ResilientInfoBlue.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "To: ${targetDestinationName ?: "Destination"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResilientTextMain
                            )
                            if (route.isBlocked) {
                                Text(
                                    text = "⛔ All connecting paths blocked by hazards!",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ResilientErrorRed
                                )
                            } else {
                                Text(
                                    text = "${route.totalDistanceMeters}m • ~${route.estimatedWalkMinutes} min walk" +
                                            if (route.isDetour) " • ⚠️ Detour Active" else " • Optimal Path",
                                    fontSize = 11.sp,
                                    color = if (route.isDetour) ResilientWarningAmber else ResilientTextSecondary
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { showDirectionsSheet = !showDirectionsSheet },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(if (showDirectionsSheet) "Hide" else "Steps", fontSize = 10.sp)
                            }
                            IconButton(
                                onClick = {
                                    targetDestinationCoord = null
                                    targetDestinationName = null
                                    activeRouteResult = null
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("✕", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ResilientTextSecondary)
                            }
                        }
                    }

                    // Collapsible Turn-by-Turn Steps
                    AnimatedVisibility(visible = showDirectionsSheet) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            HorizontalDivider(color = ResilientCardBorder)
                            Spacer(modifier = Modifier.height(4.dp))
                            route.turnByTurnSteps.forEachIndexed { idx, step ->
                                Text(
                                    text = "${idx + 1}. $step",
                                    fontSize = 11.sp,
                                    color = if (step.startsWith("⚠️")) ResilientWarningAmber else ResilientTextMain,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Hazard & Peer Feed ───────────────────────────────────────
        Text(
            text = "HAZARD REPORTS (${hazards.size}) • PEERS ONLINE (${consolidatedPeers.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ResilientTextSecondary,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(hazards.sortedByDescending { it.timestamp }) { hazard ->
                HazardFeedCard(
                    hazard = hazard,
                    distanceMeters = hazard.distanceTo(centerLat, centerLon),
                    onAlignRadar = if (hazard.senderId != viewModel.myNodeId) {
                        {
                            viewModel.setAnchorCoordinates(
                                hazard.latitude,
                                hazard.longitude,
                                "Node ${hazard.senderId}"
                            )
                            Toast.makeText(
                                context,
                                "Map anchored to Node ${hazard.senderId} coordinates!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else null,
                    onRouteToHazard = {
                        targetDestinationCoord = Pair(hazard.latitude, hazard.longitude)
                        targetDestinationName = hazard.description.ifBlank { hazard.type.label }
                    }
                )
            }

            if (hazards.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🗺", fontSize = 26.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Zero Hazards Reported",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ResilientTextMain
                            )
                            Text(
                                text = "Tap map in Test mode to simulate roads/buildings, or tap Route to navigate.",
                                fontSize = 11.sp,
                                color = ResilientTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Safe Route Selection Dialog ──────────────────────────────────
    if (showRouteDialog) {
        AlertDialog(
            onDismissRequest = { showRouteDialog = false },
            title = {
                Text(
                    text = "🧭 Calculate Safe Route (A*)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResilientTextMain
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select a destination. The A* engine will automatically bypass all flooded roads, debris, and collapsed structures:",
                        fontSize = 12.sp,
                        color = ResilientTextSecondary
                    )

                    Text("CAMPUS BUILDINGS & SHELTERS:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ResilientTextSecondary)
                    campusMap.buildings.take(6).forEach { bld ->
                        OutlinedButton(
                            onClick = {
                                targetDestinationCoord = bld.center
                                targetDestinationName = bld.name
                                showRouteDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ResilientTextMain),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(bld.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                val dist = OfflineMapData.calculateDistanceMeters(centerLat, centerLon, bld.center.first, bld.center.second).roundToInt()
                                Text("${dist}m", fontSize = 11.sp, color = ResilientTextSecondary)
                            }
                        }
                    }

                    if (consolidatedPeers.isNotEmpty()) {
                        Text("ACTIVE MESH PEERS:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ResilientTextSecondary)
                        consolidatedPeers.forEach { peer ->
                            OutlinedButton(
                                onClick = {
                                    targetDestinationCoord = Pair(peer.latitude, peer.longitude)
                                    targetDestinationName = "${peer.displayName} (${peer.role})"
                                    showRouteDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ResilientInfoBlue),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ResilientInfoBlue.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("👤 ${peer.displayName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    val dist = OfflineMapData.calculateDistanceMeters(centerLat, centerLon, peer.latitude, peer.longitude).roundToInt()
                                    Text("${dist}m", fontSize = 11.sp, color = ResilientTextSecondary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRouteDialog = false }) {
                    Text("Close", color = ResilientTextSecondary)
                }
            },
            containerColor = ResilientCardBg,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // ── Report Hazard Dialog ─────────────────────────────────────────
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = {
                showReportDialog = false
                reportDescription = ""
            },
            title = {
                Text(
                    text = "Report ${selectedHazardType.label}",
                    color = ResilientTextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isRouteTraceMode && tracedPoints.size >= 2)
                            "Broadcasting ${tracedPoints.size}-point route to all mesh peers"
                        else
                            "Broadcasting point hazard at ${"%.4f".format(tracedPoints.lastOrNull()?.first ?: 0.0)}, ${"%.4f".format(tracedPoints.lastOrNull()?.second ?: 0.0)}",
                        fontSize = 12.sp,
                        color = ResilientTextSecondary
                    )
                    OutlinedTextField(
                        value = reportDescription,
                        onValueChange = { reportDescription = it },
                        label = { Text("Description / Location Note") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ResilientCardBg,
                            unfocusedContainerColor = ResilientInputBg,
                            focusedBorderColor = ResilientPrimaryGreen,
                            unfocusedBorderColor = ResilientCardBorder
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reportHazard(
                            type = selectedHazardType,
                            points = tracedPoints.toList(),
                            description = reportDescription.ifBlank { selectedHazardType.label }
                        )
                        showReportDialog = false
                        reportDescription = ""
                        tracedPoints.clear()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ResilientErrorRed)
                ) {
                    Text("📡 BROADCAST MESH", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReportDialog = false
                    reportDescription = ""
                }) {
                    Text("Cancel", color = ResilientTextSecondary)
                }
            },
            containerColor = ResilientCardBg,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // ── Location & Anchor Dialog ─────────────────────────────────────
    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = {
                Text(
                    text = "📍 Map Reference Anchor & GPS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResilientTextMain
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val dialogAcc = locationState.accuracyMeters
                    val accText = if (dialogAcc != null) " (±${dialogAcc.toInt()}m)" else ""
                    Text(
                        text = "Center: ${"%.6f".format(centerLat)}, ${"%.6f".format(centerLon)}\n" +
                                "Source: ${locationState.provider}$accText",
                        fontSize = 12.sp,
                        color = ResilientTextSecondary
                    )

                    Button(
                        onClick = {
                            refreshGps()
                            showLocationDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ResilientPrimaryGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🔄 Request Fresh GPS Fix Now", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    val latestPeerHazard = hazards.firstOrNull { it.senderId != viewModel.myNodeId }
                    if (latestPeerHazard != null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.setAnchorCoordinates(
                                    latestPeerHazard.latitude,
                                    latestPeerHazard.longitude,
                                    "Node ${latestPeerHazard.senderId}"
                                )
                                Toast.makeText(
                                    context,
                                    "Map anchored to Node ${latestPeerHazard.senderId} coordinates!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                showLocationDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ResilientInfoBlue),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientInfoBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "🎯 Align with Peer (${latestPeerHazard.senderId})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text("Or enter anchor coordinates manually:", fontSize = 11.sp, color = ResilientTextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualLatText,
                            onValueChange = { manualLatText = it },
                            label = { Text("Latitude", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ResilientPrimaryGreen,
                                unfocusedBorderColor = ResilientCardBorder
                            )
                        )
                        OutlinedTextField(
                            value = manualLonText,
                            onValueChange = { manualLonText = it },
                            label = { Text("Longitude", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ResilientPrimaryGreen,
                                unfocusedBorderColor = ResilientCardBorder
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val lat = manualLatText.toDoubleOrNull()
                            val lon = manualLonText.toDoubleOrNull()
                            if (lat != null && lon != null) {
                                viewModel.setAnchorCoordinates(lat, lon, "manual")
                                Toast.makeText(context, "Anchor coordinates updated!", Toast.LENGTH_SHORT).show()
                                showLocationDialog = false
                            } else {
                                Toast.makeText(context, "Invalid coordinates format", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ResilientInputBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Set Custom Map Center", color = ResilientTextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text("Close", color = ResilientTextSecondary)
                }
            },
            containerColor = ResilientCardBg,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // ── Peer Quick Action Dialog ─────────────────────────────────────
    if (showPeerDialog && selectedPeerForAction != null) {
        val peer = selectedPeerForAction!!
        AlertDialog(
            onDismissRequest = { showPeerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("👤 ${peer.displayName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Box(
                        modifier = Modifier
                            .background(if (peer.role == "ADMIN") ResilientInfoBlue.copy(alpha = 0.15f) else ResilientPrimaryGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(peer.role, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (peer.role == "ADMIN") ResilientInfoBlue else ResilientPrimaryGreen)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val dist = OfflineMapData.calculateDistanceMeters(centerLat, centerLon, peer.latitude, peer.longitude).roundToInt()
                    Text("Node ID: ${peer.peerId}\nDistance: ${dist}m\nCoordinates: ${"%.4f".format(peer.latitude)}, ${"%.4f".format(peer.longitude)}", fontSize = 12.sp, color = ResilientTextSecondary)

                    Button(
                        onClick = {
                            targetDestinationCoord = Pair(peer.latitude, peer.longitude)
                            targetDestinationName = "${peer.displayName} (${peer.role})"
                            showPeerDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ResilientInfoBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🧭 Calculate Safe Route to Peer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.setAnchorCoordinates(peer.latitude, peer.longitude, "Node ${peer.peerId}")
                            Toast.makeText(context, "Radar anchored to ${peer.displayName}!", Toast.LENGTH_SHORT).show()
                            showPeerDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ResilientPrimaryGreen),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ResilientPrimaryGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🎯 Align Anchor with Peer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPeerDialog = false }) {
                    Text("Close", color = ResilientTextSecondary)
                }
            },
            containerColor = ResilientCardBg,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Full Offline Vector Map Drawing Engine
// ─────────────────────────────────────────────────────────────────────

private fun DrawScope.drawVectorMap(
    centerLat: Double,
    centerLon: Double,
    radiusMeters: Double,
    zoomScale: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    orientationMode: MapOrientationMode,
    azimuth: Float,
    map: OfflineCampusMap,
    hazards: List<HazardMarker>,
    peers: List<PeerMapState>,
    activeRoute: RouteResult?,
    tracedPoints: List<Pair<Double, Double>>,
    isRouteMode: Boolean
) {
    val canvasW = size.width
    val canvasH = size.height
    val cx = canvasW / 2f + panOffsetX
    val cy = canvasH / 2f + panOffsetY
    val maxR = min(canvasW, canvasH) / 2f - 10f
    val scale = (maxR / radiusMeters.toFloat()) * zoomScale

    val cosLat = cos(Math.toRadians(centerLat))

    // Helper: translate (lat, lon) to canvas (x, y) relative to center
    fun toScreen(lat: Double, lon: Double): Offset {
        val dx = ((lon - centerLon) * 111320.0 * cosLat * scale).toFloat()
        val dy = -((lat - centerLat) * 111320.0 * scale).toFloat() // Y is inverted on canvas
        return Offset(cx + dx, cy + dy)
    }

    val rotationAngle = if (orientationMode == MapOrientationMode.HEADING_UP) -azimuth else 0f

    rotate(degrees = rotationAngle, pivot = Offset(cx, cy)) {

        // ── Layer 1: Subtle Grid Lines ───────────────────────────────
        val gridStepMeters = (50.0 * scale).toFloat()
        val gridColor = Color(0x18000000)
        var gx = (cx % gridStepMeters)
        while (gx < canvasW + 200f) {
            drawLine(gridColor, Offset(gx - 200f, -200f), Offset(gx - 200f, canvasH + 200f), strokeWidth = 1f)
            gx += gridStepMeters
        }
        var gy = (cy % gridStepMeters)
        while (gy < canvasH + 200f) {
            drawLine(gridColor, Offset(-200f, gy - 200f), Offset(canvasW + 200f, gy - 200f), strokeWidth = 1f)
            gy += gridStepMeters
        }

        // ── Layer 2: Road Network ────────────────────────────────────
        val blockedRoadIds = activeRoute?.blockedRoadEdges?.map { it.id }?.toSet() ?: emptySet()

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#4B5563")
            textSize = 20f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val blockedPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#DC2626")
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        for (road in map.roads) {
            val p1 = toScreen(road.pathPoints[0].first, road.pathPoints[0].second)
            val p2 = toScreen(road.pathPoints[1].first, road.pathPoints[1].second)
            val isBlocked = blockedRoadIds.contains(road.id)

            if (isBlocked) {
                // Red/Amber hatched warning line for blocked roads
                drawLine(
                    color = Color(0xFFDC2626),
                    start = p1,
                    end = p2,
                    strokeWidth = 12f * zoomScale.coerceIn(0.8f, 2.0f),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFFEF2F2),
                    start = p1,
                    end = p2,
                    strokeWidth = 6f * zoomScale.coerceIn(0.8f, 2.0f),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )
                // Draw blocked label at midpoint
                val midX = (p1.x + p2.x) / 2f
                val midY = (p1.y + p2.y) / 2f
                drawContext.canvas.nativeCanvas.drawText("⛔ BLOCKED", midX, midY - 6f, blockedPaint)
            } else {
                // Normal road: casing + asphalt fill
                drawLine(
                    color = Color(0xFFD1D5DB),
                    start = p1,
                    end = p2,
                    strokeWidth = 10f * zoomScale.coerceIn(0.8f, 2.0f),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFF9FAFB),
                    start = p1,
                    end = p2,
                    strokeWidth = 6f * zoomScale.coerceIn(0.8f, 2.0f),
                    cap = StrokeCap.Round
                )
                // Draw street name label if zoom is sufficient
                if (zoomScale >= 0.8f) {
                    val midX = (p1.x + p2.x) / 2f
                    val midY = (p1.y + p2.y) / 2f
                    drawContext.canvas.nativeCanvas.drawText(road.name, midX, midY - 4f, textPaint)
                }
            }
        }

        // ── Layer 3: Building Footprints ─────────────────────────────
        val bldPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1F2937")
            textSize = 21f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val collapsedPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#991B1B")
            textSize = 21f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        for (bld in map.buildings) {
            // Check if collapsed building hazard near this building
            val isCollapsed = hazards.any { h ->
                h.type == HazardType.DEMOLISHED_BUILDING &&
                        OfflineMapData.calculateDistanceMeters(h.latitude, h.longitude, bld.center.first, bld.center.second) <= 30.0
            }

            val path = Path()
            var first = true
            for ((pLat, pLon) in bld.polygon) {
                val pt = toScreen(pLat, pLon)
                if (first) { path.moveTo(pt.x, pt.y); first = false }
                else path.lineTo(pt.x, pt.y)
            }

            if (isCollapsed) {
                // Fill in Red
                drawPath(path, color = Color(0x35DC2626))
                drawPath(path, color = Color(0xFFDC2626), style = Stroke(width = 3f * zoomScale))
                val centerPt = toScreen(bld.center.first, bld.center.second)
                drawContext.canvas.nativeCanvas.drawText("⚠ COLLAPSED", centerPt.x, centerPt.y - 10f, collapsedPaint)
                drawContext.canvas.nativeCanvas.drawText(bld.name, centerPt.x, centerPt.y + 12f, collapsedPaint)
            } else {
                // Normal building
                drawPath(path, color = Color(0xFFE5E7EB))
                drawPath(path, color = Color(0xFF9CA3AF), style = Stroke(width = 1.5f * zoomScale))
                val centerPt = toScreen(bld.center.first, bld.center.second)
                drawContext.canvas.nativeCanvas.drawText(bld.name, centerPt.x, centerPt.y + 6f, bldPaint)
            }
        }

        // ── Layer 4: A* Safe Navigation Route ────────────────────────
        if (activeRoute != null && activeRoute.pathCoordinates.size >= 2) {
            val routePath = Path()
            var startPt: Offset? = null
            activeRoute.pathCoordinates.forEachIndexed { idx, (rLat, rLon) ->
                val pt = toScreen(rLat, rLon)
                if (idx == 0) {
                    routePath.moveTo(pt.x, pt.y)
                    startPt = pt
                } else {
                    routePath.lineTo(pt.x, pt.y)
                }
            }

            val routeColor = if (activeRoute.isDetour) Color(0xFFF59E0B) else Color(0xFF2563EB)

            // Outer glow
            drawPath(
                path = routePath,
                color = routeColor.copy(alpha = 0.3f),
                style = Stroke(width = 10f * zoomScale.coerceIn(0.8f, 2.0f), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Core path
            drawPath(
                path = routePath,
                color = routeColor,
                style = Stroke(width = 5f * zoomScale.coerceIn(0.8f, 2.0f), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Destination pin
            val destCoord = activeRoute.pathCoordinates.last()
            val destPt = toScreen(destCoord.first, destCoord.second)
            drawCircle(Color(0xFFDC2626), radius = 10f, center = destPt)
            drawCircle(Color.White, radius = 4f, center = destPt)
        }

        // ── Layer 5: Hazards ─────────────────────────────────────────
        for (hazard in hazards) {
            val color = hazardColor(hazard.type)
            if (hazard.isRoute && hazard.points.size >= 2) {
                val p = Path()
                var first = true
                for ((hLat, hLon) in hazard.points) {
                    val pt = toScreen(hLat, hLon)
                    if (first) { p.moveTo(pt.x, pt.y); first = false }
                    else p.lineTo(pt.x, pt.y)
                }
                drawPath(p, color = color, style = Stroke(width = 6f, cap = StrokeCap.Round))
                drawPath(p, color = Color.White, style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            } else {
                val pt = toScreen(hazard.latitude, hazard.longitude)
                drawCircle(color.copy(alpha = 0.25f), radius = 16f, center = pt)
                drawCircle(color, radius = 9f, center = pt)
                drawCircle(Color.White, radius = 4f, center = pt)
            }
        }

        // Traced points during manual hazard creation
        if (isRouteMode && tracedPoints.size >= 2) {
            val p = Path()
            var first = true
            for ((tLat, tLon) in tracedPoints) {
                val pt = toScreen(tLat, tLon)
                if (first) { p.moveTo(pt.x, pt.y); first = false }
                else p.lineTo(pt.x, pt.y)
            }
            drawPath(p, color = Color(0xFF2563EB), style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        }

        // ── Layer 6: Live Mesh Peers ─────────────────────────────────
        val peerTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        for (peer in peers) {
            val pt = toScreen(peer.latitude, peer.longitude)
            val peerColor = if (peer.role == "ADMIN") Color(0xFF2563EB) else Color(0xFF10B981)

            // Outer ring
            drawCircle(peerColor.copy(alpha = 0.3f), radius = 18f, center = pt)
            drawCircle(peerColor, radius = 10f, center = pt)
            drawCircle(Color.White, radius = 4f, center = pt)

            // Label
            val label = peer.displayName.take(10)
            drawContext.canvas.nativeCanvas.drawText(label, pt.x, pt.y - 14f, peerTextPaint)
        }

        // ── Layer 7: Self Position Marker & Compass Cone ─────────────
        val selfPt = Offset(cx, cy)

        // Direction cone (representing viewing direction from azimuth)
        val coneAngle = 55.0 // degrees spread
        val coneLen = 45f * zoomScale.coerceIn(0.8f, 2.0f)
        val headingRad = Math.toRadians((if (orientationMode == MapOrientationMode.HEADING_UP) 0f else azimuth).toDouble())

        val leftAngle = headingRad - Math.toRadians(coneAngle / 2.0)
        val rightAngle = headingRad + Math.toRadians(coneAngle / 2.0)

        val conePath = Path().apply {
            moveTo(selfPt.x, selfPt.y)
            lineTo(
                selfPt.x + (coneLen * sin(leftAngle)).toFloat(),
                selfPt.y - (coneLen * cos(leftAngle)).toFloat()
            )
            lineTo(
                selfPt.x + (coneLen * sin(rightAngle)).toFloat(),
                selfPt.y - (coneLen * cos(rightAngle)).toFloat()
            )
            close()
        }
        drawPath(conePath, color = Color(0x3521865B))

        // Center Pulsing Marker
        drawCircle(Color(0x3021865B), radius = 18f, center = selfPt)
        drawCircle(Color(0xFF21865B), radius = 8f, center = selfPt)
        drawCircle(Color.White, radius = 3.5f, center = selfPt)
    }
}

// ── Color & Emoji Helpers ────────────────────────────────────────────

private fun hazardColor(type: HazardType): Color = when (type) {
    HazardType.DEMOLISHED_BUILDING -> ResilientErrorRed
    HazardType.FLOODED_ROAD -> ResilientInfoBlue
    HazardType.OBSTRUCTED_PATH -> ResilientWarningAmber
}

private fun hazardTypeEmoji(type: HazardType): String = when (type) {
    HazardType.DEMOLISHED_BUILDING -> "🏚"
    HazardType.FLOODED_ROAD -> "🌊"
    HazardType.OBSTRUCTED_PATH -> "🚧"
}

// ── Hazard Feed Card ─────────────────────────────────────────────────

@Composable
private fun HazardFeedCard(
    hazard: HazardMarker,
    distanceMeters: Double,
    onAlignRadar: (() -> Unit)? = null,
    onRouteToHazard: (() -> Unit)? = null
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(hazardColor(hazard.type).copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = hazardTypeEmoji(hazard.type), fontSize = 17.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hazard.type.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ResilientTextMain
                )
                Text(
                    text = hazard.description,
                    fontSize = 11.sp,
                    color = ResilientTextSecondary,
                    maxLines = 1
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "From: ${hazard.senderId}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ResilientTextSecondary
                    )
                    Text(
                        text = dateFormat.format(Date(hazard.timestamp)),
                        fontSize = 10.sp,
                        color = ResilientTextSecondary
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val distText = if (distanceMeters < 1000) {
                    "${distanceMeters.toInt()}m"
                } else {
                    "${"%.1f".format(distanceMeters / 1000.0)}km"
                }
                Text(
                    text = distText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (distanceMeters < 200) ResilientErrorRed else ResilientTextMain
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onRouteToHazard != null) {
                        OutlinedButton(
                            onClick = onRouteToHazard,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ResilientInfoBlue),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientInfoBlue.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("🧭 Route", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (onAlignRadar != null) {
                        OutlinedButton(
                            onClick = onAlignRadar,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ResilientPrimaryGreen),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientPrimaryGreen.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("🎯 Align", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
