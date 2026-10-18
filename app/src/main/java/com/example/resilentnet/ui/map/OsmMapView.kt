package com.example.resilentnet.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.resilentnet.data.map.RoutePlanner
import com.example.resilentnet.data.map.RouteResult
import com.example.resilentnet.data.model.HazardMarker
import com.example.resilentnet.data.model.HazardType
import com.example.resilentnet.data.model.PeerMapState
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Compose wrapper for OSMDroid MapView.
 * Renders real OpenStreetMap tiles (cached offline after first load).
 * Overlays:
 *  - User GPS dot (MyLocationNewOverlay)
 *  - Peer markers (role-coloured)
 *  - Hazard markers (emoji-coloured icons)
 *  - A* safe route polyline (green / orange for detours)
 *  - In-progress traced route line
 *  - Tap receiver for hazard placement
 */
@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    context: Context,
    centerLat: Double,
    centerLon: Double,
    hazards: List<HazardMarker>,
    peers: List<PeerMapState>,
    activeRoute: RouteResult?,
    tracedPoints: List<Pair<Double, Double>>,
    isSimulationMode: Boolean,
    onMapTap: (lat: Double, lon: Double) -> Unit,
    onMapLongPress: ((lat: Double, lon: Double) -> Unit)? = null,
    mapViewRef: (MapView) -> Unit
) {
    // Configure osmdroid user agent once
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = "com.example.resilentnet"
        Configuration.getInstance().osmdroidBasePath =
            context.getExternalFilesDir(null) ?: context.filesDir
        Configuration.getInstance().osmdroidTileCache =
            java.io.File(
                context.getExternalFilesDir(null) ?: context.filesDir,
                "osmdroid"
            )
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(centerLat, centerLon))
            // Rotation gesture for heading-up mode
            overlays.add(RotationGestureOverlay(this).apply { isEnabled = true })
        }
    }

    DisposableEffect(Unit) {
        mapViewRef(mapView)
        onDispose {
            mapView.onDetach()
        }
    }

    // Re-center map when GPS location changes significantly
    LaunchedEffect(centerLat, centerLon) {
        mapView.controller.animateTo(GeoPoint(centerLat, centerLon))
    }

    // Rebuild overlays whenever data changes
    LaunchedEffect(hazards, peers, activeRoute, tracedPoints, isSimulationMode, centerLat, centerLon) {
        rebuildOverlays(
            mapView = mapView,
            context = context,
            centerLat = centerLat,
            centerLon = centerLon,
            hazards = hazards,
            peers = peers,
            activeRoute = activeRoute,
            tracedPoints = tracedPoints,
            isSimulationMode = isSimulationMode,
            onTap = onMapTap,
            onLongPress = onMapLongPress
        )
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { /* LaunchedEffect handles updates */ }
    )
}

private fun rebuildOverlays(
    mapView: MapView,
    context: Context,
    centerLat: Double,
    centerLon: Double,
    hazards: List<HazardMarker>,
    peers: List<PeerMapState>,
    activeRoute: RouteResult?,
    tracedPoints: List<Pair<Double, Double>>,
    isSimulationMode: Boolean,
    onTap: (Double, Double) -> Unit,
    onLongPress: ((Double, Double) -> Unit)?
) {
    // Keep only system overlays (MyLocation, Rotation), remove all custom ones
    val systemOverlays = mapView.overlays.filter {
        it is MyLocationNewOverlay || it is RotationGestureOverlay
    }.toMutableList()
    mapView.overlays.clear()
    mapView.overlays.addAll(systemOverlays)

    // ── Hazard Danger Circles (Semi-transparent exclusion zones) ──────
    for (hazard in hazards) {
        val safeRadius = when (hazard.type) {
            HazardType.DEMOLISHED_BUILDING -> 38.0
            HazardType.FLOODED_ROAD -> 30.0
            HazardType.OBSTRUCTED_PATH -> 24.0
        }
        val circlePoints = createCircleGeoPoints(hazard.latitude, hazard.longitude, safeRadius)
        val circleOverlay = Polygon(mapView).apply {
            points = circlePoints
            fillPaint.color = hazardColor(hazard.type)
            fillPaint.alpha = 45 // Translucent danger zone
            outlinePaint.color = hazardColor(hazard.type)
            outlinePaint.strokeWidth = 3f
            outlinePaint.alpha = 180
            title = "${hazardEmoji(hazard.type)} Danger Zone (${safeRadius.toInt()}m)"
        }
        mapView.overlays.add(circleOverlay)
    }

    // ── A* Route Dual Polyline ────────────────────────────────────────
    activeRoute?.let { route ->
        if (route.pathCoordinates.size >= 2) {
            val lineColor = when {
                route.isBlocked -> android.graphics.Color.parseColor("#DC2626")
                route.isDetour -> android.graphics.Color.parseColor("#D97706")
                else -> android.graphics.Color.parseColor("#16A34A")
            }

            // Outer dark casing polyline for contrast against any OSM tiles
            val casingPolyline = Polyline(mapView).apply {
                setPoints(route.pathCoordinates.map { (lat, lon) -> GeoPoint(lat, lon) })
                outlinePaint.color = android.graphics.Color.parseColor("#0F172A")
                outlinePaint.strokeWidth = 14f
                outlinePaint.alpha = 180
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
            }
            mapView.overlays.add(casingPolyline)

            // Inner vibrant safe path polyline
            val innerPolyline = Polyline(mapView).apply {
                setPoints(route.pathCoordinates.map { (lat, lon) -> GeoPoint(lat, lon) })
                outlinePaint.color = lineColor
                outlinePaint.strokeWidth = 8f
                outlinePaint.alpha = 255
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
            }
            mapView.overlays.add(innerPolyline)

            // Destination pin
            val destCoord = route.pathCoordinates.last()
            val destMarker = Marker(mapView).apply {
                position = GeoPoint(destCoord.first, destCoord.second)
                title = "🏁 Destination"
                snippet = if (route.isDetour) "Safe Detour Route" else "Direct Safe Route"
                icon = createColoredDotDrawable(context, lineColor, 38, hasBorder = true)
            }
            mapView.overlays.add(destMarker)
        }
    }

    // ── Traced Route In-Progress ──────────────────────────────────────
    if (tracedPoints.size >= 2) {
        val traceLine = Polyline(mapView).apply {
            setPoints(tracedPoints.map { (lat, lon) -> GeoPoint(lat, lon) })
            outlinePaint.color = android.graphics.Color.parseColor("#2563EB")
            outlinePaint.strokeWidth = 7f
            outlinePaint.alpha = 180
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }
        mapView.overlays.add(traceLine)
    }

    // ── Hazard Markers ────────────────────────────────────────────────
    for (hazard in hazards) {
        val geoPoint = GeoPoint(hazard.latitude, hazard.longitude)
        val marker = Marker(mapView).apply {
            position = geoPoint
            title = "${hazardEmoji(hazard.type)} ${hazard.type.label}"
            snippet = hazard.description.ifBlank { hazard.type.label }
            icon = createColoredDotDrawable(context, hazardColor(hazard.type), 36)
        }
        mapView.overlays.add(marker)

        // Draw path for route hazards
        if (hazard.isRoute && hazard.points.size >= 2) {
            val hazardLine = Polyline(mapView).apply {
                setPoints(hazard.points.map { (lat, lon) -> GeoPoint(lat, lon) })
                outlinePaint.color = hazardColor(hazard.type)
                outlinePaint.strokeWidth = 8f
                outlinePaint.alpha = 180
            }
            mapView.overlays.add(hazardLine)
        }
    }

    // ── Peer Markers ──────────────────────────────────────────────────
    for (peer in peers) {
        val color = if (peer.role == "ADMIN")
            android.graphics.Color.parseColor("#2563EB")
        else
            android.graphics.Color.parseColor("#16A34A")

        val marker = Marker(mapView).apply {
            position = GeoPoint(peer.latitude, peer.longitude)
            title = peer.displayName
            snippet = "Role: ${peer.role} • Active Node"
            icon = createColoredDotDrawable(context, color, 30)
        }
        mapView.overlays.add(marker)
    }

    // ── User Location Dot ─────────────────────────────────────────────
    val myMarker = Marker(mapView).apply {
        position = GeoPoint(centerLat, centerLon)
        title = "You"
        snippet = "My Location"
        icon = createColoredDotDrawable(context, android.graphics.Color.parseColor("#16A34A"), 42, hasBorder = true)
    }
    mapView.overlays.add(myMarker)

    // ── Tap & Long-Press Handler ──────────────────────────────────────
    val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
            onTap(p.latitude, p.longitude)
            return true
        }
        override fun longPressHelper(p: GeoPoint): Boolean {
            if (onLongPress != null) {
                onLongPress(p.latitude, p.longitude)
                return true
            }
            return false
        }
    })
    mapView.overlays.add(tapOverlay)

    mapView.invalidate()
}

private fun createCircleGeoPoints(centerLat: Double, centerLon: Double, radiusMeters: Double): List<GeoPoint> {
    val points = mutableListOf<GeoPoint>()
    for (i in 0 until 36) {
        val angle = Math.toRadians(i * 10.0)
        val dy = radiusMeters * kotlin.math.cos(angle)
        val dx = radiusMeters * kotlin.math.sin(angle)
        val lat = centerLat + dy / 111320.0
        val lon = centerLon + dx / (111320.0 * kotlin.math.cos(Math.toRadians(centerLat)))
        points.add(GeoPoint(lat, lon))
    }
    return points
}

private fun hazardEmoji(type: HazardType): String = when (type) {
    HazardType.DEMOLISHED_BUILDING -> "🏚"
    HazardType.FLOODED_ROAD -> "🌊"
    HazardType.OBSTRUCTED_PATH -> "🚧"
}

private fun hazardColor(type: HazardType): Int = when (type) {
    HazardType.DEMOLISHED_BUILDING -> android.graphics.Color.parseColor("#DC2626")
    HazardType.FLOODED_ROAD -> android.graphics.Color.parseColor("#2563EB")
    HazardType.OBSTRUCTED_PATH -> android.graphics.Color.parseColor("#D97706")
}

/**
 * Creates a simple filled circle bitmap Drawable for use as a marker icon.
 */
private fun createColoredDotDrawable(
    context: Context,
    color: Int,
    sizeDp: Int,
    hasBorder: Boolean = false
): Drawable {
    val scale = context.resources.displayMetrics.density
    val sizePx = (sizeDp * scale).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val radius = sizePx / 2f - 4f
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, paint)
    if (hasBorder) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, borderPaint)
    }
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}
