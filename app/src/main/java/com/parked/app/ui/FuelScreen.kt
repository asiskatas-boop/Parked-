package com.parked.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.parked.app.R
import com.parked.app.data.FuelLevelLog
import com.parked.app.data.FuelStore
import com.parked.app.data.ParkingStore
import com.parked.app.data.MonthlySpend
import com.parked.app.data.Refuel
import com.parked.app.data.ReminderStatus
import com.parked.app.data.ServiceReminder
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class CarPage { Main, Refuels, Service }

/**
 * The Car tab: what the car needs (ΚΤΕΟ, oil, tyres) and what it costs
 * (refuels). Service comes first because nothing else in Greece reminds you.
 */
@Composable
fun FuelScreen(fuel: FuelStore, store: ParkingStore, openServiceRequest: Boolean, onServiceRequestHandled: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(CarPage.Main) }

    LaunchedEffect(openServiceRequest) {
        if (openServiceRequest) {
            page = CarPage.Service
            onServiceRequestHandled()
        }
    }

    if (page != CarPage.Main) BackHandler { page = CarPage.Main }
    androidx.compose.animation.AnimatedContent(
        targetState = page,
        transitionSpec = { pageTransition(forward = targetState != CarPage.Main) },
        label = "carPages"
    ) { current ->
        when (current) {
            CarPage.Main -> CarMain(fuel, store, onOpen = { page = it })
            CarPage.Refuels -> RefuelHistoryPage(fuel, onBack = { page = CarPage.Main })
            CarPage.Service -> ServiceRemindersPage(fuel, onBack = { page = CarPage.Main })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarMain(fuel: FuelStore, store: ParkingStore, onOpen: (CarPage) -> Unit) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val state by store.state.collectAsState(initial = null)
    var showRefuel by remember { mutableStateOf(false) }
    var showOdo by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showLevel by remember { mutableStateOf(false) }
    val msgRefuel = stringResource(R.string.refuel_saved)
    val swatch = carColours.firstOrNull { it.first == fuel.accentName }?.second ?: carColours[4].second
    val sorted = fuel.reminders.sortedByDescending { fuel.progress(it).status.ordinal }
    val cdCar = stringResource(R.string.cd_car_preview, colourLabel(fuel.accentName))

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
    ) {
        // Name, then colour and odometer. The Bluetooth name (often a head
        // unit like "SYNC") is the title only because the user has no other.
        PageTitle(state?.deviceName ?: stringResource(R.string.home_car_title), modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            listOfNotNull(colourLabel(fuel.accentName), fuel.currentOdo.takeIf { it > 0 }?.let { formatKm(it) }).joinToString(" · "),
            fontSize = TypeScale.Label, lineHeight = 22.sp, color = TextMuted,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp)
        )
        Spacer(Modifier.height(16.dp))

        // The car, in its colour, on a studio floor. Tap to change the colour.
        CarStudio(
            swatch,
            modifier = Modifier
                .clip(RadiusCard)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.car_appearance)) { showAppearance = true }
                .semantics { contentDescription = cdCar },
        )

        // What needs attention, as chips: the two most urgent reminders.
        if (sorted.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sorted.take(2).forEach { r ->
                    val st = fuel.progress(r).status
                    StatusChip(
                        "${r.name} · ${statusWord(st)}",
                        if (st == ReminderStatus.Ok) ChipKind.Ok else ChipKind.Warn,
                        icon = if (st == ReminderStatus.Ok) Ph.checkCircle else Ph.warning,
                    )
                }
            }
        }

        // ---- Fuel level ----
        Spacer(Modifier.height(16.dp))
        FuelGaugeCard(fuel, onCorrect = { showLevel = true })

        // ---- Service and ΚΤΕΟ first ----
        Spacer(Modifier.height(28.dp))
        SectionTitle(
            stringResource(R.string.car_section_service),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            action = if (sorted.isEmpty()) null else stringResource(if (sorted.size > 3) R.string.service_all else R.string.service_manage),
            onAction = { onOpen(CarPage.Service) },
        )
        SettingsCard {
            if (sorted.isEmpty()) {
                ActionRow(
                    icon = Ph.wrench,
                    title = stringResource(R.string.service_add),
                    subtitle = stringResource(R.string.service_row_empty),
                    onClick = { onOpen(CarPage.Service) }
                )
            } else {
                sorted.take(3).forEachIndexed { i, r ->
                    if (i > 0) DividerRow()
                    val (statusText, _) = reminderStatus(fuel, r)
                    val st = fuel.progress(r).status
                    ActionRow(
                        icon = when (st) {
                            ReminderStatus.Due -> Ph.warning
                            ReminderStatus.Soon -> Ph.clockCounter
                            ReminderStatus.Ok -> Ph.wrench
                        },
                        iconTint = if (st == ReminderStatus.Ok) Green else Warn,
                        title = r.name,
                        subtitle = statusText.substringAfter(" · "),
                        onClick = { onOpen(CarPage.Service) },
                        trailing = {
                            StatusChip(statusWord(st), when (st) {
                                ReminderStatus.Due -> ChipKind.Warn
                                ReminderStatus.Soon -> ChipKind.Warn
                                ReminderStatus.Ok -> ChipKind.Ok
                            })
                        },
                    )
                }
            }
        }

        // ---- Refuels: one big number, the bars, the action ----
        Spacer(Modifier.height(28.dp))
        SectionTitle(
            stringResource(R.string.refuels),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            action = if (fuel.refuels.isEmpty()) null else stringResource(R.string.refuel_history_short),
            onAction = { onOpen(CarPage.Refuels) },
        )
        ParkedCard {
            Column(Modifier.padding(16.dp)) {
                SpendingBlock(fuel)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = stringResource(R.string.refuel), icon = Ph.gasPump, onClick = { showRefuel = true })
            }
            if (fuel.refuels.isNotEmpty()) {
                DividerRow()
                val latest = fuel.refuels.first()
                ActionRow(
                    icon = Ph.clockCounter,
                    title = stringResource(R.string.refuel_history),
                    subtitle = stringResource(
                        R.string.refuel_history_summary,
                        shortDate(latest.date), formatDecimal(latest.litres, 1), fuel.refuels.size
                    ),
                    onClick = { onOpen(CarPage.Refuels) }
                )
            }
        }

        // ---- The car itself ----
        Spacer(Modifier.height(28.dp))
        SettingsCard {
            TextRow(
                stringResource(R.string.odometer),
                fuel.currentOdo.takeIf { it > 0 }?.let { formatKm(it) } ?: stringResource(R.string.not_set),
                icon = Ph.road,
            ) { showOdo = true }
            DividerRow()
            TextRow(stringResource(R.string.car_appearance), colourLabel(fuel.accentName), icon = Ph.car) { showAppearance = true }
        }
    }

    if (showRefuel) {
        RefuelSheet(
            fuel = fuel,
            onDismiss = { showRefuel = false },
            onSaved = {
                showRefuel = false
                scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msgRefuel) }
            }
        )
    }
    if (showLevel) FuelLevelSheet(fuel = fuel, onDismiss = { showLevel = false })
    if (showOdo) OdometerSheet(fuel = fuel, onDismiss = { showOdo = false })
    if (showAppearance) AppearanceDialog(fuel = fuel, onDismiss = { showAppearance = false })
}

@Composable
private fun statusWord(st: ReminderStatus): String = stringResource(
    when (st) {
        ReminderStatus.Due -> R.string.service_status_due
        ReminderStatus.Soon -> R.string.service_status_soon
        ReminderStatus.Ok -> R.string.service_status_ok
    }
)

/**
 * The fuel gauge, back from 0.4: the estimated level as one big number, the
 * range it gives, a bar with E-to-F marks, and "Correct" to match the car.
 */
@Composable
private fun FuelGaugeCard(fuel: FuelStore, onCorrect: () -> Unit) {
    val set = fuel.levelUpdatedAt > 0L
    val pct = fuel.estimateLevelPct().coerceIn(0.0, 100.0)
    val range = fuel.estimatedRangeKm()?.let { it * pct / 100.0 }
    val low = set && pct <= 15.0
    val shown by androidx.compose.animation.core.animateFloatAsState((if (set) pct else 0.0).toFloat() / 100f, Motion.spatial(), label = "gauge")
    ParkedCard {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ph.gasPump, contentDescription = null, tint = if (low) Warn else Green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Overline(stringResource(R.string.fuel_level_title), modifier = Modifier.weight(1f))
                LinkButton(stringResource(if (set) R.string.fuel_correct else R.string.fuel_set), onClick = onCorrect)
            }
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.Bottom) {
                BigNumber(if (set) "${pct.roundToInt()}%" else "–", size = 44.sp, color = if (low) Warn else TextPrimary)
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        !set -> stringResource(R.string.fuel_not_set)
                        range != null -> stringResource(R.string.fuel_range, formatKm(range))
                        else -> stringResource(R.string.fuel_range_unknown)
                    },
                    fontSize = TypeScale.Supporting, lineHeight = 20.sp, color = TextMuted,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            val levelText = if (set) "${pct.roundToInt()}%" else stringResource(R.string.fuel_not_set)
            Canvas(
                Modifier
                    .padding(end = 8.dp)
                    .fillMaxWidth()
                    .height(10.dp)
                    .semantics { contentDescription = levelText }
            ) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(SurfaceSunk, cornerRadius = r)
                if (shown > 0f) drawRoundRect(if (low) Warn else Green, size = Size(size.width * shown, size.height), cornerRadius = r)
                for (q in 1..3) {
                    val x = size.width * q / 4f
                    drawLine(Surface, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.padding(end = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("E", "¼", "½", "¾", "F").forEach {
                    Text(it, fontSize = TypeScale.Caption, lineHeight = 16.sp, color = TextFaint, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Match the car's gauge by dragging; the tank size makes the range possible. */
@Composable
private fun FuelLevelSheet(fuel: FuelStore, onDismiss: () -> Unit) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current
    var level by remember { mutableFloatStateOf(fuel.estimateLevelPct().toFloat().coerceIn(0f, 100f).let { (it / 5f).roundToInt() * 5f }) }
    var tank by remember { mutableStateOf(fuel.tankCapacity.takeIf { it > 0 }?.roundToInt()?.toString() ?: "") }
    val msgSaved = stringResource(R.string.fuel_level_saved)
    val msgUndo = stringResource(R.string.undo)
    ParkedSheet(title = stringResource(R.string.fuel_level_title), subtitle = stringResource(R.string.fuel_sheet_subtitle), onDismiss = onDismiss) {
        BigNumber("${level.roundToInt()}%", size = 44.sp)
        Slider(
            value = level,
            onValueChange = {
                val snapped = (it / 5f).roundToInt() * 5f
                if (snapped != level) Haptics.tick(view)
                level = snapped
            },
            valueRange = 0f..100f,
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = Green,
                activeTrackColor = Green,
                inactiveTrackColor = SurfaceSunk,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("E", "¼", "½", "¾", "F").forEach {
                Text(it, fontSize = TypeScale.Caption, color = TextMuted, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
        NumberField(
            tank, { tank = it }, stringResource(R.string.fuel_tank_size), decimal = false,
            supporting = stringResource(R.string.fuel_tank_hint)
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.save)) {
            val before = Triple(fuel.levelPct, fuel.odoForLevel, fuel.levelUpdatedAt)
            parseNumber(tank)?.takeIf { it in 5.0..200.0 }?.let { fuel.updateTankCapacity(it) }
            fuel.setLevel(level.toDouble())
            Haptics.confirm(view)
            onDismiss()
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(msgSaved, actionLabel = msgUndo, withDismissAction = true)
                if (result == SnackbarResult.ActionPerformed) fuel.restoreLevel(before.first, before.second, before.third)
            }
        }
        QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
    }
}

/** A reminder's status in words plus its colour; words first, never colour alone. */
@Composable
private fun reminderStatus(fuel: FuelStore, reminder: ServiceReminder): Pair<String, Color> {
    val p = fuel.progress(reminder)
    val status = when (p.status) {
        ReminderStatus.Due -> stringResource(R.string.service_status_due)
        ReminderStatus.Soon -> stringResource(R.string.service_status_soon)
        ReminderStatus.Ok -> stringResource(R.string.service_status_ok)
    }
    val detail = listOfNotNull(
        p.kmLeft?.let {
            if (it > 0) stringResource(R.string.service_km_left, formatKm(it))
            else stringResource(R.string.service_km_over, formatKm(-it))
        },
        p.dueAt?.let { stringResource(R.string.service_due_on, shortDateYear(it)) },
    ).joinToString(" · ").ifEmpty { stringResource(R.string.service_no_odometer_short) }
    val color = when (p.status) {
        ReminderStatus.Due -> Danger
        ReminderStatus.Soon -> Warn
        ReminderStatus.Ok -> TextMuted
    }
    return "$status · $detail" to color
}

@Composable
private fun FuelStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = TypeScale.Section, lineHeight = 26.sp, color = TextPrimary)
    }
}

// ============================================================
// Monthly spending
// ============================================================

/** This month's spend as the big number, the average beside it, then six months of bars. */
@Composable
private fun SpendingBlock(fuel: FuelStore) {
    val months = remember(fuel.refuels) { fuel.monthlySpend(6).reversed() } // oldest → newest
    if (fuel.refuels.isEmpty() || months.isEmpty() || months.all { it.cost <= 0.0 }) {
        // Information only, so plain text, not something that looks tappable.
        Row(verticalAlignment = Alignment.Top) {
            Icon(Ph.info, contentDescription = null, tint = TextMuted, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.fuel_insights_body), fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = TextMuted)
        }
        return
    }
    var selected by remember(months) { mutableIntStateOf(months.lastIndex) }
    val current = months[selected]
    val average = fuel.averageL100km()
    Overline(monthNameLong(current.year, current.month))
    Row(verticalAlignment = Alignment.Bottom) {
        androidx.compose.animation.AnimatedContent(
            targetState = formatMoney(fuel.currency, current.cost),
            transitionSpec = { androidx.compose.animation.fadeIn(Motion.effects()).togetherWith(androidx.compose.animation.fadeOut(Motion.effects())) },
            label = "spend",
            modifier = Modifier.weight(1f),
        ) { BigNumber(it, size = 40.sp) }
        if (average != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                Text("${formatDecimal(average, 1)} L/100 km", fontSize = TypeScale.Label, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(stringResource(R.string.fuel_average), fontSize = TypeScale.Caption, color = TextMuted)
            }
        }
    }
    Text(
        stringResource(R.string.spend_litres, formatDecimal(current.litres, 0)),
        fontSize = TypeScale.Caption, lineHeight = 18.sp, color = TextMuted
    )
    Spacer(Modifier.height(14.dp))
    SpendBars(months = months, selected = selected, currency = fuel.currency, onSelect = { selected = it })
}

/**
 * Six monthly columns. The selected month is orange and carries the only
 * value label; tapping another month moves it there (and into the summary).
 */
@Composable
private fun SpendBars(months: List<MonthlySpend>, selected: Int, currency: String, onSelect: (Int) -> Unit) {
    val max = months.maxOf { it.cost }.coerceAtLeast(1.0)
    val summary = months.joinToString(", ") { "${monthName(it.year, it.month)} ${formatMoney(currency, it.cost)}" }
    Row(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics(mergeDescendants = false) { contentDescription = summary },
        verticalAlignment = Alignment.Bottom
    ) {
        months.forEachIndexed { i, m ->
            val isSelected = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(role = Role.Button) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    if (isSelected && m.cost > 0) formatMoney(currency, m.cost) else "",
                    fontSize = TypeScale.Caption, color = TextPrimary, maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                val fraction = (m.cost / max).toFloat()
                val grown by androidx.compose.animation.core.animateFloatAsState(fraction, Motion.spatial(), label = "bar")
                Canvas(Modifier.width(22.dp).weight(1f)) {
                    val h = (size.height * grown).coerceAtLeast(if (m.cost > 0) 4.dp.toPx() else 0f)
                    if (h > 0f) drawRoundRect(
                        if (isSelected) Green else SurfaceSunk,
                        Offset(0f, size.height - h), Size(size.width, h),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(monthName(m.year, m.month), fontSize = TypeScale.Caption, color = if (isSelected) TextPrimary else TextMuted)
            }
        }
    }
}

// ============================================================
// Refuels
// ============================================================

@Composable
private fun RefuelHistoryPage(fuel: FuelStore, onBack: () -> Unit) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Refuel?>(null) }
    SubPage(
        title = stringResource(R.string.refuels),
        onBack = onBack,
        emptyText = if (fuel.refuels.isEmpty()) stringResource(R.string.refuels_empty) else null,
        actions = {
            if (fuel.refuels.isNotEmpty()) {
                IconButton(onClick = { exportCsv(context, fuel) }) {
                    Icon(Ph.export, contentDescription = stringResource(R.string.export_csv), tint = TextPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }
    ) {
        itemsIndexed(fuel.refuels, key = { _, e -> e.date }) { idx, entry ->
            RefuelRow(entry = entry, currency = fuel.currency, index = idx, onDelete = { pendingDelete = entry })
        }
    }
    ConfirmDialog(
        visible = pendingDelete != null,
        title = stringResource(R.string.delete_refuel_title),
        message = stringResource(R.string.delete_refuel_body),
        confirmLabel = stringResource(R.string.delete),
        onDismiss = { pendingDelete = null },
        onConfirm = {
            pendingDelete?.let { fuel.deleteRefuel(it.date) }
            pendingDelete = null
        }
    )
}

@Composable
private fun RefuelRow(entry: Refuel, currency: String, index: Int, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(shortDateYear(entry.date), fontSize = TypeScale.Emphasis, lineHeight = 24.sp, color = TextPrimary)
            val details = listOfNotNull(
                entry.odometer.takeIf { it > 0 }?.let { formatKm(it) },
                stringResource(if (entry.tankFull) R.string.refuel_full else R.string.refuel_partial)
            ).joinToString(" · ")
            Text(details, fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = TextMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${formatDecimal(entry.litres, 1)} L", fontSize = TypeScale.Emphasis, lineHeight = 24.sp, color = TextPrimary)
            Text(formatMoney(currency, entry.cost), fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = TextMuted)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(Ph.trash, contentDescription = stringResource(R.string.delete_refuel_title), tint = TextMuted, modifier = Modifier.size(24.dp))
        }
    }
    DividerRow(Modifier.padding(horizontal = 24.dp))
}

private fun exportCsv(context: Context, fuel: FuelStore) {
    runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "parked-refuels.csv")
        file.writeText(fuel.refuelsCsv())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.export_csv)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// ============================================================
// Sheets
// ============================================================

/**
 * People know what they paid and the pump price, not the litres, so those are
 * the two inputs; litres are worked out from them.
 */
@Composable
private fun RefuelSheet(fuel: FuelStore, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var totalPaid by remember { mutableStateOf("") }
    var price by remember { mutableStateOf(fuel.latestPricePerLitre()?.let { formatDecimal(it, 3) } ?: "") }
    // Left empty on purpose: a pre-filled GPS estimate saved unchecked would
    // become a "real" reading and skew average consumption. It is a hint instead.
    var odometer by remember { mutableStateOf("") }
    // Off by default: most stops are not a brim-full fill-up, and a wrong "full"
    // corrupts the consumption average more than a missed one.
    var fullTank by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val eTotal = stringResource(R.string.error_total)
    val ePrice = stringResource(R.string.error_price)
    val eOdo = stringResource(R.string.error_odometer)
    val eOdoLow = stringResource(R.string.error_odometer_low)
    val eOdoFull = stringResource(R.string.error_odometer_full)
    val eGeneric = stringResource(R.string.error_refuel)

    val paidValue = parseNumber(totalPaid)?.takeIf { it > 0 }
    val priceValue = parseNumber(price)?.takeIf { it > 0 }
    val litres = if (paidValue != null && priceValue != null) paidValue / priceValue else null

    ParkedSheet(
        title = stringResource(R.string.refuel),
        subtitle = stringResource(R.string.refuel_subtitle),
        onDismiss = onDismiss
    ) {
        NumberField(totalPaid, { totalPaid = it; errorText = null }, stringResource(R.string.refuel_total, fuel.currency))
        Spacer(Modifier.height(12.dp))
        NumberField(
            price, { price = it; errorText = null },
            stringResource(R.string.refuel_price, fuel.currency),
            supporting = litres?.takeIf { it.isFinite() }?.let { stringResource(R.string.refuel_litres_calc, formatDecimal(it, 1)) }
                ?: if (fuel.latestPricePerLitre() != null) stringResource(R.string.trip_price_learned) else null
        )
        Spacer(Modifier.height(4.dp))
        NumberField(
            odometer, { odometer = it; errorText = null },
            // A full fill-up is only useful for the average with a reading.
            stringResource(if (fullTank) R.string.refuel_odometer_required else R.string.refuel_odometer),
            decimal = false,
            placeholder = fuel.currentOdo.takeIf { it > 0 }?.let { stringResource(R.string.refuel_odometer_hint, formatKm(it)) },
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { fullTank = !fullTank }
                .heightIn(min = 56.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.refuel_full_tank), fontSize = TypeScale.Emphasis, lineHeight = 24.sp, color = TextPrimary)
                Text(stringResource(R.string.refuel_full_tank_hint), fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = TextMuted)
            }
            ParkedSwitch(checked = fullTank, onCheckedChange = null)
        }
        ErrorText(errorText)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.save_refuel), onClick = {
            val o = if (odometer.isBlank()) 0.0 else parseNumber(odometer)
            errorText = when {
                paidValue == null -> eTotal
                priceValue == null || priceValue > 20 -> ePrice
                o == null || o < 0 -> eOdo
                fullTank && o <= 0 -> eOdoFull
                o > 0 && fuel.currentOdo > 0 && o + 1.0 < fuel.currentOdo -> eOdoLow
                !fuel.logRefuel(litres = paidValue / priceValue, cost = paidValue, odometer = o, tankFull = fullTank) -> eGeneric
                else -> { onSaved(); null }
            }
        })
        QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
    }
}

// ============================================================
// Service reminders
// ============================================================

@Composable
private fun ServiceRemindersPage(fuel: FuelStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ServiceReminder?>(null) }
    var adding by remember { mutableStateOf(false) }
    val msgDeleted = stringResource(R.string.service_deleted)
    val msgDone = stringResource(R.string.service_marked_done)
    val msgUndo = stringResource(R.string.undo)

    Box(Modifier.fillMaxSize()) {
        SubPage(
            title = stringResource(R.string.service_title),
            onBack = onBack,
            emptyText = if (fuel.reminders.isEmpty()) stringResource(R.string.service_empty) else null,
        ) {
            if (fuel.currentOdo <= 0 && fuel.reminders.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.service_needs_odometer),
                        fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = TextMuted,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
            val sorted = fuel.reminders.sortedByDescending { fuel.progress(it).status.ordinal }
            itemsIndexed(sorted, key = { _, r -> r.id }) { _, r ->
                ReminderRow(
                    fuel = fuel,
                    reminder = r,
                    onEdit = { editing = r },
                    onDone = {
                        val before = r
                        fuel.markReminderDone(r.id)
                        scope.launch {
                            val result = snackbar.showSnackbar(msgDone, actionLabel = msgUndo, withDismissAction = true)
                            if (result == SnackbarResult.ActionPerformed) fuel.updateReminder(before)
                        }
                    }
                )
            }
            item {
                Box(Modifier.padding(24.dp)) {
                    PrimaryButton(text = stringResource(R.string.service_add), onClick = { adding = true })
                }
            }
        }
    }

    if (adding || editing != null) {
        ReminderSheet(
            fuel = fuel,
            existing = editing,
            onDismiss = { adding = false; editing = null },
            onDelete = { r ->
                editing = null
                fuel.deleteReminder(r.id)
                scope.launch {
                    val result = snackbar.showSnackbar(msgDeleted, actionLabel = msgUndo, withDismissAction = true)
                    if (result == SnackbarResult.ActionPerformed) fuel.restoreReminder(r)
                }
            },
        )
    }
}

@Composable
private fun ReminderRow(fuel: FuelStore, reminder: ServiceReminder, onEdit: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val p = fuel.progress(reminder)
    val statusText = when (p.status) {
        ReminderStatus.Due -> stringResource(R.string.service_status_due)
        ReminderStatus.Soon -> stringResource(R.string.service_status_soon)
        ReminderStatus.Ok -> stringResource(R.string.service_status_ok)
    }
    val detail = listOfNotNull(
        p.kmLeft?.let {
            if (it > 0) stringResource(R.string.service_km_left, formatKm(it))
            else stringResource(R.string.service_km_over, formatKm(-it))
        },
        p.dueAt?.let { stringResource(R.string.service_due_on, shortDateYear(it)) },
    ).joinToString(" · ").ifEmpty { stringResource(R.string.service_no_odometer_short) }
    val statusColor = when (p.status) {
        ReminderStatus.Due -> Danger
        ReminderStatus.Soon -> Warn
        ReminderStatus.Ok -> TextMuted
    }
    Box(Modifier.clickable(role = Role.Button, onClick = onEdit)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(reminder.name, fontSize = TypeScale.Emphasis, lineHeight = 24.sp, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status is spelled out, never colour alone.
                    Icon(
                        when (p.status) {
                            ReminderStatus.Due -> Ph.warning
                            ReminderStatus.Soon -> Ph.clockCounter
                            ReminderStatus.Ok -> Ph.checkCircle
                        },
                        contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(statusText, fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = statusColor)
                }
                Text(detail, fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = TextMuted)
                Text(
                    stringResource(R.string.service_last_done, shortDateYear(reminder.lastDoneAt)),
                    fontSize = TypeScale.Caption, lineHeight = 20.sp, color = TextMuted
                )
            }
            if (p.status != ReminderStatus.Ok) {
                SecondaryButton(stringResource(R.string.service_done), icon = Ph.check, onClick = onDone)
            }
        }
    }
    DividerRow(Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun ReminderSheet(
    fuel: FuelStore,
    existing: ServiceReminder?,
    onDismiss: () -> Unit,
    onDelete: (ServiceReminder) -> Unit,
) {
    val presets = listOf(
        stringResource(R.string.service_preset_oil) to (15000 to 12),
        stringResource(R.string.service_preset_tyres) to (10000 to null),
        stringResource(R.string.service_preset_inspection) to (null to 24),
        stringResource(R.string.service_preset_brakes) to (30000 to null),
    )
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var km by remember { mutableStateOf(existing?.intervalKm?.toString() ?: "") }
    var months by remember { mutableStateOf(existing?.intervalMonths?.toString() ?: "") }
    var lastOdo by remember {
        mutableStateOf(
            (existing?.lastDoneOdo ?: fuel.currentOdo).takeIf { it > 0 }?.roundToInt()?.toString() ?: ""
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val eName = stringResource(R.string.service_error_name)
    val eInterval = stringResource(R.string.service_error_interval)

    ParkedSheet(
        title = stringResource(if (existing == null) R.string.service_add else R.string.service_edit),
        subtitle = stringResource(R.string.service_sheet_subtitle),
        onDismiss = onDismiss
    ) {
        if (existing == null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (label, interval) ->
                    FilterChip(
                        selected = name == label,
                        onClick = {
                            name = label
                            km = interval.first?.toString() ?: ""
                            months = interval.second?.toString() ?: ""
                            error = null
                        },
                        label = { Text(label, fontSize = TypeScale.Label) },
                        shape = RadiusSmall,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            labelColor = TextPrimary,
                            selectedContainerColor = Green,
                            selectedLabelColor = OnGreen,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = name == label,
                            borderColor = Border, selectedBorderColor = Green,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40); error = null },
            label = { Text(stringResource(R.string.service_name)) },
            singleLine = true,
            shape = RadiusSmall,
            colors = parkedFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = BodyFont, fontSize = TypeScale.Emphasis),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(km, { km = it; error = null }, stringResource(R.string.service_every_km), Modifier.weight(1f), decimal = false)
            NumberField(months, { months = it; error = null }, stringResource(R.string.service_every_months), Modifier.weight(1f), decimal = false)
        }
        Spacer(Modifier.height(12.dp))
        NumberField(
            lastOdo, { lastOdo = it }, stringResource(R.string.service_last_odo), decimal = false,
            supporting = stringResource(R.string.service_last_odo_hint)
        )
        ErrorText(error)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.save), onClick = {
            val k = km.trim().toIntOrNull()?.takeIf { it > 0 }
            val m = months.trim().toIntOrNull()?.takeIf { it in 1..120 }
            val odo = parseNumber(lastOdo) ?: 0.0
            when {
                name.isBlank() -> error = eName
                k == null && m == null -> error = eInterval
                existing == null -> {
                    fuel.addReminder(name, k, m, odo, System.currentTimeMillis())
                    onDismiss()
                }
                else -> {
                    fuel.updateReminder(
                        existing.copy(name = name.trim(), intervalKm = k, intervalMonths = m, lastDoneOdo = odo)
                    )
                    onDismiss()
                }
            }
        })
        if (existing != null) {
            QuietButton(stringResource(R.string.delete), color = Danger) { onDelete(existing) }
        }
        QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
    }
}
