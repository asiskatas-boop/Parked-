package com.parked.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.parked.app.R
import com.parked.app.data.FuelStore
import com.parked.app.data.ParkingState
import com.parked.app.data.ParkingStore
import com.parked.app.receiver.ParkingTimer
import com.parked.app.service.AutoParkMode
import com.parked.app.service.ParkingMonitorService
import com.parked.app.util.Channels
import com.parked.app.util.haversine
import com.parked.app.util.hasLocationPermission
import com.parked.app.util.isLocationServicesEnabled
import com.parked.app.widget.ParkedWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    store: ParkingStore,
    fuel: FuelStore,
    live: LiveLocation?,
    gps: GpsReading?,
    hasPerm: Boolean,
    locationOn: Boolean,
    onSetUpAutoPark: () -> Unit,
    saveRequest: Int,
    onSaveHandled: () -> Unit,
    permResult: Int,
    appScope: CoroutineScope,
    requestPerm: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    // Null until DataStore has loaded, so Home does not flash the empty state.
    val state by store.state.collectAsState(initial = null)
    val autoParkMode by ParkingMonitorService.mode.collectAsState()

    var pendingSave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showGarage by remember { mutableStateOf(false) }
    // Saveable: the Activity is often recreated while the camera app is open.
    var showPhoto by rememberSaveable { mutableStateOf(false) }
    var photoTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var mapCommand by remember { mutableStateOf<MapCommand?>(null) }
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var confirmReplace by remember { mutableStateOf(false) }
    val uiPrefs = remember { context.getSharedPreferences("parked_ui", Context.MODE_PRIVATE) }
    // AutoPark health. "Lost" is observable, so the banner appears the moment
    // the app has to switch AutoPark off, not one resume later.
    val autoParkLost by remember { com.parked.app.data.AutoParkHealth.lost(context) }.collectAsState()
    var hasBtPerm by remember { mutableStateOf(com.parked.app.util.hasBluetoothPermission(context)) }
    var bluetoothOn by remember { mutableStateOf(com.parked.app.util.isBluetoothEnabled(context)) }
    // "Not now" on the Bluetooth banner lasts until Bluetooth changes or the app restarts.
    var bluetoothBannerDismissed by remember { mutableStateOf(false) }
    val homeLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(homeLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasBtPerm = com.parked.app.util.hasBluetoothPermission(context)
                bluetoothOn = com.parked.app.util.isBluetoothEnabled(context)
            }
        }
        homeLifecycle.lifecycle.addObserver(observer)
        onDispose { homeLifecycle.lifecycle.removeObserver(observer) }
    }
    // Bluetooth switched from the quick settings shade doesn't resume the app,
    // so listen for the change directly.
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                bluetoothOn = com.parked.app.util.isBluetoothEnabled(context)
                bluetoothBannerDismissed = false
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context, receiver,
                android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        bluetoothOn = com.parked.app.util.isBluetoothEnabled(context)
    }
    var autoParkTipDismissedFor by remember { mutableLongStateOf(uiPrefs.getLong("autopark_tip_dismissed_for", -1L)) }

    val msgSaved = stringResource(R.string.spot_saved)
    val msgMoved = stringResource(R.string.pin_moved)
    val view = androidx.compose.ui.platform.LocalView.current
    val msgPhotoSaved = stringResource(R.string.photo_saved)
    val msgUndo = stringResource(R.string.undo)
    val msgLocationOff = stringResource(R.string.turn_on_location)
    val msgNoFix = stringResource(R.string.error_no_location)
    val msgPermission = stringResource(R.string.error_location_permission)

    fun showMessage(text: String) {
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = photoTarget
        photoTarget = null
        val file = path?.let(::File)
        if (ok && file != null && file.length() > 0) {
            appScope.launch {
                store.setPhotoPath(file.absolutePath)
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(msgPhotoSaved)
            }
            showPhoto = true
        } else {
            file?.delete()
        }
    }

    fun takePhoto() {
        val (file, uri) = SpotPhoto.newTarget(context)
        photoTarget = file.absolutePath
        runCatching { camera.launch(uri) }.onFailure { file.delete(); photoTarget = null }
    }

    // Remove photos orphaned by an interrupted camera session or an old crash.
    // Once per process, so a photo waiting on Undo is never touched.
    LaunchedEffect(state != null) {
        val loaded = state ?: return@LaunchedEffect
        if (orphanCleanupDone) return@LaunchedEffect
        orphanCleanupDone = true
        withContext(Dispatchers.IO) {
            store.deleteOrphanPhotos(setOfNotNull(loaded.photoPath, photoTarget))
        }
    }

    fun doSave() {
        if (saving) return
        saving = true
        // App-level scope: leaving Home must not cancel the Undo offer or cleanup.
        appScope.launch {
            val loc = currentLocation(context)
            if (loc == null) {
                saving = false
                showMessage(msgNoFix)
                return@launch
            }
            val previous = store.saveParking(loc.lat, loc.lng, source = ParkingStore.SOURCE_MANUAL, accuracy = loc.accuracy)
            saving = false
            // Say so when the fix was weak, so a spot a few metres off is no surprise.
            val savedText = loc.accuracy?.takeIf { it > WEAK_GPS_M }
                ?.let { context.getString(R.string.spot_saved_weak, it.roundToInt()) } ?: msgSaved
            ParkedWidget.refresh(context)
            if (previous.timerEndsAt != null) ParkingTimer.cancel(context)
            val hadSpot = previous.lat != null && previous.lng != null
            snackbar.currentSnackbarData?.dismiss()
            if (!hadSpot) {
                snackbar.showSnackbar(savedText)
                return@launch
            }
            // Saving replaces the previous spot. Offer Undo, because a stray tap
            // here would otherwise lose where the car really is.
            var undone = false
            try {
                val result = snackbar.showSnackbar(
                    message = savedText,
                    actionLabel = msgUndo,
                    duration = SnackbarDuration.Long,
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    undone = true
                    withContext(NonCancellable) {
                        store.restore(previous)
                        previous.timerEndsAt?.let { if (it > System.currentTimeMillis()) ParkingTimer.schedule(context, it) }
                        ParkedWidget.refresh(context)
                    }
                }
            } finally {
                if (!undone) withContext(NonCancellable) { store.deletePhotoFile(previous.photoPath) }
            }
        }
    }

    val onSaveSpot: () -> Unit = {
        when {
            !hasPerm -> { pendingSave = true; requestPerm() }
            !isLocationServicesEnabled(context) -> {
                openLocationSettings(context)
                showMessage(msgLocationOff)
            }
            else -> doSave()
        }
    }

    // Finish a save that was waiting for the permission dialog, or drop it if
    // the permission was refused, so granting it later can't save by surprise.
    LaunchedEffect(hasPerm, permResult) {
        if (!pendingSave || permResult == 0) return@LaunchedEffect
        pendingSave = false
        if (hasPerm) onSaveSpot() else showMessage(msgPermission)
    }

    // Save requested from the widget, tile or a "tap to save" notification.
    // Reset once handled, so coming back to Home does not save again.
    LaunchedEffect(saveRequest) {
        if (saveRequest > 0) {
            onSaveHandled()
            onSaveSpot()
        }
    }

    val s = state
    val hasSpot = s?.hasSpot == true
    val distance: Double? = if (hasSpot && live != null) {
        haversine(live.lat, live.lng, s!!.parkedLat!!, s.parkedLng!!)
    } else null
    // "Here" allows for the reported accuracy, within sensible limits.
    val hereRadius = (live?.accuracy?.toDouble() ?: 15.0).coerceIn(15.0, 40.0)
    val isAtCar = distance != null && distance < hereRadius
    val parkedMarkerIcon = remember { makeParkedMarker(context) }
    val liveMarkerIcon = remember { makeLiveMarker(context) }

    val online = rememberOnline()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // The map fills only the area above the panel, so the car marker and
    // "centre" are never hidden under it.
    val sheetHeight = with(density) { sheetHeightPx.toDp() }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Box(Modifier.fillMaxSize().padding(bottom = sheetHeight)) {
            ParkedMap(
                parkedLat = if (hasSpot) s?.parkedLat else null,
                parkedLng = if (hasSpot) s?.parkedLng else null,
                liveLat = live?.lat,
                liveLng = live?.lng,
                parkedMarkerIcon = parkedMarkerIcon,
                liveMarkerIcon = liveMarkerIcon,
                parkedLabel = stringResource(R.string.map_parked_car),
                liveLabel = stringResource(R.string.map_you),
                command = mapCommand,
                onCarMoved = { lat, lng ->
                    val before = s
                    if (before?.parkedLat != null && before.parkedLng != null) appScope.launch {
                        store.moveSpot(lat, lng, expectedAt = before.parkedAt)
                        ParkedWidget.refresh(context)
                        if (android.os.Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        snackbar.currentSnackbarData?.dismiss()
                        val result = snackbar.showSnackbar(
                            msgMoved, actionLabel = msgUndo, duration = SnackbarDuration.Long, withDismissAction = true
                        )
                        // Undo puts the pin, its accuracy and the hint back exactly,
                        // and only if this is still the same spot.
                        if (result == SnackbarResult.ActionPerformed) {
                            store.moveSpot(
                                before.parkedLat, before.parkedLng, expectedAt = before.parkedAt,
                                accuracy = before.accuracy, adjusted = before.adjusted,
                            )
                            ParkedWidget.refresh(context)
                        }
                    }
                },
            )

            // GPS quality above the map credit, stacked so large text never
            // makes one cover the other. The banner covers permission and
            // Location being off, so the signal only shows when both are fine.
            Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp)) {
                if (hasPerm && locationOn) {
                    GpsSignal(gps = gps)
                    Spacer(Modifier.height(6.dp))
                }
                OsmAttribution(Modifier)
            }

            // Tell the user why the map can't show them, and fix it in one tap.
            val issue = when {
                !hasPerm -> MapIssue.Permission
                !locationOn -> MapIssue.LocationOff
                // AutoPark is on but can't see the car disconnect. (Without the
                // Bluetooth permission the state can't be read; recovery then
                // switches AutoPark off and the "lost" banner explains.)
                s != null && s.monitoring && hasBtPerm && !bluetoothOn && !bluetoothBannerDismissed -> MapIssue.BluetoothOff
                // The app had to switch AutoPark off (a permission was removed).
                autoParkLost && s != null && !s.monitoring -> MapIssue.AutoParkLost
                // "Not now" hides the AutoPark tip until the next spot is saved.
                // Offline, the "map needs a connection" note takes this spot instead.
                online && s != null && !s.monitoring && autoParkTipDismissedFor != (s.parkedAt ?: 0L) -> MapIssue.AutoParkOff
                else -> null
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = issue != null,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it / 2 },
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                MapBanner(
                    issue = issue ?: MapIssue.Permission,
                    onDismiss = {
                        if (issue == MapIssue.AutoParkLost) {
                            com.parked.app.data.AutoParkHealth.clear(context)
                        } else if (issue == MapIssue.BluetoothOff) {
                            bluetoothBannerDismissed = true
                        } else {
                            val key = s?.parkedAt ?: 0L
                            uiPrefs.edit().putLong("autopark_tip_dismissed_for", key).apply()
                            autoParkTipDismissedFor = key
                        }
                    },
                    onAction = {
                        when (issue) {
                            MapIssue.Permission -> requestPerm()
                            MapIssue.LocationOff -> openLocationSettings(context)
                            MapIssue.AutoParkOff -> onSetUpAutoPark()
                            MapIssue.AutoParkLost -> onSetUpAutoPark()
                            MapIssue.BluetoothOff -> runCatching {
                                bluetoothLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }
                            null -> Unit
                        }
                    }
                )
            }

            // Without data the tiles can't load (an underground garage). Say so,
            // at the top edge so the car marker in the middle stays visible.
            if (!online && issue == null) {
                Text(
                    stringResource(R.string.map_offline),
                    fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = Paper,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                        .background(Panel)
                        .border(1.dp, Line)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // One button: centre on the car, or when you're away from it, fit
            // both you and the car on screen.
            if (hasSpot) {
                val both = live != null && distance != null && distance >= 40
                MapButton(
                    icon = if (both) Ph.cornersOut else Ph.mapPin,
                    description = stringResource(if (both) R.string.map_show_both else R.string.map_center_car),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)
                ) {
                    val next = (mapCommand?.id ?: 0) + 1
                    mapCommand = if (both) MapCommand.ShowBoth(next) else MapCommand.CenterOnCar(next)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Ink)
                .onSizeChanged { sheetHeightPx = it.height }
                // Never more than about 60% of the screen, so the map stays usable.
                .heightIn(max = minOf(520.dp, (LocalConfiguration.current.screenHeightDp * 0.6f).dp))
                .animateContentSize(tween(260))
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
        ) {
            androidx.compose.animation.Crossfade(
                targetState = when {
                    s == null -> 0
                    !hasSpot -> 1
                    else -> 2
                },
                animationSpec = tween(220),
                label = "homeSheet"
            ) { mode ->
                Column {
                    when {
                        mode == 0 || s == null -> Spacer(Modifier.height(160.dp))
                        mode == 1 -> EmptySpotContent(saving = saving, onSave = onSaveSpot)
                        else -> SavedSpotContent(
                            state = s,
                            autoParkMode = autoParkMode,
                            isAtCar = isAtCar,
                            distance = distance,
                            saving = saving,
                            onDirections = { openDirections(context, s) },
                            onSave = {
                                // Far from the saved car, saving here is probably a mistake: ask first.
                                if (distance != null && distance > 50) confirmReplace = true else onSaveSpot()
                            },
                            onShare = { shareSpot(context, s) },
                            onNote = { showNote = true },
                            onPhoto = { if (s.photoPath == null) takePhoto() else showPhoto = true },
                            onTimer = { showTimer = true },
                            onGarage = { showGarage = true },
                        )
                    }
                }
            }
        }
    }

    ConfirmDialog(
        visible = confirmReplace,
        title = stringResource(R.string.replace_spot_title),
        message = stringResource(R.string.replace_spot_body, distance?.let { formatDistance(it) } ?: ""),
        confirmLabel = stringResource(R.string.replace_spot_confirm),
        destructive = false,
        onDismiss = { confirmReplace = false },
        onConfirm = { confirmReplace = false; onSaveSpot() }
    )

    if (showNote && s != null) {
        NoteSheet(
            initial = s.note.orEmpty(),
            onDismiss = { showNote = false },
            onSave = { note ->
                showNote = false
                scope.launch { store.setNote(note); ParkedWidget.refresh(context) }
            }
        )
    }
    if (showGarage && s != null) {
        GarageSheet(
            level = s.level, zone = s.zone, space = s.space,
            hasPhoto = s.photoPath != null,
            onPhoto = { level, zone, space ->
                // Keep what was typed, then open the camera.
                showGarage = false
                // App scope: the Activity may be recreated while the camera is open.
                appScope.launch { store.setGarage(level, zone, space) }
                if (s.photoPath == null) takePhoto() else showPhoto = true
            },
            onDismiss = { showGarage = false },
            onSave = { level, zone, space ->
                showGarage = false
                scope.launch { store.setGarage(level, zone, space); ParkedWidget.refresh(context) }
            }
        )
    }
    if (showTimer && s != null) {
        TimerSheet(
            endsAt = s.timerEndsAt?.takeIf { it > System.currentTimeMillis() },
            onDismiss = { showTimer = false },
            onSet = { endsAt ->
                showTimer = false
                scope.launch {
                    store.setTimerEndsAt(endsAt)
                    if (endsAt == null) ParkingTimer.cancel(context) else ParkingTimer.schedule(context, endsAt)
                }
            }
        )
    }
    val photoPath = s?.photoPath
    if (showPhoto && photoPath != null) {
        PhotoDialog(
            path = photoPath,
            onDismiss = { showPhoto = false },
            onRetake = { takePhoto() },
            onRemove = { showPhoto = false; scope.launch { store.setPhotoPath(null) } },
        )
    }
}

private var orphanCleanupDone = false

@Composable
internal fun HeroText(text: String, color: Color, heading: Boolean = false) {
    Text(
        text,
        fontSize = TypeScale.Hero,
        lineHeight = TypeScale.Hero * 1.1f,
        color = color,
        letterSpacing = (-0.5).sp,
        modifier = if (heading) Modifier.semantics { heading() } else Modifier
    )
}

@Composable
internal fun EmptySpotContent(saving: Boolean, onSave: () -> Unit) {
    HeroText(stringResource(R.string.home_empty_title), Paper, heading = true)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.home_empty_body), fontSize = TypeScale.Body, lineHeight = 24.sp, color = Muted)
    Spacer(Modifier.height(24.dp))
    PrimaryButton(
        text = stringResource(if (saving) R.string.saving else R.string.save_parking_spot),
        onClick = onSave
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
internal fun SavedSpotContent(
    state: ParkingState,
    autoParkMode: AutoParkMode,
    isAtCar: Boolean,
    distance: Double?,
    saving: Boolean,
    onDirections: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onNote: () -> Unit,
    onPhoto: () -> Unit,
    onTimer: () -> Unit,
    onGarage: () -> Unit,
) {
    val context = LocalContext.current
    val parked = state.parkedAt?.let { parkedAtLabel(context, it) } ?: stringResource(R.string.parked_recently)
    // How the spot was saved. "Saved automatically when Golf disconnected"
    // is the proof that AutoPark works; without it people go back to saving
    // by hand.
    val auto = state.source == ParkingStore.SOURCE_AUTO
    val how = when {
        auto -> stringResource(R.string.home_saved_auto)
        state.source == ParkingStore.SOURCE_MANUAL -> stringResource(R.string.home_saved_manual)
        else -> null
    }
    val howLine = how?.let { h -> state.accuracy?.let { stringResource(R.string.gps_accuracy, h, it.roundToInt()) } ?: h }
    Text(parked, fontSize = TypeScale.Label, lineHeight = 22.sp, color = Muted)
    if (howLine != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            if (auto) {
                Icon(Ph.checkCircle, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(howLine, fontSize = TypeScale.Label, lineHeight = 22.sp, color = if (auto) Paper else Muted)
        }
    }
    // "AutoPark is off" is the map banner's job (and it can be dismissed there).
    if (state.monitoring && autoParkMode == AutoParkMode.Limited) {
        Text(
            stringResource(R.string.autopark_on_limited),
            fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = Muted
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            HeroText(stringResource(if (isAtCar) R.string.home_at_car else R.string.home_car_title), Paper, heading = true)
            if (distance != null && !isAtCar) {
                HeroText(stringResource(R.string.home_distance, formatDistance(distance), walkingMinutes(distance)), Accent)
            }
        }
        IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
            Icon(Ph.share, contentDescription = stringResource(R.string.share_spot), tint = Paper, modifier = Modifier.size(24.dp))
        }
    }

    // The one thing to do here: go to the car.
    Spacer(Modifier.height(20.dp))
    PrimaryButton(text = stringResource(R.string.get_directions), onClick = onDirections)

    // Details: note, photo and parking time in one row between hairlines.
    val timerEnds = state.timerEndsAt?.takeIf { it > System.currentTimeMillis() - 60 * 60_000L }
    Spacer(Modifier.height(20.dp))
    DividerRow()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        SpotCell(
            icon = Ph.note,
            label = state.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chip_add_note),
            active = !state.note.isNullOrBlank(),
            modifier = Modifier.weight(1f),
            onClick = onNote
        )
        VerticalDivider(color = Line)
        SpotCell(
            icon = Ph.camera,
            label = stringResource(R.string.chip_add_photo),
            active = state.photoPath != null,
            modifier = Modifier.weight(1f),
            onClick = onPhoto
        )
        VerticalDivider(color = Line)
        TimerCell(endsAt = timerEnds, modifier = Modifier.weight(1f), onClick = onTimer)
    }
    // Garage mode: GPS alone isn't enough underground, so level, zone and
    // space are one tap away, and all optional.
    val garage = garageSummary(state)
    ActionRow(
        icon = Ph.squaresFour,
        iconTint = if (garage != null) Accent else Paper,
        title = garage ?: stringResource(R.string.garage_add),
        subtitle = stringResource(if (garage == null) R.string.garage_add_sub else R.string.garage_edit_sub),
        onClick = onGarage,
    )
    // A pin that may be off can be put right by hand.
    // Not after the user already placed it, and not for TalkBack users, who
    // can't drag (they can add level and space, or a note, instead).
    val touchExploration = remember {
        context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
    if (!state.adjusted && !touchExploration && (state.accuracy == null || state.accuracy > WEAK_GPS_M)) {
        Text(
            stringResource(R.string.pin_drag_hint),
            fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = Muted,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    // Replacing the saved spot is the risky action here, so it is the
    // quietest one on the screen.
    LinkButton(
        stringResource(if (saving) R.string.saving else R.string.save_current_spot),
        color = Muted,
        modifier = Modifier.padding(top = 4.dp),
        onClick = onSave
    )
}

/** One column of the note / photo / timer row. Orange icon = something saved. */
@Composable
private fun SpotCell(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier,
    labelColor: Color = Paper,
    detail: String? = null,
    onClick: () -> Unit,
) {
    val saved = stringResource(R.string.cd_has_content)
    Column(
        modifier
            .fillMaxHeight()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { if (active) stateDescription = saved }
            .heightIn(min = 88.dp)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Accent else Paper, modifier = Modifier.size(24.dp))
        Column {
            Text(label, fontSize = TypeScale.Label, lineHeight = 20.sp, color = labelColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, fontSize = TypeScale.Caption, lineHeight = 18.sp, color = labelColor.takeIf { it != Paper } ?: Muted)
        }
    }
}

/**
 * Parking time: the end time and the time left. It turns amber in the last
 * ParkingTimer.WARNING_MINUTES and after the end, as before.
 */
@Composable
private fun TimerCell(endsAt: Long?, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt != null) {
            now = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    val minutesLeft = endsAt?.let { kotlin.math.ceil((it - now) / 60_000.0).toInt() }
    val expired = minutesLeft != null && minutesLeft <= 0
    val warn = minutesLeft != null && minutesLeft <= ParkingTimer.WARNING_MINUTES
    val label = when {
        endsAt == null -> stringResource(R.string.chip_timer)
        expired -> stringResource(R.string.timer_short_ended, clockTime(context, endsAt))
        else -> stringResource(R.string.timer_short_ends, clockTime(context, endsAt))
    }
    val detail = if (minutesLeft != null && !expired) {
        val left = if (minutesLeft >= 60) stringResource(R.string.duration_hm, minutesLeft / 60, minutesLeft % 60)
        else stringResource(R.string.duration_m, minutesLeft)
        stringResource(R.string.timer_left, left)
    } else null
    SpotCell(
        icon = Ph.timer,
        label = label,
        detail = detail,
        active = endsAt != null,
        labelColor = if (warn) Warn else Paper,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        onClick = onClick
    )
}

/** Accuracy above this many metres counts as weak GPS. */
private const val WEAK_GPS_M = 30f

/** A fix older than this means the phone has lost GPS (a garage, a tunnel). */
private const val GPS_STALE_MS = 20_000L

/**
 * Three bars and a label: good (up to 15 m), fair (up to 30 m), weak (worse).
 * No recent fix reads "Looking for GPS"; approximate-only permission says so.
 */
@Composable
internal fun GpsSignal(gps: GpsReading?, modifier: Modifier = Modifier, preciseOverride: Boolean? = null) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            now = System.currentTimeMillis()
        }
    }
    val precise = preciseOverride ?: (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED)
    val acc = gps?.takeIf { maxOf(now, it.at) - it.at <= GPS_STALE_MS }?.accuracy
    val fresh = gps != null && maxOf(now, gps.at) - gps.at <= GPS_STALE_MS
    val bars = when {
        !precise -> 1
        !fresh || acc == null -> 0
        acc <= 15f -> 3
        acc <= WEAK_GPS_M -> 2
        else -> 1
    }
    val color = when (bars) { 3 -> Accent; 2 -> Warn; 1 -> Danger; else -> Muted }
    // The spoken label is the level only, so TalkBack isn't re-reading a
    // changing number every few seconds; the screen shows the metres.
    val level = when {
        !precise -> stringResource(R.string.gps_approximate)
        !fresh -> stringResource(R.string.gps_searching)
        acc == null -> stringResource(R.string.gps_on)
        bars == 3 -> stringResource(R.string.gps_good)
        bars == 2 -> stringResource(R.string.gps_fair)
        else -> stringResource(R.string.gps_weak)
    }
    val shown = if (precise && fresh && acc != null) stringResource(R.string.gps_accuracy, level, acc.roundToInt()) else level
    Row(
        modifier
            .background(Ink.copy(alpha = 0.8f))
            .border(1.dp, Line)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .clearAndSetSemantics { contentDescription = level },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(5.dp, 9.dp, 13.dp).forEachIndexed { i, h ->
                Box(Modifier.size(width = 3.dp, height = h).background(if (i < bars) color else Color(0xFF616161))) // 3.3:1 on black
            }
        }
        Spacer(Modifier.width(8.dp))
        // Status is in words too, never colour alone.
        Text(shown, fontSize = TypeScale.Caption, color = Paper)
    }
}

internal enum class MapIssue { Permission, LocationOff, BluetoothOff, AutoParkLost, AutoParkOff }

@Composable
internal fun MapBanner(issue: MapIssue, onDismiss: () -> Unit, onAction: () -> Unit) {
    val (text, action) = when (issue) {
        MapIssue.Permission -> R.string.banner_permission to R.string.banner_permission_action
        MapIssue.LocationOff -> R.string.banner_location_off to R.string.banner_location_off_action
        MapIssue.AutoParkOff -> R.string.banner_autopark_off to R.string.banner_autopark_off_action
        MapIssue.BluetoothOff -> R.string.banner_bluetooth_off to R.string.banner_bluetooth_off_action
        MapIssue.AutoParkLost -> R.string.banner_autopark_lost to R.string.banner_autopark_lost_action
    }
    Column(Modifier.fillMaxWidth().background(Panel).padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.Top) {
            Icon(
                when (issue) {
                    MapIssue.AutoParkOff, MapIssue.BluetoothOff -> Ph.bluetooth
                    MapIssue.AutoParkLost -> Ph.warning
                    else -> Ph.mapPin
                },
                contentDescription = null,
                tint = if (issue == MapIssue.AutoParkLost || issue == MapIssue.BluetoothOff) Warn else Accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(stringResource(text), fontSize = TypeScale.Label, lineHeight = 22.sp, color = Paper, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(start = 32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LinkButton(stringResource(action), onClick = onAction)
            if (issue == MapIssue.AutoParkOff || issue == MapIssue.AutoParkLost || issue == MapIssue.BluetoothOff) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp), shape = androidx.compose.ui.graphics.RectangleShape) {
                    Text(stringResource(R.string.banner_not_now), fontSize = TypeScale.Emphasis, color = Muted)
                }
            }
        }
    }
}

@Composable
internal fun MapButton(icon: ImageVector, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(48.dp)
            .background(Ink)
            .border(1.dp, Line)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Paper, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun OsmAttribution(modifier: Modifier) {
    // Required by the OpenStreetMap and CARTO licences. Credit only, not a
    // link: a tiny tap target on the map was easy to hit by accident.
    Text(
        stringResource(R.string.osm_attribution),
        fontSize = 11.sp,
        color = Muted,
        modifier = modifier
            .background(Ink.copy(alpha = 0.7f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// ============================================================
// Note, timer and photo
// ============================================================

/** "Level −2 · Zone B · Space 14", or null when nothing is set. */
private fun garageSummaryText(context: Context, s: ParkingState): String? = listOfNotNull(
    s.level?.let { context.getString(R.string.garage_level_value, it.replace("-", "−")) },
    s.zone?.let { context.getString(R.string.garage_zone_value, it) },
    s.space?.let { context.getString(R.string.garage_space_value, it) },
).joinToString(" · ").ifEmpty { null }

@Composable
private fun garageSummary(s: ParkingState): String? = garageSummaryText(LocalContext.current, s)

/**
 * Garage mode: big level buttons, then zone and space. Everything is
 * optional, so someone in a hurry can tap one button and walk away.
 */
@Composable
internal fun GarageSheet(
    level: String?,
    zone: String?,
    space: String?,
    hasPhoto: Boolean,
    onPhoto: (String?, String?, String?) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String?, String?, String?) -> Unit,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    var lvl by remember { mutableStateOf(level.orEmpty()) }
    var zn by remember { mutableStateOf(zone.orEmpty()) }
    var sp by remember { mutableStateOf(space.orEmpty()) }
    ParkedSheet(
        title = stringResource(R.string.garage_title),
        subtitle = stringResource(R.string.garage_subtitle),
        onDismiss = onDismiss
    ) {
        Text(stringResource(R.string.garage_level_label), fontSize = TypeScale.Label, color = Muted)
        Spacer(Modifier.height(8.dp))
        // Two rows so every level is visible without scrolling; 56 dp squares.
        Column(
            Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(listOf("-3", "-2", "-1", "0"), listOf("1", "2", "3")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { v ->
                        val selected = lvl == v
                        Box(
                            Modifier
                                .size(56.dp)
                                .background(if (selected) Accent else Field)
                                .border(1.dp, if (selected) Accent else Line)
                                .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton) {
                                    lvl = if (selected) "" else v
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(v.replace("-", "−"), fontSize = TypeScale.Section, color = if (selected) Ink else Paper)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Typed level for names like "P2"; zone and space side by side.
        GarageField(lvl, { lvl = it }, stringResource(R.string.garage_level_other), Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GarageField(zn, { zn = it }, stringResource(R.string.garage_zone), Modifier.weight(1f))
            GarageField(sp, { sp = it }, stringResource(R.string.garage_space), Modifier.weight(1f), last = true) { onSave(lvl, zn, sp) }
        }
        Spacer(Modifier.height(16.dp))
        SecondaryButton(
            stringResource(if (hasPhoto) R.string.chip_add_photo else R.string.garage_photo),
            icon = Ph.camera,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onPhoto(lvl, zn, sp) }
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.save)) { onSave(lvl, zn, sp) }
        if (level != null || zone != null || space != null) {
            QuietButton(stringResource(R.string.garage_clear), color = Danger) { onSave(null, null, null) }
        }
        QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
    }
}

@Composable
private fun GarageField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    last: Boolean = false,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(12)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        shape = androidx.compose.ui.graphics.RectangleShape,
        colors = parkedFieldColors(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = SofiaSans, fontSize = TypeScale.Emphasis),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = if (last) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Next,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
        modifier = modifier
    )
}

@Composable
private fun NoteSheet(initial: String, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    ParkedSheet(
        title = stringResource(R.string.note_title),
        subtitle = stringResource(R.string.note_subtitle),
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(140) },
            label = { Text(stringResource(R.string.note_label)) },
            placeholder = { Text(stringResource(R.string.note_placeholder)) },
            singleLine = true,
            shape = androidx.compose.ui.graphics.RectangleShape,
            colors = parkedFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = SofiaSans, fontSize = TypeScale.Emphasis),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            ),
            // The keyboard often covered the Save button, so the keyboard's
            // Done key and the tick in the field both save as well.
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSave(text) }),
            trailingIcon = {
                IconButton(onClick = { onSave(text) }) {
                    Icon(Ph.check, contentDescription = stringResource(R.string.save_note), tint = Accent, modifier = Modifier.size(24.dp))
                }
            },
            supportingText = { Text("${text.length}/140") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.save_note), onClick = { onSave(text) })
        if (initial.isNotBlank()) {
            QuietButton(text = stringResource(R.string.remove_note), color = Danger) { onSave(null) }
        }
        QuietButton(text = stringResource(R.string.cancel), onClick = onDismiss)
    }
}

@Composable
private fun TimerSheet(endsAt: Long?, onDismiss: () -> Unit, onSet: (Long?) -> Unit) {
    val context = LocalContext.current
    var custom by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var exactAllowed by remember { mutableStateOf(ParkingTimer.canBeExact(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // Re-check when coming back from the system "Alarms & reminders" page.
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) exactAllowed = ParkingTimer.canBeExact(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val errorText = stringResource(R.string.timer_custom_error)
    val notifDenied = stringResource(R.string.timer_needs_notifications)
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var pendingMinutes by remember { mutableStateOf<Int?>(null) }

    fun commit(minutes: Int) = onSet(System.currentTimeMillis() + minutes * 60_000L)

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val minutes = pendingMinutes
        pendingMinutes = null
        if (!granted) scope.launch { snackbar.showSnackbar(notifDenied) }
        if (minutes != null) commit(minutes)
    }

    fun start(minutes: Int) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingMinutes = minutes
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (!Channels.canPost(context, Channels.TIMER)) scope.launch { snackbar.showSnackbar(notifDenied) }
            commit(minutes)
        }
    }

    ParkedSheet(
        title = stringResource(R.string.timer_title),
        subtitle = stringResource(R.string.timer_subtitle, ParkingTimer.WARNING_MINUTES),
        onDismiss = onDismiss
    ) {
        if (endsAt != null) {
            Text(
                stringResource(R.string.timer_current, clockTime(context, endsAt)),
                fontSize = TypeScale.Body, lineHeight = 24.sp, color = Paper
            )
            Spacer(Modifier.height(16.dp))
        }
        val presets = listOf(30, 60, 120, 180)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            presets.forEach { minutes ->
                OutlinedButton(
                    onClick = { start(minutes) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                ) {
                    Text(
                        if (minutes < 60) stringResource(R.string.duration_m, minutes)
                        else stringResource(R.string.duration_h, minutes / 60),
                        color = Paper, fontSize = TypeScale.Body
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        NumberField(
            value = custom,
            onValueChange = { custom = it; error = null },
            label = stringResource(R.string.timer_custom_label),
            decimal = false,
        )
        ErrorText(error)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(text = stringResource(R.string.timer_start), onClick = {
            val minutes = custom.trim().toIntOrNull()
            if (minutes == null || minutes !in 1..24 * 60) error = errorText else start(minutes)
        })
        if (!exactAllowed && Build.VERSION.SDK_INT >= 31) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.timer_inexact), fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = Muted)
            QuietButton(text = stringResource(R.string.timer_allow_exact), color = Accent) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }
            }
        }
        if (endsAt != null) {
            QuietButton(text = stringResource(R.string.timer_cancel), color = Danger) { onSet(null) }
        }
    }
}

@Composable
private fun PhotoDialog(
    path: String,
    onDismiss: () -> Unit,
    onRetake: () -> Unit,
    onRemove: () -> Unit,
) {
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) { SpotPhoto.load(path, 1600) }
        failed = bitmap == null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Ink).systemBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_spot_photo),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (failed) {
                    // The file is gone or unreadable: say so instead of spinning forever.
                    Text(stringResource(R.string.photo_load_failed), color = Paper, fontSize = TypeScale.Body)
                } else {
                    CircularProgressIndicator(color = Accent)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(stringResource(R.string.photo_retake), icon = Ph.camera, modifier = Modifier.weight(1f), onClick = onRetake)
                SecondaryButton(stringResource(R.string.photo_remove), icon = Ph.trash, modifier = Modifier.weight(1f), onClick = onRemove)
            }
            QuietButton(stringResource(R.string.close), color = Paper, onClick = onDismiss)
        }
    }
}

// ============================================================
// Actions
// ============================================================

/**
 * True while the phone has a working internet connection; updates live.
 * Uses VALIDATED, not just INTERNET: in an underground garage the phone often
 * keeps a mobile network with no actual data.
 */
@Composable
private fun rememberOnline(): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(android.net.ConnectivityManager::class.java) }
    fun usable(caps: android.net.NetworkCapabilities?) = caps != null &&
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    var online by remember {
        mutableStateOf(runCatching { usable(cm?.getNetworkCapabilities(cm.activeNetwork)) }.getOrDefault(true))
    }
    DisposableEffect(cm) {
        // The default-network callback describes one network at a time, so its
        // own arguments are the truth; no synchronous lookups inside callbacks.
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) { online = usable(caps) }
            override fun onLost(network: android.net.Network) { online = false }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return online
}

/** A location fix and its accuracy in metres, when Android reports one. */
private data class Fix(val lat: Double, val lng: Double, val accuracy: Float?)

private fun android.location.Location.toFix() = Fix(latitude, longitude, if (hasAccuracy()) accuracy else null)

/** Best available fix: a recent cached one, otherwise a fresh one. */
private suspend fun currentLocation(context: Context): Fix? {
    if (!hasLocationPermission(context)) return null
    val fused = LocationServices.getFusedLocationProviderClient(context)
    return try {
        val cached = suspendCoroutine<android.location.Location?> { cont ->
            fused.lastLocation.addOnCompleteListener { cont.resume(if (it.isSuccessful) it.result else null) }
        }
        val recent = cached?.takeIf { System.currentTimeMillis() - it.time <= 2 * 60 * 1000L && it.accuracy <= 50f }
        if (recent != null) return recent.toFix()
        val fresh = suspendCoroutine<android.location.Location?> { cont ->
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnCompleteListener { cont.resume(if (it.isSuccessful) it.result else null) }
        }
        fresh?.toFix()
            ?: cached?.takeIf { System.currentTimeMillis() - it.time <= 2 * 60 * 1000L && it.accuracy <= 150f }?.toFix()
    } catch (_: SecurityException) {
        null
    }
}

private fun openLocationSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openDirections(context: Context, s: ParkingState) {
    val lat = s.parkedLat ?: return
    val lng = s.parkedLng ?: return
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=w"))
        .setPackage("com.google.android.apps.maps")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(nav) }.isFailure) {
        // No Google Maps: any maps app, then the browser.
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(geo) }.isFailure) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

private fun shareSpot(context: Context, s: ParkingState) {
    val lat = s.parkedLat ?: return
    val lng = s.parkedLng ?: return
    val link = "https://maps.google.com/?q=$lat,$lng"
    val details = listOfNotNull(garageSummaryText(context, s), s.note?.takeIf { it.isNotBlank() }).joinToString(", ")
    val msg = if (details.isEmpty()) context.getString(R.string.share_text, link)
    else context.getString(R.string.share_text_note, details, link)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, msg)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(share, context.getString(R.string.share_spot)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
