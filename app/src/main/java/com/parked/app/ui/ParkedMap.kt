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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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
 * The OSM map. It is sized by the caller to the area that is actually visible
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
) {
    var hasCenteredOnParked by remember { mutableStateOf(false) }
    var hasCenteredOnLive by remember { mutableStateOf(false) }
    var handledCommand by remember { mutableIntStateOf(-1) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(parkedLat, parkedLng) {
        // A newly saved spot becomes the map focus.
        hasCenteredOnParked = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
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
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                controller.setZoom(16.0)
                contentDescription = parkedLabel
                // Markers are created once and only moved afterwards.
                overlays.add(Marker(this).apply {
                    id = MARKER_CAR
                    title = parkedLabel
                    icon = parkedMarkerIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    isEnabled = false
                })
                overlays.add(Marker(this).apply {
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
                car?.position = GeoPoint(parkedLat, parkedLng)
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

private const val MARKER_CAR = "car"
private const val MARKER_YOU = "you"

fun makeParkedMarker(ctx: Context): BitmapDrawable {
    val density = ctx.resources.displayMetrics.density
    val size = (64 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f
    val r = size / 2f
    paint.color = 0x306EC436
    c.drawCircle(cx, cy, r * 0.92f, paint)
    paint.color = 0xFFFFFFFF.toInt()
    c.drawCircle(cx, cy, r * 0.66f, paint)
    paint.color = 0xFF2B4A23.toInt()
    c.drawCircle(cx, cy, r * 0.58f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = r * 0.78f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val yOffset = (paint.descent() + paint.ascent()) / 2f
    c.drawText("P", cx, cy - yOffset, paint)
    return BitmapDrawable(ctx.resources, bmp)
}

fun makeLiveMarker(ctx: Context): BitmapDrawable {
    val density = ctx.resources.displayMetrics.density
    val size = (28 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f
    val r = size / 2f
    paint.color = 0x330A84FF
    c.drawCircle(cx, cy, r, paint)
    paint.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, r * 0.56f, paint)
    paint.color = 0xFF0A84FF.toInt()
    c.drawCircle(cx, cy, r * 0.4f, paint)
    return BitmapDrawable(ctx.resources, bmp)
}
