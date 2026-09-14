package com.parked.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.viewinterop.AndroidView
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent { ParkedApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkedApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ParkingStore(context) }
    val state by store.state.collectAsState(initial = com.parked.app.data.ParkingState())
    var showDevices by remember { mutableStateOf(state.deviceAddress == null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun requestPermissions() {
        val p = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(p)
    }

    LaunchedEffect(Unit) { requestPermissions() }

    MaterialTheme(colorScheme = lightColorScheme()) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("Parked!") },
                actions = {
                    TextButton(onClick = { showDevices = true }) { Text("Car") }
                }
            )
        }) { padding ->
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
                    onEnableMonitoring = {
                        requestPermissions()
                        ContextCompat.startForegroundService(context, Intent(context, ParkingMonitorService::class.java))
                        scope.launch { store.setMonitoring(true) }
                    },
                    onSaveNow = {
                        requestPermissions()
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            LocationServices.getFusedLocationProviderClient(context)
                                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                .addOnSuccessListener { loc ->
                                    if (loc != null) scope.launch { store.saveParking(loc.latitude, loc.longitude) }
                                }
                        }
                    },
                    onDirections = {
                        val lat = state.parkedLat ?: return@Home
                        val lng = state.parkedLng ?: return@Home
                        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))
                        nav.setPackage("com.google.android.apps.maps")
                        runCatching { context.startActivity(nav) }.getOrElse {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(Parked car)")))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DevicePicker(modifier: Modifier = Modifier, onSelected: (String, String) -> Unit) {
    val context = LocalContext.current
    val manager = context.getSystemService(BluetoothManager::class.java)
    val adapter: BluetoothAdapter? = manager?.adapter
    val canRead = android.os.Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val devices = if (canRead) runCatching { adapter?.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList()) else emptyList()

    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Choose your car", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Pick the Bluetooth device your car connects to. Parked! will watch for it to disconnect.")
        Spacer(Modifier.height(20.dp))
        if (!canRead) {
            Text("Bluetooth permission is required. Return after allowing it.")
        } else if (devices.isEmpty()) {
            Text("No paired Bluetooth devices found. Pair your car in Android Settings first.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(devices, key = { it.address }) { device ->
                    ElevatedCard(onClick = { onSelected(device.name ?: "Car Bluetooth", device.address) }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(device.name ?: "Unnamed device", style = MaterialTheme.typography.titleMedium)
                            Text(device.address, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Home(
    modifier: Modifier = Modifier,
    state: com.parked.app.data.ParkingState,
    onEnableMonitoring: () -> Unit,
    onSaveNow: () -> Unit,
    onDirections: () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ParkedMap(state.parkedLat, state.parkedLng)
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                shape = RoundedCornerShape(999.dp),
                tonalElevation = 4.dp
            ) {
                Text(
                    if (state.monitoring) "Watching: ${state.deviceName ?: "car"}" else "Monitoring is off",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.parkedLat != null && state.parkedLng != null) {
                    Text("Your car", style = MaterialTheme.typography.headlineSmall)
                    Text("Saved ${state.parkedAt?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "recently"}")
                    Button(onClick = onDirections, modifier = Modifier.fillMaxWidth()) { Text("Directions") }
                    OutlinedButton(onClick = onSaveNow, modifier = Modifier.fillMaxWidth()) { Text("Save current location now") }
                } else {
                    Text("No parked location yet", style = MaterialTheme.typography.headlineSmall)
                    Text("Connect to your car once. We’ll save where you parked whenever Bluetooth disconnects.")
                    if (!state.monitoring) {
                        Button(onClick = onEnableMonitoring, modifier = Modifier.fillMaxWidth()) { Text("Start monitoring") }
                    }
                    OutlinedButton(onClick = onSaveNow, modifier = Modifier.fillMaxWidth()) { Text("Save current location now") }
                }
            }
        }
    }
}

@Composable
fun ParkedMap(lat: Double?, lng: Double?) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
                controller.setCenter(GeoPoint(lat ?: 37.9838, lng ?: 23.7275))
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Marker }
            if (lat != null && lng != null) {
                val point = GeoPoint(lat, lng)
                map.controller.animateTo(point)
                Marker(map).apply {
                    position = point
                    title = "Parked car"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(this)
                }
                map.invalidate()
            }
        }
    )
}
