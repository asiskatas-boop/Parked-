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
 * CARTO Dark Matter: a dark basemap built from OpenStreetMap data. Both
 * credits are shown on Home (osm_attribution).
 */
private val DarkMatter = XYTileSource(
    "CartoDarkMatter", 0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/",
    ),
    "© OpenStreetMap contributors © CARTO",
)

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
                        (mapView?.overlays?.firstOrNull { (it as? Marker)?.id == MARKER_CAR } as? Marker)
                            ?.let { m -> latestParked?.let { m.position = it } }
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
                setTileSource(DarkMatter)
                setBackgroundColor(android.graphics.Color.BLACK)
                // Black while tiles load, instead of osmdroid's light grey grid.
                overlayManager.tilesOverlay.loadingBackgroundColor = android.graphics.Color.BLACK
                overlayManager.tilesOverlay.loadingLineColor = 0xFF1A1A1A.toInt()
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
                            val slop = 24f * resources.displayMetrics.density
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

    override fun onTouchEvent(event: android.view.MotionEvent, mapView: MapView): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; upX = downX; upY = downY }
            android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP -> { upX = event.x; upY = event.y }
            android.view.MotionEvent.ACTION_CANCEL -> onCancel(this)
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

/** Orange square "P" in a soft orange halo, as in the design. */
fun makeParkedMarker(ctx: Context): BitmapDrawable {
    val density = ctx.resources.displayMetrics.density
    val size = (48 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f
    paint.color = 0x33FF7500
    c.drawCircle(cx, cy, size / 2f, paint)
    val half = 14 * density
    paint.color = 0xFFFF7500.toInt()
    c.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
    paint.color = android.graphics.Color.BLACK
    paint.textSize = 17 * density
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = runCatching { androidx.core.content.res.ResourcesCompat.getFont(ctx, com.parked.app.R.font.sofia_sans) }
        .getOrNull()?.let { Typeface.create(it, Typeface.BOLD) } ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val yOffset = (paint.descent() + paint.ascent()) / 2f
    c.drawText("P", cx, cy - yOffset, paint)
    return BitmapDrawable(ctx.resources, bmp)
}

/** You: a white dot with a black ring. */
fun makeLiveMarker(ctx: Context): BitmapDrawable {
    val density = ctx.resources.displayMetrics.density
    val size = (20 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f
    paint.color = android.graphics.Color.BLACK
    c.drawCircle(cx, cy, 10 * density, paint)
    paint.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, 7 * density, paint)
    return BitmapDrawable(ctx.resources, bmp)
}
