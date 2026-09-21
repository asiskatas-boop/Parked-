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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.parked.app.data.ParkingStore
import com.parked.app.service.ParkingMonitorService
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.DateFormat
import java.util.Date
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent { ParkedApp() }
    }
}

// ---------- Auto-prompt for Bluetooth & GPS ----------

private fun ensureBluetoothOn(context: Context) {
    val manager = context.getSystemService(BluetoothManager::class.java) ?: return
    val adapter = manager.adapter ?: return
    if (!adapter.isEnabled) {
        runCatching {
            context.startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private fun ensureLocationOn(context: Context) {
    val lm = context.getSystemService(LocationManager::class.java) ?: return
    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// ---------- Theme ----------

private val ParkedColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF0B2C6B),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
)

// ---------- App ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkedApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ParkingStore(context) }
    val state by store.state.collectAsState(initial = com.parked.app.data.ParkingState())
    var showDevices by remember { mutableStateOf(state.deviceAddress == null) }

    var liveLat by remember { mutableStateOf<Double?>(null) }
    var liveLng by remember { mutableStateOf<Double?>(null) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
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
            ensureBluetoothOn(context)
            ensureLocationOn(context)
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

    LaunchedEffect(Unit) {
        requestPermissions()
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMaxUpdateDelayMillis(2000L)
                .build()
            try {
                fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            } catch (_: SecurityException) {}
        } else {
            try { fusedClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { fusedClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }
    }

    MaterialTheme(colorScheme = ParkedColors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(
                                        if (state.monitoring) Color(0xFF22C55E) else Color(0xFF94A3B8),
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Parked!", fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        TextButton(onClick = { showDevices = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Car")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                )
            }
        ) { padding ->
            if (showDevices) {
                DevicePicker(
                    modifier = Modifier.padding(padding),
                    onSelected = { name, address ->
                        scope.launch { store.selectDevice(name, address) }
                        showDevices = false
                    }
                )
            } else {
                Home(
                    modifier = Modifier.padding(padding),
                    state = state,
                    liveLat = liveLat,
                    liveLng = liveLng,
                    onEnableMonitoring = {
                        requestPermissions()
                        ensureBluetoothOn(context)
                        ensureLocationOn(context)
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, ParkingMonitorService::class.java)
                        )
                        scope.launch { store.setMonitoring(true) }
                    },
                    onSaveNow = {
                        requestPermissions()
                        ensureLocationOn(context)
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
                        val lat = state.parkedLat
                        val lng = state.parkedLng
                        if (lat != null && lng != null) {
                            ensureLocationOn(context)
                            val navIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                            ).apply {
                                setPackage("com.google.android.apps.maps")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val navOk = runCatching { context.startActivity(navIntent) }.isSuccess
                            if (!navOk) {
                                val geoIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("geo:$lat,$lng?q=$lat,$lng(Parked car)")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                val geoOk = runCatching { context.startActivity(geoIntent) }.isSuccess
                                if (!geoOk) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    onPickCar = { showDevices = true }
                )
            }
        }
    }
}

// ---------- Device Picker ----------

@Composable
fun DevicePicker(modifier: Modifier = Modifier, onSelected: (String, String) -> Unit) {
    val context = LocalContext.current
    val manager = context.getSystemService(BluetoothManager::class.java)
    val adapter: BluetoothAdapter? = manager?.adapter
    val canRead = android.os.Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val devices = if (canRead) runCatching { adapter?.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList()) else emptyList()

    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Choose your car", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick the Bluetooth device your car connects to. Parked! will watch for it to disconnect.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        if (!canRead) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text("Bluetooth permission is required. Return after allowing it.", Modifier.padding(16.dp))
            }
        } else if (devices.isEmpty()) {
            Card {
                Text("No paired Bluetooth devices found. Pair your car in Android Settings first.", Modifier.padding(16.dp))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(devices, key = { it.address }) { device ->
                    Card(
                        onClick = { onSelected(device.name ?: "Car Bluetooth", device.address) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(device.name ?: "Unnamed device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Home ----------

@Composable
fun Home(
    modifier: Modifier = Modifier,
    state: com.parked.app.data.ParkingState,
    liveLat: Double?,
    liveLng: Double?,
    onEnableMonitoring: () -> Unit,
    onSaveNow: () -> Unit,
    onDirections: () -> Unit,
    onPickCar: () -> Unit,
) {
    val distanceMeters = remember(liveLat, liveLng, state.parkedLat, state.parkedLng) {
        if (liveLat != null && liveLng != null && state.parkedLat != null && state.parkedLng != null) {
            haversine(liveLat, liveLng, state.parkedLat, state.parkedLng)
        } else null
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ParkedMap(
                parkedLat = state.parkedLat,
                parkedLng = state.parkedLng,
                liveLat = liveLat,
                liveLng = liveLng
            )
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(8.dp).background(
                            if (state.monitoring) Color(0xFF22C55E) else Color(0xFF94A3B8),
                            CircleShape
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.monitoring) "Watching: ${state.deviceName ?: "car"}" else "Monitoring is off",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state.parkedLat != null && state.parkedLng != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).background(
                                MaterialTheme.colorScheme.primaryContainer, CircleShape
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Your car", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "Saved ${
                                    state.parkedAt?.let {
                                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                                    } ?: "recently"
                                }",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (distanceMeters != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (distanceMeters < 1000) "${distanceMeters.toInt()} m away"
                                    else String.format("%.2f km away", distanceMeters / 1000.0),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onDirections,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Directions", fontWeight = FontWeight.SemiBold) }

                    OutlinedButton(
                        onClick = onSaveNow,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Save current location now") }

                    TextButton(onClick = onPickCar, modifier = Modifier.fillMaxWidth()) {
                        Text("Change car Bluetooth")
                    }
                } else {
                    Text("No parked location yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Connect to your car once. We'll save where you parked whenever Bluetooth disconnects.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!state.monitoring) {
                        Button(
                            onClick = onEnableMonitoring,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Start monitoring", fontWeight = FontWeight.SemiBold) }
                    }
                    OutlinedButton(
                        onClick = onSaveNow,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Save current location now") }
                    TextButton(onClick = onPickCar, modifier = Modifier.fillMaxWidth()) {
                        Text("Change car Bluetooth")
                    }
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

// ---------- Map ----------

@Composable
fun ParkedMap(
    parkedLat: Double?,
    parkedLng: Double?,
    liveLat: Double?,
    liveLng: Double?,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
                val startLat = liveLat ?: parkedLat ?: 37.9838
                val startLng = liveLng ?: parkedLng ?: 23.7275
                controller.setCenter(GeoPoint(startLat, startLng))
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Marker }

            if (parkedLat != null && parkedLng != null) {
                val parkedPoint = GeoPoint(parkedLat, parkedLng)
                map.overlays.add(Marker(map).apply {
                    position = parkedPoint
                    title = "Parked car"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }

            if (liveLat != null && liveLng != null) {
                val livePoint = GeoPoint(liveLat, liveLng)
                map.controller.animateTo(livePoint)
                map.overlays.add(Marker(map).apply {
                    position = livePoint
                    title = "You are here"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }

            map.invalidate()
        }
    )
}
