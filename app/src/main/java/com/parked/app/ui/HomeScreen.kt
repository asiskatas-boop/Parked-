package com.parked.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.Dispatchers
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
    hasPerm: Boolean,
    saveRequest: Int,
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
    var showPhoto by remember { mutableStateOf(false) }
    var mapCommand by remember { mutableStateOf<MapCommand?>(null) }
    var sheetHeightPx by remember { mutableIntStateOf(0) }

    val msgSaved = stringResource(R.string.spot_saved)
    val msgUndo = stringResource(R.string.undo)
    val msgLocationOff = stringResource(R.string.turn_on_location)
    val msgNoFix = stringResource(R.string.error_no_location)
    val msgPermission = stringResource(R.string.error_location_permission)

    fun showMessage(text: String) {
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) }
    }

    fun doSave() {
        if (saving) return
        saving = true
        scope.launch {
            val loc = currentLocation(context)
            if (loc == null) {
                saving = false
                showMessage(msgNoFix)
                return@launch
            }
            val previous = store.saveParking(loc.first, loc.second)
            saving = false
            ParkedWidget.refresh(context)
            if (previous.timerEndsAt != null) ParkingTimer.cancel(context)
            val hadSpot = previous.lat != null && previous.lng != null
            snackbar.currentSnackbarData?.dismiss()
            if (!hadSpot) {
                snackbar.showSnackbar(msgSaved)
                return@launch
            }
            // Saving replaces the previous spot. Offer Undo, because a stray tap
            // here would otherwise lose where the car really is.
            val result = snackbar.showSnackbar(
                message = msgSaved,
                actionLabel = msgUndo,
                duration = SnackbarDuration.Long,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                store.restore(previous)
                previous.timerEndsAt?.let { if (it > System.currentTimeMillis()) ParkingTimer.schedule(context, it) }
                ParkedWidget.refresh(context)
            } else {
                store.deletePhotoFile(previous.photoPath)
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

    // Finish a save that was waiting for the permission dialog.
    LaunchedEffect(hasPerm) {
        if (hasPerm && pendingSave) {
            pendingSave = false
            onSaveSpot()
        }
    }

    // Save requested from the widget, tile or a "tap to save" notification.
    LaunchedEffect(saveRequest) {
        if (saveRequest > 0) onSaveSpot()
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

    Box(Modifier.fillMaxSize()) {
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
            bottomPaddingPx = sheetHeightPx,
        )

        OsmAttribution(Modifier.align(Alignment.TopEnd))

        // Map buttons sit just above the sheet.
        if (hasSpot) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = with(density) { sheetHeightPx.toDp() } + 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (live != null) {
                    MapButton(Icons.Filled.ZoomOutMap, stringResource(R.string.map_show_both)) {
                        mapCommand = MapCommand.ShowBoth((mapCommand?.id ?: 0) + 1)
                    }
                }
                MapButton(Icons.Filled.DirectionsCar, stringResource(R.string.map_center_car)) {
                    mapCommand = MapCommand.CenterOnCar((mapCommand?.id ?: 0) + 1)
                }
            }
        }

        Surface(
            color = White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onSizeChanged { sheetHeightPx = it.height }
        ) {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                when {
                    s == null -> Spacer(Modifier.height(120.dp))
                    !hasSpot -> EmptySpotContent(saving = saving, onSave = onSaveSpot)
                    else -> SavedSpotContent(
                        state = s,
                        autoParkMode = autoParkMode,
                        isAtCar = isAtCar,
                        distance = distance,
                        saving = saving,
                        onDirections = { openDirections(context, s) },
                        onSave = onSaveSpot,
                        onShare = { shareSpot(context, s) },
                        onNote = { showNote = true },
                        onPhoto = { showPhoto = true },
                        onTimer = { showTimer = true },
                    )
                }
            }
        }
    }

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
    if (showPhoto && s != null) {
        PhotoDialog(
            path = s.photoPath,
            onDismiss = { showPhoto = false },
            onNewPhoto = { path -> scope.launch { store.setPhotoPath(path) } },
            onRemove = { showPhoto = false; scope.launch { store.setPhotoPath(null) } },
        )
    }
}

@Composable
private fun EmptySpotContent(saving: Boolean, onSave: () -> Unit) {
    Text(
        stringResource(R.string.home_empty_title),
        fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
        color = NearBlack, letterSpacing = (-0.5).sp,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.home_empty_body), fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(18.dp))
    PrimaryButton(
        text = stringResource(if (saving) R.string.saving else R.string.save_parking_spot),
        icon = Icons.Filled.Bookmark,
        onClick = onSave
    )
}

@Composable
private fun SavedSpotContent(
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
) {
    val context = LocalContext.current
    Text(
        stringResource(if (isAtCar) R.string.home_at_car else R.string.home_saved_title),
        fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
        color = NearBlack, letterSpacing = (-0.5).sp,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(6.dp))
    val parked = state.parkedAt?.let { parkedAtLabel(context, it) } ?: stringResource(R.string.parked_recently)
    val away = if (distance != null && !isAtCar) {
        stringResource(R.string.distance_walk, formatDistance(distance), walkingMinutes(distance))
    } else null
    Text(
        listOfNotNull(parked, away).joinToString(" · "),
        fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium
    )
    Text(
        stringResource(
            when {
                !state.monitoring -> R.string.autopark_off
                autoParkMode == AutoParkMode.Limited -> R.string.autopark_on_limited
                else -> R.string.autopark_on
            }
        ),
        fontSize = 13.sp, color = TextSecondary
    )

    if (!state.note.isNullOrBlank()) {
        Spacer(Modifier.height(12.dp))
        Surface(onClick = onNote, color = SurfaceTint, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EditNote, contentDescription = null, tint = OliveDark)
                Spacer(Modifier.width(10.dp))
                Text(state.note, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
            }
        }
    }

    val timerEnds = state.timerEndsAt?.takeIf { it > System.currentTimeMillis() - 60 * 60_000L }
    if (timerEnds != null) {
        Spacer(Modifier.height(10.dp))
        TimerStatus(endsAt = timerEnds, onClick = onTimer)
    }

    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SpotChip(
            icon = Icons.Outlined.EditNote,
            label = stringResource(if (state.note.isNullOrBlank()) R.string.chip_add_note else R.string.chip_edit_note),
            modifier = Modifier.weight(1f),
            onClick = onNote
        )
        SpotChip(
            icon = Icons.Outlined.CameraAlt,
            label = stringResource(if (state.photoPath == null) R.string.chip_add_photo else R.string.chip_view_photo),
            modifier = Modifier.weight(1f),
            onClick = onPhoto
        )
        SpotChip(
            icon = Icons.Outlined.Timer,
            label = stringResource(if (timerEnds == null) R.string.chip_timer else R.string.chip_timer_edit),
            modifier = Modifier.weight(1f),
            onClick = onTimer
        )
    }

    Spacer(Modifier.height(14.dp))
    PrimaryButton(text = stringResource(R.string.get_directions), icon = Icons.Filled.Navigation, onClick = onDirections)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton(
            text = stringResource(if (saving) R.string.saving else R.string.save_current_spot),
            icon = Icons.Filled.Bookmark,
            modifier = Modifier.weight(1f),
            onClick = onSave
        )
        SecondaryButton(
            text = stringResource(R.string.share_spot),
            icon = Icons.Filled.Share,
            modifier = Modifier.weight(1f),
            onClick = onShare
        )
    }
}

@Composable
private fun SpotChip(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceTint,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = OliveDark, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NearBlack, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TimerStatus(endsAt: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    val minutesLeft = ((endsAt - now) / 60_000.0).let { kotlin.math.ceil(it).toInt() }
    val expired = minutesLeft <= 0
    val text = if (expired) {
        stringResource(R.string.timer_expired_at, clockTime(context, endsAt))
    } else {
        val left = if (minutesLeft >= 60) {
            stringResource(R.string.duration_hm, minutesLeft / 60, minutesLeft % 60)
        } else stringResource(R.string.duration_m, minutesLeft)
        stringResource(R.string.timer_ends_at, clockTime(context, endsAt), left)
    }
    Surface(
        onClick = onClick,
        color = if (expired || minutesLeft <= ParkingTimer.WARNING_MINUTES) Color(0xFFFFF1E0) else SurfaceTint,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Timer, contentDescription = null, tint = if (expired) WarnAmber else OliveDark)
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (expired) WarnAmber else NearBlack)
        }
    }
}

@Composable
private fun MapButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = White,
        shadowElevation = 6.dp,
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = OliveDark)
        }
    }
}

@Composable
private fun OsmAttribution(modifier: Modifier) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.osm_attribution),
        fontSize = 11.sp,
        color = NearBlack,
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 6.dp, end = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(White.copy(alpha = 0.88f))
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright")))
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

// ============================================================
// Note, timer and photo
// ============================================================

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
            placeholder = { Text(stringResource(R.string.note_placeholder), color = TextSecondary) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            supportingText = { Text("${text.length}/140") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.save_note), onClick = { onSave(text) })
        if (initial.isNotBlank()) {
            QuietButton(text = stringResource(R.string.remove_note), color = ErrorRed) { onSave(null) }
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
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack
            )
            Spacer(Modifier.height(12.dp))
        }
        val presets = listOf(30, 60, 120, 180)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            presets.forEach { minutes ->
                OutlinedButton(
                    onClick = { start(minutes) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        if (minutes < 60) stringResource(R.string.duration_m, minutes)
                        else stringResource(R.string.duration_h, minutes / 60),
                        color = OliveDark, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
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
            Text(stringResource(R.string.timer_inexact), fontSize = 12.sp, lineHeight = 16.sp, color = TextSecondary)
            QuietButton(text = stringResource(R.string.timer_allow_exact), color = OliveDark) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }
                exactAllowed = ParkingTimer.canBeExact(context)
            }
        }
        if (endsAt != null) {
            QuietButton(text = stringResource(R.string.timer_cancel), color = ErrorRed) { onSet(null) }
        }
    }
}

@Composable
private fun PhotoDialog(
    path: String?,
    onDismiss: () -> Unit,
    onNewPhoto: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var target by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = target
        target = null
        if (ok && file != null && file.length() > 0) onNewPhoto(file.absolutePath) else file?.delete()
    }
    fun takePhoto() {
        val (file, uri) = SpotPhoto.newTarget(context)
        target = file
        runCatching { camera.launch(uri) }.onFailure { file.delete(); target = null }
    }

    // No photo yet: go straight to the camera.
    LaunchedEffect(Unit) { if (path == null) takePhoto() }
    if (path == null) {
        // Close once the camera returns without a photo.
        LaunchedEffect(target) { if (target == null) { delay(300); onDismiss() } }
        return
    }

    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) { bitmap = withContext(Dispatchers.IO) { SpotPhoto.load(path, 1600) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(NearBlack).systemBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_spot_photo),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    CircularProgressIndicator(color = LimeBright)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(stringResource(R.string.photo_retake), icon = Icons.Outlined.CameraAlt, modifier = Modifier.weight(1f)) { takePhoto() }
                SecondaryButton(stringResource(R.string.photo_remove), icon = Icons.Filled.Delete, modifier = Modifier.weight(1f), onClick = onRemove)
            }
            QuietButton(stringResource(R.string.close), color = White, onClick = onDismiss)
        }
    }
}

// ============================================================
// Actions
// ============================================================

/** Best available fix: a recent cached one, otherwise a fresh one. */
private suspend fun currentLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return null
    val fused = LocationServices.getFusedLocationProviderClient(context)
    return try {
        val cached = suspendCoroutine<android.location.Location?> { cont ->
            fused.lastLocation.addOnCompleteListener { cont.resume(if (it.isSuccessful) it.result else null) }
        }
        val recent = cached?.takeIf { System.currentTimeMillis() - it.time <= 2 * 60 * 1000L && it.accuracy <= 50f }
        if (recent != null) return recent.latitude to recent.longitude
        val fresh = suspendCoroutine<android.location.Location?> { cont ->
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnCompleteListener { cont.resume(if (it.isSuccessful) it.result else null) }
        }
        fresh?.let { it.latitude to it.longitude }
            ?: cached?.takeIf { System.currentTimeMillis() - it.time <= 2 * 60 * 1000L && it.accuracy <= 150f }
                ?.let { it.latitude to it.longitude }
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
    val msg = if (s.note.isNullOrBlank()) context.getString(R.string.share_text, link)
    else context.getString(R.string.share_text_note, s.note, link)
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
