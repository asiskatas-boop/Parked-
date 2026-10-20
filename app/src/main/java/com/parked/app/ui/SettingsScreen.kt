package com.parked.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.parked.app.ParkedApplication
import com.parked.app.R
import com.parked.app.data.Backup
import com.parked.app.data.FuelStore
import com.parked.app.data.ParkingHistoryEntry
import com.parked.app.data.ParkingState
import com.parked.app.data.ParkingStore
import com.parked.app.service.AutoParkMode
import com.parked.app.service.ParkingMonitorService
import com.parked.app.util.Channels
import com.parked.app.util.hasBluetoothPermission
import com.parked.app.util.hasLocationPermission
import com.parked.app.util.isBluetoothEnabled
import com.parked.app.util.isLocationServicesEnabled
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    fuel: FuelStore,
    store: ParkingStore,
    autoParkRequest: Int = 0,
    onAutoParkRequestHandled: () -> Unit = {},
    appScope: kotlinx.coroutines.CoroutineScope,
) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    if (showHistory) BackHandler { showHistory = false }
    androidx.compose.animation.AnimatedContent(
        targetState = showHistory,
        transitionSpec = { pageTransition(forward = targetState) },
        label = "settingsPages"
    ) { history ->
        if (history) ParkingHistoryPage(store = store, onBack = { showHistory = false })
        else SettingsMain(
            fuel = fuel,
            store = store,
            onOpenHistory = { showHistory = true },
            autoParkRequest = autoParkRequest,
            onAutoParkRequestHandled = onAutoParkRequestHandled,
            appScope = appScope,
        )
    }
}

@Composable
private fun SettingsMain(
    fuel: FuelStore,
    store: ParkingStore,
    onOpenHistory: () -> Unit,
    autoParkRequest: Int,
    onAutoParkRequestHandled: () -> Unit,
    appScope: kotlinx.coroutines.CoroutineScope,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val state by store.state.collectAsState(initial = ParkingState())
    val latestState by rememberUpdatedState(state)
    val autoParkMode by ParkingMonitorService.mode.collectAsState()

    var showDevicePicker by remember { mutableStateOf(false) }
    var confirmHistoryOff by remember { mutableStateOf(false) }
    var pendingAutoStart by remember { mutableStateOf(false) }
    var pendingDevicePickerOnly by remember { mutableStateOf(false) }
    // True only after the flow sent the user to Android's Location settings page.
    var sentToLocationSettings by remember { mutableStateOf(false) }
    // A car picked automatically, named in the final "AutoPark is on" message.
    var autoPickedCar by remember { mutableStateOf<String?>(null) }
    // The permission being explained right now, one at a time.
    var permStep by remember { mutableStateOf<PermStep?>(null) }
    // The notification prompt is asked at most once while Settings is open.
    var askedNotifications by remember { mutableStateOf(false) }
    var systemNotificationsAllowed by remember { mutableStateOf(Channels.canPost(context, Channels.ALERTS)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val sAutoReady = stringResource(R.string.autopark_turned_on)
    val sAutoFailed = stringResource(R.string.autopark_failed)
    val sAutoOff = stringResource(R.string.autopark_off)
    val sBtNeeded = stringResource(R.string.bluetooth_needed)
    val sBtOn = stringResource(R.string.turn_on_bluetooth)
    val sChooseCar = stringResource(R.string.choose_car_bluetooth)
    val sLocationNeeded = stringResource(R.string.location_needed)
    val sPermsNeeded = stringResource(R.string.permissions_needed)
    val sAlertsOn = stringResource(R.string.alerts_on)
    val sAlertsNotEnabled = stringResource(R.string.alerts_not_enabled)
    val sAlertsInSettings = stringResource(R.string.alerts_enable_in_settings)
    val sBtPermission = stringResource(R.string.bluetooth_permission_needed)
    val sCarSaved = stringResource(R.string.car_bluetooth_saved)
    val sAutoReadyCar = stringResource(R.string.autopark_turned_on_car)
    val sPermsBlocked = stringResource(R.string.perm_blocked)
    val sOpenSettings = stringResource(R.string.perm_open_settings)
    val sCarFallback = stringResource(R.string.car_bluetooth)

    fun say(text: String) {
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) }
    }

    fun needsNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= 33 && !askedNotifications &&
        fuel.notificationsEnabled &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun openLocationSettings() {
        sentToLocationSettings = pendingAutoStart
        runCatching {
            context.startActivity(Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun startMonitorReady() {
        scope.launch {
            // Persist first so the service never sees an event while the switch says off.
            store.setMonitoring(true)
            val picked = autoPickedCar
            autoPickedCar = null
            if (ParkingMonitorService.start(context)) {
                // Back on: the "AutoPark was switched off" banner no longer applies.
                com.parked.app.data.AutoParkHealth.clear(context)
                say(if (picked != null) sAutoReadyCar.format(picked) else sAutoReady)
            } else {
                store.setMonitoring(false)
                say(sAutoFailed)
            }
        }
    }

    // Set below; lets the launchers declared before beginAutoPark continue the flow.
    var continueAutoPark: () -> Unit = {}
    var resumeAutoPark: () -> Unit = {}

    /** One car among the paired devices: use it instead of asking. Otherwise show the list. */
    fun chooseCar() {
        val cars = pairedDevices(context).filter { it.isCar }
        if (cars.size == 1) {
            val car = cars.single()
            scope.launch {
                store.selectDevice(car.name ?: sCarFallback, car.address)
                // Named in the final message, so it isn't wiped by the next snackbar.
                autoPickedCar = car.name ?: sCarFallback
                continueAutoPark()
            }
        } else { say(sChooseCar); showDevicePicker = true }
    }

    // Location on with Android's own in-app dialog (one tap), instead of
    // sending people to the system settings and hoping they come back.
    val locationOnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { _ ->
        if (!pendingAutoStart) return@rememberLauncherForActivityResult
        if (isLocationServicesEnabled(context)) { pendingAutoStart = false; startMonitorReady() }
        else { pendingAutoStart = false; say(sLocationNeeded) }
    }

    fun askLocationOn() {
        val request = com.google.android.gms.location.LocationSettingsRequest.Builder()
            .addLocationRequest(
                com.google.android.gms.location.LocationRequest.Builder(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
            ).build()
        com.google.android.gms.location.LocationServices.getSettingsClient(context).checkLocationSettings(request)
            .addOnSuccessListener { if (pendingAutoStart) { pendingAutoStart = false; startMonitorReady() } }
            .addOnFailureListener { e ->
                if (!pendingAutoStart) return@addOnFailureListener
                val resolvable = e as? com.google.android.gms.common.api.ResolvableApiException
                if (resolvable != null) {
                    runCatching {
                        locationOnLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(resolvable.resolution).build())
                    }.onFailure { openLocationSettings(); say(sLocationNeeded) }
                } else { openLocationSettings(); say(sLocationNeeded) }
            }
    }


    val bluetoothEnableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        if (!pendingAutoStart && !pendingDevicePickerOnly) return@rememberLauncherForActivityResult
        when {
            !isBluetoothEnabled(context) -> {
                val wasAutoPark = pendingAutoStart
                pendingAutoStart = false
                pendingDevicePickerOnly = false
                say(if (wasAutoPark) sBtNeeded else sBtOn)
            }
            pendingDevicePickerOnly -> { pendingDevicePickerOnly = false; showDevicePicker = true }
            latestState.deviceAddress == null -> chooseCar()
            !isLocationServicesEnabled(context) -> askLocationOn()
            else -> { pendingAutoStart = false; startMonitorReady() }
        }
    }

    val autoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (!pendingAutoStart) return@rememberLauncherForActivityResult
        // An empty result comes from a duplicate or interrupted request; the real
        // answer arrives separately, so ignore this one rather than cancelling.
        if (results.isEmpty()) return@rememberLauncherForActivityResult
        if (Build.VERSION.SDK_INT >= 33 && results[Manifest.permission.POST_NOTIFICATIONS] == false) {
            // AutoPark still works without alerts. Remember the choice so turning
            // AutoPark on later does not ask again.
            fuel.updateNotificationsEnabled(false)
            systemNotificationsAllowed = false
        }
        val askedLocation = Manifest.permission.ACCESS_FINE_LOCATION in results || Manifest.permission.ACCESS_COARSE_LOCATION in results
        val askedBluetooth = Build.VERSION.SDK_INT >= 31 && Manifest.permission.BLUETOOTH_CONNECT in results
        when {
            // Location and Bluetooth are required; notifications are not.
            (askedLocation && !hasLocationPermission(context)) || (askedBluetooth && !hasBluetoothPermission(context)) -> {
                pendingAutoStart = false
                // After "don't ask again" Android shows nothing more, so offer
                // the app's settings page instead of a dead end.
                val activity = context.findActivity()
                val blocked = activity != null && results.filterValues { !it }.keys.none {
                    androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                }
                if (blocked) scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val r = snackbar.showSnackbar(sPermsBlocked, actionLabel = sOpenSettings, duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) runCatching {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                } else say(sPermsNeeded)
            }
            else -> resumeAutoPark()
        }
    }

    fun beginAutoPark(deviceReady: Boolean = latestState.deviceAddress != null, fromAutoPick: Boolean = false) {
        pendingAutoStart = true
        // Each setup starts clean: no leftover "sent to settings" or car name.
        sentToLocationSettings = false
        if (!fromAutoPick) autoPickedCar = null
        when {
            // One permission at a time, each explained in a sentence before
            // Android's own prompt appears.
            !hasLocationPermission(context) -> permStep = PermStep.Location
            !hasBluetoothPermission(context) -> permStep = PermStep.Bluetooth
            needsNotificationPermission() -> permStep = PermStep.Notifications
            !isBluetoothEnabled(context) -> bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            !deviceReady -> chooseCar()
            !isLocationServicesEnabled(context) -> askLocationOn()
            else -> { pendingAutoStart = false; startMonitorReady() }
        }
    }
    continueAutoPark = { beginAutoPark(deviceReady = true, fromAutoPick = true) }
    resumeAutoPark = { beginAutoPark(fromAutoPick = true) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        fuel.updateNotificationsEnabled(granted)
        systemNotificationsAllowed = Channels.canPost(context, Channels.ALERTS)
        say(if (granted) sAlertsOn else sAlertsNotEnabled)
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            !granted -> { pendingDevicePickerOnly = false; say(sBtPermission) }
            !isBluetoothEnabled(context) -> {
                pendingDevicePickerOnly = true
                bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            else -> { pendingDevicePickerOnly = false; showDevicePicker = true }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemNotificationsAllowed = Channels.canPost(context, Channels.ALERTS)
                // While a permission explainer is open the flow isn't finished:
                // don't start AutoPark underneath it.
                if (pendingAutoStart && permStep == null) {
                    if (latestState.deviceAddress != null && hasLocationPermission(context) && hasBluetoothPermission(context) &&
                        isBluetoothEnabled(context) && isLocationServicesEnabled(context)
                    ) {
                        pendingAutoStart = false
                        sentToLocationSettings = false
                        startMonitorReady()
                    } else if (sentToLocationSettings && !isLocationServicesEnabled(context)) {
                        // Came back from the system Location settings without turning it
                        // on: cancel, so AutoPark can't switch on during a later resume.
                        // (The in-app Location dialog reports back through its launcher.)
                        sentToLocationSettings = false
                        pendingAutoStart = false
                        say(sLocationNeeded)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // "Set up AutoPark" from onboarding or the map banner starts the same flow
    // as the switch: permissions, Bluetooth, car choice, then on.
    LaunchedEffect(autoParkRequest) {
        if (autoParkRequest > 0) {
            onAutoParkRequestHandled()
            val saved = store.current()
            if (!saved.monitoring) beginAutoPark(deviceReady = saved.deviceAddress != null)
        }
    }

    // Backup: the user picks where the file goes, so nothing leaves the phone
    // unless they send it somewhere.
    var pendingRestore by remember { mutableStateOf<Backup.Parsed?>(null) }
    val sBackupSaved = stringResource(R.string.backup_saved)
    val sBackupFailed = stringResource(R.string.backup_save_failed)
    val sBackupInvalid = stringResource(R.string.backup_not_valid)
    val sBackupTooNew = stringResource(R.string.backup_too_new)
    val sBackupRestored = stringResource(R.string.backup_restored)
    val sUndo = stringResource(R.string.undo)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { say(if (Backup.write(context, uri, store, fuel)) sBackupSaved else sBackupFailed) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            when (val r = Backup.read(context, uri)) {
                is Backup.ReadResult.Ok -> pendingRestore = r.backup
                Backup.ReadResult.NotABackup -> say(sBackupInvalid)
                Backup.ReadResult.TooNew -> say(sBackupTooNew)
            }
        }
    }

    val notificationsChecked = fuel.notificationsEnabled && systemNotificationsAllowed
    var hasCrashReport by remember { mutableStateOf(ParkedApplication.crashFile(context).exists()) }

    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())) {
        Column(Modifier.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
            PageTitle(stringResource(R.string.tab_settings))
            Spacer(Modifier.height(24.dp))
            SettingsCard {
                ToggleRow(
                    label = stringResource(R.string.autopark),
                    checked = state.monitoring,
                    supporting = when {
                        !state.monitoring -> stringResource(R.string.autopark_supporting)
                        autoParkMode == AutoParkMode.Limited -> stringResource(R.string.autopark_limited)
                        else -> null
                    },
                    onToggle = { on ->
                        if (on) beginAutoPark() else {
                            com.parked.app.data.AutoParkHealth.clear(context)
                            ParkingMonitorService.stop(context)
                            scope.launch { store.setMonitoring(false) }
                            say(sAutoOff)
                        }
                    }
                )
                DividerRow()
                TextRow(stringResource(R.string.car_bluetooth), state.deviceName ?: stringResource(R.string.not_set)) {
                    pendingDevicePickerOnly = true
                    when {
                        Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission(context) ->
                            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        !isBluetoothEnabled(context) ->
                            bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        else -> { pendingDevicePickerOnly = false; showDevicePicker = true }
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_preferences), modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            SettingsCard {
                ToggleRow(
                    label = stringResource(R.string.parking_alerts),
                    checked = notificationsChecked,
                    onToggle = { enabled ->
                        when {
                            !enabled -> fuel.updateNotificationsEnabled(false)
                            Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            !Channels.canPost(context, Channels.ALERTS) -> {
                                // Remember the wish before opening Android's settings, so
                                // alerts are on as soon as the channel is enabled there.
                                fuel.updateNotificationsEnabled(true)
                                say(sAlertsInSettings)
                                runCatching {
                                    context.startActivity(
                                        Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                                            .putExtra(AndroidSettings.EXTRA_CHANNEL_ID, Channels.ALERTS)
                                    )
                                }
                            }
                            else -> { fuel.updateNotificationsEnabled(true); say(sAlertsOn) }
                        }
                    }
                )
                DividerRow()
                ToggleRow(
                    label = stringResource(R.string.history_toggle),
                    checked = state.historyEnabled,
                    supporting = stringResource(R.string.history_toggle_supporting),
                    onToggle = { on ->
                        if (on) scope.launch { store.setHistoryEnabled(true) }
                        else if (state.history.isEmpty()) scope.launch { store.setHistoryEnabled(false) }
                        else confirmHistoryOff = true
                    }
                )
                if (state.historyEnabled) {
                    DividerRow()
                    TextRow(
                        stringResource(R.string.history_title),
                        pluralStringResource(R.plurals.history_count, state.history.size, state.history.size),
                        onClick = onOpenHistory
                    )
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    DividerRow()
                    val locale = context.resources.configuration.locales[0]
                    TextRow(
                        stringResource(R.string.intro_language),
                        locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
                    ) { openAppLanguageSettings(context) }
                }
            }

            SectionTitle(stringResource(R.string.backup_section), modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            DividerRow()
            ActionRow(
                icon = Ph.trayArrowUp,
                title = stringResource(R.string.backup_export),
                subtitle = stringResource(R.string.backup_export_sub),
                onClick = {
                    val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    runCatching { exportLauncher.launch("Parked backup $day.json") }
                }
            )
            ActionRow(
                icon = Ph.trayArrowDown,
                title = stringResource(R.string.backup_import),
                subtitle = stringResource(R.string.backup_import_sub),
                onClick = {
                    // Many file managers label .json files as plain text or binary.
                    runCatching { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
                }
            )

            // About: the version, and the crash report when there is one.
            SectionTitle(stringResource(R.string.about_section), modifier = Modifier.padding(top = 32.dp, bottom = 12.dp))
            SettingsCard {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.about_version), fontSize = TypeScale.Emphasis, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(com.parked.app.BuildConfig.VERSION_NAME, fontSize = TypeScale.Body, color = TextMuted)
                }
                if (hasCrashReport) {
                    DividerRow()
                    TextRow(stringResource(R.string.crash_report), stringResource(R.string.crash_report_share)) {
                        shareCrashReport(context)
                        hasCrashReport = false
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    pendingRestore?.let { r ->
        val message = listOfNotNull(
            r.exportedAt.takeIf { it > 0 }?.let { stringResource(R.string.backup_restore_made, dateTime(context, it)) },
            stringResource(R.string.backup_restore_body),
            // History is opt-in, so say plainly that this backup turns it on.
            if (r.parking.historyEnabled) pluralStringResource(R.plurals.backup_restore_history, r.parking.history.size, r.parking.history.size) else null,
        ).joinToString(" ")
        ConfirmDialog(
            visible = true,
            title = stringResource(R.string.backup_restore_title),
            message = message,
            confirmLabel = stringResource(R.string.backup_restore_confirm),
            onDismiss = { pendingRestore = null },
            onConfirm = {
                pendingRestore = null
                // App-wide scope: leaving Settings must not cancel the restore or its Undo.
                appScope.launch {
                    val before = Backup.snapshot(store, fuel)
                    Backup.restore(context, r, store, fuel)
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(
                        sBackupRestored,
                        actionLabel = if (before != null) sUndo else null,
                        duration = SnackbarDuration.Long,
                        withDismissAction = true,
                    )
                    if (before == null) return@launch
                    if (result == SnackbarResult.ActionPerformed) {
                        Backup.restore(context, before.data, store, fuel, before.photoPath, before.monitoring)
                    } else {
                        // Undo has passed: the replaced spot's photo belongs to nothing now.
                        store.deletePhotoFile(before.photoPath)
                    }
                }
            }
        )
    }
    permStep?.let { step ->
        PermissionStepSheet(
            step = step,
            onContinue = {
                permStep = null
                val perms = when (step) {
                    PermStep.Location -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    PermStep.Bluetooth -> arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
                    PermStep.Notifications -> { askedNotifications = true; arrayOf(Manifest.permission.POST_NOTIFICATIONS) }
                }
                runCatching { autoPermissionLauncher.launch(perms) }.onFailure { pendingAutoStart = false; say(sPermsNeeded) }
            },
            onSkip = if (step == PermStep.Notifications) {
                {
                    // Skipping is remembered, so the next setup doesn't ask again.
                    // Alerts can be turned on later in Preferences.
                    permStep = null
                    askedNotifications = true
                    fuel.updateNotificationsEnabled(false)
                    systemNotificationsAllowed = false
                    resumeAutoPark()
                }
            } else null,
            // Swiping away the optional notifications step counts as skipping it.
            onDismiss = {
                if (step == PermStep.Notifications) {
                    permStep = null
                    askedNotifications = true
                    fuel.updateNotificationsEnabled(false)
                    systemNotificationsAllowed = false
                    resumeAutoPark()
                } else { permStep = null; pendingAutoStart = false }
            },
        )
    }
    ConfirmDialog(
        visible = confirmHistoryOff,
        title = stringResource(R.string.history_off_title),
        message = stringResource(R.string.history_off_body),
        confirmLabel = stringResource(R.string.history_off_confirm),
        onDismiss = { confirmHistoryOff = false },
        onConfirm = {
            confirmHistoryOff = false
            scope.launch { store.setHistoryEnabled(false) }
        }
    )
    if (showDevicePicker) {
        DevicePickerDialog(
            onDismiss = {
                showDevicePicker = false
                pendingAutoStart = false
                pendingDevicePickerOnly = false
            },
            onSelected = { name, address ->
                autoPickedCar = null
                val shouldStart = pendingAutoStart || state.monitoring
                showDevicePicker = false
                scope.launch {
                    // Stop the old car's session before switching device.
                    if (state.monitoring) {
                        ParkingMonitorService.stop(context)
                        store.setMonitoring(false)
                    }
                    store.selectDevice(name, address)
                    if (shouldStart) beginAutoPark(deviceReady = true) else say(sCarSaved)
                }
            }
        )
    }
}

@Composable
private fun DevicePickerDialog(onDismiss: () -> Unit, onSelected: (String, String) -> Unit) {
    val context = LocalContext.current
    val canRead = hasBluetoothPermission(context)
    // Cars first; headphones, watches and speakers after them.
    val devices = remember { if (canRead) pairedDevices(context).sortedByDescending { it.isCar } else emptyList() }
    val fallbackName = stringResource(R.string.car_bluetooth)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RadiusSmall,
        titleContentColor = TextPrimary,
        textContentColor = TextMuted,
        title = { Text(stringResource(R.string.choose_your_car), fontSize = 24.sp) },
        text = {
            Column {
                when {
                    !canRead -> Text(stringResource(R.string.bluetooth_permission_needed))
                    devices.isEmpty() -> Text(stringResource(R.string.no_paired_devices))
                    else -> LazyColumn {
                        item { DividerRow() }
                        items(devices, key = { it.address }) { device ->
                            val kind = stringResource(if (device.isCar) R.string.device_kind_car else R.string.device_kind_other)
                            // The address only helps when two devices share a name.
                            val repeated = devices.count { it.name == device.name } > 1
                            ActionRow(
                                icon = if (device.isCar) Ph.car else Ph.bluetooth,
                                iconTint = if (device.isCar) Green else TextMuted,
                                title = device.name ?: stringResource(R.string.unnamed_device),
                                subtitle = if (repeated) "$kind · ${device.address}" else kind,
                                onClick = { onSelected(device.name ?: fallbackName, device.address) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, shape = RadiusSmall) { Text(stringResource(R.string.close), fontSize = TypeScale.Body, color = TextPrimary) } }
    )
}

/** A paired Bluetooth device; [isCar] when Android reports it as car audio. */
internal data class PairedDevice(val name: String?, val address: String, val isCar: Boolean)

@android.annotation.SuppressLint("MissingPermission") // Callers check hasBluetoothPermission first.
internal fun pairedDevices(context: android.content.Context): List<PairedDevice> {
    if (!hasBluetoothPermission(context)) return emptyList()
    val adapter: BluetoothAdapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return runCatching {
        adapter.bondedDevices.orEmpty().map { d ->
            val cls = runCatching { d.bluetoothClass?.deviceClass }.getOrNull()
            PairedDevice(
                name = runCatching { d.name }.getOrNull(),
                address = d.address,
                // Car audio only: many headsets also report "hands-free", and
                // auto-picking one would save a spot every time it disconnects.
                isCar = cls == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
            )
        }
    }.getOrDefault(emptyList())
}

internal val carColours = listOf(
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


/** Stored colour names stay English for compatibility; the label is translated. */
@Composable
internal fun colourLabel(name: String): String = stringResource(
    when (name) {
        "White" -> R.string.colour_white
        "Silver" -> R.string.colour_silver
        "Gray" -> R.string.colour_gray
        "Black" -> R.string.colour_black
        "Red" -> R.string.colour_red
        "Green" -> R.string.colour_green
        "Yellow" -> R.string.colour_yellow
        "Orange" -> R.string.colour_orange
        "Brown" -> R.string.colour_brown
        "Purple" -> R.string.colour_purple
        else -> R.string.colour_blue
    }
)

@Composable
internal fun AppearanceDialog(fuel: FuelStore, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RadiusSmall,
        title = { Text(stringResource(R.string.car_appearance), fontSize = 24.sp, color = TextPrimary) },
        text = {
            Column {
                Text(stringResource(R.string.appearance_hint), fontSize = TypeScale.Body, lineHeight = 24.sp, color = TextMuted)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 430.dp)) {
                    items(carColours, key = { it.first }) { (name, swatch) ->
                        val selected = fuel.accentName == name
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (selected) SurfaceSunk else Color.Transparent)
                                .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton) {
                                    fuel.setAccent(name); onDismiss()
                                }
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(20.dp).border(1.dp, Border).background(swatch))
                            Spacer(Modifier.width(16.dp))
                            Text(colourLabel(name), modifier = Modifier.weight(1f), fontSize = TypeScale.Emphasis, color = TextPrimary)
                            if (selected) Icon(Ph.check, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, shape = RadiusSmall) { Text(stringResource(R.string.close), fontSize = TypeScale.Body, color = TextPrimary) } }
    )
}

@Composable
internal fun OdometerSheet(fuel: FuelStore, onDismiss: () -> Unit) {
    var odo by remember { mutableStateOf(fuel.currentOdo.takeIf { it > 0 }?.let { formatDecimal(it, 0).filter(Char::isDigit) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val eOdo = stringResource(R.string.error_odometer)
    ParkedSheet(title = stringResource(R.string.odometer), subtitle = stringResource(R.string.odometer_subtitle), onDismiss = onDismiss) {
        NumberField(odo, { odo = it; error = null }, stringResource(R.string.odometer_label), decimal = false)
        ErrorText(error)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.save), onClick = {
            val value = parseNumber(odo)
            if (value == null || value <= 0) error = eOdo else { fuel.setOdometer(value); onDismiss() }
        })
        QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
    }
}

// ============================================================
// Parking history (opt-in)
// ============================================================

@Composable
private fun ParkingHistoryPage(store: ParkingStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by store.state.collectAsState(initial = ParkingState())
    var confirmClear by remember { mutableStateOf(false) }

    SubPage(
        title = stringResource(R.string.history_title),
        onBack = onBack,
        emptyText = if (state.history.isEmpty()) stringResource(R.string.history_empty) else null,
        actions = {
            if (state.history.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Ph.trash, contentDescription = stringResource(R.string.history_clear), tint = TextPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }
    ) {
        items(state.history, key = { it.at }) { entry ->
            HistoryRow(
                entry = entry,
                label = parkedAtLabel(context, entry.at),
                onOpen = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("geo:${entry.lat},${entry.lng}?q=${entry.lat},${entry.lng}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                onDelete = { scope.launch { store.deleteHistoryEntry(entry.at) } }
            )
            DividerRow(Modifier.padding(horizontal = 24.dp))
        }
    }
    ConfirmDialog(
        visible = confirmClear,
        title = stringResource(R.string.history_clear_title),
        message = stringResource(R.string.history_clear_body),
        confirmLabel = stringResource(R.string.history_clear),
        onDismiss = { confirmClear = false },
        onConfirm = { confirmClear = false; scope.launch { store.clearHistory() } }
    )
}

@Composable
private fun HistoryRow(entry: ParkingHistoryEntry, label: String, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen)
            .heightIn(min = 64.dp)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = TypeScale.Emphasis, lineHeight = 24.sp, color = TextPrimary)
            if (!entry.note.isNullOrBlank()) Text(entry.note, fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = TextMuted)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(Ph.trash, contentDescription = stringResource(R.string.delete), tint = TextMuted, modifier = Modifier.size(24.dp))
        }
    }
}

/** Keeps a black or dark grey car visible on the dark panel. */
internal fun visibleOnPanel(c: Color): Color =
    if (c.luminance() < 0.08f) Color(0xFF6B6F74) else c

private fun shareCrashReport(context: android.content.Context) {
    val file = ParkedApplication.crashFile(context)
    val text = runCatching { file.readText() }.getOrNull() ?: return
    runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                context.getString(R.string.crash_report)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    file.delete()
}

/** The permissions AutoPark asks for, in order. Notifications can be skipped. */
internal enum class PermStep { Location, Bluetooth, Notifications }

@Composable
private fun PermissionStepSheet(step: PermStep, onContinue: () -> Unit, onSkip: (() -> Unit)?, onDismiss: () -> Unit) {
    val (title, body) = when (step) {
        PermStep.Location -> R.string.perm_location_title to R.string.perm_location_body
        PermStep.Bluetooth -> R.string.perm_bluetooth_title to R.string.perm_bluetooth_body
        PermStep.Notifications -> R.string.perm_notif_title to R.string.perm_notif_body
    }
    ParkedSheet(title = stringResource(title), subtitle = stringResource(body), onDismiss = onDismiss) {
        PrimaryButton(stringResource(R.string.perm_continue), onClick = onContinue)
        if (onSkip != null) QuietButton(stringResource(R.string.perm_skip), onClick = onSkip)
        else QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
    }
}

/** The Activity behind a Compose context, for permission rationale checks. */
private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
