package com.example.resilentnet.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import com.example.resilentnet.data.location.LocationProviderHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.resilentnet.data.model.HazardMarker
import com.example.resilentnet.data.model.HazardType
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
import kotlin.math.sqrt

@Composable
fun HazardMapScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hazards by viewModel.hazards.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val locationState by viewModel.locationState.collectAsState()

    var radarRadiusMeters by remember { mutableDoubleStateOf(500.0) }
    var isSimulationMode by remember { mutableStateOf(false) }
    var isRouteMode by remember { mutableStateOf(false) }
    var selectedHazardType by remember { mutableStateOf(HazardType.DEMOLISHED_BUILDING) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var manualLatText by remember { mutableStateOf("") }
    var manualLonText by remember { mutableStateOf("") }
    var reportDescription by remember { mutableStateOf("") }
    val tracedRoutePoints = remember { mutableStateListOf<Pair<Double, Double>>() }

    fun refreshGps() {
        val best = LocationProviderHelper.getBestLastKnownLocation(context)
        if (best != null) {
            viewModel.updateLocation(
                best.latitude,
                best.longitude,
                if (best.hasAccuracy()) best.accuracy else null,
                best.provider ?: "gps"
            )
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

    // Continuous location listener across all available providers with 0m threshold
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

    val centerLat = currentLocation?.first ?: 12.8406
    val centerLon = currentLocation?.second ?: 80.1534

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ResilientMainBg)
    ) {
        // ── Header ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        text = "⚠ Hazard Radar",
                        fontSize = 17.sp,
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
                    text = "📍 ${"%.4f".format(centerLat)}, ${"%.4f".format(centerLon)} (Tap to align/sync)",
                    fontSize = 11.sp,
                    color = ResilientTextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Refresh GPS button
                OutlinedButton(
                    onClick = { refreshGps() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ResilientPrimaryGreen),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🔄 GPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Simulation mode toggle
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
        }

        // ── Hazard Type Selector + Mode ──────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${hazardTypeEmoji(selectedHazardType)} ${selectedHazardType.label}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                DropdownMenu(
                    expanded = showTypeMenu,
                    onDismissRequest = { showTypeMenu = false }
                ) {
                    HazardType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${hazardTypeEmoji(type)} ${type.label}",
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                selectedHazardType = type
                                showTypeMenu = false
                            }
                        )
                    }
                }
            }

            // Route mode toggle
            OutlinedButton(
                onClick = {
                    isRouteMode = !isRouteMode
                    if (!isRouteMode) tracedRoutePoints.clear()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isRouteMode) ResilientInfoBlue else ResilientTextSecondary
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isRouteMode) ResilientInfoBlue else ResilientCardBorder
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isRouteMode) "🛤 Route (${tracedRoutePoints.size})" else "📌 Point",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Tactical Radar Canvas ─────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(300.dp),
            colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isSimulationMode, isRouteMode, radarRadiusMeters) {
                            if (!isSimulationMode) return@pointerInput
                            detectTapGestures { offset ->
                                val canvasW = size.width.toFloat()
                                val canvasH = size.height.toFloat()
                                val cx = canvasW / 2f
                                val cy = canvasH / 2f
                                val scale = min(cx, cy) / radarRadiusMeters.toFloat()

                                // Convert tap pixel offset to lat/lon
                                val dxMeters =
                                    (offset.x - cx) / scale
                                val dyMeters =
                                    -(offset.y - cy) / scale // Y is inverted on canvas
                                val cosLat = cos(Math.toRadians(centerLat))
                                val tapLat =
                                    centerLat + dyMeters / 111320.0
                                val tapLon =
                                    centerLon + dxMeters / (111320.0 * cosLat)

                                if (isRouteMode) {
                                    if (tracedRoutePoints.size < 10) {
                                        tracedRoutePoints.add(Pair(tapLat, tapLon))
                                    }
                                } else {
                                    // Single point — show report dialog
                                    tracedRoutePoints.clear()
                                    tracedRoutePoints.add(Pair(tapLat, tapLon))
                                    showReportDialog = true
                                }
                            }
                        }
                ) {
                    drawRadar(
                        centerLat = centerLat,
                        centerLon = centerLon,
                        radiusMeters = radarRadiusMeters,
                        hazards = hazards,
                        tracedPoints = tracedRoutePoints.toList(),
                        isRouteMode = isRouteMode
                    )
                }

                // Zoom label
                Text(
                    text = "${radarRadiusMeters.toInt()}m",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResilientTextSecondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(ResilientCardBg.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // ── Zoom Slider ────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("100m", fontSize = 10.sp, color = ResilientTextSecondary)
            Slider(
                value = radarRadiusMeters.toFloat(),
                onValueChange = { radarRadiusMeters = it.toDouble() },
                valueRange = 100f..2000f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = ResilientPrimaryGreen,
                    activeTrackColor = ResilientPrimaryGreen,
                    inactiveTrackColor = ResilientCardBorder
                )
            )
            Text("2km", fontSize = 10.sp, color = ResilientTextSecondary)
        }

        // ── Broadcast Route Button ─────────────
        if (isRouteMode && tracedRoutePoints.size >= 2) {
            Button(
                onClick = { showReportDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ResilientInfoBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "📡 Broadcast Route (${tracedRoutePoints.size} points)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // ── Hazard Feed List ──────────────────────
        Text(
            text = "HAZARD REPORTS (${hazards.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ResilientTextSecondary,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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
                                "Radar centered on Node ${hazard.senderId} coordinates!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else null
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
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📡", fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No hazards reported yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ResilientTextMain
                            )
                            Text(
                                text = "Tap on the radar to report a hazard, or wait for mesh broadcasts",
                                fontSize = 12.sp,
                                color = ResilientTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Report Hazard Dialog ─────────────────────
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
                        text = if (isRouteMode && tracedRoutePoints.size >= 2)
                            "Broadcasting ${tracedRoutePoints.size}-point route to all mesh peers"
                        else
                            "Broadcasting point hazard at ${"%.4f".format(tracedRoutePoints.lastOrNull()?.first ?: 0.0)}, ${"%.4f".format(tracedRoutePoints.lastOrNull()?.second ?: 0.0)}",
                        fontSize = 12.sp,
                        color = ResilientTextSecondary
                    )
                    OutlinedTextField(
                        value = reportDescription,
                        onValueChange = { reportDescription = it },
                        label = { Text("Description (optional)") },
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
                            points = tracedRoutePoints.toList(),
                            description = reportDescription.ifBlank { selectedHazardType.label }
                        )
                        showReportDialog = false
                        reportDescription = ""
                        tracedRoutePoints.clear()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ResilientErrorRed)
                ) {
                    Text("📡 BROADCAST", color = Color.White, fontWeight = FontWeight.Bold)
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

    // ── Location & Anchor Dialog ─────────────────
    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = {
                Text(
                    text = "📍 Radar Location & Anchor",
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

                    // Refresh GPS Button
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

                    // Align with peer if hazards exist
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
                                    "Radar anchored to Node ${latestPeerHazard.senderId} coordinates!",
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

                    // Manual coordinates for indoor testing
                    Text(
                        text = "Or enter anchor coordinates manually:",
                        fontSize = 11.sp,
                        color = ResilientTextSecondary
                    )
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
                        Text(
                            text = "Set Custom Radar Center",
                            color = ResilientTextMain,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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
}

// ── Radar Drawing ───────────────────────────────────────────

private fun DrawScope.drawRadar(
    centerLat: Double,
    centerLon: Double,
    radiusMeters: Double,
    hazards: List<HazardMarker>,
    tracedPoints: List<Pair<Double, Double>>,
    isRouteMode: Boolean
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = min(cx, cy) - 12f
    val scale = maxR / radiusMeters.toFloat()

    val ringColor = android.graphics.Color.parseColor("#E4EAE5")
    val ringColorCompose = Color(ringColor)
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#69756E")
        textSize = 22f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    // Concentric range rings
    val rings = listOf(0.25, 0.5, 0.75, 1.0)
    for (fraction in rings) {
        val r = (maxR * fraction).toFloat()
        drawCircle(
            color = ringColorCompose,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
        )
        val labelMeters = (radiusMeters * fraction).toInt()
        drawContext.canvas.nativeCanvas.drawText(
            "${labelMeters}m",
            cx + r - 16f,
            cy - 4f,
            textPaint
        )
    }

    // Crosshair
    val crossColor = Color(0x3021865B)
    drawLine(crossColor, Offset(cx, cy - maxR), Offset(cx, cy + maxR), strokeWidth = 1f)
    drawLine(crossColor, Offset(cx - maxR, cy), Offset(cx + maxR, cy), strokeWidth = 1f)

    // Center dot (you are here)
    drawCircle(Color(0xFF21865B), radius = 6f, center = Offset(cx, cy))
    drawCircle(Color(0x4021865B), radius = 14f, center = Offset(cx, cy))

    val cosLat = cos(Math.toRadians(centerLat))

    // Draw existing hazards
    for (hazard in hazards) {
        if (hazard.isRoute && hazard.points.size >= 2) {
            // Draw route as connected line
            val path = Path()
            var started = false
            for ((lat, lon) in hazard.points) {
                val dx = ((lon - centerLon) * 111320.0 * cosLat * scale).toFloat()
                val dy = -((lat - centerLat) * 111320.0 * scale).toFloat()
                val px = cx + dx
                val py = cy + dy
                if (!started) { path.moveTo(px, py); started = true }
                else path.lineTo(px, py)
            }
            drawPath(
                path = path,
                color = hazardColor(hazard.type),
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Draw dots at route vertices
            for ((lat, lon) in hazard.points) {
                val dx = ((lon - centerLon) * 111320.0 * cosLat * scale).toFloat()
                val dy = -((lat - centerLat) * 111320.0 * scale).toFloat()
                drawCircle(hazardColor(hazard.type), radius = 5f, center = Offset(cx + dx, cy + dy))
            }
        } else {
            // Draw single point hazard
            val dx = ((hazard.longitude - centerLon) * 111320.0 * cosLat * scale).toFloat()
            val dy = -((hazard.latitude - centerLat) * 111320.0 * scale).toFloat()
            val px = cx + dx
            val py = cy + dy

            // Outer glow
            drawCircle(hazardColor(hazard.type).copy(alpha = 0.25f), radius = 18f, center = Offset(px, py))
            // Inner marker
            drawCircle(hazardColor(hazard.type), radius = 9f, center = Offset(px, py))
            // White center
            drawCircle(Color.White, radius = 4f, center = Offset(px, py))
        }
    }

    // Draw traced route points (in-progress)
    if (tracedPoints.isNotEmpty()) {
        if (isRouteMode && tracedPoints.size >= 2) {
            val path = Path()
            for (i in tracedPoints.indices) {
                val (lat, lon) = tracedPoints[i]
                val dx = ((lon - centerLon) * 111320.0 * cosLat * scale).toFloat()
                val dy = -((lat - centerLat) * 111320.0 * scale).toFloat()
                if (i == 0) path.moveTo(cx + dx, cy + dy)
                else path.lineTo(cx + dx, cy + dy)
            }
            drawPath(
                path = path,
                color = Color(0xFF2563EB),
                style = Stroke(
                    width = 3f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                )
            )
        }
        // Draw individual traced dots
        for ((lat, lon) in tracedPoints) {
            val dx = ((lon - centerLon) * 111320.0 * cosLat * scale).toFloat()
            val dy = -((lat - centerLat) * 111320.0 * scale).toFloat()
            drawCircle(Color(0xFF2563EB), radius = 7f, center = Offset(cx + dx, cy + dy))
            drawCircle(Color.White, radius = 3f, center = Offset(cx + dx, cy + dy))
        }
    }
}

private fun hazardColor(type: HazardType): Color = when (type) {
    HazardType.DEMOLISHED_BUILDING -> Color(0xFFD92D35)
    HazardType.FLOODED_ROAD -> Color(0xFF2563EB)
    HazardType.OBSTRUCTED_PATH -> Color(0xFF9A6700)
}

private fun hazardTypeEmoji(type: HazardType): String = when (type) {
    HazardType.DEMOLISHED_BUILDING -> "🏚"
    HazardType.FLOODED_ROAD -> "🌊"
    HazardType.OBSTRUCTED_PATH -> "🚧"
}

// ── Hazard Feed Card ─────────────────────────────────────────

@Composable
private fun HazardFeedCard(
    hazard: HazardMarker,
    distanceMeters: Double,
    onAlignRadar: (() -> Unit)? = null
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
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        hazardColor(hazard.type).copy(alpha = 0.12f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = hazardTypeEmoji(hazard.type),
                    fontSize = 18.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hazard.type.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ResilientTextMain
                )
                Text(
                    text = hazard.description,
                    fontSize = 12.sp,
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

            // Distance badge & Align button
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (distanceMeters < 200) ResilientErrorRed else ResilientTextMain
                )
                if (onAlignRadar != null) {
                    OutlinedButton(
                        onClick = onAlignRadar,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ResilientInfoBlue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ResilientInfoBlue.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            text = "🎯 Align",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = if (hazard.isRoute) "Route" else "Point",
                        fontSize = 10.sp,
                        color = ResilientTextSecondary
                    )
                }
            }
        }
    }
}
