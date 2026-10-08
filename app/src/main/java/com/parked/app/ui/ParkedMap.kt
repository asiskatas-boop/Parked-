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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** A one-off camera move requested by the map buttons. */
sealed interface MapCommand {
    val id: Int
    data class CenterOnCar(override val id: Int) : MapCommand
    data class ShowBoth(override val id: Int) : MapCommand
}

@Composable
fun ParkedMap(
    parkedLat: Double?, parkedLng: Double?,
    liveLat: Double?, liveLng: Double?,
    parkedMarkerIcon: BitmapDrawable,
    liveMarkerIcon: BitmapDrawable,
    parkedLabel: String,
    liveLabel: String,
    command: MapCommand?,
    bottomPaddingPx: Int,
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onPause()
            mapView?.onDetach()
            mapView = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(16.0)
                contentDescription = parkedLabel
                mapView = this
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onResume()
            }
        },
        update = { map ->
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
                when (command) {
                    is MapCommand.CenterOnCar -> if (parkedLat != null && parkedLng != null) {
                        map.controller.animateTo(GeoPoint(parkedLat, parkedLng), 17.5, 400L)
                    }
                    is MapCommand.ShowBoth -> if (parkedLat != null && parkedLng != null && liveLat != null && liveLng != null) {
                        val box = BoundingBox.fromGeoPoints(
                            listOf(GeoPoint(parkedLat, parkedLng), GeoPoint(liveLat, liveLng))
                        )
                        // Keep both markers clear of the bottom sheet.
                        map.post { map.zoomToBoundingBox(box.increaseByScale(1.6f), true, bottomPaddingPx / 2) }
                    }
                }
            }
            map.overlays.removeAll { it is Marker }
            if (parkedLat != null && parkedLng != null) {
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(parkedLat, parkedLng)
                    title = parkedLabel
                    icon = parkedMarkerIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
            if (liveLat != null && liveLng != null) {
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(liveLat, liveLng)
                    title = liveLabel
                    icon = liveMarkerIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
            map.invalidate()
        }
    )
}

fun makeParkedMarker(ctx: Context): BitmapDrawable {
    val size = 260
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f
    paint.color = 0x306EC436
    c.drawCircle(cx, cy, 118f, paint)
    paint.color = 0x186EC436
    c.drawCircle(cx, cy, 130f, paint)
    paint.color = 0xFF2B4A23.toInt()
    c.drawCircle(cx, cy, 82f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 100f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val yOffset = (paint.descent() + paint.ascent()) / 2f
    c.drawText("P", cx, cy - yOffset, paint)
    return BitmapDrawable(ctx.resources, bmp)
}

fun makeLiveMarker(ctx: Context): BitmapDrawable {
    val size = 80
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f
    paint.color = 0x330A84FF
    c.drawCircle(cx, cy, 36f, paint)
    paint.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, 20f, paint)
    paint.color = 0xFF0A84FF.toInt()
    c.drawCircle(cx, cy, 14f, paint)
    return BitmapDrawable(ctx.resources, bmp)
}
