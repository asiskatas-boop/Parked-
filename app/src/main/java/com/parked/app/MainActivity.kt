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
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.Dispatchers
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
// Palette — from user's swatches
// ============================================================

private val OliveDark      = Color(0xFF2B4A23)
private val OliveDeeper    = Color(0xFF314C1C)
private val LimeBright     = Color(0xFF6EC436)
private val LimeMid        = Color(0xFF5FA52F)
private val White          = Color(0xFFFFFFFF)
private val NearBlack      = Color(0xFF08090B)
private val GrayMid        = Color(0xFF909090)
private val GrayIcon       = Color(0xFF4D4D4D)
private val RedFuel        = Color(0xFFE84848)
private val OliveFaded25   = Color(0x402B4A23)
private val LimeFaded50    = Color(0x806EC436)

// ============================================================
// Theme
// ============================================================

@Composable
fun ParkedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = LimeBright,
            onPrimary = NearBlack,
            secondary = OliveDark,
            onSecondary = White,
            background = White,
            onBackground = NearBlack,
            surface = White,
            onSurface = NearBlack,
            surfaceVariant = Color(0xFFF5F5F5),
            onSurfaceVariant = GrayMid,
        ),
        content = content
    )
}

// ============================================================
// Root
// ============================================================

@Composable
fun ParkedRoot() {
    val context = LocalContext.current
    val fuel = remember { FuelStore(context) }

    ParkedTheme {
        ParkedApp(fuel = fuel)
    }
}

@Composable
fun ParkedApp(fuel: FuelStore) {
    var showOnboarding by remember { mutableStateOf(true) }
    var onboardingPage by remember { mutableStateOf(0) }

    AnimatedContent(
        targetState = showOnboarding,
        transitionSpec = {
            fadeIn(tween(400)) togetherWith fadeOut(tween(300))
        },
        label = "splashOnboard"
    ) { onboard ->
        if (onboard) {
            OnboardingFlow(
                page = onboardingPage,
                onNext = {
                    if (onboardingPage == 0) onboardingPage = 1
                    else showOnboarding = false
                }
            )
        } else {
            MainContent(fuel = fuel)
        }
    }
}

// ============================================================
// Onboarding
// ============================================================

@Composable
fun OnboardingFlow(page: Int, onNext: () -> Unit) {
    val bg = if (page == 0) OliveDark else LimeBright
    val fg = if (page == 0) White else OliveDark
    val carColor = if (page == 0) White.copy(alpha = 0.35f) else White.copy(alpha = 0.7f)

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .clickable { onNext() }
    ) {
        CarPattern(color = carColor, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Parked!",
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                color = fg,
                letterSpacing = (-1.5).sp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "helps you find the last\nlocation of your car through\nBT and GPS connection.",
                fontSize = 17.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Medium,
                color = fg.copy(alpha = 0.92f),
                textAlign = TextAlign.Center
            )
        }

        Text(
            if (page == 0) "Tap to continue" else "Tap to start",
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun CarPattern(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val carW = 60f
        val carH = 34f
        val stepX = carW * 2.2f
        val stepY = carH * 3.2f
        val cols = (size.width / stepX).toInt() + 2
        val rows = (size.height / stepY).toInt() + 2

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = c * stepX + (if (r % 2 == 1) stepX / 2f else 0f) - stepX / 2f
                val y = r * stepY - stepY / 2f
                drawCarSilhouette(Offset(x, y), carW, carH, color)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCarSilhouette(
    topLeft: Offset, w: Float, h: Float, color: Color
) {
    val path = Path().apply {
        moveTo(topLeft.x + w * 0.08f, topLeft.y + h * 0.7f)
        lineTo(topLeft.x + w * 0.12f, topLeft.y + h * 0.47f)
        quadraticBezierTo(
            topLeft.x + w * 0.16f, topLeft.y + h * 0.35f,
            topLeft.x + w * 0.25f, topLeft.y + h * 0.33f
        )
        lineTo(topLeft.x + w * 0.75f, topLeft.y + h * 0.33f)
        quadraticBezierTo(
            topLeft.x + w * 0.84f, topLeft.y + h * 0.35f,
            topLeft.x + w * 0.88f, topLeft.y + h * 0.47f
        )
        lineTo(topLeft.x + w * 0.92f, topLeft.y + h * 0.7f)
        lineTo(topLeft.x + w * 0.92f, topLeft.y + h * 0.88f)
        lineTo(topLeft.x + w * 0.8f, topLeft.y + h * 0.88f)
        lineTo(topLeft.x + w * 0.8f, topLeft.y + h * 0.82f)
        lineTo(topLeft.x + w * 0.2f, topLeft.y + h * 0.82f)
        lineTo(topLeft.x + w * 0.2f, topLeft.y + h * 0.88f)
        lineTo(topLeft.x + w * 0.08f, topLeft.y + h * 0.88f)
        close()
    }
    drawPath(path, color)
}

// ============================================================
// Main content — bottom nav
// ============================================================

enum class AppTab(val label: String, val filled: androidx.compose.ui.graphics.vector.ImageVector, val outlined: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Fuel("Fuel", Icons.Filled.LocalGasStation, Icons.Outlined.LocalGasStation),
    Settings("Settings", Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar),
}

@Composable
fun MainContent(fuel: FuelStore) {
    var tab by remember { mutableStateOf(AppTab.Home) }

    Column(Modifier.fillMaxSize().background(White)) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val offset = if (forward) 1 else -1
                    (slideInVertically(tween(260)) { it / 14 } + fadeIn(tween(220)))
                        .togetherWith(slideOutVertically(tween(220)) { -it / 14 } + fadeOut(tween(180)))
                },
                label = "tabContent"
            ) { t ->
                when (t) {
                    AppTab.Home -> HomeScreen(fuel = fuel)
                    AppTab.Fuel -> FuelScreen(fuel = fuel)
                    AppTab.Settings -> SettingsScreen(fuel = fuel)
                }
            }
        }

        BottomNav(tab = tab, onChange = { tab = it })
    }
}

@Composable
fun BottomNav(tab: AppTab, onChange: (AppTab) -> Unit) {
    Surface(
        color = White,
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.values().forEach { t ->
                val selected = tab == t
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onChange(t) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (selected) OliveDark else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (selected) t.filled else t.outlined,
                            contentDescription = t.label,
                            tint = if (selected) LimeBright else GrayIcon,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// Home / Parking
// ============================================================

@Composable
fun HomeScreen(fuel: FuelStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ParkingStore(context) }
    val state by store.state.collectAsState(initial = com.parked.app.data.ParkingState())

    var liveLat by remember { mutableStateOf<Double?>(null) }
    var liveLng by remember { mutableStateOf<Double?>(null) }
    var liveAccuracy by remember { mutableStateOf(100f) }
    var toastVisible by remember { mutableStateOf(false) }

    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                liveLat = loc.latitude
                liveLng = loc.longitude
                liveAccuracy = loc.accuracy
            }
        }
    }

    var hasPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPerm = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasPerm) {
            ensureBluetoothOn(context)
            ensureLocationOn(context)
        }
    }

    LaunchedEffect(Unit) {
        permLauncher.launch(buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray())
    }

    LaunchedEffect(hasPerm) {
        if (hasPerm) {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMaxUpdateDelayMillis(2000L).build()
            try {
                fused.requestLocationUpdates(req, locCallback, Looper.getMainLooper())
            } catch (_: SecurityException) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose { try { fused.removeLocationUpdates(locCallback) } catch (_: Exception) {} }
    }

    // Show toast after a fresh save
    LaunchedEffect(state.parkedAt) {
        if (state.parkedAt != null && state.parkedLat != null && !(state.parkedLat == 0.0)) {
            if (fuel.refuels.isNotEmpty()) {
                toastVisible = true
                delay(2800)
                toastVisible = false
            }
        }
    }

    val hasValid = state.parkedLat != null && state.parkedLng != null && !(state.parkedLat == 0.0 && state.parkedLng == 0.0)
    val distance: Double? = remember(liveLat, liveLng, state.parkedLat, state.parkedLng, hasValid) {
        if (liveLat != null && liveLng != null && hasValid)
            haversine(liveLat, liveLng, state.parkedLat!!, state.parkedLng!!)
        else null
    }
    val isAtCar = distance != null && distance < 15
    val lowAcc = liveAccuracy > 50f
    val markerIcon = remember { makeParkedMarker(context) }

    val onSaveSpot = {
        permLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
        ensureLocationOn(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) scope.launch { store.saveParking(loc.latitude, loc.longitude) }
                }
        }
    }

    val onDirections = {
        val lat = state.parkedLat; val lng = state.parkedLng
        if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) {
            ensureLocationOn(context)
            val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=d")).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(nav) }.isFailure) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    val onShare = {
        val lat = state.parkedLat; val lng = state.parkedLng
        if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) {
            val msg = "I parked here: https://maps.google.com/?q=$lat,$lng"
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, msg)
            }
            runCatching { context.startActivity(Intent.createChooser(share, "Share parking spot")) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ParkedMap(
                    parkedLat = if (hasValid) state.parkedLat else null,
                    parkedLng = if (hasValid) state.parkedLng else null,
                    liveLat = liveLat,
                    liveLng = liveLng,
                    markerIcon = markerIcon
                )
            }

            Surface(
                color = White,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth().offset(y = (-22).dp)
            ) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    when {
                        !hasValid -> {
                            Text(
                                "Save where you parked",
                                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                                color = NearBlack, letterSpacing = (-0.5).sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Never lose track of your car again.",
                                fontSize = 14.sp, color = GrayMid, fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(18.dp))
                            PrimaryButton(text = "Save parking spot", onClick = onSaveSpot)
                        }

                        liveLat == null -> {
                            Text(
                                "Finding you location",
                                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                                color = NearBlack, letterSpacing = (-0.5).sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "AutoPark is on",
                                fontSize = 14.sp, color = GrayMid, fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(28.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                HourglassIcon()
                            }
                            Spacer(Modifier.height(28.dp))
                        }

                        isAtCar -> {
                            Text(
                                "Your car is here!",
                                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                                color = NearBlack, letterSpacing = (-0.5).sp
                            )
                            Spacer(Modifier.height(6.dp))
                            MetaRow(
                                left = state.parkedAt?.let { naturalTimestamp(it) } ?: "Parked recently",
                                right = "AutoPark is on"
                            )
                            Spacer(Modifier.height(18.dp))
                            PrimaryButton(text = "Get directions", onClick = onDirections)
                            Spacer(Modifier.height(10.dp))
                            SecondaryRow(
                                saveLabel = "Save Current Spot",
                                shareLabel = "Share Spot",
                                onSave = onSaveSpot,
                                onShare = onShare
                            )
                        }

                        else -> {
                            Text(
                                "Parking is saved.",
                                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                                color = NearBlack, letterSpacing = (-0.5).sp
                            )
                            Spacer(Modifier.height(6.dp))
                            MetaRow(
                                left = state.parkedAt?.let { naturalTimestamp(it) } ?: "Parked recently",
                                right = "AutoPark is on"
                            )
                            Spacer(Modifier.height(18.dp))
                            PrimaryButton(text = "Get directions", onClick = onDirections)
                            Spacer(Modifier.height(10.dp))
                            SecondaryRow(
                                saveLabel = "Save Current Spot",
                                shareLabel = "Share Spot",
                                onSave = onSaveSpot,
                                onShare = onShare
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toastVisible,
            enter = slideInVertically(tween(400)) { -it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(300)) { -it } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
        ) {
            Surface(
                color = White,
                shape = RoundedCornerShape(999.dp),
                shadowElevation = 12.dp
            ) {
                Text(
                    "Log your fuel!",
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NearBlack
                )
            }
        }
    }
}

@Composable
private fun MetaRow(left: String, right: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(left, fontSize = 14.sp, color = GrayMid, fontWeight = FontWeight.Medium)
        Text(right, fontSize = 14.sp, color = GrayMid, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LimeBright)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Navigation, null, tint = NearBlack, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NearBlack)
        }
    }
}

@Composable
private fun SecondaryRow(
    saveLabel: String,
    shareLabel: String,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(OliveDark)
                .clickable { onSave() }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(saveLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = White)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(OliveDark)
                .clickable { onShare() }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Share, null, tint = White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(shareLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = White)
            }
        }
    }
}

@Composable
private fun HourglassIcon() {
    androidx.compose.foundation.Canvas(Modifier.size(42.dp, 60.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 6f
        val p = Path().apply {
            // Top triangle
            moveTo(w * 0.15f, h * 0.08f)
            lineTo(w * 0.85f, h * 0.08f)
            lineTo(w * 0.55f, h * 0.5f)
            lineTo(w * 0.85f, h * 0.92f)
            lineTo(w * 0.15f, h * 0.92f)
            lineTo(w * 0.45f, h * 0.5f)
            close()
        }
        drawPath(p, OliveDark, style = Stroke(width = stroke))
    }
}

// ============================================================
// Fuel screen
// ============================================================

@Composable
fun FuelScreen(fuel: FuelStore) {
    var sliderFraction by remember { mutableFloatStateOf(fuel.estimateLevelPct().toFloat() / 100f) }
    var priceOverride by remember { mutableStateOf("") }
    var showAllLogs by remember { mutableStateOf(false) }

    val tank = if (fuel.tankCapacity > 0) fuel.tankCapacity else 50.0
    val liters = sliderFraction * tank
    val defaultPrice = 2.00
    val computedCost = liters * defaultPrice
    val costText = priceOverride.ifBlank { String.format(Locale.US, "%.2f", computedCost) }

    Column(Modifier.fillMaxSize().background(White)) {
        // Green header with capsule
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(OliveDark, OliveDeeper))
                )
                .padding(top = 44.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Fuel Volume",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = White
            )
            Spacer(Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // € label
                Text("€$costText", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = White)
                Spacer(Modifier.width(8.dp))
                TrianglePointer(pointingRight = true)
                Spacer(Modifier.width(10.dp))

                // Capsule
                FuelCapsule(
                    fraction = sliderFraction,
                    onFractionChange = { sliderFraction = it }
                )

                Spacer(Modifier.width(10.dp))
                TrianglePointer(pointingRight = false)
                Spacer(Modifier.width(8.dp))
                Text("${liters.toInt()}L", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = White)
            }

            Spacer(Modifier.height(20.dp))

            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(GrayIcon)
                    .clickable {
                        if (fuel.isConfigured && liters > 0.1) {
                            val odo = if (fuel.currentOdo > 0) fuel.currentOdo
                                      else (fuel.baselineOdo.takeIf { it > 0 } ?: 0.0)
                            fuel.logRefuel(
                                litres = liters,
                                cost = computedCost,
                                odometer = odo,
                                tankFull = sliderFraction > 0.95f
                            )
                            priceOverride = ""
                        }
                    }
                    .padding(horizontal = 32.dp, vertical = 12.dp)
            ) {
                Text("Save", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = White)
            }
        }

        // Logs
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Last logs", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack)
                if (fuel.refuels.isNotEmpty()) {
                    Text(
                        "View all",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OliveDark,
                        modifier = Modifier.clickable { showAllLogs = !showAllLogs }
                    )
                }
            }

            val logs = if (showAllLogs) fuel.refuels else fuel.refuels.take(4)
            if (logs.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.LocalGasStation, null,
                        tint = RedFuel,
                        modifier = Modifier.size(100.dp)
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(logs) { r ->
                        LogRow(r, fuel)
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelCapsule(
    fraction: Float,
    onFractionChange: (Float) -> Unit
) {
    val capsuleShape = RoundedCornerShape(999.dp)
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var dragStart by remember { mutableFloatStateOf(fraction) }

    Box(
        Modifier
            .size(width = 130.dp, height = 180.dp)
            .clip(capsuleShape)
            .background(White)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragStart = fraction
                        dragAccum = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccum += -dragAmount
                        val delta = dragAccum / size.height.toFloat()
                        val next = (dragStart + delta).coerceIn(0f, 1f)
                        onFractionChange(next)
                    }
                )
            }
    ) {
        // Fill from bottom
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(fraction.coerceIn(0f, 1f))
                .background(meshBrush(fraction))
        )
    }
}

@Composable
private fun meshBrush(fraction: Float): Brush {
    // Mesh depending on level
    return when {
        fraction < 0.30f -> Brush.linearGradient(
            listOf(Color(0xFFE84848), Color(0xFFFF8A5B), Color(0xFFFFB88A))
        )
        fraction < 0.60f -> Brush.linearGradient(
            listOf(Color(0xFFFF8A3B), Color(0xFFFFB347), Color(0xFFFFD98A))
        )
        else -> Brush.linearGradient(
            listOf(Color(0xFF3E8A2E), Color(0xFF6EC436), Color(0xFFB0E56B))
        )
    }
}

@Composable
private fun TrianglePointer(pointingRight: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(14.dp, 18.dp)) {
        val p = Path()
        if (pointingRight) {
            p.moveTo(0f, 0f)
            p.lineTo(size.width, size.height / 2f)
            p.lineTo(0f, size.height)
        } else {
            p.moveTo(size.width, 0f)
            p.lineTo(0f, size.height / 2f)
            p.lineTo(size.width, size.height)
        }
        p.close()
        drawPath(p, White)
    }
}

@Composable
private fun LogRow(r: Refuel, fuel: FuelStore) {
    val fmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (r.hashCode() % 2 == 0) Color(0xFFE8F3E0) else White)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(fmt.format(Date(r.date)), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
        Text(
            "${String.format(Locale.US, "%.1f", r.litres)} L - ${fuel.currency}${String.format(Locale.US, "%.0f", r.cost)}",
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = NearBlack
        )
    }
}

// ============================================================
// Settings screen
// ============================================================

@Composable
fun SettingsScreen(fuel: FuelStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ParkingStore(context) }
    val state by store.state.collectAsState(initial = com.parked.app.data.ParkingState())

    var showFuelSettings by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(White)) {
        // Green header
        Box(
            Modifier
                .fillMaxWidth()
                .background(OliveDark)
                .padding(top = 60.dp, bottom = 40.dp, start = 24.dp, end = 24.dp)
        ) {
            Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = White, letterSpacing = (-0.5).sp)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            SectionTitle("Vehicle")
            Card {
                ToggleRow(
                    "AutoPark",
                    state.monitoring,
                    onToggle = { on ->
                        if (on) {
                            ContextCompat.startForegroundService(
                                context, Intent(context, ParkingMonitorService::class.java)
                            )
                            scope.launch { store.setMonitoring(true) }
                        } else {
                            context.stopService(Intent(context, ParkingMonitorService::class.java))
                            scope.launch { store.setMonitoring(false) }
                        }
                    }
                )
                DividerRow()
                TextRow("BT Device", "Change") { }
                DividerRow()
                TextRow(
                    "Odometer",
                    if (fuel.isConfigured) String.format(Locale.US, "%.0fk km", fuel.currentOdo / 1000)
                    else "—"
                ) { showFuelSettings = true }
            }

            SectionTitle("Preferences")
            Card {
                ToggleRow(
                    "Notifications",
                    fuel.notificationsEnabled,
                    onToggle = { fuel.updateNotificationsEnabled(it) }
                )
                DividerRow()
                TextRow("Appearance", "Change") { }
                DividerRow()
                TextRow("Match my car", "+") { }
            }

            Spacer(Modifier.height(20.dp))

            // Blue car illustration placeholder
            Box(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                BlueCarIllustration()
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showFuelSettings) {
        VehicleDataModal(fuel = fuel, onDismiss = { showFuelSettings = false })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 10.dp)
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = White,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = White,
                checkedTrackColor = LimeBright,
                uncheckedThumbColor = White,
                uncheckedTrackColor = Color(0xFFDDDDDD),
            )
        )
    }
}

@Composable
private fun TextRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GrayMid)
    }
}

@Composable
private fun DividerRow() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F0F0)))
}

@Composable
private fun BlueCarIllustration() {
    androidx.compose.foundation.Canvas(Modifier.size(200.dp, 120.dp)) {
        // Shadow
        drawOval(
            Color(0x14000000),
            topLeft = Offset(size.width * 0.15f, size.height * 0.85f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.7f, size.height * 0.08f)
        )
        // Body
        val body = Path().apply {
            moveTo(size.width * 0.20f, size.height * 0.65f)
            lineTo(size.width * 0.23f, size.height * 0.48f)
            quadraticBezierTo(
                size.width * 0.26f, size.height * 0.38f,
                size.width * 0.32f, size.height * 0.36f
            )
            lineTo(size.width * 0.68f, size.height * 0.36f)
            quadraticBezierTo(
                size.width * 0.74f, size.height * 0.38f,
                size.width * 0.77f, size.height * 0.48f
            )
            lineTo(size.width * 0.80f, size.height * 0.65f)
            lineTo(size.width * 0.80f, size.height * 0.78f)
            quadraticBezierTo(
                size.width * 0.80f, size.height * 0.82f,
                size.width * 0.76f, size.height * 0.82f
            )
            lineTo(size.width * 0.24f, size.height * 0.82f)
            quadraticBezierTo(
                size.width * 0.20f, size.height * 0.82f,
                size.width * 0.20f, size.height * 0.78f
            )
            close()
        }
        drawPath(body, Color(0xFF4A90E2))

        // Windows
        val win = Path().apply {
            moveTo(size.width * 0.32f, size.height * 0.46f)
            lineTo(size.width * 0.42f, size.height * 0.46f)
            lineTo(size.width * 0.42f, size.height * 0.60f)
            lineTo(size.width * 0.28f, size.height * 0.60f)
            close()
        }
        drawPath(win, Color(0xFFA8D2F0))
        val win2 = Path().apply {
            moveTo(size.width * 0.46f, size.height * 0.46f)
            lineTo(size.width * 0.68f, size.height * 0.46f)
            lineTo(size.width * 0.72f, size.height * 0.60f)
            lineTo(size.width * 0.46f, size.height * 0.60f)
            close()
        }
        drawPath(win2, Color(0xFFA8D2F0))

        // Wheels
        drawCircle(Color(0xFF2B4A23), radius = size.height * 0.08f,
            center = Offset(size.width * 0.32f, size.height * 0.82f))
        drawCircle(Color(0xFF2B4A23), radius = size.height * 0.08f,
            center = Offset(size.width * 0.68f, size.height * 0.82f))
    }
}

// ============================================================
// Vehicle data modal
// ============================================================

@Composable
private fun VehicleDataModal(fuel: FuelStore, onDismiss: () -> Unit) {
    var tank by remember { mutableStateOf(if (fuel.tankCapacity > 0) String.format(Locale.US, "%.0f", fuel.tankCapacity) else "") }
    var odo by remember { mutableStateOf(if (fuel.baselineOdo > 0) String.format(Locale.US, "%.0f", fuel.baselineOdo) else "") }
    var cur by remember { mutableStateOf(fuel.currency) }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            Modifier.fillMaxWidth().clickable(enabled = false) {}.navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = White
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Vehicle data", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = tank, onValueChange = { tank = it },
                    label = { Text("Tank capacity (L)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = odo, onValueChange = { odo = it },
                    label = { Text("Current odometer (km)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = cur, onValueChange = { if (it.length <= 3) cur = it },
                    label = { Text("Currency symbol") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Save", onClick = {
                    tank.toDoubleOrNull()?.let { fuel.updateTankCapacity(it) }
                    odo.toDoubleOrNull()?.let { fuel.setOdometer(it) }
                    fuel.updateCurrency(cur.ifBlank { "€" })
                    onDismiss()
                })
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().clickable { onDismiss() }.padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", fontSize = 15.sp, color = GrayMid, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ============================================================
// Map
// ============================================================

@Composable
fun ParkedMap(
    parkedLat: Double?, parkedLng: Double?,
    liveLat: Double?, liveLng: Double?,
    markerIcon: BitmapDrawable,
) {
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
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Marker }
            if (parkedLat != null && parkedLng != null) {
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(parkedLat, parkedLng)
                    title = "Parked car"
                    icon = markerIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
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

// ============================================================
// Parked marker — green circle with P
// ============================================================

private fun makeParkedMarker(ctx: Context): BitmapDrawable {
    val size = 240
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f

    // Outer pulse ring
    paint.color = 0x406EC436
    c.drawCircle(cx, cy, 110f, paint)
    paint.color = 0x206EC436
    c.drawCircle(cx, cy, 120f, paint)

    // White ring
    paint.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, 78f, paint)

    // Olive green fill
    paint.color = 0xFF2B4A23.toInt()
    c.drawCircle(cx, cy, 68f, paint)

    // "P" letter
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 90f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val yOffset = (paint.descent() + paint.ascent()) / 2f
    c.drawText("P", cx, cy - yOffset, paint)

    return BitmapDrawable(ctx.resources, bmp)
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

private fun naturalTimestamp(ts: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    val sameYear = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    val sameDay = sameYear && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val yesterday = sameYear && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1
    return when {
        sameDay -> "Parked today at $timeStr"
        yesterday -> "Parked yesterday at $timeStr"
        else -> {
            val dateStr = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ts))
            "Parked $dateStr at $timeStr"
        }
    }
}
