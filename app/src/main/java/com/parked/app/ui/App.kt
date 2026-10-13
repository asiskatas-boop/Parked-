package com.parked.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
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
import androidx.compose.foundation.selection.selectable
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
        if (!canRecover) {
            // Never show AutoPark as on when Android can no longer run it.
            ParkingMonitorService.stop(context)
            parkingStore.setMonitoring(false)
        } else if (ParkingMonitorService.mode.value != AutoParkMode.Full) {
            if (!ParkingMonitorService.start(context)) parkingStore.setMonitoring(false)
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

    // The brand video plays on every cold start, except when the app was opened
    // for a quick action (widget, tile, notification) or animations are off.
    val splashSeenBefore = remember { uiPrefs.getBoolean("splash_seen", false) }
    var showSplash by rememberSaveable {
        mutableStateOf(pendingAction == null && ValueAnimator.areAnimatorsEnabled())
    }
    val stage = when {
        showSplash -> Stage.Splash
        showOnboarding -> Stage.Onboarding
        else -> Stage.Main
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(420, easing = LinearOutSlowInEasing)) togetherWith fadeOut(tween(300)) },
        label = "appStage"
    ) { current ->
        when (current) {
            Stage.Splash -> VideoSplash(
                // Full speed the first time; after that the same animation at
                // double speed, so it doesn't hold you up when you need the car.
                fast = splashSeenBefore,
                onFinished = {
                    uiPrefs.edit().putBoolean("splash_seen", true).apply()
                    showSplash = false
                }
            )
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

private enum class Stage { Splash, Onboarding, Main }

// ============================================================
// Splash video
// ============================================================

/**
 * Plays res/raw/onboarding_car.mp4 once, full screen. Uses a TextureView: a
 * VideoView draws on a surface behind the window, where the screen's own
 * background hid it completely. Tap anywhere to skip.
 */
@Composable
private fun VideoSplash(fast: Boolean, onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)
    var done by remember { mutableStateOf(false) }
    val end: () -> Unit = {
        if (!done) {
            done = true
            finish()
        }
    }
    // Never get stuck here if the video can't play.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(if (fast) 3_500L else 6_000L)
        end()
    }
    val skipLabel = stringResource(R.string.splash_skip)
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .clickable(role = Role.Button, onClickLabel = skipLabel) { end() }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> SplashVideoView(ctx, speed = if (fast) 2f else 1f, onEnd = { end() }) },
            onRelease = { it.release() }
        )
        Text(
            skipLabel,
            fontSize = TypeScale.Supporting,
            color = Muted,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp)
        )
    }
}

private class SplashVideoView(
    context: Context,
    private val speed: Float,
    private val onEnd: () -> Unit,
) : TextureView(context),
    TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var videoWidth = 0
    private var videoHeight = 0

    init {
        surfaceTextureListener = this
        isOpaque = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val s = Surface(texture).also { surface = it }
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(context, Uri.parse("android.resource://${context.packageName}/${R.raw.onboarding_car}"))
                setSurface(s)
                setVolume(0f, 0f)
                isLooping = false
                setOnPreparedListener { mp ->
                    this@SplashVideoView.videoWidth = mp.videoWidth
                    this@SplashVideoView.videoHeight = mp.videoHeight
                    applyCenterCrop()
                    mp.start()
                    if (speed != 1f) runCatching { mp.playbackParams = mp.playbackParams.setSpeed(speed) }
                }
                setOnCompletionListener { onEnd() }
                setOnErrorListener { _, _, _ -> onEnd(); true }
                prepareAsync()
            }
        }.onFailure { onEnd() }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = applyCenterCrop()

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    /** Fill the screen without stretching: scale up and crop the overflow. */
    private fun applyCenterCrop() {
        if (videoWidth == 0 || videoHeight == 0 || width == 0 || height == 0) return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val scale = maxOf(viewW / videoWidth, viewH / videoHeight)
        val sx = videoWidth * scale / viewW
        val sy = videoHeight * scale / viewH
        setTransform(Matrix().apply { setScale(sx, sy, viewW / 2f, viewH / 2f) })
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        runCatching { surface?.release() }
        surface = null
    }
}

// ============================================================
// Intro
// ============================================================

/**
 * One black intro screen: the name, what the app does, and the choice to set
 * up AutoPark now or later. The language row opens Android's per-app language
 * setting, which exists from Android 13.
 */
@Composable
private fun OnboardingPage(onNext: (setUpAutoPark: Boolean) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .systemBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 32.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                stringResource(R.string.intro_title),
                fontSize = TypeScale.Display,
                lineHeight = TypeScale.Display * 1.05f,
                color = Paper,
                letterSpacing = (-1).sp,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.intro_tagline),
                fontSize = TypeScale.Display,
                lineHeight = TypeScale.Display * 1.05f,
                color = Accent,
                letterSpacing = (-1).sp,
            )
        }
        Text(
            stringResource(R.string.intro_body),
            fontSize = TypeScale.Emphasis,
            lineHeight = 26.sp,
            color = Paper,
            modifier = Modifier.widthIn(max = 320.dp).padding(bottom = 24.dp)
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            DividerRow()
            TextRow(
                label = stringResource(R.string.intro_language),
                value = currentLanguageName(context),
                onClick = { openAppLanguageSettings(context) }
            )
            DividerRow()
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(stringResource(R.string.onboarding_setup_autopark)) { onNext(true) }
        Spacer(Modifier.height(8.dp))
        LinkButton(stringResource(R.string.onboarding_later)) { onNext(false) }
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

enum class AppTab(val labelRes: Int, val icon: Int) {
    Home(R.string.tab_home, R.drawable.ph_squares_four),
    Fuel(R.string.tab_fuel, R.drawable.ph_gas_pump),
    Settings(R.string.tab_settings, R.drawable.ph_sliders),
}

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
                val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000L)
                    .setMinUpdateIntervalMillis(3000L)
                    .setMaxUpdateDelayMillis(8000L)
                    .build()
                fused.requestLocationUpdates(req, locCallback, Looper.getMainLooper())
            } catch (_: SecurityException) {}
        } else {
            runCatching { fused.removeLocationUpdates(locCallback) }
            live = null
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
        containerColor = Ink,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Panel,
                    contentColor = Paper,
                    actionColor = Accent,
                    dismissActionContentColor = Paper,
                    shape = RectangleShape,
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
                        openServiceRequest = openServiceRequest,
                        onServiceRequestHandled = { openServiceRequest = false }
                    )
                    AppTab.Settings -> SettingsScreen(
                        fuel = fuel,
                        store = store,
                        autoParkRequest = autoParkRequest,
                        onAutoParkRequestHandled = { autoParkRequest = 0 }
                    )
                }
            }
        }
    }
}

/**
 * Icon-only tab bar, as in the reference: no labels, no border, the active
 * tab in orange. Each tab still announces its name to screen readers.
 */
@Composable
private fun BottomNav(tab: AppTab, onChange: (AppTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink)
            .navigationBarsPadding()
            .height(72.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTab.entries.forEach { t ->
            val selected = tab == t
            val tint by androidx.compose.animation.animateColorAsState(
                if (selected) Accent else Paper, tween(200), label = "tabTint"
            )
            val label = stringResource(t.labelRes)
            Box(
                Modifier
                    .size(width = 72.dp, height = 56.dp)
                    .selectable(selected = selected, role = Role.Tab) { onChange(t) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    ImageVector.vectorResource(t.icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
