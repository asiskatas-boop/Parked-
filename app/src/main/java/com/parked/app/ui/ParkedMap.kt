package com.parked.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.parked.app.util.haversine
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** A one-off camera move requested by the map buttons. */
sealed interface MapCommand {
    val id: Int
    data class CenterOnCar(override val id: Int) : MapCommand
    data class ShowBoth(override val id: Int) : MapCommand
}

/**
 * CARTO Voyager: a light, warm basemap built from OpenStreetMap data, which
 * sits well on the cream page. Both credits are shown on Home (osm_attribution).
 */
private val Voyager = object : XYTileSource(
    // New name = new tile cache, so watermarked tiles saved before the key are not reused.
    "CartoVoyagerKeyed", 0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://d.basemaps.cartocdn.com/rastertiles/voyager/",
    ),
    "© OpenStreetMap contributors © CARTO",
) {
    // Since August 2026 CARTO wants a (free) key on every tile request.
    override fun getTileURLString(pMapTileIndex: Long): String {
        val url = super.getTileURLString(pMapTileIndex)
        val key = com.parked.app.BuildConfig.CARTO_KEY
        return if (key.isEmpty()) url else "$url?key=$key"
    }
}

/**
 * The map. It is sized by the caller to the area that is actually visible
 * (above the bottom sheet), so "centre" really means the visible centre.
 */
@Composable
fun ParkedMap(
    parkedLat: Double?, parkedLng: Double?,
    liveLat: Double?, liveLng: Double?,
    parkedMarkerIcon: BitmapDrawable,
    liveMarkerIcon: BitmapDrawable,
    parkedLabel: String,
    liveLabel: String,
    command: MapCommand?,
    modifier: Modifier = Modifier,
    /** Called when the user long-presses and drags the car marker to a new place. */
    onCarMoved: ((Double, Double) -> Unit)? = null,
) {
    val latestOnCarMoved by rememberUpdatedState(onCarMoved)
    val latestParked by rememberUpdatedState(if (parkedLat != null && parkedLng != null) GeoPoint(parkedLat, parkedLng) else null)
    val view = androidx.compose.ui.platform.LocalView.current
    // A drag moves the spot on purpose: keep the camera where the user left it.
    var movedByDrag by remember { mutableStateOf(false) }
    // While a finger holds the marker, recompositions must not snap it back.
    val dragging = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var hasCenteredOnParked by remember { mutableStateOf(false) }
    var hasCenteredOnLive by remember { mutableStateOf(false) }
    var handledCommand by remember { mutableIntStateOf(-1) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(parkedLat, parkedLng) {
        // A newly saved spot becomes the map focus; a dragged one stays put.
        if (movedByDrag) movedByDrag = false else hasCenteredOnParked = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    // A drag cut short by leaving the app must not freeze the pin.
                    if (dragging.getAndSet(false)) {
                        (mapView?.overlays?.firstOrNull { (it as? Marker)?.id == MARKER_CAR } as? CarMarker)
                            ?.let { m -> m.cancelDrag(); latestParked?.let { m.position = it } }
                    }
                    mapView?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(Voyager)
                setBackgroundColor(0xFFF3EEE6.toInt())
                // Warm sand while tiles load, instead of osmdroid's grey grid.
                overlayManager.tilesOverlay.loadingBackgroundColor = 0xFFF3EEE6.toInt()
                overlayManager.tilesOverlay.loadingLineColor = 0xFFE7E5E4.toInt()
                // The map sits inside a scrolling page: while a finger is on
                // it, panning belongs to the map, not to the page.
                setOnTouchListener { v, e ->
                    when (e.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                controller.setZoom(16.0)
                contentDescription = parkedLabel
                // Markers are created once and only moved afterwards.
                overlays.add(CarMarker(this, onCancel = { marker ->
                    // ACTION_CANCEL (back gesture, a call) ends a drag without
                    // onMarkerDragEnd: release it and put the pin back.
                    if (dragging.getAndSet(false)) latestParked?.let { p -> marker.position = p; invalidate() }
                }).apply {
                    id = MARKER_CAR
                    title = parkedLabel
                    icon = parkedMarkerIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    isEnabled = false
                    // Long-press and drag to correct the spot (garages, GPS drift).
                    isDraggable = true
                    setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                        override fun onMarkerDragStart(marker: Marker) {
                            dragging.set(true)
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        }
                        override fun onMarkerDrag(marker: Marker) = Unit
                        override fun onMarkerDragEnd(marker: Marker) {
                            dragging.set(false)
                            val car = marker as CarMarker
                            // A long-press without moving is not a move: osmdroid has
                            // already snapped the pin to the finger, so put it back.
                            // Touch slop, so even a few metres' correction counts.
                            val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                            if (car.fingerTravel() < slop) {
                                latestParked?.let { marker.position = it }
                                invalidate()
                                return
                            }
                            val p = marker.position
                            movedByDrag = true
                            latestOnCarMoved?.invoke(p.latitude, p.longitude)
                        }
                    })
                })
                // "You" never takes a long-press, so the car under it can still be dragged.
                overlays.add(PassiveMarker(this).apply {
                    id = MARKER_YOU
                    title = liveLabel
                    icon = liveMarkerIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    isEnabled = false
                })
                mapView = this
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onResume()
            }
        },
        update = { map ->
            // Compose can still update the view while Home animates out; a map
            // that has been released must not be touched.
            if (mapView !== map) return@AndroidView
            val car = map.overlays.firstOrNull { (it as? Marker)?.id == MARKER_CAR } as? Marker
            val you = map.overlays.firstOrNull { (it as? Marker)?.id == MARKER_YOU } as? Marker

            if (parkedLat != null && parkedLng != null) {
                if (!dragging.get()) car?.position = GeoPoint(parkedLat, parkedLng)
                car?.isEnabled = true
            } else car?.isEnabled = false
            if (liveLat != null && liveLng != null) {
                you?.position = GeoPoint(liveLat, liveLng)
                you?.isEnabled = true
            } else you?.isEnabled = false

            if (!hasCenteredOnParked && parkedLat != null && parkedLng != null) {
                map.controller.setZoom(17.0)
                map.controller.setCenter(GeoPoint(parkedLat, parkedLng))
                hasCenteredOnParked = true
            }
            if (!hasCenteredOnParked && !hasCenteredOnLive && liveLat != null && liveLng != null) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(GeoPoint(liveLat, liveLng))
                hasCenteredOnLive = true
            }
            if (command != null && command.id != handledCommand) {
                handledCommand = command.id
                runCatching {
                    when (command) {
                        is MapCommand.CenterOnCar -> if (parkedLat != null && parkedLng != null) {
                            map.controller.animateTo(GeoPoint(parkedLat, parkedLng), 17.5, 450L)
                        }
                        is MapCommand.ShowBoth -> showBoth(map, parkedLat, parkedLng, liveLat, liveLng)
                    }
                }
            }
            map.invalidate()
        },
        onRelease = { map ->
            mapView = null
            map.onPause()
            map.onDetach()
        }
    )
}

private fun showBoth(map: MapView, parkedLat: Double?, parkedLng: Double?, liveLat: Double?, liveLng: Double?) {
    if (parkedLat == null || parkedLng == null || liveLat == null || liveLng == null) return
    val distance = haversine(parkedLat, parkedLng, liveLat, liveLng)
    if (distance < 40) {
        // Practically the same place: a bounding box would be zero-sized.
        map.controller.animateTo(GeoPoint((parkedLat + liveLat) / 2, (parkedLng + liveLng) / 2), 18.5, 450L)
        return
    }
    val box = BoundingBox.fromGeoPoints(listOf(GeoPoint(parkedLat, parkedLng), GeoPoint(liveLat, liveLng)))
    val border = (minOf(map.width, map.height) * 0.18f).toInt().coerceAtLeast(48)
    map.post {
        if (map.isAttachedToWindow && map.width > 0 && map.height > 0) {
            runCatching { map.zoomToBoundingBox(box, true, border) }
        }
    }
}

/** The car pin: remembers how far the finger moved, and reports a cancelled touch. */
private class CarMarker(map: MapView, private val onCancel: (Marker) -> Unit) : Marker(map) {
    private var downX = 0f
    private var downY = 0f
    private var upX = 0f
    private var upY = 0f

    fun fingerTravel(): Float = kotlin.math.hypot(upX - downX, upY - downY)

    /** Ends osmdroid's own drag state, which it otherwise clears only on ACTION_UP. */
    fun cancelDrag() { mIsDragged = false }

    override fun onTouchEvent(event: android.view.MotionEvent, mapView: MapView): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; upX = downX; upY = downY }
            android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP -> { upX = event.x; upY = event.y }
            android.view.MotionEvent.ACTION_CANCEL -> {
                // Without this, the next ordinary pan would carry the pin along.
                val wasDragging = mIsDragged
                cancelDrag()
                onCancel(this)
                if (wasDragging) return true
            }
        }
        return super.onTouchEvent(event, mapView)
    }

    // A hidden pin (no spot saved) must not start a drag.
    override fun onLongPress(event: android.view.MotionEvent, mapView: MapView): Boolean =
        isEnabled && super.onLongPress(event, mapView)
}

/** A marker that ignores taps and long-presses, so the car under it stays reachable. */
private class PassiveMarker(map: MapView) : Marker(map) {
    override fun onLongPress(event: android.view.MotionEvent, mapView: MapView): Boolean = false
    override fun onSingleTapConfirmed(event: android.view.MotionEvent, mapView: MapView): Boolean = false
}

private const val MARKER_CAR = "car"
private const val MARKER_YOU = "you"

/** 0.8: navy rounded tile with the white car (the tab bar's car), white edge, soft navy halo. */
fun makeParkedMarker(ctx: Context): BitmapDrawable {
    val density = ctx.resources.displayMetrics.density
    val size = (52 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f
    paint.color = 0x29225079
    c.drawCircle(cx, cy, size / 2f, paint)
    val half = 15 * density
    val r = 5 * density
    paint.color = android.graphics.Color.WHITE
    c.drawRoundRect(cx - half - 2 * density, cy - half - 2 * density, cx + half + 2 * density, cy + half + 2 * density, r + 2 * density, r + 2 * density, paint)
    paint.color = 0xFF225079.toInt()
    c.drawRoundRect(cx - half, cy - half, cx + half, cy + half, r, r, paint)
    // The Figma car (29.82 x 26.51 at 182,763), drawn 18 dp wide in the middle.
    runCatching {
        val car = androidx.core.graphics.PathParser.createPathFromPathData(CAR_PATH)
        val k = 18f * density / 29.82f
        val m = android.graphics.Matrix().apply {
            setTranslate(-182f, -763f)
            postScale(k, k)
            postTranslate(cx - 29.82f * k / 2f, cy - 26.508f * k / 2f)
        }
        car.transform(m)
        paint.color = android.graphics.Color.WHITE
        c.drawPath(car, paint)
    }
    return BitmapDrawable(ctx.resources, bmp)
}

private const val CAR_PATH = "M186.97 786.193L186.97 787.022C186.97 787.712 186.729 788.299 186.246 788.783C185.763 789.266 185.176 789.508 184.485 789.507C183.794 789.506 183.207 789.264 182.726 788.783C182.244 788.301 182.002 787.714 182 787.022L182 775.177C182 774.983 182.014 774.79 182.041 774.597C182.069 774.403 182.11 774.224 182.166 774.058L185.272 765.237C185.493 764.574 185.893 764.035 186.473 763.621C187.053 763.207 187.702 763 188.42 763L205.4 763C206.118 763 206.767 763.207 207.347 763.621C207.927 764.035 208.327 764.574 208.548 765.237L211.654 774.058C211.71 774.224 211.751 774.403 211.779 774.597C211.806 774.79 211.82 774.983 211.82 775.177L211.82 787.022C211.82 787.712 211.579 788.299 211.096 788.783C210.613 789.266 210.026 789.508 209.335 789.507C208.644 789.506 208.057 789.264 207.576 788.783C207.094 788.301 206.852 787.714 206.85 787.022L206.85 786.193L186.97 786.193M186.639 771.283L207.181 771.283L205.442 766.313L188.378 766.313L186.639 771.283M189.455 781.223C190.145 781.223 190.732 780.982 191.216 780.499C191.7 780.017 191.941 779.43 191.94 778.738C191.939 778.047 191.698 777.461 191.216 776.979C190.734 776.497 190.147 776.256 189.455 776.253C188.763 776.251 188.176 776.493 187.696 776.979C187.215 777.465 186.973 778.051 186.97 778.738C186.967 779.425 187.209 780.012 187.696 780.499C188.183 780.986 188.769 781.228 189.455 781.223M204.365 781.223C205.055 781.223 205.642 780.982 206.126 780.499C206.61 780.017 206.851 779.43 206.85 778.738C206.849 778.047 206.608 777.461 206.126 776.979C205.645 776.497 205.057 776.256 204.365 776.253C203.673 776.251 203.086 776.493 202.606 776.979C202.125 777.465 201.883 778.051 201.88 778.738C201.877 779.425 202.119 780.012 202.606 780.499C203.093 780.986 203.679 781.228 204.365 781.223"

/** You: the familiar blue dot with a white ring and a soft halo. */
fun makeLiveMarker(ctx: Context): BitmapDrawable {
    val density = ctx.resources.displayMetrics.density
    val size = (32 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f
    paint.color = 0x2E3B82F6
    c.drawCircle(cx, cy, 16 * density, paint)
    paint.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, 9 * density, paint)
    paint.color = 0xFF3B82F6.toInt()
    c.drawCircle(cx, cy, 6.5f * density, paint)
    return BitmapDrawable(ctx.resources, bmp)
}
