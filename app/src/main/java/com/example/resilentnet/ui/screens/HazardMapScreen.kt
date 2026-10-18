package com.example.resilentnet.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resilentnet.data.location.LocationProviderHelper
import com.example.resilentnet.data.map.OfflineMapData
import com.example.resilentnet.data.map.RoutePlanner
import com.example.resilentnet.data.map.RouteResult
import com.example.resilentnet.data.model.HazardMarker
import com.example.resilentnet.data.model.HazardType
import com.example.resilentnet.data.model.PeerMapState
import com.example.resilentnet.ui.map.OsmMapView
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
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.roundToInt

/**
 * HazardMapScreen — Full-Screen Tactical Map with Real-Time Spatial A* Safe Routing.
 * Designed with a clean, floating non-overlapping UI:
 *  - Immersive full-bleed OpenStreetMap canvas (cached offline)
 *  - Floating top control bar with GPS status, route launcher, and mode chips
 *  - Interactive long-press context sheet: Navigate or Report Hazard anywhere on the real map
 *  - Smart navigation banner with turn-by-turn guidance and dynamic detour reporting
 *  - Collapsible hazard feed bottom sheet
 *  - Floating recenter FAB with collision-free padding
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

    val centerLat = currentLocation?.first ?: OfflineMapData.DEFAULT_ANCHOR_LAT
    val centerLon = currentLocation?.second ?: OfflineMapData.DEFAULT_ANCHOR_LON

    val campusMap = remember(centerLat, centerLon) {
        OfflineMapData.getMapForAnchor(centerLat, centerLon)
    }

    var osmMapView by remember { mutableStateOf<MapView?>(null) }

    // Navigation and A* route state
    var targetDestCoord by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var targetDestName by remember { mutableStateOf<String?>(null) }
    var activeRoute by remember { mutableStateOf<RouteResult?>(null) }
    var showRouteDialog by remember { mutableStateOf(false) }
    var showDirections by remember { mutableStateOf(false) }

    // Interactivity state
    var selectedMapCoord by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var isSimulationMode by remember { mutableStateOf(false) }
    var isRouteTraceMode by remember { mutableStateOf(false) }
    var showHazardFeedSheet by remember { mutableStateOf(false) }

    // Hazard reporting state
    var showReportDialog by remember { mutableStateOf(false) }
    var reportInitialCoord by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var selectedHazardType by remember { mutableStateOf(HazardType.DEMOLISHED_BUILDING) }
    var reportDescription by remember { mutableStateOf("") }
    val tracedPoints = remember { mutableStateListOf<Pair<Double, Double>>() }

    val consolidatedPeers = remember(peerLocations, peers, hazards) {
        viewModel.getConsolidatedPeers()
    }

    // Dynamic A* recalculation whenever destination, hazards, or location changes
    LaunchedEffect(targetDestCoord, hazards, centerLat, centerLon) {
        val dest = targetDestCoord
        activeRoute = if (dest != null) {
            RoutePlanner.calculateSafeRoute(
                start = Pair(centerLat, centerLon),
                destination = dest,
                map = campusMap,
                activeHazards = hazards
            )
        } else null
    }

    // Continuous location tracking
    DisposableEffect(Unit) {
        LocationProviderHelper.getBestLastKnownLocation(context)?.let { loc ->
            viewModel.updateLocation(
                loc.latitude, loc.longitude,
                if (loc.hasAccuracy()) loc.accuracy else null,
                loc.provider ?: "cache"
            )
        }
        val pair = LocationProviderHelper.registerLocationUpdates(context, 2000L) { loc ->
            viewModel.updateLocation(
                loc.latitude, loc.longitude,
                if (loc.hasAccuracy()) loc.accuracy else null,
                loc.provider ?: "gps"
            )
        }
        onDispose { LocationProviderHelper.unregisterLocationUpdates(pair) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ResilientMainBg)
    ) {
        // ── 1. Hero Full-Bleed Map Canvas ────────────────────────────────────
        OsmMapView(
            modifier = Modifier.fillMaxSize(),
            context = context,
            centerLat = centerLat,
            centerLon = centerLon,
            hazards = hazards,
            peers = consolidatedPeers,
            activeRoute = activeRoute,
            tracedPoints = tracedPoints.toList(),
            isSimulationMode = isSimulationMode,
            onMapTap = { lat, lon ->
                if (isRouteTraceMode) {
                    if (tracedPoints.size < 25) tracedPoints.add(Pair(lat, lon))
                } else if (isSimulationMode) {
                    selectedMapCoord = Pair(lat, lon)
                } else {
                    // In standard mode, tapping sets context sheet for inspection
                    selectedMapCoord = Pair(lat, lon)
                }
            },
            onMapLongPress = { lat, lon ->
                // Long press anywhere on the real map opens action sheet
                selectedMapCoord = Pair(lat, lon)
            },
            mapViewRef = { osmMapView = it }
        )

        // ── 2. Floating Top Header Card ──────────────────────────────────────
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = ResilientCardBg.copy(alpha = 0.96f)),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top Row: Title, GPS Status & Refresh Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🗺 Tactical Map",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ResilientTextMain
                        )
                        // GPS accuracy pill
                        val gpsText = when {
                            locationState.isRealFix && locationState.accuracyMeters != null ->
                                "GPS ±${locationState.accuracyMeters!!.toInt()}m"
                            locationState.provider == "manual" -> "Custom Fix"
                            else -> "Scanning GPS"
                        }
                        val gpsColor = when {
                            locationState.isRealFix -> ResilientPrimaryGreen
                            locationState.provider == "manual" -> ResilientInfoBlue
                            else -> ResilientWarningAmber
                        }
                        Box(
                            modifier = Modifier
                                .background(gpsColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .border(1.dp, gpsColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "📡 $gpsText",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = gpsColor
                            )
                        }
                    }

                    // Recenter GPS Button
                    TextButton(
                        onClick = {
                            val best = LocationProviderHelper.getBestLastKnownLocation(context)
                            if (best != null) {
                                viewModel.updateLocation(
                                    best.latitude, best.longitude,
                                    if (best.hasAccuracy()) best.accuracy else null,
                                    best.provider ?: "gps"
                                )
                                osmMapView?.controller?.animateTo(GeoPoint(best.latitude, best.longitude))
                                Toast.makeText(context, "Centered on GPS: %.4f, %.4f".format(best.latitude, best.longitude), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Scanning for GPS fix...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("🔄 Sync", fontSize = 11.sp, color = ResilientPrimaryGreen, fontWeight = FontWeight.Bold)
                    }
                }

                // Bottom Action Row: Route Selector, Report Button, Mode Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Navigate / Route Button
                    Button(
                        onClick = { showRouteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ResilientPrimaryGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Text("🧭 Navigate", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Report Hazard Button
                    Button(
                        onClick = {
                            reportInitialCoord = Pair(centerLat, centerLon)
                            showReportDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ResilientErrorRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⚠️ Report", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Hazard Feed Toggle Chip
                    OutlinedButton(
                        onClick = { showHazardFeedSheet = !showHazardFeedSheet },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (showHazardFeedSheet) ResilientInfoBlue.copy(0.12f) else ResilientInputBg
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (showHazardFeedSheet) ResilientInfoBlue else ResilientCardBorder
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "📋 ${hazards.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showHazardFeedSheet) ResilientInfoBlue else ResilientTextMain
                        )
                    }

                    // Simulation Test Mode Chip
                    OutlinedButton(
                        onClick = {
                            isSimulationMode = !isSimulationMode
                            if (!isSimulationMode) isRouteTraceMode = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSimulationMode) ResilientWarningAmber.copy(0.15f) else ResilientInputBg
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSimulationMode) ResilientWarningAmber else ResilientCardBorder
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isSimulationMode) "🧪 On" else "🧪 Sim",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSimulationMode) ResilientWarningAmber else ResilientTextSecondary
                        )
                    }
                }
            }
        }

        // ── 3. Active Navigation Banner ──────────────────────────────────────
        AnimatedVisibility(
            visible = activeRoute != null,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 110.dp, start = 12.dp, end = 12.dp)
        ) {
            activeRoute?.let { route ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (route.isBlocked) ResilientErrorRed.copy(0.95f)
                        else if (route.isDetour) ResilientCardBg.copy(0.96f)
                        else ResilientCardBg.copy(0.96f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (route.isBlocked) ResilientErrorRed
                        else if (route.isDetour) ResilientWarningAmber
                        else ResilientPrimaryGreen
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🏁 ${targetDestName ?: "Active Destination"}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (route.isBlocked) Color.White else ResilientTextMain
                                )
                                val statusText = when {
                                    route.isBlocked -> "⛔ ALL PATHS BLOCKED BY HAZARDS"
                                    route.isDetour -> "⚠️ Safe Detour: ${route.totalDistanceMeters}m • ~${route.estimatedWalkMinutes} min"
                                    else -> "✅ Safe Route: ${route.totalDistanceMeters}m • ~${route.estimatedWalkMinutes} min"
                                }
                                Text(
                                    text = statusText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = when {
                                        route.isBlocked -> Color.White.copy(0.9f)
                                        route.isDetour -> ResilientWarningAmber
                                        else -> ResilientPrimaryGreen
                                    }
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { showDirections = !showDirections },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (showDirections) "Hide" else "Steps",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (route.isBlocked) Color.White else ResilientInfoBlue
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        targetDestCoord = null
                                        targetDestName = null
                                        activeRoute = null
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("✕ Exit", fontSize = 11.sp, color = if (route.isBlocked) Color.White else ResilientErrorRed)
                                }
                            }
                        }

                        // Turn-by-Turn expandable steps
                        AnimatedVisibility(visible = showDirections) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                HorizontalDivider(color = ResilientCardBorder)
                                Spacer(Modifier.height(6.dp))
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(route.turnByTurnSteps) { step ->
                                        Text(
                                            text = "• $step",
                                            fontSize = 11.sp,
                                            color = if (step.startsWith("⚠️") || step.startsWith("⛔"))
                                                ResilientWarningAmber else ResilientTextMain,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 4. Route Tracing Banner (when tracing in Simulation mode) ────────
        if (isSimulationMode && isRouteTraceMode) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 80.dp),
                colors = CardDefaults.cardColors(containerColor = ResilientCardBg.copy(0.95f)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ResilientInfoBlue),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🛤 Tracing Hazard: ${tracedPoints.size} pts",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResilientInfoBlue
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { tracedPoints.clear() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear", fontSize = 11.sp, color = ResilientTextSecondary)
                        }
                        Button(
                            onClick = {
                                if (tracedPoints.size >= 2) {
                                    reportInitialCoord = tracedPoints.first()
                                    showReportDialog = true
                                } else {
                                    Toast.makeText(context, "Tap at least 2 points along the path", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ResilientErrorRed),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Submit Trace", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // ── 5. Recenter Floating Action Button (FAB) ─────────────────────────
        val fabBottomPadding = when {
            showHazardFeedSheet -> 280.dp
            selectedMapCoord != null -> 160.dp
            isSimulationMode && isRouteTraceMode -> 140.dp
            else -> 24.dp
        }
        Button(
            onClick = {
                osmMapView?.controller?.animateTo(GeoPoint(centerLat, centerLon))
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = fabBottomPadding),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = ResilientCardBg),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, ResilientCardBorder),
            contentPadding = PaddingValues(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
        ) {
            Text("🎯", fontSize = 20.sp)
        }

        // ── 6. Long-Press / Map Tap Context Action Card ───────────────────────
        selectedMapCoord?.let { coord ->
            val distFromUser = OfflineMapData.calculateDistanceMeters(
                centerLat, centerLon, coord.first, coord.second
            ).roundToInt()

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "📍 Selected Map Location",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResilientTextMain
                            )
                            Text(
                                text = "%.4f, %.4f • %dm away".format(coord.first, coord.second, distFromUser),
                                fontSize = 11.sp,
                                color = ResilientTextSecondary
                            )
                        }
                        TextButton(
                            onClick = { selectedMapCoord = null },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("✕", fontSize = 16.sp, color = ResilientTextSecondary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Route Here Button
                        Button(
                            onClick = {
                                targetDestCoord = coord
                                targetDestName = "Pin (%.4f, %.4f)".format(coord.first, coord.second)
                                selectedMapCoord = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ResilientPrimaryGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("🧭 Route Here", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // Report Hazard Here Button
                        Button(
                            onClick = {
                                reportInitialCoord = coord
                                showReportDialog = true
                                selectedMapCoord = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ResilientErrorRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("⚠️ Report Hazard", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // ── 7. Collapsible Hazard Feed Bottom Sheet ──────────────────────────
        AnimatedVisibility(
            visible = showHazardFeedSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️ Active Mesh Hazards (${hazards.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ResilientTextMain
                        )
                        TextButton(
                            onClick = { showHazardFeedSheet = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("✕ Close", fontSize = 12.sp, color = ResilientTextSecondary)
                        }
                    }
                    Spacer(Modifier.height(6.dp))

                    if (hazards.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No hazards reported in local mesh area.", fontSize = 12.sp, color = ResilientTextSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(hazards.sortedByDescending { it.timestamp }) { hazard ->
                                val dist = OfflineMapData.calculateDistanceMeters(
                                    centerLat, centerLon, hazard.latitude, hazard.longitude
                                ).roundToInt()

                                HazardFeedCard(
                                    hazard = hazard,
                                    distanceMeters = dist.toDouble(),
                                    onRouteToHazard = {
                                        targetDestCoord = Pair(hazard.latitude, hazard.longitude)
                                        targetDestName = hazard.description.ifBlank { hazard.type.label }
                                        showHazardFeedSheet = false
                                    },
                                    onCenterOnHazard = {
                                        osmMapView?.controller?.animateTo(GeoPoint(hazard.latitude, hazard.longitude))
                                        showHazardFeedSheet = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Route Selection Dialog ────────────────────────────────────────────────
    if (showRouteDialog) {
        RouteSelectionDialog(
            campusMap = campusMap,
            consolidatedPeers = consolidatedPeers,
            centerLat = centerLat,
            centerLon = centerLon,
            onSelect = { coord, name ->
                targetDestCoord = coord
                targetDestName = name
                showRouteDialog = false
            },
            onDismiss = { showRouteDialog = false }
        )
    }

    // ── Hazard Report Dialog ──────────────────────────────────────────────────
    if (showReportDialog) {
        val targetCoord = reportInitialCoord ?: Pair(centerLat, centerLon)
        HazardReportDialog(
            hazardType = selectedHazardType,
            coordinate = targetCoord,
            tracedPoints = tracedPoints.toList(),
            isRouteMode = isRouteTraceMode,
            description = reportDescription,
            onTypeSelect = { selectedHazardType = it },
            onDescriptionChange = { reportDescription = it },
            onConfirm = {
                val pts = if (isRouteTraceMode && tracedPoints.isNotEmpty()) tracedPoints.toList() else listOf(targetCoord)
                viewModel.reportHazard(
                    type = selectedHazardType,
                    points = pts,
                    description = reportDescription.ifBlank { selectedHazardType.label }
                )
                showReportDialog = false
                reportDescription = ""
                tracedPoints.clear()
                isRouteTraceMode = false
                Toast.makeText(context, "Hazard broadcasted to mesh peers", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showReportDialog = false
                reportDescription = ""
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Subcomponents & Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RouteSelectionDialog(
    campusMap: com.example.resilentnet.data.map.OfflineCampusMap,
    consolidatedPeers: List<PeerMapState>,
    centerLat: Double,
    centerLon: Double,
    onSelect: (Pair<Double, Double>, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🧭 Select Navigation Target",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ResilientTextMain
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(340.dp)
            ) {
                item {
                    Text(
                        "CAMPUS LANDMARKS & SHELTERS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResilientTextSecondary,
                        letterSpacing = 0.8.sp
                    )
                }
                items(campusMap.buildings) { bld ->
                    val dist = OfflineMapData.calculateDistanceMeters(
                        centerLat, centerLon, bld.center.first, bld.center.second
                    ).roundToInt()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(bld.center, bld.name) },
                        colors = CardDefaults.cardColors(containerColor = ResilientInputBg),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(bld.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ResilientTextMain)
                            Text("${dist}m", fontSize = 11.sp, color = ResilientPrimaryGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (consolidatedPeers.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "REACHABLE MESH PEERS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ResilientTextSecondary,
                            letterSpacing = 0.8.sp
                        )
                    }
                    items(consolidatedPeers) { peer ->
                        val dist = OfflineMapData.calculateDistanceMeters(
                            centerLat, centerLon, peer.latitude, peer.longitude
                        ).roundToInt()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(Pair(peer.latitude, peer.longitude), "${peer.displayName} (${peer.role})")
                                },
                            colors = CardDefaults.cardColors(containerColor = ResilientInfoBlue.copy(0.08f)),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientInfoBlue.copy(0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("👤 ${peer.displayName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ResilientInfoBlue)
                                Text("${dist}m", fontSize = 11.sp, color = ResilientTextSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = ResilientTextSecondary) }
        },
        containerColor = ResilientCardBg,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun HazardReportDialog(
    hazardType: HazardType,
    coordinate: Pair<Double, Double>,
    tracedPoints: List<Pair<Double, Double>>,
    isRouteMode: Boolean,
    description: String,
    onTypeSelect: (HazardType) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(hazardColor(hazardType).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(hazardEmoji(hazardType), fontSize = 18.sp)
                }
                Column {
                    Text("Report Hazard", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ResilientTextMain)
                    Text("Broadcasted peer-to-peer to all mesh nodes", fontSize = 11.sp, color = ResilientTextSecondary)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "SELECT HAZARD TYPE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResilientTextSecondary,
                    letterSpacing = 0.6.sp
                )

                // Hazard Type Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HazardType.entries.forEach { type ->
                        val isSelected = type == hazardType
                        val color = hazardColor(type)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) color.copy(alpha = 0.15f) else ResilientInputBg,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) color else ResilientCardBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onTypeSelect(type) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(hazardEmoji(type), fontSize = 16.sp)
                                Text(
                                    type.label.take(8),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) color else ResilientTextSecondary
                                )
                            }
                        }
                    }
                }

                val coordInfo = if (isRouteMode && tracedPoints.size >= 2) {
                    "Multi-point route hazard (${tracedPoints.size} waypoints)"
                } else {
                    "Coordinate: %.4f, %.4f".format(coordinate.first, coordinate.second)
                }
                Text(coordInfo, fontSize = 11.sp, color = ResilientTextSecondary)

                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Details (e.g., Deep flood on South Road)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ResilientPrimaryGreen,
                        unfocusedBorderColor = ResilientCardBorder,
                        focusedContainerColor = ResilientCardBg,
                        unfocusedContainerColor = ResilientInputBg
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ResilientErrorRed),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("📡 Broadcast Hazard", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ResilientTextSecondary) }
        },
        containerColor = ResilientCardBg,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun HazardFeedCard(
    hazard: HazardMarker,
    distanceMeters: Double,
    onRouteToHazard: () -> Unit,
    onCenterOnHazard: () -> Unit
) {
    val typeColor = hazardColor(hazard.type)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = typeColor.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, typeColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(typeColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(hazardEmoji(hazard.type), fontSize = 16.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(hazard.type.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ResilientTextMain)
                val desc = hazard.description.ifBlank { "Active obstacle" }
                Text(desc, fontSize = 11.sp, color = ResilientTextSecondary, maxLines = 1)
                val distText = if (distanceMeters < 1000) "${distanceMeters.roundToInt()}m away"
                else "${"%.1f".format(distanceMeters / 1000)}km away"
                Text(
                    "Node ${hazard.senderId} • $distText",
                    fontSize = 10.sp,
                    color = ResilientTextSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onRouteToHazard,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Avoid", fontSize = 11.sp, color = ResilientPrimaryGreen, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onCenterOnHazard,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("🎯", fontSize = 13.sp)
                }
            }
        }
    }
}

private fun hazardEmoji(type: HazardType): String = when (type) {
    HazardType.DEMOLISHED_BUILDING -> "🏚"
    HazardType.FLOODED_ROAD -> "🌊"
    HazardType.OBSTRUCTED_PATH -> "🚧"
}

private fun hazardColor(type: HazardType): Color = when (type) {
    HazardType.DEMOLISHED_BUILDING -> ResilientErrorRed
    HazardType.FLOODED_ROAD -> ResilientInfoBlue
    HazardType.OBSTRUCTED_PATH -> ResilientWarningAmber
}
