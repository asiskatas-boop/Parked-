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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
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
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.parked.app.data.ParkingStore
import com.parked.app.service.ParkingMonitorService
import kotlinx.coroutines.flow.first
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

// ---------- Colors (Apple Maps / Uber inspired) ----------

private val AccentBlue = Color(0xFF0A84FF)
private val SuccessGreen = Color(0xFF34C759)
private val WarningOrange = Color(0xFFFF9500)
private val TextPrimary = Color(0xFF000000)
private val TextSecondary = Color(0xFF8E8E93)
private val CardGray = Color(0xFFF2F2F7)
private val HairlineGray = Color(0xFFE5E5EA)

private val ParkedColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EFFF),
    onPrimaryContainer = Color(0xFF002F66),
    secondary = AccentBlue,
    onSecondary = Color.White,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = CardGray,
    onSurfaceVariant = TextSecondary,
    background = Color.White,
    onBackground = TextPrimary,
    outline = HairlineGray,
)

// ---------- App ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkedApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ParkingStore(context) }
    val state by store.state.collectAsState(initial = com.parked.app.data.ParkingState())

    // ✅ FIX: don't show the picker every launch. Wait for DataStore to load once.
    var showDevices by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val real = store.state.first()
        if (real.deviceAddress == null) showDevices = true
    }

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

    LaunchedEffect(Unit) { requestPermissions() }

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
            containerColor = Color.White,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .background(
                                        if (state.monitoring) SuccessGreen else TextSecondary,
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Parked!", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        }
                    },
                    actions = {
                        TextButton(onClick = { showDevices = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Car", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = TextPrimary,
                        actionIconContentColor = AccentBlue,
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

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Choose your car", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick the Bluetooth device your car connects to. Parked! will watch for it to disconnect.",
            fontSize = 15.sp,
            color = TextSecondary,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(28.dp))
        if (!canRead) {
            Surface(shape = RoundedCornerShape(16.dp), color = CardGray) {
                Text(
                    "Bluetooth permission is required. Return after allowing it.",
                    Modifier.padding(18.dp),
                    color = TextPrimary
                )
            }
        } else if (devices.isEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = CardGray) {
                Text(
                    "No paired Bluetooth devices found. Pair your car in Android Settings first.",
                    Modifier.padding(18.dp),
                    color = TextPrimary
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(devices, key = { it.address }) { device ->
                    Surface(
                        onClick = { onSelected(device.name ?: "Car Bluetooth", device.address) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth().border(1.dp, HairlineGray, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(40.dp).background(Color(0xFFE1EFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Place, contentDescription = null, tint = AccentBlue)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    device.name ?: "Unnamed device",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    device.address,
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
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
    val mapRef = remember { mutableStateOf<MapView?>(null) }

    val distanceMeters: Double? = remember(liveLat, liveLng, state.parkedLat, state.parkedLng) {
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
                liveLng = liveLng,
                onMapReady = { mapRef.value = it }
            )

            // Status pill — top center
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.White,
                shadowElevation = 6.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(8.dp).background(
                            if (state.monitoring) SuccessGreen else TextSecondary,
                            CircleShape
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.monitoring) "Watching: ${state.deviceName ?: "car"}" else "Monitoring off",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            // Two floating buttons — right side
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Recenter on parked car
                Surface(
                    onClick = {
                        val lat = state.parkedLat
                        val lng = state.parkedLng
                        if (lat != null && lng != null) {
                            mapRef.value?.controller?.animateTo(GeoPoint(lat, lng))
                        }
                    },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = "Center on parked car",
                            tint = AccentBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Recenter on me
                Surface(
                    onClick = {
                        if (liveLat != null && liveLng != null) {
                            mapRef.value?.controller?.animateTo(GeoPoint(liveLat, liveLng))
                        }
                    },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = "Center on my location",
                            tint = AccentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Bottom sheet
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color.White,
            shadowElevation = 16.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Grab handle
                Box(
                    Modifier
                        .padding(top = 4.dp, bottom = 16.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .background(HairlineGray, RoundedCornerShape(999.dp))
                )

                if (state.parkedLat != null && state.parkedLng != null) {

                    // Big distance number
                    val numberText: String = when {
                        distanceMeters == null -> "—"
                        distanceMeters < 1000 -> "${distanceMeters.toInt()}"
                        else -> String.format("%.1f", distanceMeters / 1000.0)
                    }
                    val unitText: String = when {
                        distanceMeters == null -> ""
                        distanceMeters < 1000 -> "m"
                        else -> "km"
                    }

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            numberText,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            lineHeight = 64.sp
                        )
                        if (unitText.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                unitText,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    Text(
                        if (distanceMeters == null) "Waiting for GPS…" else "away from your car",
                        fontSize = 15.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(20.dp))

                    // Parked info card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CardGray,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(38.dp).background(Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Your car",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    "Saved ${
                                        state.parkedAt?.let {
                                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                                        } ?: "recently"
                                    }",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Directions button
                    Button(
                        onClick = onDirections,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Directions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(10.dp))

                    // Save button
                    OutlinedButton(
                        onClick = onSaveNow,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HairlineGray),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("Save current location", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.height(4.dp))

                    TextButton(onClick = onPickCar, modifier = Modifier.fillMaxWidth()) {
                        Text("Change car Bluetooth", color = AccentBlue, fontSize = 14.sp)
                    }

                    Spacer(Modifier.height(8.dp))

                } else {
                    // No parked location yet
                    Text(
                        "No parked location yet",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Connect to your car once. We'll save where you parked whenever Bluetooth disconnects.",
                        fontSize = 15.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 21.sp
                    )

                    Spacer(Modifier.height(20.dp))

                    if (!state.monitoring) {
                        Button(
                            onClick = onEnableMonitoring,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Start monitoring", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    OutlinedButton(
                        onClick = onSaveNow,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HairlineGray),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("Save current location", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.height(4.dp))

                    TextButton(onClick = onPickCar, modifier = Modifier.fillMaxWidth()) {
                        Text("Change car Bluetooth", color = AccentBlue, fontSize = 14.sp)
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

// ---------- Map ----------

@Composable
fun ParkedMap(
    parkedLat: Double?,
    parkedLng: Double?,
    liveLat: Double?,
    liveLng: Double?,
    onMapReady: (MapView) -> Unit,
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
                onMapReady(this)
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
