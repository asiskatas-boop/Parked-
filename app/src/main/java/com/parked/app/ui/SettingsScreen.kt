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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val state by store.state.collectAsState(initial = ParkingState())
    val latestState by rememberUpdatedState(state)
    val autoParkMode by ParkingMonitorService.mode.collectAsState()

    var showOdo by remember { mutableStateOf(false) }
    var showTank by remember { mutableStateOf(false) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var confirmHistoryOff by remember { mutableStateOf(false) }
    var pendingAutoStart by remember { mutableStateOf(false) }
    var pendingDevicePickerOnly by remember { mutableStateOf(false) }
    var systemNotificationsAllowed by remember { mutableStateOf(Channels.canPost(context, Channels.ALERTS)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val sAutoReady = stringResource(R.string.autopark_ready)
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

    fun say(text: String) {
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) }
    }

    fun needsNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= 33 &&
        fuel.notificationsEnabled &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun openLocationSettings() {
        runCatching {
            context.startActivity(Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun startMonitorReady() {
        scope.launch {
            // Persist first so the service never sees an event while the switch says off.
            store.setMonitoring(true)
            if (ParkingMonitorService.start(context)) say(sAutoReady) else {
                store.setMonitoring(false)
                say(sAutoFailed)
            }
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
            latestState.deviceAddress == null -> { say(sChooseCar); showDevicePicker = true }
            !isLocationServicesEnabled(context) -> { openLocationSettings(); say(sLocationNeeded) }
            else -> { pendingAutoStart = false; startMonitorReady() }
        }
    }

    val autoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (!pendingAutoStart) return@rememberLauncherForActivityResult
        if (Build.VERSION.SDK_INT >= 33 && results[Manifest.permission.POST_NOTIFICATIONS] == false) {
            // AutoPark still works without alerts. Remember the choice so turning
            // AutoPark on later does not ask again.
            fuel.updateNotificationsEnabled(false)
            systemNotificationsAllowed = false
        }
        when {
            !hasLocationPermission(context) || !hasBluetoothPermission(context) -> { pendingAutoStart = false; say(sPermsNeeded) }
            !isBluetoothEnabled(context) -> bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            latestState.deviceAddress == null -> { say(sChooseCar); showDevicePicker = true }
            !isLocationServicesEnabled(context) -> { openLocationSettings(); say(sLocationNeeded) }
            else -> { pendingAutoStart = false; startMonitorReady() }
        }
    }

    fun beginAutoPark(deviceReady: Boolean = latestState.deviceAddress != null) {
        pendingAutoStart = true
        when {
            !hasLocationPermission(context) || !hasBluetoothPermission(context) || needsNotificationPermission() -> {
                val required = buildList {
                    if (!hasLocationPermission(context)) {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    if (!hasBluetoothPermission(context) && Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
                    if (needsNotificationPermission()) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                autoPermissionLauncher.launch(required.toTypedArray())
            }
            !isBluetoothEnabled(context) -> bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            !deviceReady -> { say(sChooseCar); showDevicePicker = true }
            !isLocationServicesEnabled(context) -> { openLocationSettings(); say(sLocationNeeded) }
            else -> { pendingAutoStart = false; startMonitorReady() }
        }
    }

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
                if (pendingAutoStart) {
                    if (latestState.deviceAddress != null && hasLocationPermission(context) && hasBluetoothPermission(context) &&
                        isBluetoothEnabled(context) && isLocationServicesEnabled(context)
                    ) {
                        pendingAutoStart = false
                        startMonitorReady()
                    } else if (!isLocationServicesEnabled(context)) {
                        // Came back from Location settings without turning it on: cancel
                        // the request so AutoPark can't switch on during a later resume.
                        pendingAutoStart = false
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
    var pendingRestore by remember { mutableStateOf<Backup.ReadResult.Ok?>(null) }
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
                is Backup.ReadResult.Ok -> pendingRestore = r
                Backup.ReadResult.NotABackup -> say(sBackupInvalid)
                Backup.ReadResult.TooNew -> say(sBackupTooNew)
            }
        }
    }

    val notificationsChecked = fuel.notificationsEnabled && systemNotificationsAllowed
    var hasCrashReport by remember { mutableStateOf(ParkedApplication.crashFile(context).exists()) }

    val carSwatch = carColours.firstOrNull { it.first == fuel.accentName }?.second ?: carColours[4].second
    val autoParkLabel = stringResource(
        when {
            !state.monitoring -> R.string.autopark_off
            autoParkMode == AutoParkMode.Limited -> R.string.autopark_on_limited
            else -> R.string.autopark_on
        }
    )

    Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState())) {
        // The car, in its colour, on a dark panel. Tap to change the colour.
        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .fillMaxWidth()
                .height(240.dp)
                .background(Panel)
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { showAppearance = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Ph.carProfile,
                contentDescription = stringResource(R.string.cd_car_preview, colourLabel(fuel.accentName)),
                tint = visibleOnPanel(carSwatch),
                modifier = Modifier.size(240.dp)
            )
        }

        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
            PageTitle(state.deviceName ?: stringResource(R.string.home_car_title))
            Spacer(Modifier.height(4.dp))
            Text(
                "${colourLabel(fuel.accentName)} · $autoParkLabel",
                fontSize = TypeScale.Label, lineHeight = 22.sp, color = Muted
            )
            Spacer(Modifier.height(20.dp))
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
                DividerRow()
                TextRow(
                    stringResource(R.string.odometer),
                    fuel.currentOdo.takeIf { it > 0 }?.let { formatKm(it) } ?: stringResource(R.string.not_set)
                ) { showOdo = true }
                DividerRow()
                TextRow(
                    stringResource(R.string.tank_capacity),
                    fuel.tankCapacity.takeIf { it > 0 }?.let { "${formatDecimal(it, 0)} L" } ?: stringResource(R.string.not_set)
                ) { showTank = true }
                DividerRow()
                TextRow(stringResource(R.string.car_appearance), colourLabel(fuel.accentName)) { showAppearance = true }
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
                if (hasCrashReport) {
                    DividerRow()
                    TextRow(stringResource(R.string.crash_report), stringResource(R.string.crash_report_share)) {
                        shareCrashReport(context)
                        hasCrashReport = false
                    }
                }
            }

            SectionTitle(stringResource(R.string.backup_section), modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            DividerRow()
            ActionRow(
                icon = Ph.export,
                title = stringResource(R.string.backup_export),
                subtitle = stringResource(R.string.backup_export_sub),
                onClick = {
                    val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    runCatching { exportLauncher.launch("parked-backup-$day.json") }
                }
            )
            ActionRow(
                icon = Ph.clockCounter,
                title = stringResource(R.string.backup_import),
                subtitle = stringResource(R.string.backup_import_sub),
                onClick = {
                    // Many file managers label .json files as plain text or binary.
                    runCatching { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
                }
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showOdo) OdometerSheet(fuel = fuel, onDismiss = { showOdo = false })
    if (showTank) TankSheet(fuel = fuel, onDismiss = { showTank = false })
    if (showAppearance) AppearanceDialog(fuel = fuel, onDismiss = { showAppearance = false })
    pendingRestore?.let { r ->
        ConfirmDialog(
            visible = true,
            title = stringResource(R.string.backup_restore_title),
            message = stringResource(R.string.backup_restore_body, dateTime(context, r.exportedAt)),
            confirmLabel = stringResource(R.string.backup_restore_confirm),
            onDismiss = { pendingRestore = null },
            onConfirm = {
                pendingRestore = null
                scope.launch {
                    // Keep what was here, so the restore can be undone.
                    val before = Backup.build(store, fuel)
                    Backup.restore(context, r.data, store, fuel)
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(sBackupRestored, actionLabel = sUndo, duration = SnackbarDuration.Long, withDismissAction = true)
                    if (result == SnackbarResult.ActionPerformed) Backup.restore(context, before, store, fuel)
                }
            }
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
    val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    val canRead = hasBluetoothPermission(context)
    val devices = remember {
        if (canRead) runCatching { adapter?.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList()) else emptyList()
    }
    val fallbackName = stringResource(R.string.car_bluetooth)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        shape = androidx.compose.ui.graphics.RectangleShape,
        titleContentColor = Paper,
        textContentColor = Muted,
        title = { Text(stringResource(R.string.choose_your_car), fontSize = 24.sp) },
        text = {
            Column {
                when {
                    !canRead -> Text(stringResource(R.string.bluetooth_permission_needed))
                    devices.isEmpty() -> Text(stringResource(R.string.no_paired_devices))
                    else -> LazyColumn {
                        item { DividerRow() }
                        items(devices, key = { it.address }) { device ->
                            val name = runCatching { device.name }.getOrNull()
                            ActionRow(
                                icon = Ph.bluetooth,
                                title = name ?: stringResource(R.string.unnamed_device),
                                subtitle = device.address,
                                onClick = { onSelected(name ?: fallbackName, device.address) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, shape = androidx.compose.ui.graphics.RectangleShape) { Text(stringResource(R.string.close), fontSize = TypeScale.Body, color = Paper) } }
    )
}

private val carColours = listOf(
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
private fun colourLabel(name: String): String = stringResource(
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
private fun AppearanceDialog(fuel: FuelStore, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        shape = androidx.compose.ui.graphics.RectangleShape,
        title = { Text(stringResource(R.string.car_appearance), fontSize = 24.sp, color = Paper) },
        text = {
            Column {
                Text(stringResource(R.string.appearance_hint), fontSize = TypeScale.Body, lineHeight = 24.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 430.dp)) {
                    items(carColours, key = { it.first }) { (name, swatch) ->
                        val selected = fuel.accentName == name
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (selected) Field else Color.Transparent)
                                .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton) {
                                    fuel.setAccent(name); onDismiss()
                                }
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(20.dp).border(1.dp, Line).background(swatch))
                            Spacer(Modifier.width(16.dp))
                            Text(colourLabel(name), modifier = Modifier.weight(1f), fontSize = TypeScale.Emphasis, color = Paper)
                            if (selected) Icon(Ph.check, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, shape = androidx.compose.ui.graphics.RectangleShape) { Text(stringResource(R.string.close), fontSize = TypeScale.Body, color = Paper) } }
    )
}

@Composable
private fun OdometerSheet(fuel: FuelStore, onDismiss: () -> Unit) {
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

@Composable
internal fun TankSheet(fuel: FuelStore, onDismiss: () -> Unit) {
    var tank by remember { mutableStateOf(fuel.tankCapacity.takeIf { it > 0 }?.let { formatDecimal(it, 0) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val eInvalid = stringResource(R.string.error_tank)
    val eHigh = stringResource(R.string.error_tank_high)
    ParkedSheet(title = stringResource(R.string.tank_capacity), subtitle = stringResource(R.string.tank_subtitle), onDismiss = onDismiss) {
        NumberField(tank, { tank = it; error = null }, stringResource(R.string.tank_label))
        ErrorText(error)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.save), onClick = {
            val parsed = parseNumber(tank)
            error = when {
                parsed == null || parsed <= 0 -> eInvalid
                parsed > 300 -> eHigh
                else -> { fuel.updateTankCapacity(parsed); onDismiss(); null }
            }
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
                    Icon(Ph.trash, contentDescription = stringResource(R.string.history_clear), tint = Paper, modifier = Modifier.size(24.dp))
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
            Text(label, fontSize = TypeScale.Emphasis, lineHeight = 24.sp, color = Paper)
            if (!entry.note.isNullOrBlank()) Text(entry.note, fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = Muted)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(Ph.trash, contentDescription = stringResource(R.string.delete), tint = Muted, modifier = Modifier.size(24.dp))
        }
    }
}

/** Keeps a black or dark grey car visible on the dark panel. */
private fun visibleOnPanel(c: Color): Color =
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
