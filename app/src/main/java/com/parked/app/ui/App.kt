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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
enum class AppAction { SaveSpot, FindCar, OpenSettings, OpenService }

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
    var onboardingPage by remember { mutableIntStateOf(0) }

    // A widget or tile tap on first launch should not be swallowed by onboarding.
    LaunchedEffect(pendingAction) {
        if (pendingAction != null && showOnboarding) {
            uiPrefs.edit().putBoolean("onboarding_done", true).apply()
            showOnboarding = false
        }
    }

    // The brand video plays on every cold start, except when the app was opened
    // for a quick action (widget, tile, notification) or animations are off.
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
            Stage.Splash -> VideoSplash(onFinished = { showSplash = false })
            Stage.Onboarding -> AnimatedContent(
                targetState = onboardingPage,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(tween(380, delayMillis = 80)) + slideInHorizontally(tween(380)) { it / 8 })
                        .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { -it / 8 })
                },
                label = "onboardPages"
            ) { page ->
                OnboardingPage(
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
            Stage.Main -> MainContent(fuel = fuel, store = store, pendingAction = pendingAction, onActionHandled = onActionHandled)
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
private fun VideoSplash(onFinished: () -> Unit) {
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
        kotlinx.coroutines.delay(6_000L)
        end()
    }
    val skipLabel = stringResource(R.string.splash_skip)
    Box(
        Modifier
            .fillMaxSize()
            .background(OliveDark)
            .clickable(role = Role.Button, onClickLabel = skipLabel) { end() }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> SplashVideoView(ctx, onEnd = { end() }) },
            onRelease = { it.release() }
        )
    }
}

private class SplashVideoView(context: Context, private val onEnd: () -> Unit) : TextureView(context),
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
                    videoWidth = mp.videoWidth
                    videoHeight = mp.videoHeight
                    applyCenterCrop()
                    mp.start()
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
// Onboarding
// ============================================================

@Composable
private fun OnboardingPage(page: Int, onNext: () -> Unit) {
    val bg = if (page == 0) OliveDark else LimeBright
    val fg = if (page == 0) White else OliveDark
    val headline = stringResource(if (page == 0) R.string.onboarding_1_title else R.string.onboarding_2_title)
    val body = stringResource(if (page == 0) R.string.onboarding_1_body else R.string.onboarding_2_body)
    val action = stringResource(if (page == 0) R.string.onboarding_continue else R.string.onboarding_start)

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .clickable(role = Role.Button, onClickLabel = action) { onNext() }
    ) {
        if (page == 0) {
            // The splash video has just played; show its final logo still.
            Image(
                painter = painterResource(R.drawable.ic_parked_logo),
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp).size(240.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Image(
                painter = painterResource(R.drawable.splash_lime),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // On the patterned page the text sits on a solid lime panel, so the cars
        // in the wallpaper never run behind the words.
        Surface(
            color = if (page == 0) Color.Transparent else LimeBright,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .align(if (page == 0) Alignment.BottomCenter else Alignment.Center)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = if (page == 0) 42.dp else 24.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 18.dp, vertical = if (page == 0) 0.dp else 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = fg,
                    letterSpacing = (-1.5).sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    headline,
                    fontSize = 25.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = fg,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 330.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    body,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = fg,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 330.dp)
                )
                Spacer(Modifier.height(22.dp))
                Text(action, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg)
            }
        }
    }
}

// ============================================================
// Tabs
// ============================================================

enum class AppTab(val labelRes: Int, val filled: ImageVector, val outlined: ImageVector) {
    Home(R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    Fuel(R.string.tab_fuel, Icons.Filled.LocalGasStation, Icons.Outlined.LocalGasStation),
    Settings(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
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

    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            AppAction.SaveSpot -> { tab = AppTab.Home; saveRequest++ }
            AppAction.FindCar -> tab = AppTab.Home
            AppAction.OpenSettings -> tab = AppTab.Settings
            AppAction.OpenService -> { tab = AppTab.Fuel; openServiceRequest = true }
            null -> return@LaunchedEffect
        }
        onActionHandled()
    }

    var live by remember { mutableStateOf<LiveLocation?>(null) }
    var hasPerm by remember { mutableStateOf(hasLocationPermission(context)) }
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
        containerColor = White,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = NearBlack,
                    contentColor = White,
                    actionColor = LimeBright,
                    dismissActionContentColor = White,
                    shape = RoundedCornerShape(14.dp),
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
                    AppTab.Settings -> SettingsScreen(fuel = fuel, store = store)
                }
            }
        }
    }
}

@Composable
private fun BottomNav(tab: AppTab, onChange: (AppTab) -> Unit) {
    Surface(color = White, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AppTab.entries.forEach { t ->
                val selected = tab == t
                val label = stringResource(t.labelRes)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Tab) { onChange(t) }
                        .semantics {
                            this.selected = selected
                            contentDescription = label
                        },
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
                            contentDescription = null,
                            tint = if (selected) LimeBright else GrayIcon,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
            }
        }
    }
}
