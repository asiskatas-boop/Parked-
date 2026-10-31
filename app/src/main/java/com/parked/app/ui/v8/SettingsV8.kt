package com.parked.app.ui.v8

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parked.app.R
import com.parked.app.ui.Haptics

/** Grey of the Preferences card (Figma #DFDFDF). */
private val PrefsGrey = Color(0xFFDFDFDF)

/**
 * Settings, 0.8 design (Figma frame "Settings", 393 x 869). Coordinates in
 * the comments are Figma's.
 */
@Composable
fun SettingsV8(
    autoParkStatus: String,
    deviceName: String,
    odometer: String,
    notificationsOn: Boolean,
    historyOn: Boolean,
    historyCount: String,
    language: String?,
    version: String,
    onAutoPark: () -> Unit,
    onCarBt: () -> Unit,
    onOdometer: () -> Unit,
    onNotifications: (Boolean) -> Unit,
    onHistory: (Boolean) -> Unit,
    onSeeAll: () -> Unit,
    onLanguage: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    crashReport: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(White)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Title (baseline 58), AutoPark row (84-142).
        Box(Modifier.fillMaxWidth().height(84.dp)) {
            FigmaText(stringResource(R.string.v8_settings), x = 37f, baseline = 58f, size = 28f, weight = FontWeight.Bold, color = Black,
                modifier = Modifier.semantics { heading() })
        }
        SettingRow(DesignIcons.SetAutoPark, 20f, 21.27f, 17f, 18f, Black, stringResource(R.string.v8_autopark), autoParkStatus, onAutoPark)
        // "My car" (baseline 175), then Car BT (193-251) and Odometer (259-317).
        Box(Modifier.fillMaxWidth().height(51.dp)) {
            FigmaText(stringResource(R.string.v8_my_car_label), x = 35f, baseline = 33f, size = 15f, weight = FontWeight.Light, color = Black)
        }
        SettingRow(DesignIcons.Bluetooth, 17f, 20f, 18f, 19f, Navy, stringResource(R.string.v8_car_bt), deviceName, onCarBt, labelX = 51f)
        Spacer(Modifier.height(8.dp))
        SettingRow(DesignIcons.Gauge, 20f, 20f, 17f, 19f, Navy, stringResource(R.string.odometer), odometer, onOdometer, labelX = 51f)

        // "Preferences" (baseline 386, 20 light), the card (414-593).
        Box(Modifier.fillMaxWidth().height(97.dp)) {
            FigmaText(stringResource(R.string.settings_preferences), x = 37f, baseline = 69f, size = 20f, weight = FontWeight.Light, color = Black)
        }
        Column(
            Modifier
                .padding(start = 35.dp)
                .width(322.dp)
                .background(PrefsGrey, R15)
                .padding(bottom = 10.5.dp)
        ) {
            PrefRow(DesignIcons.Bell, 19f, 20f, 18f, stringResource(R.string.v8_notifications), top = 11.5f) {
                SwitchV8(notificationsOn, stringResource(R.string.v8_notifications), onNotifications)
            }
            PrefRow(DesignIcons.History, 18f, 18f, 19f, stringResource(R.string.v8_history), top = 0f) {
                SwitchV8(historyOn, stringResource(R.string.v8_history), onHistory)
            }
            AnimatedVisibility(historyOn) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 52.dp, end = 18.dp).height(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.v8_see_all),
                        style = designStyle(13.sp, FontWeight.Normal, Navy).copy(textDecoration = TextDecoration.Underline),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button, onClick = onSeeAll)
                            .padding(vertical = 6.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(historyCount, style = designStyle(13.sp, FontWeight.Normal, Ink), maxLines = 1)
                }
            }
            if (language != null) {
                Spacer(Modifier.height(if (historyOn) 17.dp else 6.dp))
                PrefRow(DesignIcons.Language, 20.32f, 11.62f, 19f, stringResource(R.string.v8_language), top = 0f, onClick = onLanguage) {
                    Text(language, style = designStyle(13.sp, FontWeight.Medium, Ink), maxLines = 1)
                }
            }
        }

        // "Backup" (baseline 636), Export and Import (654-712).
        Box(Modifier.fillMaxWidth().height(61.dp)) {
            FigmaText(stringResource(R.string.v8_backup), x = 35f, baseline = 43f, size = 15f, weight = FontWeight.Light, color = Black)
        }
        Row(Modifier.padding(start = 35.dp)) {
            BackupButton(DesignIcons.Export, 22f, 16f, stringResource(R.string.v8_export), onExport)
            Spacer(Modifier.width(34.dp))
            BackupButton(DesignIcons.Import, 22f, 15.9f, stringResource(R.string.v8_import), onImport)
        }
        // Version (baseline 761).
        Box(Modifier.fillMaxWidth().height(63.dp)) {
            FigmaText(stringResource(R.string.v8_version, version), x = 37f, baseline = 49f, size = 15f, weight = FontWeight.Medium, color = Black)
        }
        if (crashReport != null) LinkV8(stringResource(R.string.crash_report_share), Modifier.padding(start = 27.dp), onClick = crashReport)
        Spacer(Modifier.height(27.dp))
    }
}

/** Outlined row (322 x 58, 3 px grey) with icon, bold label and a value on the right. */
@Composable
private fun SettingRow(
    icon: ImageVector, w: Float, h: Float, ix: Float, iy: Float, tint: Color,
    label: String, value: String, onClick: () -> Unit, labelX: Float = 53f,
) {
    val view = LocalView.current
    Box(
        Modifier
            .padding(start = 35.dp)
            .size(322.dp, 58.dp)
            .pressable(R15) { Haptics.tick(view); onClick() }
            .border(3.dp, CardGrey, R15)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.offset(ix.dp, iy.dp).size(w.dp, h.dp))
        FigmaText(label, x = labelX, baseline = 34f, size = 13f, weight = FontWeight.Bold, color = Ink)
        Text(
            value, style = designStyle(13.sp, FontWeight.Normal, Ink), maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd).offset((-18).dp, topForBaseline(34f, 13f)).widthIn(max = 180.dp),
        )
    }
}

/** A row inside the Preferences card: icon, bold label, and a control on the right. */
@Composable
private fun PrefRow(
    icon: ImageVector, w: Float, h: Float, ix: Float, label: String, top: Float,
    onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .padding(top = top.dp)
            .fillMaxWidth()
            .height(42.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = ix.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width((52f - ix).dp)) { Icon(icon, null, tint = Navy, modifier = Modifier.size(w.dp, h.dp)) }
        Text(label, style = designStyle(13.sp, FontWeight.Bold, Ink), maxLines = 1, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun BackupButton(icon: ImageVector, w: Float, h: Float, label: String, onClick: () -> Unit) {
    val view = LocalView.current
    Row(
        Modifier
            .size(141.dp, 58.dp)
            .pressable(R15) { Haptics.tick(view); onClick() }
            .border(3.dp, CardGrey, R15),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Navy, modifier = Modifier.size(w.dp, h.dp))
        Spacer(Modifier.width(11.dp))
        Text(label, style = designStyle(13.sp, FontWeight.Bold, Ink), maxLines = 1)
    }
}

/** Sheet body for a yes/no question: title, text, the main button, and a quiet second choice. */
@Composable
fun ColumnScope.ConfirmSheetV8(
    title: String, body: String, confirm: String, onConfirm: () -> Unit,
    secondary: String? = null, onSecondary: (() -> Unit)? = null, destructive: Boolean = false,
) {
    SheetTitle(title)
    SheetSub(body)
    Spacer(Modifier.height(24.dp))
    PrimaryV8(confirm, onClick = onConfirm)
    if (secondary != null && onSecondary != null) Text(
        secondary,
        style = designStyle(15.sp, FontWeight.Light, if (destructive) ErrorRed else Navy),
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onSecondary)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/** One entry of the parking history page. */
data class HistoryItemV8(val at: Long, val title: String, val note: String?)

/** Parking history, full page with a back arrow; tap opens the spot in the maps app. */
@Composable
fun ParkingHistoryV8(
    visible: Boolean,
    items: List<HistoryItemV8>,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
) {
    if (visible) BackHandler(onBack = onBack)
    AnimatedVisibility(
        visible,
        enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it },
        exit = slideOutHorizontally(tween(280)) { it },
    ) {
        val view = LocalView.current
        Column(
            Modifier
                .fillMaxSize()
                .background(White)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 36.dp)
        ) {
            BackButton(onBack)
            Text(stringResource(R.string.history_title), style = designStyle(28.sp, FontWeight.Bold, Black), modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            Text(
                if (items.isEmpty()) stringResource(R.string.history_empty) else stringResource(R.string.v8_history_sub),
                style = designStyle(15.sp, FontWeight.Light, Black).copy(lineHeight = 20.sp),
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            )
            items.forEach { e ->
                Row(
                    Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth()
                        .height(64.dp)
                        .pressable(R15) { Haptics.tick(view); onOpen(e.at) }
                        .border(3.dp, CardGrey, R15)
                        .padding(start = 17.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = designStyle(13.sp, FontWeight.Bold, Ink), maxLines = 1)
                        if (!e.note.isNullOrBlank()) Text(e.note, style = designStyle(13.sp, FontWeight.Light, Ink), maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                    }
                    Text(
                        stringResource(R.string.v8_delete),
                        style = designStyle(13.sp, FontWeight.Normal, ErrorRed),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { onDelete(e.at) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                }
            }
            if (items.isNotEmpty()) LinkV8(stringResource(R.string.history_clear), Modifier.offset((-10).dp), onClick = onClear)
            Spacer(Modifier.height(120.dp))
        }
    }
}

/** Device picker sheet body: paired devices, cars first, as option rows. */
@Composable
fun ColumnScope.DeviceSheetV8(devices: List<Triple<String, String, Boolean>>, canRead: Boolean, current: String?, onSelected: (String, String) -> Unit) {
    SheetTitle(stringResource(R.string.v8_car_bluetooth_title))
    SheetSub(
        when {
            !canRead -> stringResource(R.string.bluetooth_permission_needed)
            devices.isEmpty() -> stringResource(R.string.no_paired_devices)
            else -> stringResource(R.string.v8_car_bluetooth_sub)
        }
    )
    if (devices.isNotEmpty()) {
        val car = stringResource(R.string.device_kind_car)
        val other = stringResource(R.string.device_kind_other)
        OptionsV8(
            items = devices.map { (name, _, isCar) -> name to (if (isCar) car else other) },
            selected = devices.indexOfFirst { it.second == current },
            onSelect = { i -> onSelected(devices[i].first, devices[i].second) },
        )
    }
    Spacer(Modifier.height(8.dp))
}

/** Sheet bodies used by the Settings screen (called as `with(Sheets) { ... }` inside a SheetV8). */
object Sheets {
    @Composable
    fun ColumnScope.ConfirmBody(
        title: String, body: String, confirm: String, onConfirm: () -> Unit,
        secondary: String? = null, onSecondary: (() -> Unit)? = null,
    ) = ConfirmSheetV8(title, body, confirm, onConfirm, secondary, onSecondary)

    @Composable
    fun ColumnScope.Odometer(fuel: com.parked.app.data.FuelStore, onSaved: () -> Unit) = OdometerForm(fuel, onSaved)

    @Composable
    fun ColumnScope.Devices(devices: List<Triple<String, String, Boolean>>, canRead: Boolean, current: String?, onSelected: (String, String) -> Unit) =
        DeviceSheetV8(devices, canRead, current, onSelected)
}
