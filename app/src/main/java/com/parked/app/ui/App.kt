package com.parked.app.ui

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.parked.app.R
import com.parked.app.data.FuelStore
import com.parked.app.data.ParkingStore
import com.parked.app.service.AutoParkMode
import com.parked.app.service.ParkingMonitorService
import com.parked.app.util.Channels
import com.parked.app.util.hasBluetoothPermission
import com.parked.app.util.hasLocationPermission

/** Requests that arrive from a widget, the Quick Settings tile or a notification. */
enum class AppAction { SaveSpot, FindCar, OpenSettings, OpenService, SetUpAutoPark }

@Composable
fun ParkedRoot(pendingAction: AppAction?, onActionHandled: () -> Unit) {
    val context = LocalContext.current
    val fuel = remember { FuelStore(context) }
    val parkingStore = remember { ParkingStore(context) }
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(fuel) { onDispose { fuel.close() } }

    LaunchedEffect(Unit) { Channels.ensure(context) }

    // Bring AutoPark back after the process was reclaimed, and upgrade a service
    // that was started in the background (limited mode) now that the app is
    // visible and Android allows location access.
    suspend fun recoverAutoPark() {
        val state = parkingStore.current()
        if (!state.monitoring) return
        // Location being switched off for a while is fine: the service then sends a
        // tap-to-save alert. Missing permissions or car are not recoverable.
        val canRecover = state.deviceAddress != null && hasLocationPermission(context) && hasBluetoothPermission(context)
        // Remember that the app, not the user, switched AutoPark off, so Home
        // can say so instead of leaving people to find out at the next park.
        fun markLost() = com.parked.app.data.AutoParkHealth.markLost(context)
        if (!canRecover) {
            // Never show AutoPark as on when Android can no longer run it.
            ParkingMonitorService.stop(context)
            parkingStore.setMonitoring(false)
            markLost()
        } else if (ParkingMonitorService.mode.value != AutoParkMode.Full) {
            if (!ParkingMonitorService.start(context)) { parkingStore.setMonitoring(false); markLost() }
        }
    }

    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick) { if (resumeTick > 0) recoverAutoPark() }

    ParkedTheme {
        CompositionLocalProvider(LocalSnackbar provides snackbar) {
            ParkedApp(fuel = fuel, store = parkingStore, pendingAction = pendingAction, onActionHandled = onActionHandled)
        }
    }
}

@Composable
private fun ParkedApp(
    fuel: FuelStore,
    store: ParkingStore,
    pendingAction: AppAction?,
    onActionHandled: () -> Unit,
) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("parked_ui", Context.MODE_PRIVATE) }
    var showOnboarding by remember { mutableStateOf(!uiPrefs.getBoolean("onboarding_done", false)) }
    // Actions raised inside the app (e.g. "Set up AutoPark" at the end of onboarding).
    var internalAction by remember { mutableStateOf<AppAction?>(null) }

    // A widget or tile tap on first launch should not be swallowed by onboarding.
    LaunchedEffect(pendingAction) {
        if (pendingAction != null && showOnboarding) {
            uiPrefs.edit().putBoolean("onboarding_done", true).apply()
            showOnboarding = false
        }
    }

    // No splash: opening Parked! is often the moment you're looking for the
    // car, so the app goes straight to it.
    val stage = when {
        showOnboarding -> Stage.Onboarding
        else -> Stage.Main
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(420, easing = LinearOutSlowInEasing)) togetherWith fadeOut(tween(300)) },
        label = "appStage"
    ) { current ->
        when (current) {
            Stage.Onboarding -> OnboardingPage(
                onNext = { setUp ->
                    uiPrefs.edit().putBoolean("onboarding_done", true).apply()
                    if (setUp) internalAction = AppAction.SetUpAutoPark
                    showOnboarding = false
                }
            )
            Stage.Main -> MainContent(
                fuel = fuel,
                store = store,
                pendingAction = pendingAction ?: internalAction,
                onActionHandled = { internalAction = null; onActionHandled() }
            )
        }
    }
}

private enum class Stage { Onboarding, Main }

// ============================================================
// Intro
// ============================================================

/**
 * One intro screen: the car in Parked green on a studio floor, what the app
 * does, and the choice to set up AutoPark now or later. The language row opens
 * Android's per-app language setting, which exists from Android 13.
 */
@Composable
private fun OnboardingPage(onNext: (setUpAutoPark: Boolean) -> Unit) {
    val context = LocalContext.current
    // Scrolls when large text or a short screen doesn't fit.
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CarStudio(Green, height = 200.dp, carWidth = 320.dp)
                Spacer(Modifier.height(32.dp))
                Text(
                    stringResource(R.string.intro_tagline),
                    fontFamily = HeadingFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = TypeScale.Hero,
                    lineHeight = TypeScale.Hero * 1.12f,
                    color = TextPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.intro_body),
                    fontSize = TypeScale.Emphasis,
                    lineHeight = 26.sp,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.widthIn(max = 340.dp)
                )
            }
            Column(Modifier.padding(top = 32.dp)) {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    SettingsCard {
                        TextRow(
                            label = stringResource(R.string.intro_language),
                            value = currentLanguageName(context),
                            icon = Ph.globe,
                            onClick = { openAppLanguageSettings(context) }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                PrimaryButton(stringResource(R.string.onboarding_setup_autopark), icon = Ph.bluetooth) { onNext(true) }
                Spacer(Modifier.height(4.dp))
                QuietButton(stringResource(R.string.onboarding_later)) { onNext(false) }
            }
        }
    }
}

private fun currentLanguageName(context: Context): String {
    val locale = context.resources.configuration.locales[0]
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

internal fun openAppLanguageSettings(context: Context) {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    runCatching {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        )
    }
}

// ============================================================
// Tabs
// ============================================================

enum class AppTab(val labelRes: Int, val icon: Int, val iconSelected: Int) {
    Home(R.string.tab_home, R.drawable.ph_house, R.drawable.ph_house_fill),
    Fuel(R.string.tab_car, R.drawable.ph_car, R.drawable.ph_car_fill),
    Settings(R.string.tab_settings, R.drawable.ph_gear_six, R.drawable.ph_gear_six_fill),
}

/** Accuracy of the latest location fix (metres) and when it arrived. */
data class GpsReading(val accuracy: Float?, val at: Long)

/** The phone's live position while Home is visible. */
data class LiveLocation(val lat: Double, val lng: Double, val accuracy: Float?)

@Composable
private fun MainContent(
    fuel: FuelStore,
    store: ParkingStore,
    pendingAction: AppAction?,
    onActionHandled: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
    // Lives across tab switches, so a save's Undo survives leaving Home.
    val appScope = rememberCoroutineScope()
    var saveRequest by remember { mutableIntStateOf(0) }
    var permResult by remember { mutableIntStateOf(0) }
    var openServiceRequest by remember { mutableStateOf(false) }
    var autoParkRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            AppAction.SaveSpot -> { tab = AppTab.Home; saveRequest++ }
            AppAction.FindCar -> tab = AppTab.Home
            AppAction.OpenSettings -> tab = AppTab.Settings
            AppAction.OpenService -> { tab = AppTab.Fuel; openServiceRequest = true }
            AppAction.SetUpAutoPark -> { tab = AppTab.Settings; autoParkRequest++ }
            null -> return@LaunchedEffect
        }
        onActionHandled()
    }

    var live by remember { mutableStateOf<LiveLocation?>(null) }
    // The latest fix's accuracy and when it arrived, for the GPS signal on the map.
    var gps by remember { mutableStateOf<GpsReading?>(null) }
    var hasPerm by remember { mutableStateOf(hasLocationPermission(context)) }
    var locationOn by remember { mutableStateOf(com.parked.app.util.isLocationServicesEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var activityResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                // Every fix counts for the GPS signal, including poor ones.
                gps = GpsReading(if (loc.hasAccuracy()) loc.accuracy else null, System.currentTimeMillis())
                // A very poor fix would wrongly claim the user is at (or far from) the car.
                if (loc.hasAccuracy() && loc.accuracy > 100f) return
                live = LiveLocation(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)
            }
        }
    }

    // Live location only while Home is on screen and the app is in front.
    LaunchedEffect(hasPerm, tab, activityResumed) {
        if (hasPerm && tab == AppTab.Home && activityResumed) {
            try {
                fused.lastLocation.addOnSuccessListener { loc ->
                    val recent = loc != null && System.currentTimeMillis() - loc.time in 0..(5 * 60 * 1000L)
                    val accurate = loc != null && (!loc.hasAccuracy() || loc.accuracy <= 150f)
                    if (loc != null && recent && accurate && live == null) {
                        live = LiveLocation(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)
                    }
                }
                // High accuracy only while Home is on screen, so the GPS signal
                // shown on the map is the real GPS, not a Wi-Fi/cell estimate.
                val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                    .setMinUpdateIntervalMillis(3000L)
                    .setMaxUpdateDelayMillis(8000L)
                    .build()
                fused.requestLocationUpdates(req, locCallback, Looper.getMainLooper())
            } catch (_: SecurityException) {}
        } else {
            runCatching { fused.removeLocationUpdates(locCallback) }
            live = null
            gps = null
        }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { fused.removeLocationUpdates(locCallback) } }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    activityResumed = true
                    hasPerm = hasLocationPermission(context)
                    locationOn = com.parked.app.util.isLocationServicesEnabled(context)
                }
                Lifecycle.Event.ON_PAUSE -> activityResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasPerm = hasLocationPermission(context)
        permResult++
    }

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Surface,
                    contentColor = TextPrimary,
                    actionColor = Green,
                    dismissActionContentColor = TextPrimary,
                    shape = RadiusSmall,
                )
            }
        },
        bottomBar = { BottomNav(tab = tab, onChange = { tab = it }) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize(),
                // Material "fade through": the old tab fades out quickly, the new one
                // fades and settles in, so switching feels calm rather than jumpy.
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 70, easing = LinearOutSlowInEasing)) +
                        scaleIn(tween(220, delayMillis = 70, easing = LinearOutSlowInEasing), initialScale = 0.97f))
                        .togetherWith(fadeOut(tween(90, easing = FastOutLinearInEasing)))
                },
                label = "tabContent"
            ) { t ->
                when (t) {
                    AppTab.Home -> HomeScreen(
                        store = store,
                        fuel = fuel,
                        live = live,
                        gps = gps,
                        hasPerm = hasPerm,
                        locationOn = locationOn,
                        onSetUpAutoPark = { tab = AppTab.Settings; autoParkRequest++ },
                        saveRequest = saveRequest,
                        onSaveHandled = { saveRequest = 0 },
                        permResult = permResult,
                        appScope = appScope,
                        requestPerm = {
                            permLauncher.launch(arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    )
                    AppTab.Fuel -> FuelScreen(
                        fuel = fuel,
                        store = store,
                        openServiceRequest = openServiceRequest,
                        onServiceRequestHandled = { openServiceRequest = false }
                    )
                    AppTab.Settings -> SettingsScreen(
                        fuel = fuel,
                        store = store,
                        autoParkRequest = autoParkRequest,
                        onAutoParkRequestHandled = { autoParkRequest = 0 },
                        appScope = appScope,
                    )
                }
            }
        }
    }
}

/**
 * Tab bar: white, a hairline on top, icon and label on every tab. The active
 * tab is green, with a filled icon on a soft green pill that springs in.
 */
@Composable
private fun BottomNav(tab: AppTab, onChange: (AppTab) -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    Column(Modifier.fillMaxWidth().background(Surface)) {
        DividerRow()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { t ->
                val selected = tab == t
                val tint by androidx.compose.animation.animateColorAsState(
                    if (selected) Green else TextMuted, Motion.effects(), label = "tabTint"
                )
                val pill by androidx.compose.animation.core.animateFloatAsState(
                    if (selected) 1f else 0f, Motion.spatialFast(), label = "tabPill"
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) { if (!selected) { Haptics.tick(view); onChange(t) } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(width = 60.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { scaleX = 0.6f + 0.4f * pill; alpha = pill }
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(GreenSoft)
                        )
                        Icon(
                            ImageVector.vectorResource(if (selected) t.iconSelected else t.icon),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(t.labelRes),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
