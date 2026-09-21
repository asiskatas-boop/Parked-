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
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EFFF),
    onPrimaryContainer = Color(0xFF002F66),
    background = Color.White,
    onBackground = Color(0xFF000000),
    surface = Color.White,
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFFE5E5EA),
    error = Color(0xFFFF3B30),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A2540),
    onPrimaryContainer = Color(0xFFE1EFFF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF2C2C2E),
    error = Color(0xFFFF453A),
)

@Composable
fun ParkedTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
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

enum class AppScreen { Map, Fuel }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkedApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ParkingStore(context) }
    val fuel = remember { FuelStore(context) }
    val state by store.state.collectAsState(initial = com.parked.app.data.ParkingState())

    var screen by remember { mutableStateOf(AppScreen.Map) }
    var showDevices by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val real = store.state.first()
        if (real.deviceAddress == null) showDevices = true
    }

    var liveLat by remember { mutableStateOf<Double?>(null) }
    var liveLng by remember { mutableStateOf<Double?>(null) }

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
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(9.dp).background(
                                if (state.monitoring) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant,
                                CircleShape
                            )
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Parked!", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { screen = if (screen == AppScreen.Fuel) AppScreen.Map else AppScreen.Fuel }) {
                        Icon(
                            if (screen == AppScreen.Fuel) Icons.Filled.LocationOn else Icons.Filled.LocalGasStation,
                            contentDescription = "Fuel",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { showDevices = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Car", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { padding ->
        when {
            showDevices -> DevicePicker(
                modifier = Modifier.padding(padding),
                onSelected = { name, address ->
                    scope.launch { store.selectDevice(name, address) }
                    showDevices = false
                }
            )
            screen == AppScreen.Map -> HomeScreen(
                modifier = Modifier.padding(padding),
                state = state,
                liveLat = liveLat,
                liveLng = liveLng,
                onEnableMonitoring = {
                    requestPermissions()
                    ensureBluetoothOn(context); ensureLocationOn(context)
                    ContextCompat.startForegroundService(
                        context, Intent(context, ParkingMonitorService::class.java)
                    )
                    scope.launch { store.setMonitoring(true) }
                },
                onSaveNow = {
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
                onDirections = {
                    val lat = state.parkedLat; val lng = state.parkedLng
                    if (lat != null && lng != null) {
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
                },
                onPickCar = { showDevices = true }
            )
            else -> FuelScreen(
                modifier = Modifier.padding(padding),
                fuel = fuel
            )
        }
    }
}

@Composable
fun DevicePicker(modifier: Modifier = Modifier, onSelected: (String, String) -> Unit) {
    val context = LocalContext.current
    val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    val canRead = android.os.Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val devices = if (canRead) runCatching { adapter?.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList()) else emptyList()

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Choose your car", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick the Bluetooth device your car connects to. Parked! will watch for it to disconnect.",
            fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp
        )
        Spacer(Modifier.height(28.dp))
        if (!canRead) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("Bluetooth permission is required. Return after allowing it.", Modifier.padding(18.dp))
            }
        } else if (devices.isEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("No paired Bluetooth devices found.", Modifier.padding(18.dp))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(devices, key = { it.address }) { device ->
                    Surface(
                        onClick = { onSelected(device.name ?: "Car Bluetooth", device.address) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(device.name ?: "Unnamed", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(device.address, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    state: com.parked.app.data.ParkingState,
    liveLat: Double?, liveLng: Double?,
    onEnableMonitoring: () -> Unit,
    onSaveNow: () -> Unit,
    onDirections: () -> Unit,
    onPickCar: () -> Unit,
) {
    val mapRef = remember { mutableStateOf<MapView?>(null) }
    val distanceMeters: Double? = remember(liveLat, liveLng, state.parkedLat, state.parkedLng) {
        if (liveLat != null && liveLng != null && state.parkedLat != null && state.parkedLng != null)
            haversine(liveLat, liveLng, state.parkedLat, state.parkedLng) else null
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ParkedMap(state.parkedLat, state.parkedLng, liveLat, liveLng) { mapRef.value = it }

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(
                        if (state.monitoring) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.monitoring) "Watching: ${state.deviceName ?: "car"}" else "Monitoring off",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = {
                        val lat = state.parkedLat; val lng = state.parkedLng
                        if (lat != null && lng != null) mapRef.value?.controller?.animateTo(GeoPoint(lat, lng))
                    },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.LocationOn, "Parked car", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
                Surface(
                    onClick = {
                        if (liveLat != null && liveLng != null) mapRef.value?.controller?.animateTo(GeoPoint(liveLat, liveLng))
                    },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MyLocation, "My location", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.padding(top = 4.dp, bottom = 16.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                )

                if (state.parkedLat != null && state.parkedLng != null) {
                    val num: String = when {
                        distanceMeters == null -> "—"
                        distanceMeters < 1000 -> "${distanceMeters.toInt()}"
                        else -> String.format(Locale.US, "%.1f", distanceMeters / 1000.0)
                    }
                    val unit = when {
                        distanceMeters == null -> ""
                        distanceMeters < 1000 -> "m"
                        else -> "km"
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(num, fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 64.sp)
                        if (unit.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text(unit, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                    Text(
                        if (distanceMeters == null) "Waiting for GPS…" else "away from your car",
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(20.dp))

                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Your car", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Saved ${
                                        state.parkedAt?.let {
                                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                                        } ?: "recently"
                                    }",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onDirections,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Directions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onSaveNow,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) { Text("Save current location", fontSize = 16.sp) }
                    TextButton(onClick = onPickCar, modifier = Modifier.fillMaxWidth()) {
                        Text("Change car Bluetooth", fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text("No parked location yet", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Connect to your car once. We'll save where you parked whenever Bluetooth disconnects.",
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    if (!state.monitoring) {
                        Button(
                            onClick = onEnableMonitoring,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Start monitoring", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedButton(
                        onClick = onSaveNow,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) { Text("Save current location", fontSize = 16.sp) }
                    TextButton(onClick = onPickCar, modifier = Modifier.fillMaxWidth()) {
                        Text("Change car Bluetooth", fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
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

@Composable
fun FuelScreen(modifier: Modifier = Modifier, fuel: FuelStore) {
    var showSlider by remember { mutableStateOf(false) }
    var showRefuel by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val level by remember { derivedStateOf { fuel.estimateLevelPct() } }
    val rangeRemaining by remember { derivedStateOf { fuel.estimatedRangeRemainingKm() } }
    val avg = remember { derivedStateOf { fuel.averageL100km() } }
    val rangeFull = remember { derivedStateOf { fuel.estimatedRangeKm() } }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fuel", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    val sub = if (fuel.isConfigured) {
                        val odoText = String.format(Locale.US, "%,.0f km", fuel.currentOdo)
                        val avgText = avg?.let { " · avg ${String.format(Locale.US, "%.1f", it)} L/100km" } ?: ""
                        odoText + avgText
                    } else "Not configured yet"
                    Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, "Settings", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (!fuel.isConfigured) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Set up your car", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Enter your tank capacity and current odometer to start tracking fuel economy.",
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { showSettings = true }, shape = RoundedCornerShape(12.dp)) {
                            Text("Set up")
                        }
                    }
                }
            } else {
                if (fuel.shouldShowPrompt()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Looks like ${level.toInt()}% full.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Is that right?", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            TextButton(onClick = { fuel.dismissPrompt() }) { Text("Yes") }
                            TextButton(onClick = { showSlider = true }) { Text("Adjust") }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text("ESTIMATED LEVEL", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(level.toInt().toString(), fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                                Text("%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))

                        val barColor = when {
                            level < 15 -> Color(0xFFFF3B30)
                            level < 40 -> Color(0xFFFFCC00)
                            else -> Color(0xFF34C759)
                        }
                        Box(
                            Modifier.fillMaxWidth().height(14.dp)
                                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                                .clickable { showSlider = true }
                        ) {
                            Box(
                                Modifier.fillMaxHeight().fillMaxWidth(level.toFloat() / 100f)
                                    .background(barColor, RoundedCornerShape(999.dp))
                            )
                        }

                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("0", "25", "50", "75", "100").forEach {
                                Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(14.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "≈ ${rangeRemaining?.toInt() ?: 0} km range",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            Text("est.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("AVERAGE", avg?.let { String.format(Locale.US, "%.1f", it) } ?: "—", "L/100", "Last 5 fills", Modifier.weight(1f))
                    val last = fuel.refuels.firstOrNull()
                    StatCard("LAST FILL", last?.let { String.format(Locale.US, "%.1f", it.litres) } ?: "—", "L",
                        last?.let { "${fuel.currency}${String.format(Locale.US, "%.2f", it.cost)}" } ?: "No fills yet", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    val sinceFill = fuel.refuels.firstOrNull()?.let { (fuel.currentOdo - it.odometer).coerceAtLeast(0.0) } ?: 0.0
                    StatCard("DISTANCE", String.format(Locale.US, "%,.0f", sinceFill), "km", "Since last fill", Modifier.weight(1f))
                    val costPerKm = run {
                        val last5 = fuel.refuels.take(5)
                        if (last5.size < 2) null else {
                            val dist = last5.first().odometer - last5.last().odometer
                            val cost = last5.dropLast(1).sumOf { it.cost }
                            if (dist > 50) cost / dist else null
                        }
                    }
                    StatCard("COST", costPerKm?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                        fuel.currency + "/km", "Fuel only", Modifier.weight(1f))
                }

                Spacer(Modifier.height(22.dp))

                Text("RECENT FILLS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))

                if (fuel.refuels.isEmpty()) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Text("No refuels logged yet. Tap + to add one.", Modifier.padding(18.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    fuel.refuels.take(20).forEach { r -> RefuelRow(r, fuel) }
                }

                Spacer(Modifier.height(90.dp))
            }
        }

        if (fuel.isConfigured) {
            Surface(
                onClick = { showRefuel = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).size(60.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 10.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, "Log refuel", tint = Color.White, modifier = Modifier.size(28.dp))
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
    if (showSettings) {
        SettingsModal(
            fuel = fuel,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, unit: String, sub: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(unit, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.LocalGasStation, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
                Text("FULL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

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
                Text("≈ ${range.toInt()} km range", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun SettingsModal(fuel: FuelStore, onDismiss: () -> Unit) {
    var tank by remember { mutableStateOf(if (fuel.tankCapacity > 0) String.format(Locale.US, "%.0f", fuel.tankCapacity) else "") }
    var odo by remember { mutableStateOf(if (fuel.baselineOdo > 0) String.format(Locale.US, "%.0f", fuel.baselineOdo) else "") }
    var cur by remember { mutableStateOf(fuel.currency) }

    ModalScaffold(onDismiss = onDismiss) {
        Text("Car setup", fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                tank.toDoubleOrNull()?.let { fuel.setTankCapacity(it) }
                odo.toDoubleOrNull()?.let { fuel.setOdometer(it) }
                fuel.setCurrency(cur.ifBlank { "€" })
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
