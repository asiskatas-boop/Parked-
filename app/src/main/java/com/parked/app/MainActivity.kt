package com.parked.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent { ParkedTheme { ParkedApp() } }
    }
}

// ============ Colors ============

private val AccentBlue = Color(0xFF0A84FF)
private val SuccessGreen = Color(0xFF34C759)
private val WarningYellow = Color(0xFFFFCC00)
private val DangerRed = Color(0xFFFF3B30)
private val TextPrimary = Color(0xFF000000)
private val TextSecondary = Color(0xFF6B7280)
private val CardGray = Color(0xFFF2F2F7)
private val HairlineGray = Color(0xFFE5E5EA)

private val LightColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EFFF),
    onPrimaryContainer = Color(0xFF002F66),
    background = Color.White,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = CardGray,
    onSurfaceVariant = TextSecondary,
    outline = HairlineGray,
    error = DangerRed,
)

private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A2540),
    onPrimaryContainer = Color(0xFFE1EFFF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF2C2C2E),
    error = Color(0xFFFF453A),
)

@Composable
fun ParkedTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

// ============ Helpers ============

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

// Distance display rules from UX doc
private fun distanceDisplay(meters: Double?, lowAccuracy: Boolean): String {
    if (meters == null) return "Finding your location…"
    val rounded10 = (meters / 10.0).roundToInt() * 10
    val rounded50 = (meters / 50.0).roundToInt() * 50
    return when {
        meters < 15 -> "You're at your car"
        meters < 100 -> "About $rounded10 m away"
        meters < 1000 && lowAccuracy -> "Approx. $rounded50 m away"
        meters < 1000 -> "$rounded10 m away"
        lowAccuracy -> "Approx. ${String.format(Locale.US, "%.1f", meters / 1000)} km away"
        else -> "${String.format(Locale.US, "%.1f", meters / 1000)} km away"
    }
}

// ============ Screens ============

enum class AppScreen(
    val label: String,
    val filled: ImageVector,
    val outlined: ImageVector,
) {
    Parking("Parking", Icons.Filled.LocalParking, Icons.Outlined.LocalParking),
    Fuel("Fuel", Icons.Filled.LocalGasStation, Icons.Outlined.LocalGasStation),
    Car("Car", Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar),
}

// ============ Root ============

@Composable
fun ParkedApp() {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1600)
        showSplash = false
    }

    Crossfade(targetState = showSplash, animationSpec = tween(500)) { splash ->
        if (splash) SplashScreen() else MainContent()
    }
}

@Composable
fun SplashScreen() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).background(AccentBlue, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("P", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Parked!",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Never lose your car again",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ParkingStore(context) }
    val fuel = remember { FuelStore(context) }
    val state by store.state.collectAsState(initial = com.parked.app.data.ParkingState())

    var screen by remember { mutableStateOf(AppScreen.Parking) }

    // First-run: if no car selected, jump to Car screen
    LaunchedEffect(Unit) {
        val real = store.state.first()
        if (real.deviceAddress == null) screen = AppScreen.Car
    }

    var liveLat by remember { mutableStateOf<Double?>(null) }
    var liveLng by remember { mutableStateOf<Double?>(null) }
    var liveAccuracy by remember { mutableStateOf<Float>(100f) }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                AppScreen.values().forEach { s ->
                    NavigationBarItem(
                        selected = screen == s,
                        onClick = { screen = s },
                        icon = {
                            Icon(
                                if (screen == s) s.filled else s.outlined,
                                contentDescription = s.label
                            )
                        },
                        label = { Text(s.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentBlue,
                            selectedTextColor = AccentBlue,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                AppScreen.Parking -> ParkingScreen(
                    state = state,
                    liveLat = liveLat,
                    liveLng = liveLng,
                    liveAccuracy = liveAccuracy,
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
                    onDisableMonitoring = {
                        runCatching { context.stopService(Intent(context, ParkingMonitorService::class.java)) }
                        scope.launch { store.setMonitoring(false) }
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
                AppScreen.Fuel -> FuelScreen(fuel = fuel)
                AppScreen.Car -> CarScreen(
                    state = state,
                    fuel = fuel,
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

// ============ Parking ============

@Composable
fun ParkingScreen(
    state: com.parked.app.data.ParkingState,
    liveLat: Double?,
    liveLng: Double?,
    liveAccuracy: Float,
    onEnableMonitoring: () -> Unit,
    onDisableMonitoring: () -> Unit,
    onUpdateSpot: () -> Unit,
    onEndParking: () -> Unit,
    onDirections: () -> Unit,
) {
    val mapRef = remember { mutableStateOf<MapView?>(null) }
    var confirmMove by remember { mutableStateOf(false) }

    val hasValidParking = state.parkedLat != null && state.parkedLng != null &&
        !(state.parkedLat == 0.0 && state.parkedLng == 0.0)

    val distanceMeters: Double? = remember(liveLat, liveLng, state.parkedLat, state.parkedLng, hasValidParking) {
        if (liveLat != null && liveLng != null && hasValidParking)
            haversine(liveLat, liveLng, state.parkedLat!!, state.parkedLng!!)
        else null
    }

    val isAtCar = distanceMeters != null && distanceMeters < 15
    val lowAccuracy = liveAccuracy > 50f

    Column(Modifier.fillMaxSize()) {
        // Header
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text("Parking", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).background(
                        if (state.monitoring) SuccessGreen else TextSecondary, CircleShape
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.monitoring) "Parked" else "Not watching",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Map
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ParkedMap(
                parkedLat = if (hasValidParking) state.parkedLat else null,
                parkedLng = if (hasValidParking) state.parkedLng else null,
                liveLat = liveLat,
                liveLng = liveLng,
                onMapReady = { mapRef.value = it }
            )

            // Two floating buttons (right side, no zoom)
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasValidParking) {
                    Surface(
                        onClick = {
                            val lat = state.parkedLat; val lng = state.parkedLng
                            if (lat != null && lng != null) mapRef.value?.controller?.animateTo(GeoPoint(lat, lng))
                        },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.LocationOn, "Saved car", tint = AccentBlue, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Surface(
                    onClick = {
                        if (liveLat != null && liveLng != null) mapRef.value?.controller?.animateTo(GeoPoint(liveLat, liveLng))
                    },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MyLocation, "Recenter on me", tint = AccentBlue, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Info card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    !hasValidParking -> {
                        Text(
                            "No parking spot saved yet",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Save your spot to find it later.",
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    liveLat == null -> {
                        Text("Finding your location…", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Your parking spot is saved.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Text(
                            distanceDisplay(distanceMeters, lowAccuracy),
                            fontSize = 22.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Parked ${
                                state.parkedAt?.let {
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                                } ?: "recently"
                            }",
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Primary CTA
                if (hasValidParking && isAtCar) {
                    Button(
                        onClick = onEndParking,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuccessGreen, contentColor = Color.White
                        )
                    ) { Text("End parking", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                } else if (hasValidParking) {
                    Button(
                        onClick = onDirections,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Get directions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                } else if (!state.monitoring) {
                    Button(
                        onClick = onEnableMonitoring,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Start automatic parking", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }

                // Secondary CTA
                TextButton(
                    onClick = {
                        if (hasValidParking) confirmMove = true else onUpdateSpot()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (hasValidParking) "Update parking spot" else "Save current location", color = AccentBlue, fontSize = 14.sp)
                }

                // Automatic parking status row (inside Parking only)
                if (state.monitoring) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).background(SuccessGreen, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text("Automatic parking on", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (confirmMove) {
        AlertDialog(
            onDismissRequest = { confirmMove = false },
            title = { Text("Move your parking spot?") },
            text = { Text("This will replace the saved spot with your current location.") },
            confirmButton = {
                TextButton(onClick = { confirmMove = false; onUpdateSpot() }) {
                    Text("Move", color = AccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMove = false }) { Text("Cancel") }
            }
        )
    }
}

// ============ Fuel ============

@Composable
fun FuelScreen(fuel: FuelStore) {
    var showSlider by remember { mutableStateOf(false) }
    var showRefuel by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }

    val level by remember { derivedStateOf { fuel.estimateLevelPct() } }
    val rangeRemaining by remember { derivedStateOf { fuel.estimatedRangeRemainingKm() } }
    val avg by remember { derivedStateOf { fuel.averageL100km() } }
    val rangeFull by remember { derivedStateOf { fuel.estimatedRangeKm() } }

    val hasAnyFill = fuel.refuels.isNotEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {

        // Header
        Text("Fuel", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (fuel.isConfigured) "Odometer · ${String.format(Locale.US, "%,.0f", fuel.currentOdo)} km"
            else "Set up your car to track fuel",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        if (!fuel.isConfigured) {
            // Not configured at all — quick setup prompt
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Set up your car",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Add tank capacity and odometer in the Car tab.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, lineHeight = 20.sp
                    )
                }
            }
            return@Column
        }

        // Fuel level card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "FUEL LEVEL",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(level.toInt().toString(), fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
                    Text(
                        "%", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp, bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))

                val lowFuel = level < 20
                val barColor = if (lowFuel) WarningYellow else SuccessGreen

                Box(
                    Modifier.fillMaxWidth().height(12.dp)
                        .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                        .clickable { showSlider = true }
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth((level.toFloat() / 100f).coerceIn(0f, 1f))
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(14.dp))

                if (rangeRemaining != null) {
                    Text(
                        "≈ ${rangeRemaining!!.toInt()} km estimated range",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text("Range unavailable", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Log your first fill to start estimating range.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (!hasAnyFill) {
            // First-run state — no stats, no floating button
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Track your fuel usage", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Log your first fill to see consumption, costs and estimated range.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp
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
            // Normal state — stats + list
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
                color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            fuel.refuels.take(20).forEach { r -> RefuelRow(r, fuel) }
            Spacer(Modifier.height(90.dp))
        }
    }

    // Floating + only when there's already data
    if (hasAnyFill) {
        Box(Modifier.fillMaxSize()) {
            Surface(
                onClick = { showRefuel = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).size(56.dp),
                shape = CircleShape,
                color = AccentBlue,
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
            onDismiss = { showSlider = false },
            onSave = { v -> fuel.setLevel(v); fuel.dismissPrompt(); showSlider = false }
        )
    }
    if (showRefuel) {
        RefuelModal(
            fuel = fuel,
            onDismiss = { showRefuel = false },
            onSave = { litres, cost, odo, tankFull ->
                fuel.logRefuel(litres, cost, odo, tankFull); showRefuel = false
            }
        )
    }
    if (showSetup) showSetup = false
}

// ============ Car ============

@Composable
fun CarScreen(
    state: com.parked.app.data.ParkingState,
    fuel: FuelStore,
    onMonitoringToggle: (Boolean) -> Unit,
    onSelectDevice: (String, String) -> Unit,
) {
    var showDevicePicker by remember { mutableStateOf(false) }
    var showFuelSettings by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("Car", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // Vehicle
        SectionHeader("VEHICLE")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Your car", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    state.deviceName ?: "Not set",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Automatic parking
        SectionHeader("AUTOMATIC PARKING")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp).background(
                                    if (state.monitoring) SuccessGreen else TextSecondary, CircleShape
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.monitoring) "On" else "Off",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (state.monitoring && state.deviceName != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Connected to ${state.deviceName}",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = state.monitoring,
                        onCheckedChange = { onMonitoringToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SuccessGreen,
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { showDevicePicker = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.DirectionsCar, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bluetooth device", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            state.deviceName ?: "Choose a device",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("Change", fontSize = 13.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Odometer
        SectionHeader("ODOMETER")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable { showFuelSettings = true }
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (fuel.isConfigured) String.format(Locale.US, "%,.0f km", fuel.currentOdo)
                        else "Not set",
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Tracked in background via GPS",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Edit", fontSize = 13.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Fuel settings
        SectionHeader("FUEL")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable { showFuelSettings = true }
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (fuel.tankCapacity > 0) "Tank · ${fuel.tankCapacity.toInt()} L"
                        else "Tank size not set",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Currency · ${fuel.currency}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Edit", fontSize = 13.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Preferences
        SectionHeader("PREFERENCES")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                PreferenceRow("Units", "L/100 km")
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                PreferenceRow("Notifications", "On")
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showDevicePicker) {
        DevicePickerDialog(
            onDismiss = { showDevicePicker = false },
            onSelected = { name, address ->
                onSelectDevice(name, address); showDevicePicker = false
            }
        )
    }
    if (showFuelSettings) {
        FuelSettingsModal(
            fuel = fuel,
            onDismiss = { showFuelSettings = false }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun PreferenceRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============ Device Picker ============

@Composable
fun DevicePickerDialog(
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
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(device.name ?: "Unnamed", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(device.address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// ============ Map ============

@Composable
fun ParkedMap(
    parkedLat: Double?, parkedLng: Double?,
    liveLat: Double?, liveLng: Double?,
    onMapReady: (MapView) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
                val sLat = liveLat ?: parkedLat ?: 37.9838
                val sLng = liveLng ?: parkedLng ?: 23.7275
                controller.setCenter(GeoPoint(sLat, sLng))
                onMapReady(this)
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Marker }
            if (parkedLat != null && parkedLng != null) {
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(parkedLat, parkedLng)
                    title = "Parked car"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            if (liveLat != null && liveLng != null) {
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(liveLat, liveLng)
                    title = "You are here"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            map.invalidate()
        }
    )
}

// ============ Stat Card + Refuel Row ============

@Composable
private fun StatCard(label: String, value: String, unit: String, sub: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(unit, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RefuelRow(r: Refuel, fuel: FuelStore) {
    val fmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.LocalGasStation, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fmt.format(Date(r.date)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${String.format(Locale.US, "%.1f", r.litres)} L · ${fuel.currency}${String.format(Locale.US, "%.2f", r.cost)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (r.tankFull) {
                Text("FULL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
            }
        }
    }
}

// ============ Modals ============

@Composable
private fun SliderModal(
    currentLevel: Double,
    rangeFullKm: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var v by remember { mutableFloatStateOf(currentLevel.toFloat()) }
    val range = rangeFullKm?.let { it * v / 100 } ?: 0.0

    ModalScaffold(onDismiss = onDismiss) {
        Text("Adjust fuel level", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Look at your car's gauge and match it below", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))

        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(v.toInt().toString(), fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp)
                    Text("%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 3.dp, bottom = 8.dp))
                }
                if (range > 0) {
                    Text("≈ ${range.toInt()} km range", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(20.dp))
                Slider(
                    value = v, onValueChange = { v = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Empty", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Half", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Full", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSave(v.toDouble()) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun RefuelModal(
    fuel: FuelStore,
    onDismiss: () -> Unit,
    onSave: (Double, Double, Double, Boolean) -> Unit,
) {
    var litres by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var odo by remember { mutableStateOf(if (fuel.currentOdo > 0) String.format(Locale.US, "%.0f", fuel.currentOdo) else "") }
    var tankFull by remember { mutableStateOf(true) }

    ModalScaffold(onDismiss = onDismiss) {
        Text("Log refuel", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Fill in what you see at the pump", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable { tankFull = !tankFull }
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = tankFull, onCheckedChange = { tankFull = it })
                Column {
                    Text("Tank is full", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("Gives the most accurate readings", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun FuelSettingsModal(fuel: FuelStore, onDismiss: () -> Unit) {
    var tank by remember { mutableStateOf(if (fuel.tankCapacity > 0) String.format(Locale.US, "%.0f", fuel.tankCapacity) else "") }
    var odo by remember { mutableStateOf(if (fuel.baselineOdo > 0) String.format(Locale.US, "%.0f", fuel.baselineOdo) else "") }
    var cur by remember { mutableStateOf(fuel.currency) }

    ModalScaffold(onDismiss = onDismiss) {
        Text("Fuel settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("These values power the fuel estimate", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun ModalScaffold(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                content = content
            )
        }
    }
}
