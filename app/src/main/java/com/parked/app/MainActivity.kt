package com.parked.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent { ParkedRoot() }
    }
}

data class AccentDef(val name: String, val color: Color)

private val Accents = listOf(
    AccentDef("Electric",     Color(0xFF0A84FF)),
    AccentDef("Iris",         Color(0xFF7C5CFF)),
    AccentDef("Racing Green", Color(0xFF10B981)),
    AccentDef("Ember",        Color(0xFFFF6B35)),
    AccentDef("Carmine",      Color(0xFFE11D48)),
    AccentDef("Mono",         Color(0xFFE5E7EB)),
)

private val AppBg        = Color(0xFF0B0B0D)
private val CardBg       = Color(0xFF16171A)
private val ElevatedBg   = Color(0xFF1F2024)
private val BorderSubtle = Color(0x14FFFFFF)
private val TextPrimary  = Color(0xFFF5F5F7)
private val TextSecondary= Color(0xFF9CA3AF)
private val TextMuted    = Color(0xFF6B7280)

private val SuccessGreen = Color(0xFF34C759)
private val WarningYellow= Color(0xFFFFCC00)
private val DangerRed    = Color(0xFFFF453A)

@Composable
fun ParkedTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.15f),
        onPrimaryContainer = accent,
        background = AppBg,
        onBackground = TextPrimary,
        surface = CardBg,
        onSurface = TextPrimary,
        surfaceVariant = CardBg,
        onSurfaceVariant = TextSecondary,
        outline = BorderSubtle,
        error = DangerRed,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun ParkedRoot() {
    val context = LocalContext.current
    val fuel = remember { FuelStore(context) }
    val accentName = fuel.accentName
    val accent = Accents.firstOrNull { it.name == accentName }?.color ?: Accents[0].color

    ParkedTheme(accent = accent) {
        ParkedApp(fuel = fuel, accent = accent)
    }
}

@Composable
fun ParkedApp(fuel: FuelStore, accent: Color) {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1500)
        showSplash = false
    }
    Crossfade(targetState = showSplash, animationSpec = tween(450)) { splash ->
        if (splash) SplashScreen(accent) else MainContent(fuel = fuel, accent = accent)
    }
}

@Composable
fun SplashScreen(accent: Color) {
    Box(
        Modifier.fillMaxSize().background(AppBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).background(accent, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("P", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(20.dp))
            Text("Parked!", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text("Never lose your car again", fontSize = 14.sp, color = TextSecondary)
        }
    }
}

enum class AppScreen(
    val label: String,
    val filled: ImageVector,
    val outlined: ImageVector,
) {
    Parking("Parking", Icons.Filled.LocalParking, Icons.Outlined.LocalParking),
    Fuel("Fuel", Icons.Filled.LocalGasStation, Icons.Outlined.LocalGasStation),
    Car("Car", Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar),
}

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
    val r10 = (meters / 10.0).roundToInt() * 10
    val r50 = (meters / 50.0).roundToInt() * 50
    return when {
        meters < 15 -> "You're at your car"
        meters < 100 -> "About $r10 m away"
        meters < 1000 && lowAccuracy -> "Approx. $r50 m away"
        meters < 1000 -> "$r10 m away"
        lowAccuracy -> "Approx. ${String.format(Locale.US, "%.1f", meters / 1000)} km away"
        else -> "${String.format(Locale.US, "%.1f", meters / 1000)} km away"
    }
}

private fun makeCarMarker(ctx: Context, accent: Color): BitmapDrawable {
    val size = 120
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f - 14f

    paint.color = accent.toArgb()
    val path = Path().apply {
        moveTo(cx - 11f, cy + 22f)
        lineTo(cx, cy + 44f)
        lineTo(cx + 11f, cy + 22f)
        close()
    }
    c.drawPath(path, paint)
    c.drawCircle(cx, cy, 30f, paint)

    paint.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, 22f, paint)

    paint.color = accent.toArgb()
    c.drawRoundRect(RectF(cx - 14f, cy - 2f, cx + 14f, cy + 10f), 4f, 4f, paint)
    c.drawRoundRect(RectF(cx - 9f, cy - 10f, cx + 9f, cy + 2f), 3f, 3f, paint)
    c.drawCircle(cx - 9f, cy + 11f, 2.5f, paint)
    c.drawCircle(cx + 9f, cy + 11f, 2.5f, paint)

    return BitmapDrawable(ctx.resources, bmp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(fuel: FuelStore, accent: Color) {
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
        topBar = {
            Column {
                CompactHeader(
                    title = screen.label,
                    subtitle = if (screen == AppScreen.Fuel) fuelOdoSub else null
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            }
        },
        bottomBar = {
            BottomNav(screen = screen, accent = accent, onChange = { screen = it })
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
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
    }
}

@Composable
fun CompactHeader(title: String, subtitle: String? = null) {
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun BottomNav(screen: AppScreen, accent: Color, onChange: (AppScreen) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CardBg)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppScreen.values().forEach { s ->
                val selected = screen == s
                val tint by animateColorAsState(
                    targetValue = if (selected) accent else TextMuted,
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
                            .width(20.dp)
                            .height(2.dp)
                            .background(if (selected) accent else Color.Transparent, RoundedCornerShape(999.dp))
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
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ParkingScreen(
    state: com.parked.app.data.ParkingState,
    liveLat: Double?,
    liveLng: Double?,
    liveAccuracy: Float,
    accent: Color,
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

            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp),
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

        AmbientSheet(accent = accent) {
            when {
                !hasValidParking -> {
                    Text("Save where you parked", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Never lose track of your car again.", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onUpdateSpot,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Save parking spot", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
                liveLat == null -> {
                    Text("Finding your location…", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Your parking spot is saved.", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onDirections,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Get directions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
                isAtCar -> {
                    Text("You're at your car", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        "Parked ${
                            state.parkedAt?.let {
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                            } ?: "recently"
                        }",
                        fontSize = 14.sp, color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onEndParking,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuccessGreen, contentColor = Color.White
                        )
                    ) { Text("End parking", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    TextButton(
                        onClick = { confirmMove = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Update parking spot", color = accent, fontSize = 14.sp) }
                }
                else -> {
                    Text(
                        distanceDisplay(distance, lowAcc),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                    Text(
                        "Parked ${
                            state.parkedAt?.let {
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                            } ?: "recently"
                        }",
                        fontSize = 14.sp, color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onDirections,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Get directions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    TextButton(
                        onClick = { confirmMove = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Update parking spot", color = accent, fontSize = 14.sp) }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(
                    if (state.monitoring) SuccessGreen else TextMuted, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.monitoring) "Automatic parking on" else "Automatic parking off",
                    fontSize = 13.sp, color = TextSecondary
                )
                if (!state.monitoring) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onEnableMonitoring) {
                        Text("Turn on", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (confirmMove) {
        AlertDialog(
            onDismissRequest = { confirmMove = false },
            containerColor = CardBg,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Move your parking spot?") },
            text = { Text("This will replace the saved spot with your current location.") },
            confirmButton = {
                TextButton(onClick = { confirmMove = false; onUpdateSpot() }) {
                    Text("Move spot", color = accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMove = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
fun FloatingControl(icon: ImageVector, label: String, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = ElevatedBg,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun AmbientSheet(accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = CardBg,
        shadowElevation = 12.dp
    ) {
        Box {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                            start = Offset(0f, 0f),
                            end = Offset(800f, 350f)
                        )
                    )
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
fun FuelScreen(fuel: FuelStore, accent: Color) {
    var showSlider by remember { mutableStateOf(false) }
    var showRefuel by remember { mutableStateOf(false) }

    val level by remember { derivedStateOf { fuel.estimateLevelPct() } }
    val rangeRemaining by remember { derivedStateOf { fuel.estimatedRangeRemainingKm() } }
    val avg by remember { derivedStateOf { fuel.averageL100km() } }
    val rangeFull by remember { derivedStateOf { fuel.estimatedRangeKm() } }

    val hasAnyFill = fuel.refuels.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {

            if (!fuel.isConfigured) {
                Text("Set up your car", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add your tank capacity and odometer in the Car tab to start tracking fuel.",
                    fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp
                )
                return@Column
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardBg,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "FUEL LEVEL",
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = TextSecondary, letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(level.toInt().toString(), fontSize = 42.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 42.sp)
                        Text(
                            "%", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 3.dp, bottom = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))

                    val lowFuel = level < 20
                    val barColor by animateColorAsState(
                        targetValue = if (lowFuel) WarningYellow else accent,
                        animationSpec = tween(400),
                        label = "barColor"
                    )
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
                                .background(barColor, RoundedCornerShape(999.dp))
                        )
                    }
                    if (lowFuel) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(WarningYellow, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text("Low fuel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WarningYellow)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = BorderSubtle)
                    Spacer(Modifier.height(14.dp))

                    if (rangeRemaining != null) {
                        Text(
                            "≈ ${rangeRemaining!!.toInt()} km estimated range",
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                        )
                    } else {
                        Text("Range unavailable", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Log your first fill to start estimating range.",
                            fontSize = 13.sp, color = TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (!hasAnyFill) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Track your fuel usage", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Log your first fill to see consumption, costs and estimated range.",
                            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showRefuel = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Log first fill", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    val last = fuel.refuels.first()
                    StatCard(
                        "LAST FILL",
                        String.format(Locale.US, "%.1f", last.litres), "L",
                        "${fuel.currency}${String.format(Locale.US, "%.2f", last.cost)}",
                        Modifier.weight(1f)
                    )
                    val sinceFill = (fuel.currentOdo - last.odometer).coerceAtLeast(0.0)
                    StatCard(
                        "DISTANCE",
                        String.format(Locale.US, "%,.0f", sinceFill), "km",
                        "Since fill",
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
            Surface(
                onClick = { showRefuel = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).size(56.dp),
                shape = CircleShape,
                color = accent,
                shadowElevation = 10.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, "Log refuel", tint = Color.White, modifier = Modifier.size(26.dp))
                }
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

@Composable
fun CarScreen(
    state: com.parked.app.data.ParkingState,
    fuel: FuelStore,
    accent: Color,
    onMonitoringToggle: (Boolean) -> Unit,
    onSelectDevice: (String, String) -> Unit,
) {
    var showDevicePicker by remember { mutableStateOf(false) }
    var showFuelSettings by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        SectionHeader("VEHICLE")
        SettingsRow(
            title = state.deviceName ?: "Not set",
            subtitle = "Your car",
            accent = accent,
            onClick = { showDevicePicker = true }
        )

        Spacer(Modifier.height(20.dp))

        SectionHeader("APPEARANCE")
        SettingsRow(
            title = Accents.firstOrNull { it.color.value == accent.value }?.name ?: "Accent",
            subtitle = "Accent color",
            accent = accent,
            onClick = { showAppearance = true },
            leading = {
                Box(
                    Modifier.size(28.dp).background(accent, CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                )
            }
        )

        Spacer(Modifier.height(20.dp))

        SectionHeader("AUTOMATIC PARKING")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CardBg,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp).background(
                                    if (state.monitoring) SuccessGreen else TextMuted, CircleShape
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.monitoring) "On" else "Off",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                            )
                        }
                        if (state.monitoring && state.deviceName != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Connected to ${state.deviceName}",
                                fontSize = 13.sp, color = TextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = state.monitoring,
                        onCheckedChange = { onMonitoringToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = ElevatedBg,
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { showDevicePicker = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.DirectionsCar, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bluetooth device", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            state.deviceName ?: "Choose a device",
                            fontSize = 13.sp, color = TextSecondary
                        )
                    }
                    Text("Change", fontSize = 13.sp, color = accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        SectionHeader("ODOMETER")
        SettingsRow(
            title = if (fuel.isConfigured) String.format(Locale.US, "%,.0f km", fuel.currentOdo) else "Not set",
            subtitle = "Tracked via GPS while connected",
            accent = accent,
            onClick = { showFuelSettings = true }
        )

        Spacer(Modifier.height(20.dp))

        SectionHeader("FUEL")
        SettingsRow(
            title = if (fuel.tankCapacity > 0) "Tank · ${fuel.tankCapacity.toInt()} L" else "Tank size not set",
            subtitle = "Currency · ${fuel.currency}",
            accent = accent,
            onClick = { showFuelSettings = true }
        )

        Spacer(Modifier.height(20.dp))

        SectionHeader("PREFERENCES")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CardBg,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                PreferenceRow("Units", "L/100 km")
                HorizontalDivider(color = BorderSubtle)
                PreferenceRow("Notifications", "On")
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
            onDismiss = { showAppearance = false }
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
private fun SettingsRow(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            Text("Edit", fontSize = 13.sp, color = accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PreferenceRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = TextSecondary)
    }
}

@Composable
private fun AppearanceModal(fuel: FuelStore, accent: Color, onDismiss: () -> Unit) {
    ModalScaffold(accent = accent, onDismiss = onDismiss) {
        Text("Appearance", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("Accent color", fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(18.dp))

        Accents.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                row.forEach { def ->
                    AccentSwatch(
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
        Spacer(Modifier.height(6.dp))
        Text(
            "Coming soon — uses a photo of your car to pick the accent.",
            fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp
        )

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Done", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun AccentSwatch(
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
                .background(def.color, CircleShape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
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

@Composable
fun DevicePickerDialog(
    accent: Color,
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
        containerColor = CardBg,
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
            TextButton(onClick = onDismiss) { Text("Close", color = accent) }
        }
    )
}

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
    accent: Color,
    onMapReady: (MapView) -> Unit,
    onUserTap: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
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

                    // Start with anchor way below → marker appears high above ground
                    val marker = Marker(map).apply {
                        position = newParked
                        title = "Parked car"
                        icon = carMarkerIcon
                        setAnchor(Marker.ANCHOR_CENTER, 1.8f)
                    }
                    map.overlays.add(marker)
                    parkedMarkerRef.value = marker

                    val ripple = RippleOverlay(newParked, accent.toArgb())
                    map.overlays.add(ripple)
                    rippleRef.value = ripple

                    // Drop: animate anchor from 1.8 → 1.0
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
                        ripple.start(map)
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

@Composable
private fun StatCard(label: String, value: String, unit: String, sub: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
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
private fun RefuelRow(r: Refuel, fuel: FuelStore, accent: Color) {
    val fmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(ElevatedBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.LocalGasStation, null, tint = accent, modifier = Modifier.size(18.dp))
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
                Text("FULL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
            }
        }
    }
}

@Composable
private fun SliderModal(
    currentLevel: Double,
    rangeFullKm: Double?,
    accent: Color,
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
                        thumbColor = accent,
                        activeTrackColor = accent,
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
        Button(
            onClick = { onSave(v.toDouble()) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = TextSecondary)
        }
    }
}

@Composable
private fun RefuelModal(
    fuel: FuelStore,
    accent: Color,
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
                    colors = CheckboxDefaults.colors(checkedColor = accent)
                )
                Column {
                    Text("Tank is full", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text("Gives the most accurate readings", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val enabled = litres.toDoubleOrNull() != null && odo.toDoubleOrNull() != null
        Button(
            onClick = {
                val l = litres.toDoubleOrNull() ?: return@Button
                val c = cost.toDoubleOrNull() ?: 0.0
                val o = odo.toDoubleOrNull() ?: return@Button
                onSave(l, c, o, tankFull)
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = TextSecondary)
        }
    }
}

@Composable
private fun FuelSettingsModal(fuel: FuelStore, accent: Color, onDismiss: () -> Unit) {
    var tank by remember { mutableStateOf(if (fuel.tankCapacity > 0) String.format(Locale.US, "%.0f", fuel.tankCapacity) else "") }
    var odo by remember { mutableStateOf(if (fuel.baselineOdo > 0) String.format(Locale.US, "%.0f", fuel.baselineOdo) else "") }
    var cur by remember { mutableStateOf(fuel.currency) }

    ModalScaffold(accent = accent, onDismiss = onDismiss) {
        Text("Fuel settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
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

        Button(
            onClick = {
                tank.toDoubleOrNull()?.let { fuel.updateTankCapacity(it) }
                odo.toDoubleOrNull()?.let { fuel.setOdometer(it) }
                fuel.updateCurrency(cur.ifBlank { "€" })
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = TextSecondary)
        }
    }
}

@Composable
private fun ModalScaffold(
    accent: Color,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = CardBg,
            shadowElevation = 16.dp
        ) {
            Box {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                                start = Offset(0f, 0f),
                                end = Offset(800f, 350f)
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
