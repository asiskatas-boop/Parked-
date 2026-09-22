package com.parked.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.parked.app.data.FuelStore
import com.parked.app.data.ParkingStore
import com.parked.app.data.Refuel
import com.parked.app.service.ParkingMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.*

// ============================================================
// MainActivity
// ============================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Configuration.getInstance().userAgentValue = packageName
        setContent { ParkedRoot() }
    }
}

// ============================================================
// Accent themes — gradients
// ============================================================

data class AccentDef(
    val name: String,
    val gradStart: Color,
    val gradEnd: Color,
) {
    val solid: Color get() = gradStart
    val brush: Brush get() = Brush.linearGradient(listOf(gradStart, gradEnd))
    val softGlow: Brush
        get() = Brush.radialGradient(
            listOf(gradEnd.copy(alpha = 0.22f), Color.Transparent)
        )
}

private val Accents = listOf(
    AccentDef("Racing Green", Color(0xFF059669), Color(0xFF84CC16)),
    AccentDef("Electric",     Color(0xFF06B6D4), Color(0xFF3B82F6)),
    AccentDef("Iris",         Color(0xFF8B5CF6), Color(0xFFEC4899)),
    AccentDef("Ember",        Color(0xFFFF6B35), Color(0xFFFFB020)),
    AccentDef("Carmine",      Color(0xFFE11D48), Color(0xFFFF6F91)),
    AccentDef("Mono",         Color(0xFF6B7280), Color(0xFFE5E7EB)),
)

// ---- Surfaces ----
private val AppBg         = Color(0xFF08090B)
private val GlassBg       = Color(0xCC121316)
private val GlassStroke   = Color(0x1AFFFFFF)
private val GlassHighlight= Color(0x0DFFFFFF)
private val ElevatedBg    = Color(0xFF1A1B1F)
private val BorderSubtle  = Color(0x1AFFFFFF)

// ---- Text ----
private val TextPrimary   = Color(0xFFF7F7F7)
private val TextSecondary = Color(0xFFD0D0D3)
private val TextMuted     = Color(0xFFB3B3BA)

// ---- Semantic ----
private val SuccessGreen  = Color(0xFF34C759)
private val WarningYellow = Color(0xFFFFCC00)
private val DangerRed     = Color(0xFFFF453A)

// ============================================================
// Reduce Motion
// ============================================================

@Composable
private fun reduceMotionEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(
                ctx.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) { false }
    }
}

private fun animOrZero(reduce: Boolean, durationMs: Int): Int =
    if (reduce) 0 else durationMs

// ============================================================
// Theme
// ============================================================

@Composable
fun ParkedTheme(accent: AccentDef, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent.solid,
        onPrimary = Color.White,
        primaryContainer = accent.solid.copy(alpha = 0.15f),
        onPrimaryContainer = accent.solid,
        background = AppBg,
        onBackground = TextPrimary,
        surface = GlassBg,
        onSurface = TextPrimary,
        surfaceVariant = GlassBg,
        onSurfaceVariant = TextSecondary,
        outline = GlassStroke,
        error = DangerRed,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

// ============================================================
// Glass surface
// ============================================================

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    accent: AccentDef,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(GlassBg)
        .border(1.dp, GlassStroke, shape)

    Box(modifier) {
        Box(
            modifier = if (onClick != null) base.clickable { onClick() } else base
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(GlassHighlight, Color.Transparent),
                            endY = 60f
                        )
                    )
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.gradEnd.copy(alpha = 0.06f), Color.Transparent),
                            center = Offset(1000f, 1000f),
                            radius = 800f
                        )
                    )
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                content = content
            )
        }
    }
}

// ============================================================
// Root — resolves accent (including custom)
// ============================================================

@Composable
fun ParkedRoot() {
    val context = LocalContext.current
    val fuel = remember { FuelStore(context) }

    val accent = remember(
        fuel.accentName,
        fuel.customAccentStart,
        fuel.customAccentEnd
    ) {
        when {
            fuel.accentName == "Match my car" && fuel.customAccentStart != 0 ->
                AccentDef(
                    name = "Match my car",
                    gradStart = Color(fuel.customAccentStart),
                    gradEnd = Color(fuel.customAccentEnd)
                )
            else -> Accents.firstOrNull { it.name == fuel.accentName } ?: Accents[0]
        }
    }

    ParkedTheme(accent = accent) {
        ParkedApp(fuel = fuel, accent = accent)
    }
}

@Composable
fun ParkedApp(fuel: FuelStore, accent: AccentDef) {
    val reduce = reduceMotionEnabled()
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(if (reduce) 900 else 2400)
        showSplash = false
    }
    Crossfade(
        targetState = showSplash,
        animationSpec = tween(animOrZero(reduce, 700))
    ) { splash ->
        if (splash) SplashScreen(accent, reduce) else MainContent(fuel = fuel, accent = accent)
    }
}

// ============================================================
// Splash
// ============================================================

@Composable
fun SplashScreen(accent: AccentDef, reduce: Boolean) {
    val logoScale = remember { Animatable(if (reduce) 1f else 0.6f) }
    val logoAlpha = remember { Animatable(if (reduce) 1f else 0f) }
    val textAlpha = remember { Animatable(if (reduce) 1f else 0f) }
    val titleSlide = remember { Animatable(if (reduce) 0f else 24f) }
    val glowAlpha = remember { Animatable(if (reduce) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        launch { glowAlpha.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        launch { logoAlpha.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        logoScale.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        launch { textAlpha.animateTo(1f, tween(550, delayMillis = 450, easing = FastOutSlowInEasing)) }
        titleSlide.animateTo(0f, tween(700, delayMillis = 450, easing = FastOutSlowInEasing))
    }

    Box(
        Modifier.fillMaxSize().background(AppBg),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(300.dp)
                .alpha(glowAlpha.value)
                .background(accent.softGlow, CircleShape)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_parked_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(160.dp)
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value)
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "Parked!",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .offset(y = titleSlide.value.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Never lose your car again",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier
                    .alpha(textAlpha.value * 0.95f)
                    .offset(y = titleSlide.value.dp)
            )
        }
    }
}

// ============================================================
// Screens enum
// ============================================================

enum class AppScreen(
    val label: String,
    val filled: ImageVector,
    val outlined: ImageVector,
) {
    Parking("Parking", Icons.Filled.LocationOn, Icons.Outlined.LocationOn),
    Fuel("Fuel", Icons.Filled.LocalGasStation, Icons.Outlined.LocalGasStation),
    Car("Car", Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar),
}

// ============================================================
// Helpers
// ============================================================

private fun ensureBluetoothOn(context: Context) {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
    if (!adapter.isEnabled) runCatching {
        context.startActivity(
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun ensureLocationOn(context: Context) {
    val lm = context.getSystemService(LocationManager::class.java) ?: return
    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun distanceDisplay(meters: Double?, lowAccuracy: Boolean): String {
    if (meters == null) return "Finding your location…"
    return when {
        meters < 15 -> "You're at your car"
        meters < 100 -> {
            val m = (meters / 10.0).roundToInt() * 10
            if (lowAccuracy) "Approx. $m m away" else "About $m m away"
        }
        meters < 1000 -> {
            val m = meters.roundToInt()
            if (lowAccuracy) "Approx. $m m away" else "$m m away"
        }
        else -> {
            val km = (meters / 1000.0).roundToInt()
            if (lowAccuracy) "Approx. $km km away" else "$km km away"
        }
    }
}

private fun naturalTimestamp(ts: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
    val sameYear = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    val sameDay = sameYear && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val yesterday = sameYear && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1
    return when {
        sameDay -> "Parked today at $timeStr"
        yesterday -> "Parked yesterday at $timeStr"
        else -> {
            val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
            "Parked $dateStr at $timeStr"
        }
    }
}

private fun makeCarMarker(ctx: Context, accent: AccentDef): BitmapDrawable {
    val size = 160
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f - 20f

    paint.color = accent.gradEnd.copy(alpha = 0.18f).toArgb()
    c.drawCircle(cx, cy, 52f, paint)
    paint.color = accent.gradEnd.copy(alpha = 0.10f).toArgb()
    c.drawCircle(cx, cy, 64f, paint)

    paint.color = accent.solid.toArgb()
    val path = Path().apply {
        moveTo(cx - 11f, cy + 24f)
        lineTo(cx, cy + 48f)
        lineTo(cx + 11f, cy + 24f)
        close()
    }
    c.drawPath(path, paint)

    c.drawCircle(cx, cy, 30f, paint)

    paint.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, 22f, paint)

    paint.color = accent.solid.toArgb()
    c.drawRoundRect(RectF(cx - 14f, cy - 2f, cx + 14f, cy + 10f), 4f, 4f, paint)
    c.drawRoundRect(RectF(cx - 9f, cy - 10f, cx + 9f, cy + 2f), 3f, 3f, paint)
    c.drawCircle(cx - 9f, cy + 11f, 2.5f, paint)
    c.drawCircle(cx + 9f, cy + 11f, 2.5f, paint)

    return BitmapDrawable(ctx.resources, bmp)
}

// ============================================================
// Color extraction from a car photo
// ============================================================

private fun extractAccentFromUri(context: Context, uri: Uri): Pair<Color, Color> {
    val fallback = Color(0xFF059669) to Color(0xFF84CC16)
    return try {
        // Decode downsampled
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        val (w, h) = opts.outWidth to opts.outHeight
        if (w <= 0 || h <= 0) return fallback

        var sample = 1
        while (w / sample > 128 || h / sample > 128) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return fallback

        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)

        val hsv = FloatArray(3)
        var sinSum = 0f
        var cosSum = 0f
        var sumS = 0f
        var sumV = 0f
        var count = 0

        for (p in pixels) {
            val a = (p ushr 24) and 0xFF
            if (a < 200) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            if (hsv[1] < 0.25f) continue
            if (hsv[2] < 0.15f || hsv[2] > 0.95f) continue
            val hRad = Math.toRadians(hsv[0].toDouble())
            sinSum += sin(hRad).toFloat()
            cosSum += cos(hRad).toFloat()
            sumS += hsv[1]
            sumV += hsv[2]
            count++
        }

        if (count < 20) return fallback

        var avgHue = Math.toDegrees(atan2(sinSum.toDouble(), cosSum.toDouble())).toFloat()
        if (avgHue < 0) avgHue += 360f
        val avgSat = (sumS / count).coerceIn(0.45f, 1f)
        val avgVal = (sumV / count).coerceIn(0.40f, 0.90f)

        val startHsv = floatArrayOf(avgHue, avgSat, (avgVal * 0.85f).coerceIn(0.35f, 0.85f))
        val endHue = (avgHue + 25f) % 360f
        val endHsv = floatArrayOf(endHue, (avgSat * 0.90f).coerceIn(0.45f, 1f), (avgVal * 1.20f).coerceIn(0.50f, 1f))

        Color(android.graphics.Color.HSVToColor(startHsv)) to
                Color(android.graphics.Color.HSVToColor(endHsv))
    } catch (_: Exception) {
        fallback
    }
}

// ============================================================
// Main content
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(fuel: FuelStore, accent: AccentDef) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ParkingStore(context) }
    val state by store.state.collectAsState(initial = com.parked.app.data.ParkingState())

    var screen by remember { mutableStateOf(AppScreen.Parking) }

    LaunchedEffect(Unit) {
        val real = store.state.first()
        if (real.deviceAddress == null) screen = AppScreen.Car
    }

    var liveLat by remember { mutableStateOf<Double?>(null) }
    var liveLng by remember { mutableStateOf<Double?>(null) }
    var liveAccuracy by remember { mutableStateOf(100f) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                liveLat = loc.latitude
                liveLng = loc.longitude
                liveAccuracy = loc.accuracy
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasLocationPermission) {
            ensureBluetoothOn(context); ensureLocationOn(context)
        }
    }

    fun requestPermissions() {
        val p = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(p)
    }

    LaunchedEffect(Unit) { requestPermissions() }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMaxUpdateDelayMillis(2000L).build()
            try {
                fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            } catch (_: SecurityException) {}
        } else {
            try { fusedClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose { try { fusedClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {} }
    }

    val fuelOdoSub = if (fuel.isConfigured)
        "Odometer · ${String.format(Locale.US, "%,.0f", fuel.currentOdo)} km"
    else null

    Scaffold(
        containerColor = AppBg,
        bottomBar = {
            BottomNav(screen = screen, accent = accent, onChange = { screen = it })
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                Crossfade(
                    targetState = screen,
                    animationSpec = tween(220),
                    label = "screenFade"
                ) { s ->
                    when (s) {
                        AppScreen.Parking -> ParkingScreen(
                            state = state,
                            liveLat = liveLat,
                            liveLng = liveLng,
                            liveAccuracy = liveAccuracy,
                            accent = accent,
                            onEnableMonitoring = {
                                requestPermissions()
                                ensureBluetoothOn(context); ensureLocationOn(context)
                                runCatching {
                                    ContextCompat.startForegroundService(
                                        context, Intent(context, ParkingMonitorService::class.java)
                                    )
                                }
                                scope.launch { store.setMonitoring(true) }
                            },
                            onUpdateSpot = {
                                requestPermissions(); ensureLocationOn(context)
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    LocationServices.getFusedLocationProviderClient(context)
                                        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                        .addOnSuccessListener { loc ->
                                            if (loc != null) scope.launch {
                                                store.saveParking(loc.latitude, loc.longitude)
                                            }
                                        }
                                }
                            },
                            onEndParking = {
                                scope.launch { store.saveParking(0.0, 0.0) }
                            },
                            onDirections = {
                                val lat = state.parkedLat; val lng = state.parkedLng
                                if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) {
                                    ensureLocationOn(context)
                                    val navIntent = Intent(
                                        Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                                    ).apply {
                                        setPackage("com.google.android.apps.maps")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    val ok = runCatching { context.startActivity(navIntent) }.isSuccess
                                    if (!ok) runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW,
                                                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            }
                        )
                        AppScreen.Fuel -> FuelScreen(fuel = fuel, accent = accent)
                        AppScreen.Car -> CarScreen(
                            state = state,
                            fuel = fuel,
                            accent = accent,
                            onMonitoringToggle = { on ->
                                if (on) {
                                    requestPermissions()
                                    ensureBluetoothOn(context); ensureLocationOn(context)
                                    runCatching {
                                        ContextCompat.startForegroundService(
                                            context, Intent(context, ParkingMonitorService::class.java)
                                        )
                                    }
                                    scope.launch { store.setMonitoring(true) }
                                } else {
                                    runCatching { context.stopService(Intent(context, ParkingMonitorService::class.java)) }
                                    scope.launch { store.setMonitoring(false) }
                                }
                            },
                            onSelectDevice = { name, address ->
                                scope.launch { store.selectDevice(name, address) }
                            }
                        )
                    }
                }
            }

            FloatingHeader(
                title = screen.label,
                subtitle = if (screen == AppScreen.Fuel) fuelOdoSub else null,
                accent = accent,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

// ============================================================
// Floating header pill
// ============================================================

@Composable
fun FloatingHeader(
    title: String,
    subtitle: String? = null,
    accent: AccentDef,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .statusBarsPadding()
            .padding(start = 14.dp, top = 10.dp, end = 14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = GlassBg,
            border = BorderStroke(1.dp, GlassStroke),
            shadowElevation = 10.dp
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(accent.brush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// Bottom nav
// ============================================================

@Composable
fun BottomNav(screen: AppScreen, accent: AccentDef, onChange: (AppScreen) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(GlassBg)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = GlassStroke)
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppScreen.values().forEach { s ->
                val selected = screen == s
                val tint by animateColorAsState(
                    targetValue = if (selected) accent.solid else TextMuted,
                    animationSpec = tween(220),
                    label = "navTint"
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onChange(s) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 2.dp)
                            .width(24.dp)
                            .height(2.dp)
                            .background(
                                if (selected) accent.brush else Brush.linearGradient(
                                    listOf(Color.Transparent, Color.Transparent)
                                ),
                                RoundedCornerShape(999.dp)
                            )
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (selected) s.filled else s.outlined,
                            contentDescription = s.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            s.label,
                            fontSize = 11.sp,
                            color = tint,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// Parking screen
// ============================================================

@Composable
fun ParkingScreen(
    state: com.parked.app.data.ParkingState,
    liveLat: Double?,
    liveLng: Double?,
    liveAccuracy: Float,
    accent: AccentDef,
    onEnableMonitoring: () -> Unit,
    onUpdateSpot: () -> Unit,
    onEndParking: () -> Unit,
    onDirections: () -> Unit,
) {
    val context = LocalContext.current
    val mapRef = remember { mutableStateOf<MapView?>(null) }
    var confirmMove by remember { mutableStateOf(false) }

    val carMarkerIcon = remember(accent) { makeCarMarker(context, accent) }

    val hasValidParking = state.parkedLat != null && state.parkedLng != null &&
        !(state.parkedLat == 0.0 && state.parkedLng == 0.0)

    val distance: Double? = remember(liveLat, liveLng, state.parkedLat, state.parkedLng, hasValidParking) {
        if (liveLat != null && liveLng != null && hasValidParking)
            haversine(liveLat, liveLng, state.parkedLat!!, state.parkedLng!!)
        else null
    }

    val isAtCar = distance != null && distance < 15
    val lowAcc = liveAccuracy > 50f

    var parkedButtonVisible by remember { mutableStateOf(true) }
    LaunchedEffect(parkedButtonVisible, hasValidParking) {
        if (parkedButtonVisible && hasValidParking) {
            delay(5000)
            parkedButtonVisible = false
        }
    }
    LaunchedEffect(hasValidParking) {
        if (hasValidParking) parkedButtonVisible = true
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ParkedMap(
                parkedLat = if (hasValidParking) state.parkedLat else null,
                parkedLng = if (hasValidParking) state.parkedLng else null,
                liveLat = liveLat,
                liveLng = liveLng,
                carMarkerIcon = carMarkerIcon,
                accent = accent,
                onMapReady = { mapRef.value = it },
                onUserTap = { parkedButtonVisible = true }
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(AppBg.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
            )

            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 70.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (hasValidParking && parkedButtonVisible) {
                    FloatingControl(
                        icon = Icons.Filled.LocationOn,
                        label = "Show parked car",
                        accent = accent
                    ) {
                        val lat = state.parkedLat; val lng = state.parkedLng
                        if (lat != null && lng != null) {
                            mapRef.value?.controller?.animateTo(GeoPoint(lat, lng))
                        }
                        parkedButtonVisible = true
                    }
                }
                FloatingControl(
                    icon = Icons.Filled.MyLocation,
                    label = "Recenter on me",
                    accent = accent
                ) {
                    if (liveLat != null && liveLng != null) {
                        mapRef.value?.controller?.animateTo(GeoPoint(liveLat, liveLng))
                    }
                    parkedButtonVisible = true
                }
            }
        }

        AmbientGlassSheet(accent = accent) {
            when {
                !hasValidParking -> {
                    Text("Save where you parked", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Never lose track of your car again.", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    GradientButton(
                        text = "Save parking spot",
                        accent = accent,
                        onClick = onUpdateSpot
                    )
                }
                liveLat == null -> {
                    Text("Finding your location…", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Your parking spot is saved.", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    GradientButton(
                        text = "Get directions",
                        accent = accent,
                        onClick = onDirections
                    )
                }
                isAtCar -> {
                    Text("You're at your car", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        state.parkedAt?.let { naturalTimestamp(it) } ?: "Parked recently",
                        fontSize = 14.sp, color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    GradientButton(
                        text = "End parking",
                        accent = accent,
                        onClick = onEndParking,
                        overrideStart = SuccessGreen,
                        overrideEnd = SuccessGreen
                    )
                    TextButton(
                        onClick = { confirmMove = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Update parking spot", color = accent.solid, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
                else -> {
                    Text(
                        distanceDisplay(distance, lowAcc),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                    Text(
                        state.parkedAt?.let { naturalTimestamp(it) } ?: "Parked recently",
                        fontSize = 14.sp, color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    GradientButton(
                        text = "Get directions",
                        accent = accent,
                        onClick = onDirections
                    )
                    TextButton(
                        onClick = { confirmMove = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Update parking spot", color = accent.solid, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.monitoring) {
                    Box(Modifier.size(9.dp).background(accent.brush, CircleShape))
                } else {
                    Box(Modifier.size(9.dp).background(TextMuted, CircleShape))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (state.monitoring) "Automatic parking on" else "Automatic parking off",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                if (!state.monitoring) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onEnableMonitoring) {
                        Text("Turn on", color = accent.solid, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (confirmMove) {
        AlertDialog(
            onDismissRequest = { confirmMove = false },
            containerColor = GlassBg,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Move your parking spot?") },
            text = { Text("This will replace the saved spot with your current location.") },
            confirmButton = {
                TextButton(onClick = { confirmMove = false; onUpdateSpot() }) {
                    Text("Move spot", color = accent.solid, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMove = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun GradientButton(
    text: String,
    accent: AccentDef,
    onClick: () -> Unit,
    overrideStart: Color? = null,
    overrideEnd: Color? = null,
) {
    val start = overrideStart ?: accent.gradStart
    val end = overrideEnd ?: accent.gradEnd
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(start, end)))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
fun FloatingControl(icon: ImageVector, label: String, accent: AccentDef, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = GlassBg,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, GlassStroke)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = accent.solid, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun AmbientGlassSheet(accent: AccentDef, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = GlassBg,
        shadowElevation = 12.dp
    ) {
        Box {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.gradEnd.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(100f, 0f),
                            radius = 700f
                        )
                    )
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    }
}

// ============================================================
// Fuel screen
// ============================================================

@Composable
fun FuelScreen(fuel: FuelStore, accent: AccentDef) {
    var showSlider by remember { mutableStateOf(false) }
    var showRefuel by remember { mutableStateOf(false) }

    val level by remember { derivedStateOf { fuel.estimateLevelPct() } }
    val rangeRemaining by remember { derivedStateOf { fuel.estimatedRangeRemainingKm() } }
    val avg by remember { derivedStateOf { fuel.averageL100km() } }
    val rangeFull by remember { derivedStateOf { fuel.estimatedRangeKm() } }

    val hasAnyFill = fuel.refuels.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 90.dp, bottom = 20.dp)
        ) {

            if (!fuel.isConfigured) {
                Text("Set up your car", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add your tank capacity and odometer in the Car tab to start tracking fuel.",
                    fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp
                )
                return@Column
            }

            GlassCard(accent = accent) {
                Text(
                    "FUEL LEVEL",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = TextSecondary, letterSpacing = 1.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(level.toInt().toString(), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 40.sp)
                    Text(
                        "%", fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 3.dp, bottom = 5.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))

                val lowFuel = level < 20
                val fraction by animateFloatAsState(
                    targetValue = (level.toFloat() / 100f).coerceIn(0f, 1f),
                    animationSpec = tween(700),
                    label = "fuelFrac"
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(ElevatedBg, RoundedCornerShape(999.dp))
                        .clickable { showSlider = true }
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(
                                if (lowFuel) Brush.linearGradient(listOf(WarningYellow, WarningYellow))
                                else accent.brush,
                                RoundedCornerShape(999.dp)
                            )
                    )
                }
                if (lowFuel) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(WarningYellow, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text("Low fuel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WarningYellow)
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(Modifier.height(10.dp))

                if (rangeRemaining != null) {
                    Text(
                        "≈ ${rangeRemaining!!.toInt()} km estimated range",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                    )
                } else {
                    Text("Range unavailable", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Log your first fill to estimate range.",
                        fontSize = 13.sp, color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (!hasAnyFill) {
                GlassCard(accent = accent) {
                    Text("Track your fuel usage", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "See consumption, costs and estimated range after your first fill.",
                        fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    GradientButton(
                        text = "Log first fill",
                        accent = accent,
                        onClick = { showRefuel = true }
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    val last = fuel.refuels.first()
                    StatCard(
                        "LAST FILL",
                        String.format(Locale.US, "%.1f", last.litres), "L",
                        "${fuel.currency}${String.format(Locale.US, "%.2f", last.cost)}",
                        accent,
                        Modifier.weight(1f)
                    )
                    val sinceFill = (fuel.currentOdo - last.odometer).coerceAtLeast(0.0)
                    StatCard(
                        "DISTANCE",
                        String.format(Locale.US, "%,.0f", sinceFill), "km",
                        "Since fill",
                        accent,
                        Modifier.weight(1f)
                    )
                }

                if (avg != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            "AVERAGE",
                            String.format(Locale.US, "%.1f", avg!!), "L/100 km",
                            "Last fills",
                            accent,
                            Modifier.weight(1f)
                        )
                        val costPerKm = run {
                            val last5 = fuel.refuels.take(5)
                            if (last5.size < 2) null else {
                                val dist = last5.first().odometer - last5.last().odometer
                                val cost = last5.dropLast(1).sumOf { it.cost }
                                if (dist > 50) cost / dist else null
                            }
                        }
                        StatCard(
                            "COST",
                            costPerKm?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                            "${fuel.currency}/km",
                            "Fuel only",
                            accent,
                            Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    "RECENT FILLS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = TextSecondary, letterSpacing = 1.sp
                )
                Spacer(Modifier.height(10.dp))
                fuel.refuels.take(20).forEach { r -> RefuelRow(r, fuel, accent) }
                Spacer(Modifier.height(90.dp))
            }
        }

        if (hasAnyFill) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accent.brush)
                    .clickable { showRefuel = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, "Log refuel", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }

    if (showSlider) {
        SliderModal(
            currentLevel = level,
            rangeFullKm = rangeFull,
            accent = accent,
            onDismiss = { showSlider = false },
            onSave = { v -> fuel.setLevel(v); fuel.dismissPrompt(); showSlider = false }
        )
    }
    if (showRefuel) {
        RefuelModal(
            fuel = fuel,
            accent = accent,
            onDismiss = { showRefuel = false },
            onSave = { litres, cost, odo, tankFull ->
                fuel.logRefuel(litres, cost, odo, tankFull); showRefuel = false
            }
        )
    }
}

// ============================================================
// Car screen
// ============================================================

@Composable
fun CarScreen(
    state: com.parked.app.data.ParkingState,
    fuel: FuelStore,
    accent: AccentDef,
    onMonitoringToggle: (Boolean) -> Unit,
    onSelectDevice: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showDevicePicker by remember { mutableStateOf(false) }
    var showFuelSettings by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showResetOdo by remember { mutableStateOf(false) }

    val accentLabel = accent.name

    // Gallery picker for "Match my car"
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val (start, end) = withContext(Dispatchers.IO) {
                    extractAccentFromUri(context, uri)
                }
                fuel.setCustomAccent(start.toArgb(), end.toArgb())
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 90.dp, bottom = 24.dp)
    ) {

        SectionHeader("VEHICLE")
        GlassCard(accent = accent, onClick = { showDevicePicker = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsCar, null, tint = accent.solid, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.deviceName ?: "Not set",
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                    )
                    Text("Your car", fontSize = 12.sp, color = TextSecondary)
                }
                Text("›", fontSize = 22.sp, color = TextMuted)
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionHeader("APPEARANCE")
        GlassCard(accent = accent) {
            // Accent row
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showAppearance = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(accent.brush)
                        .border(1.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    accentLabel,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text("›", fontSize = 22.sp, color = TextMuted)
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(Modifier.height(6.dp))
            // Match my car
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { photoPicker.launch("image/*") }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (fuel.customAccentStart != 0)
                                Brush.linearGradient(
                                    listOf(Color(fuel.customAccentStart), Color(fuel.customAccentEnd))
                                )
                            else
                                Brush.linearGradient(listOf(ElevatedBg, ElevatedBg))
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (fuel.customAccentStart == 0) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Match my car",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                    )
                    Text(
                        if (fuel.customAccentStart != 0) "Tap to change photo"
                        else "Pick a photo of your car",
                        fontSize = 12.sp, color = TextSecondary
                    )
                }
                Text("›", fontSize = 22.sp, color = TextMuted)
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionHeader("AUTOMATIC PARKING")
        GlassCard(accent = accent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Automatic parking",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                    )
                    Text(
                        if (state.monitoring) "On" else "Off",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary
                    )
                }
                Switch(
                    checked = state.monitoring,
                    onCheckedChange = { onMonitoringToggle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.solid,
                        uncheckedThumbColor = Color(0xFF8E8E93),
                        uncheckedTrackColor = ElevatedBg,
                    )
                )
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().clickable { showDevicePicker = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.DirectionsCar, null, tint = accent.solid, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bluetooth device", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        state.deviceName ?: "Choose a device",
                        fontSize = 13.sp, color = TextSecondary
                    )
                }
                Text("Change", fontSize = 13.sp, color = accent.solid, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionHeader("VEHICLE DATA")
        GlassCard(accent = accent) {
            GroupedRow(
                label = "Odometer",
                value = if (fuel.isConfigured) String.format(Locale.US, "%,.0f km", fuel.currentOdo) else "Not set"
            ) { showFuelSettings = true }
            HorizontalDivider(color = BorderSubtle)
            GroupedRow(
                label = "Fuel tank",
                value = if (fuel.tankCapacity > 0) "${fuel.tankCapacity.toInt()} L" else "Not set"
            ) { showFuelSettings = true }
            HorizontalDivider(color = BorderSubtle)
            GroupedRow(
                label = "Currency",
                value = fuel.currency
            ) { showFuelSettings = true }
        }

        Spacer(Modifier.height(18.dp))

        SectionHeader("PREFERENCES")
        GlassCard(accent = accent) {
            // Notifications toggle
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Notifications", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        if (fuel.notificationsEnabled) "On — saved-spot alerts"
                        else "Off — silent saves",
                        fontSize = 12.sp, color = TextSecondary
                    )
                }
                Switch(
                    checked = fuel.notificationsEnabled,
                    onCheckedChange = { fuel.setNotificationsEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.solid,
                        uncheckedThumbColor = Color(0xFF8E8E93),
                        uncheckedTrackColor = ElevatedBg,
                    )
                )
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(Modifier.height(6.dp))
            // Reset odometer tracker
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showResetOdo = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Reset odometer tracker", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        "Clears GPS-accumulated km",
                        fontSize = 12.sp, color = TextSecondary
                    )
                }
                Text("›", fontSize = 22.sp, color = TextMuted)
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showDevicePicker) {
        DevicePickerDialog(
            accent = accent,
            onDismiss = { showDevicePicker = false },
            onSelected = { name, address ->
                onSelectDevice(name, address); showDevicePicker = false
            }
        )
    }
    if (showFuelSettings) {
        FuelSettingsModal(
            fuel = fuel,
            accent = accent,
            onDismiss = { showFuelSettings = false }
        )
    }
    if (showAppearance) {
        AppearanceModal(
            fuel = fuel,
            accent = accent,
            onDismiss = { showAppearance = false },
            onMatchMyCar = { photoPicker.launch("image/*") }
        )
    }
    if (showResetOdo) {
        AlertDialog(
            onDismissRequest = { showResetOdo = false },
            containerColor = GlassBg,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Reset odometer tracker?") },
            text = {
                Text(
                    "The GPS-accumulated distance will be folded into your odometer baseline and tracking restarts from zero. Your odometer value stays the same."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    fuel.resetOdometerTracker()
                    showResetOdo = false
                }) { Text("Reset", color = accent.solid, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showResetOdo = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun GroupedRow(
    label: String,
    value: String,
    onClick: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = TextSecondary)
        Spacer(Modifier.width(6.dp))
        Text("›", fontSize = 22.sp, color = TextMuted)
    }
}

// ============================================================
// Appearance modal
// ============================================================

@Composable
private fun AppearanceModal(
    fuel: FuelStore,
    accent: AccentDef,
    onDismiss: () -> Unit,
    onMatchMyCar: () -> Unit,
) {
    ModalScaffold(accent = accent, onDismiss = onDismiss) {
        Text("Appearance", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("Accent gradient", fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(18.dp))

        Accents.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                row.forEach { def ->
                    AccentOrb(
                        def = def,
                        selected = fuel.accentName == def.name,
                        onClick = { fuel.setAccent(def.name) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("MATCH MY CAR", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clickable { onMatchMyCar() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (fuel.customAccentStart != 0)
                            Brush.linearGradient(
                                listOf(Color(fuel.customAccentStart), Color(fuel.customAccentEnd))
                            )
                        else
                            Brush.linearGradient(listOf(ElevatedBg, ElevatedBg))
                    )
                    .border(
                        width = if (fuel.accentName == "Match my car") 2.dp else 1.dp,
                        color = if (fuel.accentName == "Match my car") Color.White else Color.White.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (fuel.customAccentStart == 0) {
                    Icon(Icons.Filled.Add, "Pick photo", tint = TextMuted, modifier = Modifier.size(22.dp))
                } else if (fuel.accentName == "Match my car") {
                    Icon(Icons.Filled.Check, "Selected", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (fuel.customAccentStart != 0) "Use a new photo" else "Pick a photo of your car",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                )
                Text(
                    "Extracts the color and applies it as the accent",
                    fontSize = 12.sp, color = TextSecondary
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        GradientButton(text = "Done", accent = accent, onClick = onDismiss)
    }
}

@Composable
private fun AccentOrb(
    def: AccentDef,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(def.brush)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Filled.Check, "Selected", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            def.name,
            fontSize = 11.sp,
            color = if (selected) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ============================================================
// Device picker dialog
// ============================================================

@Composable
fun DevicePickerDialog(
    accent: AccentDef,
    onDismiss: () -> Unit,
    onSelected: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    val canRead = android.os.Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val devices = if (canRead) runCatching { adapter?.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList()) else emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassBg,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("Choose your car") },
        text = {
            Column {
                if (!canRead) {
                    Text("Bluetooth permission is required.")
                } else if (devices.isEmpty()) {
                    Text("No paired Bluetooth devices found.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(devices, key = { it.address }) { device ->
                            Surface(
                                onClick = { onSelected(device.name ?: "Car Bluetooth", device.address) },
                                shape = RoundedCornerShape(10.dp),
                                color = ElevatedBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(device.name ?: "Unnamed", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text(device.address, fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = accent.solid) }
        }
    )
}

// ============================================================
// Map
// ============================================================

private class RippleOverlay(
    private val point: GeoPoint,
    private val color: Int,
) : Overlay() {
    private var progress = 0f
    private var running = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun start(mapView: MapView) {
        running = true
        progress = 0f
        val handler = Handler(Looper.getMainLooper())
        val startTime = SystemClock.uptimeMillis()
        val durationMs = 800L
        val runnable = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - startTime
                progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                mapView.invalidate()
                if (progress < 1f) handler.postDelayed(this, 16)
            }
        }
        handler.post(runnable)
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (!running && progress >= 1f) return
        val pt = mapView.projection.toPixels(point, null)
        val density = mapView.resources.displayMetrics.density
        val maxRadius = 60f * density
        val radius = maxRadius * progress
        val alpha = ((1f - progress) * 180).toInt().coerceIn(0, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f * density
        paint.color = color
        paint.alpha = alpha
        canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), radius, paint)
    }
}

@Composable
fun ParkedMap(
    parkedLat: Double?, parkedLng: Double?,
    liveLat: Double?, liveLng: Double?,
    carMarkerIcon: BitmapDrawable,
    accent: AccentDef,
    onMapReady: (MapView) -> Unit,
    onUserTap: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val reduce = reduceMotionEnabled()
    val lastParked = remember { mutableStateOf<GeoPoint?>(null) }
    val lastLive = remember { mutableStateOf<GeoPoint?>(null) }
    val parkedMarkerRef = remember { mutableStateOf<Marker?>(null) }
    val liveMarkerRef = remember { mutableStateOf<Marker?>(null) }
    val rippleRef = remember { mutableStateOf<RippleOverlay?>(null) }
    var eventsOverlayAdded by remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                controller.setZoom(16.0)
                val sLat = liveLat ?: parkedLat ?: 37.9838
                val sLng = liveLng ?: parkedLng ?: 23.7275
                controller.setCenter(GeoPoint(sLat, sLng))
                onMapReady(this)
            }
        },
        update = { map ->
            if (!eventsOverlayAdded) {
                val receiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        onUserTap()
                        return false
                    }
                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                }
                map.overlays.add(0, MapEventsOverlay(receiver))
                eventsOverlayAdded = true
            }

            val newParked = if (parkedLat != null && parkedLng != null)
                GeoPoint(parkedLat, parkedLng) else null

            if (newParked != null) {
                val same = lastParked.value?.latitude == parkedLat &&
                        lastParked.value?.longitude == parkedLng
                if (!same) {
                    parkedMarkerRef.value?.let { map.overlays.remove(it) }
                    rippleRef.value?.let { map.overlays.remove(it) }

                    val marker = Marker(map).apply {
                        position = newParked
                        title = "Parked car"
                        icon = carMarkerIcon
                        setAnchor(Marker.ANCHOR_CENTER, 1.8f)
                    }
                    map.overlays.add(marker)
                    parkedMarkerRef.value = marker

                    if (!reduce) {
                        val ripple = RippleOverlay(newParked, accent.gradEnd.toArgb())
                        map.overlays.add(ripple)
                        rippleRef.value = ripple
                    }

                    if (reduce) {
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    } else {
                        scope.launch {
                            val frames = 24
                            for (i in 0..frames) {
                                val t = i.toFloat() / frames
                                val eased = 1f - (1f - t) * (1f - t)
                                val anchorV = 1.8f - 0.8f * eased
                                marker.setAnchor(Marker.ANCHOR_CENTER, anchorV)
                                map.invalidate()
                                delay(20)
                            }
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            map.invalidate()
                        }
                        scope.launch {
                            delay(400)
                            rippleRef.value?.start(map)
                        }
                    }

                    lastParked.value = newParked
                }
            } else {
                if (lastParked.value != null) {
                    parkedMarkerRef.value?.let { map.overlays.remove(it) }
                    rippleRef.value?.let { map.overlays.remove(it) }
                    parkedMarkerRef.value = null
                    rippleRef.value = null
                    lastParked.value = null
                }
            }

            val newLive = if (liveLat != null && liveLng != null)
                GeoPoint(liveLat, liveLng) else null

            if (newLive != null) {
                val same = lastLive.value?.latitude == liveLat &&
                        lastLive.value?.longitude == liveLng
                if (!same) {
                    if (liveMarkerRef.value == null) {
                        val marker = Marker(map).apply {
                            position = newLive
                            title = "You are here"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(marker)
                        liveMarkerRef.value = marker
                    } else {
                        liveMarkerRef.value?.position = newLive
                    }
                    lastLive.value = newLive
                }
            }

            map.invalidate()
        }
    )
}

// ============================================================
// Stat card + refuel row
// ============================================================

@Composable
private fun StatCard(
    label: String, value: String, unit: String, sub: String,
    accent: AccentDef,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        GlassCard(accent = accent) {
            Text(
                label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = TextSecondary, letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 20.sp)
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(unit, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(sub, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun RefuelRow(r: Refuel, fuel: FuelStore, accent: AccentDef) {
    val fmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    GlassCard(
        accent = accent,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(ElevatedBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.LocalGasStation, null, tint = accent.solid, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fmt.format(Date(r.date)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    "${String.format(Locale.US, "%.1f", r.litres)} L · ${fuel.currency}${String.format(Locale.US, "%.2f", r.cost)}",
                    fontSize = 12.sp, color = TextSecondary
                )
            }
            if (r.tankFull) {
                Text("FULL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent.solid)
            }
        }
    }
}

// ============================================================
// Modals
// ============================================================

@Composable
private fun SliderModal(
    currentLevel: Double,
    rangeFullKm: Double?,
    accent: AccentDef,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var v by remember { mutableFloatStateOf(currentLevel.toFloat()) }
    val range = rangeFullKm?.let { it * v / 100 } ?: 0.0

    ModalScaffold(accent = accent, onDismiss = onDismiss) {
        Text("Adjust fuel level", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("Look at your car's gauge and match it below", fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(20.dp))

        Surface(shape = RoundedCornerShape(16.dp), color = ElevatedBg) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(v.toInt().toString(), fontSize = 52.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 52.sp)
                    Text(
                        "%", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 3.dp, bottom = 8.dp)
                    )
                }
                if (range > 0) {
                    Text("≈ ${range.toInt()} km range", fontSize = 13.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(20.dp))
                Slider(
                    value = v, onValueChange = { v = it },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = accent.solid,
                        activeTrackColor = accent.solid,
                        inactiveTrackColor = ElevatedBg,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Empty", fontSize = 11.sp, color = TextSecondary)
                    Text("Half", fontSize = 11.sp, color = TextSecondary)
                    Text("Full", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        GradientButton(text = "Save", accent = accent, onClick = { onSave(v.toDouble()) })
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = TextSecondary)
        }
    }
}

@Composable
private fun RefuelModal(
    fuel: FuelStore,
    accent: AccentDef,
    onDismiss: () -> Unit,
    onSave: (Double, Double, Double, Boolean) -> Unit,
) {
    var litres by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var odo by remember { mutableStateOf(if (fuel.currentOdo > 0) String.format(Locale.US, "%.0f", fuel.currentOdo) else "") }
    var tankFull by remember { mutableStateOf(true) }

    ModalScaffold(accent = accent, onDismiss = onDismiss) {
        Text("Log refuel", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("Fill in what you see at the pump", fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = litres, onValueChange = { litres = it },
                label = { Text("Litres") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                ),
                singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = cost, onValueChange = { cost = it },
                label = { Text("Cost (${fuel.currency})") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                ),
                singleLine = true, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = odo, onValueChange = { odo = it },
            label = { Text("Odometer (km)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(12.dp), color = ElevatedBg,
            modifier = Modifier.fillMaxWidth().clickable { tankFull = !tankFull }
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = tankFull,
                    onCheckedChange = { tankFull = it },
                    colors = CheckboxDefaults.colors(checkedColor = accent.solid)
                )
                Column {
                    Text("Tank is full", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text("Gives the most accurate readings", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val enabled = litres.toDoubleOrNull() != null && odo.toDoubleOrNull() != null
        if (enabled) {
            GradientButton(
                text = "Save",
                accent = accent,
                onClick = {
                    val l = litres.toDoubleOrNull() ?: return@GradientButton
                    val c = cost.toDoubleOrNull() ?: 0.0
                    val o = odo.toDoubleOrNull() ?: return@GradientButton
                    onSave(l, c, o, tankFull)
                }
            )
        } else {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = TextSecondary)
        }
    }
}

@Composable
private fun FuelSettingsModal(fuel: FuelStore, accent: AccentDef, onDismiss: () -> Unit) {
    var tank by remember { mutableStateOf(if (fuel.tankCapacity > 0) String.format(Locale.US, "%.0f", fuel.tankCapacity) else "") }
    var odo by remember { mutableStateOf(if (fuel.baselineOdo > 0) String.format(Locale.US, "%.0f", fuel.baselineOdo) else "") }
    var cur by remember { mutableStateOf(fuel.currency) }

    ModalScaffold(accent = accent, onDismiss = onDismiss) {
        Text("Vehicle data", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("These values power the fuel estimate", fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = tank, onValueChange = { tank = it },
            label = { Text("Tank capacity (litres)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            ),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = odo, onValueChange = { odo = it },
            label = { Text("Current odometer (km)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = cur, onValueChange = { if (it.length <= 3) cur = it },
            label = { Text("Currency symbol") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        GradientButton(
            text = "Save",
            accent = accent,
            onClick = {
                tank.toDoubleOrNull()?.let { fuel.updateTankCapacity(it) }
                odo.toDoubleOrNull()?.let { fuel.setOdometer(it) }
                fuel.updateCurrency(cur.ifBlank { "€" })
                onDismiss()
            }
        )
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = TextSecondary)
        }
    }
}

@Composable
private fun ModalScaffold(
    accent: AccentDef,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
                .navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = GlassBg,
            shadowElevation = 16.dp
        ) {
            Box {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(accent.gradEnd.copy(alpha = 0.12f), Color.Transparent),
                                center = Offset(100f, 0f),
                                radius = 900f
                            )
                        )
                )
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    content = content
                )
            }
        }
    }
}
