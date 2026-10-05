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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GasMeter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.*

// ============================================================
// MainActivity
// ============================================================

class MainActivity : ComponentActivity() {
    private var requestedTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedTab = intent?.getStringExtra(EXTRA_OPEN_TAB)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = "Parked/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
        }
        setContent {
            ParkedRoot(
                requestedTab = requestedTab,
                onTabHandled = { requestedTab = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab = intent.getStringExtra(EXTRA_OPEN_TAB)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "com.parked.app.OPEN_TAB"
        const val TAB_FUEL = "fuel"
    }
}

// ============================================================
// Palette
// ============================================================

private val OliveDark      = Color(0xFF2B4A23)
private val LimeBright     = Color(0xFF6EC436)
private val White          = Color(0xFFFFFFFF)
private val NearBlack      = Color(0xFF08090B)
private val GrayMid        = Color(0xFF909090)
private val GrayIcon       = Color(0xFF4D4D4D)
private val RedFuel        = Color(0xFFE84848)
private val ErrorRed       = Color(0xFFB91C1C)

// ============================================================
// Font — Inter
// ============================================================

private val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

private fun interTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge   = base.displayLarge.copy(fontFamily = InterFamily),
        displayMedium  = base.displayMedium.copy(fontFamily = InterFamily),
        displaySmall   = base.displaySmall.copy(fontFamily = InterFamily),
        headlineLarge  = base.headlineLarge.copy(fontFamily = InterFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = InterFamily),
        headlineSmall  = base.headlineSmall.copy(fontFamily = InterFamily),
        titleLarge     = base.titleLarge.copy(fontFamily = InterFamily),
        titleMedium    = base.titleMedium.copy(fontFamily = InterFamily),
        titleSmall     = base.titleSmall.copy(fontFamily = InterFamily),
        bodyLarge      = base.bodyLarge.copy(fontFamily = InterFamily),
        bodyMedium     = base.bodyMedium.copy(fontFamily = InterFamily),
        bodySmall      = base.bodySmall.copy(fontFamily = InterFamily),
        labelLarge     = base.labelLarge.copy(fontFamily = InterFamily),
        labelMedium    = base.labelMedium.copy(fontFamily = InterFamily),
        labelSmall     = base.labelSmall.copy(fontFamily = InterFamily),
    )
}

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
        typography = interTypography(),
        content = content
    )
}

// ============================================================
// Root
// ============================================================

@Composable
fun ParkedRoot(requestedTab: String? = null, onTabHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val fuel = remember { FuelStore(context) }
    val parkingStore = remember { ParkingStore(context) }

    DisposableEffect(fuel) {
        onDispose { fuel.close() }
    }

    // Recover AutoPark after the app process was reclaimed. Starting it while the
    // activity is visible is allowed and avoids a stale "on" switch with no service.
    LaunchedEffect(Unit) {
        val state = parkingStore.state.first()
        if (state.monitoring) {
            val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasBluetooth = android.os.Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val canRecover = state.deviceAddress != null && hasLocation && hasBluetooth && isLocationServicesEnabled(context)

            // A persisted switch must never say AutoPark is on when Android can no
            // longer run the monitor. Bluetooth itself may be off temporarily; the
            // foreground service can safely wait for it to come back.
            if (!canRecover) {
                parkingStore.setMonitoring(false)
            } else {
                val started = runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, ParkingMonitorService::class.java)
                    )
                }.isSuccess
                if (!started) parkingStore.setMonitoring(false)
            }
        }
    }
    ParkedTheme { ParkedApp(fuel = fuel, requestedTab = requestedTab, onTabHandled = onTabHandled) }
}

@Composable
fun ParkedApp(fuel: FuelStore, requestedTab: String? = null, onTabHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("parked_ui", Context.MODE_PRIVATE) }
    var showOnboarding by remember { mutableStateOf(!uiPrefs.getBoolean("onboarding_done", false)) }
    var onboardingPage by remember { mutableStateOf(0) }

    AnimatedContent(
        targetState = showOnboarding,
        transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(350)) },
        label = "splashOnboard"
    ) { onboard ->
        if (onboard) {
            AnimatedContent(
                targetState = onboardingPage,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(400)) },
                label = "onboardPages"
            ) { page ->
                OnboardingFlow(
                    page = page,
                    onNext = {
                        if (page == 0) onboardingPage = 1
                        else {
                            uiPrefs.edit().putBoolean("onboarding_done", true).apply()
                            showOnboarding = false
                        }
                    }
                )
            }
        } else {
            MainContent(fuel = fuel, requestedTab = requestedTab, onTabHandled = onTabHandled)
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

    Box(Modifier.fillMaxSize().background(bg).clickable { onNext() }) {
        Image(
            painter = painterResource(if (page == 0) R.drawable.splash_dark else R.drawable.splash_lime),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .offset(y = 70.dp)
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Parked!",
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                color = fg,
                letterSpacing = (-1.5).sp
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "helps you find the last location of your car through BT and GPS connection.",
                fontSize = 17.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Normal,
                color = fg.copy(alpha = 0.94f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp)
            )
        }
    }
}

// ============================================================
// Bottom nav
// ============================================================

enum class AppTab(
    val label: String,
    val filled: androidx.compose.ui.graphics.vector.ImageVector,
    val outlined: androidx.compose.ui.graphics.vector.ImageVector
) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Fuel("Fuel", Icons.Filled.LocalGasStation, Icons.Outlined.LocalGasStation),
    Settings("Settings", Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar),
}

@Composable
fun MainContent(fuel: FuelStore, requestedTab: String? = null, onTabHandled: () -> Unit = {}) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(AppTab.Home) }

    LaunchedEffect(requestedTab) {
        if (requestedTab == MainActivity.TAB_FUEL) {
            tab = AppTab.Fuel
            onTabHandled()
        }
    }

    fun hasLocationPermission(ctx: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    var liveLat by remember { mutableStateOf<Double?>(null) }
    var liveLng by remember { mutableStateOf<Double?>(null) }
    var hasPerm by remember { mutableStateOf(hasLocationPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                liveLat = loc.latitude
                liveLng = loc.longitude
            }
        }
    }

    LaunchedEffect(hasPerm, tab) {
        if (!hasPerm || tab != AppTab.Home) return@LaunchedEffect
        try {
            fused.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && liveLat == null) {
                    liveLat = loc.latitude
                    liveLng = loc.longitude
                }
            }
        } catch (_: SecurityException) {}
    }

    LaunchedEffect(hasPerm, tab) {
        if (hasPerm && tab == AppTab.Home) {
            val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .setMaxUpdateDelayMillis(8000L).build()
            try {
                fused.requestLocationUpdates(req, locCallback, Looper.getMainLooper())
            } catch (_: SecurityException) {}
        } else {
            try { fused.removeLocationUpdates(locCallback) } catch (_: Exception) {}
            liveLat = null
            liveLng = null
        }
    }

    DisposableEffect(Unit) {
        onDispose { try { fused.removeLocationUpdates(locCallback) } catch (_: Exception) {} }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPerm = hasLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Do not push the user into Location settings just because the app opened.
        // Permission/location prompts are now tied to an explicit parking action.
        hasPerm = hasLocationPermission(context)
    }

    Column(Modifier.fillMaxSize().background(White)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (slideInVertically(tween(260)) { it / 14 } + fadeIn(tween(220)))
                        .togetherWith(slideOutVertically(tween(220)) { -it / 14 } + fadeOut(tween(180)))
                },
                label = "tabContent"
            ) { t ->
                when (t) {
                    AppTab.Home -> HomeScreen(
                        fuel = fuel,
                        liveLat = liveLat,
                        liveLng = liveLng,
                        hasPerm = hasPerm,
                        requestPerm = { permLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )) }
                    )
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
    Surface(color = White, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AppTab.values().forEach { t ->
                val selected = tab == t
                Box(
                    Modifier
                        .weight(1f).fillMaxHeight()
                        .clickable { onChange(t) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (selected) 64.dp else 58.dp)
                            .clip(
                                if (selected) RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
                                else RoundedCornerShape(0.dp)
                            )
                            .background(if (selected) OliveDark else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (selected) t.filled else t.outlined,
                            contentDescription = t.label,
                            tint = if (selected) LimeBright else GrayIcon,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// Home
// ============================================================

@Composable
fun HomeScreen(
    fuel: FuelStore,
    liveLat: Double?,
    liveLng: Double?,
    hasPerm: Boolean,
    requestPerm: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ParkingStore(context) }
    val state by store.state.collectAsState(initial = com.parked.app.data.ParkingState())

    var toastVisible by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var lastSeenParkedAt by remember { mutableStateOf<Long?>(null) }
    var pendingParkingSave by remember { mutableStateOf(false) }

    LaunchedEffect(hasPerm) {
        if (hasPerm && pendingParkingSave) {
            pendingParkingSave = false
            if (!isLocationServicesEnabled(context)) {
                ensureLocationOn(context)
                saveErrorMessage = "Turn on Location, then try again"
            } else {
                saveCurrentParkingSpot(context, store, scope) { msg -> saveErrorMessage = msg }
            }
        }
    }

    LaunchedEffect(saveErrorMessage) {
        if (saveErrorMessage != null) {
            delay(3200)
            saveErrorMessage = null
        }
    }

    LaunchedEffect(state.parkedAt) {
        val current = state.parkedAt
        val previous = lastSeenParkedAt
        if (current != null && previous != null && current != previous) {
            val lastFuelLogTime = maxOf(
                fuel.fuelLogs.firstOrNull()?.date ?: 0L,
                fuel.refuels.firstOrNull()?.date ?: 0L
            )
            val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            if (lastFuelLogTime < oneDayAgo) {
                toastVisible = true
                delay(2800)
                toastVisible = false
            }
        }
        lastSeenParkedAt = current
    }

    val hasValid = state.parkedLat != null && state.parkedLng != null &&
        !(state.parkedLat == 0.0 && state.parkedLng == 0.0)
    val liveLatLocal = liveLat
    val liveLngLocal = liveLng
    val parkedLatLocal = state.parkedLat
    val parkedLngLocal = state.parkedLng
    val distance: Double? = remember(liveLatLocal, liveLngLocal, parkedLatLocal, parkedLngLocal, hasValid) {
        if (liveLatLocal != null && liveLngLocal != null && parkedLatLocal != null && parkedLngLocal != null && hasValid)
            haversine(liveLatLocal, liveLngLocal, parkedLatLocal, parkedLngLocal)
        else null
    }
    val isAtCar = distance != null && distance < 15
    val parkedMarkerIcon = remember { makeParkedMarker(context) }
    val liveMarkerIcon = remember { makeLiveMarker(context) }

    val onSaveSpot = {
        if (!hasPerm) {
            pendingParkingSave = true
            requestPerm()
        } else if (!isLocationServicesEnabled(context)) {
            ensureLocationOn(context)
            saveErrorMessage = "Turn on Location, then try again"
        } else {
            saveCurrentParkingSpot(context, store, scope) { msg -> saveErrorMessage = msg }
        }
    }

    val onDirections = {
        val lat = state.parkedLat; val lng = state.parkedLng
        if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) {
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
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(share, "Share parking spot").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(chooser) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ParkedMap(
            parkedLat = if (hasValid) state.parkedLat else null,
            parkedLng = if (hasValid) state.parkedLng else null,
            liveLat = liveLat,
            liveLng = liveLng,
            parkedMarkerIcon = parkedMarkerIcon,
            liveMarkerIcon = liveMarkerIcon
        )

        Text(
            "© OpenStreetMap contributors",
            fontSize = 9.sp,
            color = NearBlack.copy(alpha = 0.75f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 6.dp, end = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(White.copy(alpha = 0.82f))
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))
                        )
                    }
                }
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )

        Surface(
            color = White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                val autoParkLabel = if (state.monitoring) "AutoPark is on" else "AutoPark is off"

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
                        // A saved parking position is still useful even when the
                        // phone cannot provide a live location. Do not trap the
                        // user behind a GPS/location prompt just to see the car.
                        Text(
                            "Parking is saved.",
                            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                            color = NearBlack, letterSpacing = (-0.5).sp
                        )
                        Spacer(Modifier.height(6.dp))
                        MetaRow(
                            left = state.parkedAt?.let { naturalTimestamp(it) } ?: "Parked recently",
                            right = autoParkLabel
                        )
                        Spacer(Modifier.height(18.dp))
                        PrimaryButton(text = "Open saved location", showDirectionIcon = true, onClick = onDirections)
                        Spacer(Modifier.height(10.dp))
                        SecondaryRow(onSave = onSaveSpot, onShare = onShare)
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
                            right = autoParkLabel
                        )
                        Spacer(Modifier.height(18.dp))
                        PrimaryButton(text = "Get directions", showDirectionIcon = true, onClick = onDirections)
                        Spacer(Modifier.height(10.dp))
                        SecondaryRow(onSave = onSaveSpot, onShare = onShare)
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
                            right = autoParkLabel
                        )
                        Spacer(Modifier.height(18.dp))
                        PrimaryButton(text = "Get directions", showDirectionIcon = true, onClick = onDirections)
                        Spacer(Modifier.height(10.dp))
                        SecondaryRow(onSave = onSaveSpot, onShare = onShare)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = saveErrorMessage != null,
            enter = slideInVertically(tween(300)) { -it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(250)) { -it } + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
        ) {
            Surface(color = ErrorRed, shape = RoundedCornerShape(999.dp), shadowElevation = 12.dp) {
                Text(
                    saveErrorMessage ?: "",
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = White
                )
            }
        }

        AnimatedVisibility(
            visible = toastVisible,
            enter = slideInVertically(tween(400)) { -it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(300)) { -it } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
        ) {
            Surface(color = White, shape = RoundedCornerShape(999.dp), shadowElevation = 12.dp) {
                Text(
                    "Log your fuel!",
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NearBlack
                )
            }
        }
    }
}

private fun saveCurrentParkingSpot(
    context: Context,
    store: ParkingStore,
    scope: kotlinx.coroutines.CoroutineScope,
    onFailure: (String) -> Unit = {}
) {
    val fineGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted && !coarseGranted) {
        onFailure("Location permission required")
        return
    }
    val fused = LocationServices.getFusedLocationProviderClient(context)

    // Prefer a recent cached fix. This makes manual Save behave like AutoPark:
    // it does not wake high-accuracy GPS when Android already knows where the
    // phone was a moment ago.
    try {
        fused.lastLocation
            .addOnCompleteListener { cachedTask ->
                val cached = if (cachedTask.isSuccessful) cachedTask.result else null
                val recent = cached?.takeIf {
                    System.currentTimeMillis() - it.time <= 2 * 60 * 1000L && it.accuracy <= 150f
                }
                if (recent != null) {
                    scope.launch { store.saveParking(recent.latitude, recent.longitude) }
                    return@addOnCompleteListener
                }

                try {
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                        .addOnCompleteListener { currentTask ->
                            val loc = if (currentTask.isSuccessful) currentTask.result else null
                            if (loc != null) {
                                scope.launch { store.saveParking(loc.latitude, loc.longitude) }
                            } else {
                                onFailure("Couldn't determine your location")
                            }
                        }
                } catch (_: SecurityException) {
                    onFailure("Location permission required")
                }
            }
    } catch (_: SecurityException) {
        onFailure("Location permission required")
    }
}

@Composable
private fun MetaRow(left: String, right: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(left, fontSize = 14.sp, color = GrayMid, fontWeight = FontWeight.Medium)
        Text(right, fontSize = 14.sp, color = GrayMid, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PrimaryButton(text: String, showDirectionIcon: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(LimeBright)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showDirectionIcon) {
                Icon(
                    Icons.Filled.Navigation, null,
                    tint = NearBlack,
                    modifier = Modifier.size(20.dp).rotate(35f)
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NearBlack)
        }
    }
}

@Composable
private fun SecondaryRow(onSave: () -> Unit, onShare: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .weight(1f).height(50.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(OliveDark)
                .clickable { onSave() }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bookmark, null, tint = White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Save Current Spot",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            Modifier
                .weight(1f).height(50.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(OliveDark)
                .clickable { onShare() }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Share, null, tint = White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Share Spot",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HourglassIcon() {
    Canvas(Modifier.size(42.dp, 60.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.16f, h * 0.06f)
            lineTo(w * 0.84f, h * 0.06f)
            lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.84f, h * 0.94f)
            lineTo(w * 0.16f, h * 0.94f)
            lineTo(w * 0.5f, h * 0.5f)
            close()
        }
        drawPath(p, OliveDark, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ============================================================
// Fuel screen
// ============================================================

@Composable
fun FuelScreen(fuel: FuelStore) {
    val tank = if (fuel.tankCapacity > 0) fuel.tankCapacity else 50.0
    val initialFraction = (fuel.estimateLevelPct() / 100.0).coerceIn(0.0, 1.0).toFloat()
    var sliderFraction by remember { mutableFloatStateOf(initialFraction) }
    var showAllLogs by remember { mutableStateOf(false) }
    var showRefuelHistory by remember { mutableStateOf(false) }
    var showRefuelModal by remember { mutableStateOf(false) }
    var showTripCostModal by remember { mutableStateOf(false) }
    var lastSaveTime by remember { mutableLongStateOf(0L) }
    var pendingRefuelDelete by remember { mutableStateOf<Refuel?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val litersInTank = tank * sliderFraction
    val lastSavedText = fuel.fuelLogs.firstOrNull()?.let { log ->
        val fmt = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())
        "Last saved ${fmt.format(Date(log.date))}"
    }

    fun deleteFuelLogWithUndo(log: com.parked.app.data.FuelLevelLog) {
        fuel.deleteFuelLog(log.date)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Fuel log deleted",
                actionLabel = "Undo",
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) fuel.restoreFuelLog(log)
        }
    }

    if (showAllLogs) {
        BackHandler { showAllLogs = false }
        Box(Modifier.fillMaxSize()) {
            FuelHistoryPage(
                title = "Fuel logs",
                emptyText = "No fuel logs yet",
                isEmpty = fuel.fuelLogs.isEmpty(),
                onBack = { showAllLogs = false },
            ) {
                itemsIndexed(fuel.fuelLogs, key = { _, log -> log.date }) { idx, log ->
                    FuelLevelLogRow(log = log, index = idx, onDelete = { deleteFuelLogWithUndo(log) })
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)
            )
        }
        return
    }

    if (showRefuelHistory) {
        BackHandler { showRefuelHistory = false }
        FuelHistoryPage(
            title = "Refuels",
            emptyText = "No refuels yet",
            isEmpty = fuel.refuels.isEmpty(),
            onBack = { showRefuelHistory = false },
        ) {
            itemsIndexed(fuel.refuels, key = { _, entry -> entry.date }) { idx, entry ->
                RefuelLogRow(entry = entry, fuel = fuel, index = idx, onDelete = { pendingRefuelDelete = entry })
            }
        }
        DeleteConfirmDialog(
            visible = pendingRefuelDelete != null,
            title = "Delete refuel?",
            message = "This refuel entry will be removed. Your current odometer and fuel level will not be changed.",
            onDismiss = { pendingRefuelDelete = null },
            onConfirm = {
                pendingRefuelDelete?.let { fuel.deleteRefuel(it.date) }
                pendingRefuelDelete = null
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(White)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 58.dp, bottomEnd = 58.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(OliveDark, Color(0xFF4E8F27), LimeBright),
                        start = Offset.Zero,
                        end = Offset(850f, 1000f)
                    )
                )
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Fuel level", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = White)
            Spacer(Modifier.height(12.dp))

            NativeFuelGauge(
                fraction = sliderFraction,
                litersInTank = litersInTank,
                lastUpdatedText = lastSavedText,
                onFractionChange = { sliderFraction = it }
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastSaveTime < 1200) return@Button
                        lastSaveTime = now
                        fuel.logFuelLevel(sliderFraction * 100.0)
                        scope.launch { snackbarHostState.showSnackbar("Fuel level saved") }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3C3A3F),
                        contentColor = White
                    )
                ) {
                    Text("Save level", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showRefuelModal = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = White,
                        contentColor = OliveDark
                    )
                ) {
                    Icon(Icons.Filled.LocalGasStation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Refuel", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { showTripCostModal = true },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF5F7F3),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = OliveDark)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Trip cost", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NearBlack)
                                Text("Estimate fuel cost for a drive", fontSize = 12.sp, color = GrayMid)
                            }
                            Text("›", fontSize = 24.sp, color = OliveDark)
                        }
                    }
                    Surface(
                        onClick = { showRefuelHistory = true },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF5F7F3),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.LocalGasStation, contentDescription = null, tint = OliveDark)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Refuel history", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NearBlack)
                                Text(
                                    if (fuel.refuels.isEmpty()) "No refuels yet" else "${fuel.refuels.size} saved refuel${if (fuel.refuels.size == 1) "" else "s"}",
                                    fontSize = 12.sp,
                                    color = GrayMid
                                )
                            }
                            Text("›", fontSize = 24.sp, color = OliveDark)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fuel logs", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack)
                    if (fuel.fuelLogs.size > 4) {
                        Text(
                            "View all",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OliveDark,
                            modifier = Modifier.clickable { showAllLogs = true }
                        )
                    }
                }

                val logs = fuel.fuelLogs.take(4)
            if (logs.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.GasMeter, null, tint = RedFuel, modifier = Modifier.size(78.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Move the gauge and save your first fuel level", color = GrayMid, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(logs, key = { _, log -> log.date }) { idx, log ->
                        FuelLevelLogRow(log = log, index = idx, onDelete = { deleteFuelLogWithUndo(log) })
                    }
                }
            }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)
            )
        }
    }

    if (showRefuelModal) {
        RefuelModal(
            fuel = fuel,
            onDismiss = { showRefuelModal = false },
            onSaved = {
                sliderFraction = (fuel.estimateLevelPct() / 100.0).coerceIn(0.0, 1.0).toFloat()
                showRefuelModal = false
                scope.launch { snackbarHostState.showSnackbar("Refuel saved") }
            }
        )
    }

    if (showTripCostModal) {
        TripCostModal(fuel = fuel, onDismiss = { showTripCostModal = false })
    }
}

@Composable
private fun FuelHistoryPage(
    title: String,
    emptyText: String,
    isEmpty: Boolean,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(White)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(OliveDark, Color(0xFF4E8F27), LimeBright),
                        start = Offset.Zero,
                        end = Offset(900f, 260f)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹", fontSize = 32.sp, color = White, lineHeight = 30.sp) }
            Spacer(Modifier.width(4.dp))
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = White)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (isEmpty) {
                item {
                    Box(
                        Modifier.fillMaxWidth().height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emptyText, color = GrayMid, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                content()
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun NativeFuelGauge(
    fraction: Float,
    litersInTank: Double,
    lastUpdatedText: String?,
    onFractionChange: (Float) -> Unit,
) {
    val normalized = fraction.coerceIn(0f, 1f)
    val pct = (normalized * 100f).roundToInt()

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        shape = RoundedCornerShape(22.dp),
        color = White.copy(alpha = 0.97f),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "$pct%",
                        fontSize = 30.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NearBlack
                    )
                    Text("tank level", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GrayMid)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${String.format(Locale.US, "%.0f", litersInTank)} L",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NearBlack
                    )
                    Text("approx.", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GrayMid)
                }
            }

            Spacer(Modifier.height(4.dp))

            Slider(
                value = normalized,
                onValueChange = { onFractionChange(it.coerceIn(0f, 1f)) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = OliveDark,
                    activeTrackColor = LimeBright,
                    inactiveTrackColor = Color(0xFFDDE2D9),
                    activeTickColor = OliveDark,
                    inactiveTickColor = Color(0xFF8A9185)
                ),
                modifier = Modifier.semantics {
                    contentDescription = "Fuel level"
                }
            )

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().height(24.dp)
            ) {
                Text(
                    "E",
                    modifier = Modifier.align(Alignment.CenterStart),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMid
                )
                Text(
                    "F",
                    modifier = Modifier.align(Alignment.CenterEnd),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMid
                )

                val markerWidth = 28.dp
                listOf(0.25f to "¼", 0.50f to "½", 0.75f to "¾").forEach { (position, label) ->
                    Column(
                        modifier = Modifier
                            .offset(x = (maxWidth * position) - (markerWidth / 2f))
                            .width(markerWidth),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.width(1.dp).height(5.dp).background(Color(0xFF8A9185)))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NearBlack
                        )
                    }
                }
            }
            if (lastUpdatedText != null) {
                Spacer(Modifier.height(4.dp))
                Text(lastUpdatedText, fontSize = 11.sp, color = GrayMid)
            }
        }
    }
}

@Composable
private fun FuelLevelLogRow(
    log: com.parked.app.data.FuelLevelLog,
    index: Int,
    onDelete: () -> Unit,
) {
    val fmt = remember { SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (index % 2 == 0) Color(0xFFE8F3E0) else White)
            .padding(start = 24.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(fmt.format(Date(log.date)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
            if (log.odometer > 0) {
                Text("${String.format(Locale.US, "%.0f", log.odometer)} km", fontSize = 11.sp, color = GrayMid)
            }
        }
        Text(
            "${log.levelPct.roundToInt()}%",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = NearBlack
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete fuel log",
                tint = GrayIcon,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RefuelLogRow(entry: Refuel, fuel: FuelStore, index: Int, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (index % 2 == 0) Color(0xFFE8F3E0) else White)
            .padding(start = 24.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(fmt.format(Date(entry.date)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
            if (entry.odometer > 0) {
                Text("${String.format(Locale.US, "%.0f", entry.odometer)} km", fontSize = 11.sp, color = GrayMid)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${String.format(Locale.US, "%.1f", entry.litres)} L", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NearBlack)
            Text("${fuel.currency}${String.format(Locale.US, "%.2f", entry.cost)}", fontSize = 12.sp, color = GrayMid)
        }
        TextButton(onClick = onDelete) {
            Text("Delete", fontSize = 12.sp, color = ErrorRed, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        title = { Text(title, fontWeight = FontWeight.ExtraBold, color = NearBlack) },
        text = { Text(message, color = GrayMid) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = ErrorRed, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OliveDark) }
        }
    )
}

@Composable
private fun RefuelModal(fuel: FuelStore, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var litres by remember { mutableStateOf("") }
    var totalPaid by remember { mutableStateOf("") }
    var odometer by remember {
        mutableStateOf(if (fuel.currentOdo > 0) String.format(Locale.US, "%.0f", fuel.currentOdo) else "")
    }
    var fullTank by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val litresValue = litres.replace(',', '.').toDoubleOrNull()
    val totalValue = totalPaid.replace(',', '.').toDoubleOrNull()
    val pricePerLitre = if (litresValue != null && litresValue > 0 && totalValue != null && totalValue >= 0) {
        totalValue / litresValue
    } else null

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            Modifier.fillMaxWidth().clickable { }.navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = White
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Refuel", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack)
                Spacer(Modifier.height(4.dp))
                Text("Only use this when you actually add fuel.", fontSize = 13.sp, color = GrayMid)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = litres,
                    onValueChange = { litres = it; errorText = null },
                    label = { Text("Litres added") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = totalPaid,
                    onValueChange = { totalPaid = it; errorText = null },
                    label = { Text("Total paid (${fuel.currency})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (pricePerLitre != null && pricePerLitre.isFinite()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${fuel.currency}${String.format(Locale.US, "%.3f", pricePerLitre)} / L",
                        fontSize = 12.sp,
                        color = GrayMid,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it; errorText = null },
                    label = { Text("Odometer (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Filled the tank", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
                        Text("Turn off for a partial top-up", fontSize = 11.sp, color = GrayMid)
                    }
                    Switch(checked = fullTank, onCheckedChange = { fullTank = it })
                }

                if (errorText != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(errorText!!, fontSize = 12.sp, color = ErrorRed)
                }

                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Save refuel", onClick = {
                    val l = litres.replace(',', '.').toDoubleOrNull()
                    val c = totalPaid.replace(',', '.').toDoubleOrNull()
                    val o = if (odometer.isBlank()) 0.0 else odometer.replace(',', '.').toDoubleOrNull()
                    when {
                        l == null || !l.isFinite() || l <= 0 -> errorText = "Enter the litres added"
                        c == null || !c.isFinite() || c < 0 -> errorText = "Enter the total paid"
                        o == null || !o.isFinite() || o < 0 -> errorText = "Check the odometer"
                        else -> {
                            fuel.logRefuel(litres = l, cost = c, odometer = o, tankFull = fullTank)
                            onSaved()
                        }
                    }
                })
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clickable { onDismiss() }.padding(10.dp), contentAlignment = Alignment.Center) {
                    Text("Cancel", fontSize = 14.sp, color = GrayMid, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TripCostModal(fuel: FuelStore, onDismiss: () -> Unit) {
    val learnedConsumption = fuel.averageL100km()
    val learnedPrice = fuel.customFuelPrice.takeIf { it > 0 }
        ?: fuel.refuels.firstOrNull { it.litres > 0 && it.cost > 0 }?.let { it.cost / it.litres }

    var distance by remember { mutableStateOf("") }
    var consumption by remember(learnedConsumption) {
        mutableStateOf(learnedConsumption?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var price by remember(learnedPrice) {
        mutableStateOf(learnedPrice?.let { String.format(Locale.US, "%.3f", it) } ?: "")
    }
    var roundTrip by remember { mutableStateOf(false) }

    fun number(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

    val distanceValue = number(distance)
    val consumptionValue = number(consumption)
    val priceValue = number(price)
    val tripDistance = distanceValue?.times(if (roundTrip) 2.0 else 1.0)
    val litresNeeded = if (tripDistance != null && consumptionValue != null) tripDistance * consumptionValue / 100.0 else null
    val tripCost = if (litresNeeded != null && priceValue != null) litresNeeded * priceValue else null

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            Modifier.fillMaxWidth().clickable { }.navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = White
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Trip cost", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack)
                Spacer(Modifier.height(4.dp))
                Text("Quick estimate only — this does not create a fuel log or refuel.", fontSize = 13.sp, color = GrayMid)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = distance,
                    onValueChange = { distance = it },
                    label = { Text("Distance (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = consumption,
                    onValueChange = { consumption = it },
                    label = { Text("Consumption (L/100 km)") },
                    supportingText = {
                        if (learnedConsumption != null) Text("Pre-filled from your refuel history")
                        else Text("Enter your car's average consumption")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Fuel price (${fuel.currency}/L)") },
                    supportingText = {
                        if (learnedPrice != null) Text("Pre-filled from your latest known fuel price")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Round trip", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
                    Switch(checked = roundTrip, onCheckedChange = { roundTrip = it })
                }

                Spacer(Modifier.height(14.dp))
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF5F7F3), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Estimated cost", fontSize = 12.sp, color = GrayMid)
                        Text(
                            tripCost?.let { "${fuel.currency}${String.format(Locale.US, "%.2f", it)}" } ?: "—",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NearBlack
                        )
                        if (litresNeeded != null && tripDistance != null) {
                            Text(
                                "${String.format(Locale.US, "%.0f", tripDistance)} km · ~${String.format(Locale.US, "%.1f", litresNeeded)} L",
                                fontSize = 12.sp,
                                color = GrayMid
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().clickable { onDismiss() }.padding(12.dp), contentAlignment = Alignment.Center) {
                    Text("Done", fontSize = 15.sp, color = OliveDark, fontWeight = FontWeight.Bold)
                }
            }
        }
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
    val latestParkingState by rememberUpdatedState(state)

    var showOdoModal by remember { mutableStateOf(false) }
    var showTankModal by remember { mutableStateOf(false) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var showAppearanceModal by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingAutoStart by remember { mutableStateOf(false) }
    var pendingDevicePickerOnly by remember { mutableStateOf(false) }
    var systemNotificationsAllowed by remember { mutableStateOf(areNotificationsAllowed(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun hasLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBluetooth(): Boolean = android.os.Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun needsNotificationPermission(): Boolean = android.os.Build.VERSION.SDK_INT >= 33 &&
        fuel.notificationsEnabled &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun startMonitorReady() {
        scope.launch {
            // Persist first so the service cannot receive a Bluetooth event while
            // DataStore still says monitoring is off.
            store.setMonitoring(true)
            val started = runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, ParkingMonitorService::class.java)
                )
            }.isSuccess
            if (started) {
                statusMessage = "AutoPark is on"
            } else {
                store.setMonitoring(false)
                statusMessage = "Couldn't start AutoPark"
            }
        }
    }

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (!pendingAutoStart && !pendingDevicePickerOnly) return@rememberLauncherForActivityResult
        if (!isBluetoothEnabled(context)) {
            val wasAutoPark = pendingAutoStart
            pendingAutoStart = false
            pendingDevicePickerOnly = false
            statusMessage = if (wasAutoPark) "Bluetooth needed" else "Turn on Bluetooth"
        } else if (pendingDevicePickerOnly) {
            pendingDevicePickerOnly = false
            showDevicePicker = true
        } else if (latestParkingState.deviceAddress == null) {
            statusMessage = "Choose car Bluetooth"
            showDevicePicker = true
        } else if (!isLocationServicesEnabled(context)) {
            // Location settings are opened only because the user explicitly chose
            // AutoPark; Parked! no longer pushes this prompt on normal app launch.
            ensureLocationOn(context)
            statusMessage = "Location needed"
        } else {
            pendingAutoStart = false
            startMonitorReady()
        }
    }

    val autoPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (!pendingAutoStart) return@rememberLauncherForActivityResult
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            results.containsKey(Manifest.permission.POST_NOTIFICATIONS) &&
            results[Manifest.permission.POST_NOTIFICATIONS] != true
        ) {
            // AutoPark itself can still run without alert permission. Remember the
            // denial so enabling AutoPark later does not nag for notifications again.
            fuel.updateNotificationsEnabled(false)
            systemNotificationsAllowed = false
        }
        if (!hasLocation() || !hasBluetooth()) {
            pendingAutoStart = false
            statusMessage = "Location + Bluetooth needed"
        } else if (!isBluetoothEnabled(context)) {
            bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else if (latestParkingState.deviceAddress == null) {
            statusMessage = "Choose car Bluetooth"
            showDevicePicker = true
        } else if (!isLocationServicesEnabled(context)) {
            ensureLocationOn(context)
            statusMessage = "Location needed"
        } else {
            pendingAutoStart = false
            startMonitorReady()
        }
    }

    fun beginAutoPark(deviceReady: Boolean = state.deviceAddress != null) {
        pendingAutoStart = true
        if (!hasLocation() || !hasBluetooth() || needsNotificationPermission()) {
            val required = buildList {
                if (!hasLocation()) {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                if (!hasBluetooth() && android.os.Build.VERSION.SDK_INT >= 31) {
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (needsNotificationPermission()) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            autoPermissionLauncher.launch(required.toTypedArray())
        } else if (!isBluetoothEnabled(context)) {
            // Native Android Bluetooth enable dialog. When accepted, the flow
            // continues automatically; no second AutoPark toggle is required.
            bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else if (!deviceReady) {
            statusMessage = "Choose car Bluetooth"
            showDevicePicker = true
        } else if (!isLocationServicesEnabled(context)) {
            ensureLocationOn(context)
            statusMessage = "Location needed"
        } else {
            pendingAutoStart = false
            startMonitorReady()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        fuel.updateNotificationsEnabled(granted)
        systemNotificationsAllowed = areNotificationsAllowed(context)
        statusMessage = if (granted) "Parking alerts are on" else "Notifications weren't enabled"
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            pendingDevicePickerOnly = false
            statusMessage = "Bluetooth permission needed"
        } else if (!isBluetoothEnabled(context)) {
            pendingDevicePickerOnly = true
            bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            pendingDevicePickerOnly = false
            showDevicePicker = true
        }
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(3000)
            statusMessage = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemNotificationsAllowed = areNotificationsAllowed(context)
                if (pendingAutoStart && latestParkingState.deviceAddress != null && hasLocation() && hasBluetooth() &&
                    isBluetoothEnabled(context) && isLocationServicesEnabled(context)
                ) {
                    pendingAutoStart = false
                    startMonitorReady()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsChecked = fuel.notificationsEnabled && systemNotificationsAllowed

    Column(Modifier.fillMaxSize().background(White)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(OliveDark, Color(0xFF4D8D26), LimeBright),
                        start = Offset.Zero,
                        end = Offset(900f, 850f)
                    )
                )
                .statusBarsPadding()
                .padding(top = 18.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
        ) {
            Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = White, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(20.dp))
            Text("Vehicle", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = White, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            Card {
                ToggleRow(
                    "AutoPark",
                    state.monitoring,
                    onToggle = { on ->
                        if (on) {
                            beginAutoPark()
                        } else {
                            runCatching {
                                context.stopService(Intent(context, ParkingMonitorService::class.java))
                            }
                            scope.launch { store.setMonitoring(false) }
                            statusMessage = "AutoPark is off"
                        }
                    }
                )
                DividerRow()
                TextRow("Car Bluetooth", state.deviceName ?: "Not set") {
                    pendingDevicePickerOnly = true
                    if (android.os.Build.VERSION.SDK_INT >= 31 && !hasBluetooth()) {
                        bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else if (!isBluetoothEnabled(context)) {
                        bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    } else {
                        pendingDevicePickerOnly = false
                        showDevicePicker = true
                    }
                }
                DividerRow()
                TextRow("Odometer", formatOdometer(fuel.currentOdo)) { showOdoModal = true }
                DividerRow()
                TextRow(
                    "Tank capacity",
                    if (fuel.tankCapacity > 0) "${String.format(Locale.US, "%.0f", fuel.tankCapacity)} L" else "Not set"
                ) { showTankModal = true }
            }

            Text("Preferences", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = White, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 10.dp))
            Card {
                ToggleRow(
                    "Notifications",
                    notificationsChecked,
                    onToggle = { enabled ->
                        if (!enabled) {
                            fuel.updateNotificationsEnabled(false)
                        } else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!areNotificationsAllowed(context)) {
                            statusMessage = "Enable Parking alerts in Android settings"
                            runCatching {
                                val intent = if (android.os.Build.VERSION.SDK_INT >= 26) {
                                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .putExtra(Settings.EXTRA_CHANNEL_ID, ParkingMonitorService.ALERT_CHANNEL)
                                } else {
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        } else {
                            fuel.updateNotificationsEnabled(true)
                            statusMessage = "Parking alerts are on"
                        }
                    }
                )
                DividerRow()
                TextRow("Car appearance", fuel.accentName) { showAppearanceModal = true }
            }

        }

        Box(
            Modifier.fillMaxSize().background(White),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.clickable { showAppearanceModal = true }) {
                CarIllustration(fuel.accentName)
            }

            SettingsStatusToast(
                message = statusMessage,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
            )
        }
    }

    if (showOdoModal) {
        OdometerModal(fuel = fuel, onDismiss = { showOdoModal = false })
    }
    if (showTankModal) {
        TankCapacityModal(fuel = fuel, onDismiss = { showTankModal = false })
    }
    if (showDevicePicker) {
        DevicePickerDialog(
            onDismiss = {
                showDevicePicker = false
                pendingAutoStart = false
                pendingDevicePickerOnly = false
            },
            onSelected = { name, address ->
                val shouldStartAfterSelection = pendingAutoStart || state.monitoring
                showDevicePicker = false
                scope.launch {
                    // If AutoPark was already tracking another device, stop that
                    // service before swapping the address. Otherwise its old-car
                    // location session can keep running after the selection changes.
                    if (state.monitoring) {
                        runCatching {
                            context.stopService(Intent(context, ParkingMonitorService::class.java))
                        }
                        store.setMonitoring(false)
                    }
                    store.selectDevice(name, address)
                    if (shouldStartAfterSelection) {
                        pendingAutoStart = true
                        beginAutoPark(deviceReady = true)
                    } else {
                        statusMessage = "Car Bluetooth saved"
                    }
                }
            }
        )
    }
    if (showAppearanceModal) {
        AppearanceModal(fuel = fuel, onDismiss = { showAppearanceModal = false })
    }
}


@Composable
private fun SettingsStatusToast(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    Surface(
        color = NearBlack,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Text(
            message,
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            color = White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatOdometer(km: Double): String {
    if (km <= 0) return "Not set"
    return if (km >= 1000) String.format(Locale.US, "%.1fk km", km / 1000)
    else String.format(Locale.US, "%,.0f km", km)
}

@Composable
private fun DevicePickerDialog(
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
        containerColor = White,
        titleContentColor = NearBlack,
        textContentColor = GrayMid,
        title = { Text("Choose your car") },
        text = {
            Column {
                if (!canRead) Text("Bluetooth permission required.")
                else if (devices.isEmpty()) Text("No paired devices found.")
                else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(devices, key = { it.address }) { device ->
                            Surface(
                                onClick = { onSelected(device.name ?: "Car Bluetooth", device.address) },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF5F5F5),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(device.name ?: "Unnamed", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
                                    Text(device.address, fontSize = 12.sp, color = GrayMid)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = OliveDark) } }
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)
    ) { Column(content = content) }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
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
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
        Text(
            value,
            fontSize = if (value == "+") 26.sp else 14.sp,
            fontWeight = if (value == "+") FontWeight.Normal else FontWeight.Medium,
            color = OliveDark.copy(alpha = 0.88f)
        )
    }
}

@Composable
private fun DividerRow() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F0F0)))
}

@Composable
private fun CarIllustration(accentName: String) {
    val drawable = when (accentName) {
        "White" -> R.drawable.design_car_white
        "Silver" -> R.drawable.design_car_silver
        "Gray" -> R.drawable.design_car_gray
        "Black" -> R.drawable.design_car_black
        "Red" -> R.drawable.design_car_red
        "Yellow" -> R.drawable.design_car_yellow
        "Orange" -> R.drawable.design_car_orange
        "Brown" -> R.drawable.design_car_brown
        "Purple" -> R.drawable.design_car_purple
        "Green" -> R.drawable.design_car_green
        "Blue" -> R.drawable.design_car
        else -> R.drawable.design_car
    }
    Image(
        painter = painterResource(drawable),
        contentDescription = "Car appearance preview",
        modifier = Modifier.fillMaxWidth(0.92f).height(248.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun AppearanceModal(fuel: FuelStore, onDismiss: () -> Unit) {
    val options = listOf(
        "White" to Color(0xFFF4F4F2),
        "Silver" to Color(0xFFBFC3C7),
        "Gray" to Color(0xFF70757A),
        "Black" to Color(0xFF202124),
        "Blue" to Color(0xFF2F80ED),
        "Red" to Color(0xFFD73A49),
        "Green" to Color(0xFF42A846),
        "Yellow" to Color(0xFFF4C542),
        "Orange" to Color(0xFFF28C28),
        "Brown" to Color(0xFF795548),
        "Purple" to Color(0xFF7E57C2),
    )

    fun isSelected(name: String): Boolean = fuel.accentName == name

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        title = { Text("Car appearance", fontWeight = FontWeight.ExtraBold, color = NearBlack) },
        text = {
            Column {
                Text("Choose the closest colour.", fontSize = 13.sp, color = GrayMid)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(options, key = { it.first }) { (name, swatch) ->
                        val selected = isSelected(name)
                        Surface(
                            onClick = { fuel.setAccent(name); onDismiss() },
                            color = if (selected) Color(0xFFE8F3E0) else Color(0xFFF7F7F7),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(swatch)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    name,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NearBlack
                                )
                                if (selected) Text("✓", color = OliveDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = OliveDark) } }
    )
}

// ============================================================
// Modals
// ============================================================

@Composable
private fun OdometerModal(fuel: FuelStore, onDismiss: () -> Unit) {
    var odo by remember { mutableStateOf(if (fuel.currentOdo > 0) String.format(Locale.US, "%.0f", fuel.currentOdo) else "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            Modifier.fillMaxWidth().clickable { }.navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = White
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Odometer", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = odo, onValueChange = { odo = it; errorText = null },
                    label = { Text("Current odometer (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(errorText!!, fontSize = 12.sp, color = ErrorRed)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Save", onClick = {
                    val od = odo.replace(',', '.').toDoubleOrNull()
                    if (od == null || !od.isFinite() || od <= 0) {
                        errorText = "Enter a valid odometer"
                    } else {
                        fuel.setOdometer(od)
                        onDismiss()
                    }
                })
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clickable { onDismiss() }.padding(12.dp), contentAlignment = Alignment.Center) {
                    Text("Cancel", fontSize = 15.sp, color = GrayMid, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TankCapacityModal(fuel: FuelStore, onDismiss: () -> Unit) {
    var tank by remember { mutableStateOf(if (fuel.tankCapacity > 0) String.format(Locale.US, "%.0f", fuel.tankCapacity) else "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            Modifier.fillMaxWidth().clickable { }.navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = White
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Tank capacity", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack)
                Spacer(Modifier.height(4.dp))
                Text("Used for fuel estimates and trip cost.", fontSize = 13.sp, color = GrayMid)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = tank, onValueChange = { tank = it; errorText = null },
                    label = { Text("Capacity (L)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(errorText!!, fontSize = 12.sp, color = ErrorRed)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Save", onClick = {
                    val parsed = tank.replace(',', '.').toDoubleOrNull()
                    when {
                        parsed == null || !parsed.isFinite() || parsed <= 0 -> errorText = "Enter a valid tank capacity"
                        parsed > 300 -> errorText = "That looks too high"
                        else -> { fuel.updateTankCapacity(parsed); onDismiss() }
                    }
                })
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clickable { onDismiss() }.padding(12.dp), contentAlignment = Alignment.Center) {
                    Text("Cancel", fontSize = 15.sp, color = GrayMid, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FuelPriceModal(fuel: FuelStore, onDismiss: () -> Unit) {
    var price by remember { mutableStateOf(if (fuel.customFuelPrice > 0) String.format(Locale.US, "%.2f", fuel.customFuelPrice) else "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            Modifier.fillMaxWidth().clickable { }.navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = White
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Fuel price", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack)
                Spacer(Modifier.height(4.dp))
                Text("Leave blank to auto-calculate from logs", fontSize = 13.sp, color = GrayMid)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = price, onValueChange = { price = it; errorText = null },
                    label = { Text("${fuel.currency} per liter") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(errorText!!, fontSize = 12.sp, color = ErrorRed)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Save", onClick = {
                    if (price.isBlank()) {
                        fuel.updateCustomFuelPrice(0.0)
                        onDismiss()
                    } else {
                        val parsed = price.toDoubleOrNull()
                        when {
                            parsed == null -> errorText = "Enter a valid number"
                            parsed <= 0 -> errorText = "Price must be greater than 0"
                            parsed > 100 -> errorText = "That looks too high"
                            else -> {
                                fuel.updateCustomFuelPrice(parsed)
                                onDismiss()
                            }
                        }
                    }
                })
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clickable { onDismiss() }.padding(12.dp), contentAlignment = Alignment.Center) {
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
    parkedMarkerIcon: BitmapDrawable,
    liveMarkerIcon: BitmapDrawable,
) {
    var hasCenteredOnParked by remember { mutableStateOf(false) }
    var hasCenteredOnLive by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(parkedLat, parkedLng) {
        // A newly saved spot should immediately become the map focus instead of
        // leaving the camera on the previous parking location.
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
                setBuiltInZoomControls(false)
                controller.setZoom(16.0)
                mapView = this
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    onResume()
                }
            }
        },
        update = { map ->
            if (!hasCenteredOnParked && parkedLat != null && parkedLng != null) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(GeoPoint(parkedLat, parkedLng))
                hasCenteredOnParked = true
            }
            if (!hasCenteredOnParked && !hasCenteredOnLive && liveLat != null && liveLng != null) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(GeoPoint(liveLat, liveLng))
                hasCenteredOnLive = true
            }
            map.overlays.removeAll { it is Marker }
            if (parkedLat != null && parkedLng != null) {
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(parkedLat, parkedLng)
                    title = "Parked car"
                    icon = parkedMarkerIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
            if (liveLat != null && liveLng != null) {
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(liveLat, liveLng)
                    title = "You are here"
                    icon = liveMarkerIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
            map.invalidate()
        }
    )
}

// ============================================================
// Markers
// ============================================================

private fun makeParkedMarker(ctx: Context): BitmapDrawable {
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

private fun makeLiveMarker(ctx: Context): BitmapDrawable {
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

// ============================================================
// Helpers
// ============================================================

private fun ensureBluetoothOn(context: Context) {
    if (android.os.Build.VERSION.SDK_INT >= 31 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        != PackageManager.PERMISSION_GRANTED
    ) return
    runCatching {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled) {
            context.startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private fun isBluetoothEnabled(context: Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= 31 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) return false
    return runCatching {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }.getOrDefault(false)
}

private fun ensureLocationOn(context: Context) {
    if (isLocationServicesEnabled(context)) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun isLocationServicesEnabled(context: Context): Boolean {
    val lm = context.getSystemService(LocationManager::class.java) ?: return false
    return if (android.os.Build.VERSION.SDK_INT >= 28) {
        lm.isLocationEnabled
    } else {
        runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }
}

private fun areNotificationsAllowed(context: Context): Boolean {
    val runtimePermission = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (!runtimePermission || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

    if (android.os.Build.VERSION.SDK_INT >= 26) {
        val channel = context.getSystemService(android.app.NotificationManager::class.java)
            ?.getNotificationChannel(ParkingMonitorService.ALERT_CHANNEL)
        if (channel != null && channel.importance == android.app.NotificationManager.IMPORTANCE_NONE) return false
    }
    return true
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

    val yCal = now.clone() as Calendar
    yCal.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = then.get(Calendar.YEAR) == yCal.get(Calendar.YEAR) &&
            then.get(Calendar.DAY_OF_YEAR) == yCal.get(Calendar.DAY_OF_YEAR)

    return when {
        sameDay -> "Parked today at $timeStr"
        isYesterday -> "Parked yesterday at $timeStr"
        else -> {
            val dateStr = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ts))
            "Parked $dateStr at $timeStr"
        }
    }
}
