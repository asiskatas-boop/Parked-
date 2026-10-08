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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import com.parked.app.data.MonthlySpend
import com.parked.app.data.Refuel
import com.parked.app.data.ReminderStatus
import com.parked.app.data.ServiceReminder
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class FuelPage { Main, Refuels, LevelChecks, Service }

@Composable
fun FuelScreen(fuel: FuelStore, openServiceRequest: Boolean, onServiceRequestHandled: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(FuelPage.Main) }

    LaunchedEffect(openServiceRequest) {
        if (openServiceRequest) {
            page = FuelPage.Service
            onServiceRequestHandled()
        }
    }

    if (page != FuelPage.Main) BackHandler { page = FuelPage.Main }
    androidx.compose.animation.AnimatedContent(
        targetState = page,
        transitionSpec = { pageTransition(forward = targetState != FuelPage.Main) },
        label = "fuelPages"
    ) { current ->
        when (current) {
            FuelPage.Main -> FuelMain(fuel, onOpen = { page = it })
            FuelPage.Refuels -> RefuelHistoryPage(fuel, onBack = { page = FuelPage.Main })
            FuelPage.LevelChecks -> LevelChecksPage(fuel, onBack = { page = FuelPage.Main })
            FuelPage.Service -> ServiceRemindersPage(fuel, onBack = { page = FuelPage.Main })
        }
    }
}

@Composable
private fun FuelMain(fuel: FuelStore, onOpen: (FuelPage) -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val configuredTank = fuel.tankCapacity.takeIf { it.isFinite() && it > 0 }
    val hasSavedFuelLevel = fuel.levelUpdatedAt > 0L

    // The gauge shows the saved level minus the fuel used since, so range drops
    // as you drive. Rounded to whole kilometres so GPS updates don't jitter it.
    val drivenKm = fuel.kmSinceLevelSet()?.roundToInt()
    val estimatedFraction = (fuel.estimateLevelPct() / 100.0).coerceIn(0.0, 1.0).toFloat()
    var sliderFraction by remember {
        mutableFloatStateOf(if (hasSavedFuelLevel) estimatedFraction else 0.5f)
    }
    var userAdjusting by remember { mutableStateOf(false) }
    LaunchedEffect(estimatedFraction, fuel.levelUpdatedAt) {
        if (hasSavedFuelLevel && !userAdjusting) sliderFraction = estimatedFraction
    }

    var showRefuel by remember { mutableStateOf(false) }
    var showTripCost by remember { mutableStateOf(false) }
    var showTank by remember { mutableStateOf(false) }
    val msgLevel = stringResource(R.string.fuel_level_updated)
    val msgRefuel = stringResource(R.string.refuel_saved)

    val litersInTank = configuredTank?.times(sliderFraction)
    val averageConsumption = fuel.averageL100km()
    val fullTankRangeKm = fuel.estimatedRangeKm()
    val rangeFromCurrentLevel = if (hasSavedFuelLevel) fullTankRangeKm?.times(sliderFraction.toDouble()) else null
    val updatedText = when {
        !hasSavedFuelLevel -> stringResource(R.string.fuel_not_set)
        drivenKm != null && !userAdjusting -> stringResource(R.string.fuel_estimated_since, dateTime(context, fuel.levelUpdatedAt), drivenKm)
        else -> stringResource(R.string.fuel_updated, dateTime(context, fuel.levelUpdatedAt))
    }

    Column(Modifier.fillMaxSize().background(White).verticalScroll(rememberScrollState())) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(HeaderGradient, RoundedCornerShape(bottomStart = 58.dp, bottomEnd = 58.dp))
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.tab_fuel), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = White)
            Spacer(Modifier.height(12.dp))
            FuelGauge(
                fraction = sliderFraction,
                litersInTank = litersInTank,
                updatedText = updatedText,
                onFractionChange = { userAdjusting = true; sliderFraction = it },
                onSetTank = { showTank = true },
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        fuel.setLevel(sliderFraction * 100.0)
                        userAdjusting = false
                        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msgLevel) }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3C3A3F), contentColor = White)
                ) {
                    Text(
                        stringResource(if (hasSavedFuelLevel) R.string.fuel_correct_level else R.string.fuel_set_level),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                    )
                }
                Button(
                    onClick = { showRefuel = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = OliveDark)
                ) {
                    Icon(Icons.Filled.LocalGasStation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.refuel), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle(stringResource(R.string.fuel_at_a_glance))
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceTint, modifier = Modifier.fillMaxWidth()) {
                if (averageConsumption != null && rangeFromCurrentLevel != null) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FuelStat(stringResource(R.string.fuel_range), "~${formatKm(rangeFromCurrentLevel)}", Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(42.dp).background(Hairline))
                        FuelStat(stringResource(R.string.fuel_average), "${formatDecimal(averageConsumption, 1)} L/100 km", Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
                        Text(stringResource(R.string.fuel_insights_title), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NearBlack)
                        Spacer(Modifier.height(3.dp))
                        Text(stringResource(R.string.fuel_insights_body), fontSize = 13.sp, lineHeight = 18.sp, color = TextSecondary)
                    }
                }
            }

            SpendingCard(fuel)

            val latest = fuel.refuels.firstOrNull()
            ActionRow(
                icon = Icons.Filled.History,
                title = stringResource(R.string.refuel_history),
                subtitle = if (latest == null) stringResource(R.string.refuel_history_empty)
                else stringResource(
                    R.string.refuel_history_summary,
                    shortDate(latest.date), formatDecimal(latest.litres, 1), fuel.refuels.size
                ),
                onClick = { onOpen(FuelPage.Refuels) }
            )

            val dueCount = fuel.reminders.count { fuel.progress(it).status != ReminderStatus.Ok }
            ActionRow(
                icon = Icons.Outlined.Build,
                title = stringResource(R.string.service_title),
                subtitle = when {
                    fuel.reminders.isEmpty() -> stringResource(R.string.service_row_empty)
                    dueCount > 0 -> pluralStringResource(R.plurals.service_row_due, dueCount, dueCount)
                    else -> stringResource(R.string.service_row_ok)
                },
                onClick = { onOpen(FuelPage.Service) }
            )

            ActionRow(
                icon = Icons.Filled.DirectionsCar,
                title = stringResource(R.string.trip_cost),
                subtitle = stringResource(R.string.trip_cost_row),
                onClick = { showTripCost = true }
            )

            // Level checks saved by older builds stay visible for people who upgraded.
            if (fuel.fuelLogs.isNotEmpty()) {
                ActionRow(
                    icon = Icons.Filled.GasMeter,
                    title = stringResource(R.string.level_checks),
                    subtitle = stringResource(R.string.level_checks_row, fuel.fuelLogs.size),
                    onClick = { onOpen(FuelPage.LevelChecks) }
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showRefuel) {
        RefuelSheet(
            fuel = fuel,
            onDismiss = { showRefuel = false },
            onSaved = {
                showRefuel = false
                userAdjusting = false
                scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msgRefuel) }
            }
        )
    }
    if (showTripCost) TripCostSheet(fuel = fuel, onDismiss = { showTripCost = false })
    if (showTank) TankSheet(fuel = fuel, onDismiss = { showTank = false })
}

@Composable
private fun FuelStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack)
    }
}

@Composable
private fun FuelGauge(
    fraction: Float,
    litersInTank: Double?,
    updatedText: String,
    onFractionChange: (Float) -> Unit,
    onSetTank: () -> Unit,
) {
    val normalized = fraction.coerceIn(0f, 1f)
    val pct = (normalized * 100f).roundToInt()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        shape = RoundedCornerShape(22.dp),
        color = White,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("$pct%", fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack)
                    Text(stringResource(R.string.fuel_current_level), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                }
                if (litersInTank != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${formatDecimal(litersInTank, 0)} L", fontSize = 18.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = NearBlack)
                        Text(stringResource(R.string.fuel_approx), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                    }
                } else {
                    // Without a tank size there are no litres to show, so offer to set it.
                    TextButton(onClick = onSetTank, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(stringResource(R.string.fuel_set_tank), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OliveDark)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            FuelBar(value = normalized, pct = pct, onChange = onFractionChange)
            Spacer(Modifier.height(10.dp))
            Text(updatedText, fontSize = 12.sp, lineHeight = 16.sp, color = TextSecondary)
        }
    }
}

/**
 * Fuel gauge bar. Drawn by hand so the E, ¼, ½, ¾, F marks sit exactly under
 * the positions the handle points to (a stock slider insets its track by an
 * amount that varies by version, which left the labels misaligned).
 */
@Composable
private fun FuelBar(value: Float, pct: Int, onChange: (Float) -> Unit) {
    val latestChange by rememberUpdatedState(onChange)
    val label = stringResource(R.string.fuel_level)
    val inset = 14.dp
    Column(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(Unit) {
                    val insetPx = inset.toPx()
                    fun at(x: Float) = ((x - insetPx) / (size.width - 2 * insetPx)).coerceIn(0f, 1f)
                    detectTapGestures { latestChange(at(it.x)) }
                }
                .pointerInput(Unit) {
                    val insetPx = inset.toPx()
                    fun at(x: Float) = ((x - insetPx) / (size.width - 2 * insetPx)).coerceIn(0f, 1f)
                    detectHorizontalDragGestures(
                        onDragStart = { latestChange(at(it.x)) },
                        onHorizontalDrag = { change, _ -> change.consume(); latestChange(at(change.position.x)) }
                    )
                }
                .semantics {
                    contentDescription = label
                    stateDescription = "$pct%"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f, steps = 19)
                    setProgress { target -> latestChange(target.coerceIn(0f, 1f)); true }
                }
        ) {
            val insetPx = inset.toPx()
            val left = insetPx
            val right = size.width - insetPx
            val cy = size.height / 2f
            val trackH = 10.dp.toPx()
            val x = left + (right - left) * value
            drawRoundRect(Hairline, Offset(left, cy - trackH / 2), Size(right - left, trackH), CornerRadius(trackH / 2))
            if (x > left) drawRoundRect(OliveMid, Offset(left, cy - trackH / 2), Size(x - left, trackH), CornerRadius(trackH / 2))
            drawCircle(White, radius = 13.dp.toPx(), center = Offset(x, cy))
            drawCircle(OliveDark, radius = 10.dp.toPx(), center = Offset(x, cy))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = inset).height(22.dp)) {
            val mark = 28.dp
            listOf(0f to "E", 0.25f to "¼", 0.5f to "½", 0.75f to "¾", 1f to "F").forEach { (position, text) ->
                Column(
                    Modifier.offset(x = maxWidth * position - mark / 2).width(mark),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.width(1.dp).height(5.dp).background(Color(0xFF8A9185)))
                    Text(
                        text,
                        fontSize = 12.sp,
                        fontWeight = if (position == 0f || position == 1f) FontWeight.Medium else FontWeight.Bold,
                        color = if (position == 0f || position == 1f) TextSecondary else NearBlack
                    )
                }
            }
        }
    }
}

// ============================================================
// Monthly spending
// ============================================================

@Composable
private fun SpendingCard(fuel: FuelStore) {
    if (fuel.refuels.isEmpty()) return
    val months = remember(fuel.refuels) { fuel.monthlySpend(6).reversed() } // oldest → newest
    if (months.all { it.cost <= 0.0 }) return
    var selected by remember(months) { mutableIntStateOf(months.lastIndex) }
    val current = months[selected]

    Surface(shape = RoundedCornerShape(18.dp), color = SurfaceTint, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.spend_title), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NearBlack)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(
                    R.string.spend_month_summary,
                    monthNameLong(current.year, current.month),
                    formatMoney(fuel.currency, current.cost),
                    formatDecimal(current.litres, 0)
                ),
                fontSize = 13.sp, color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            SpendBars(months = months, selected = selected, currency = fuel.currency, onSelect = { selected = it })
        }
    }
}

/**
 * Six monthly columns in one hue. The selected month carries the only value
 * label; tapping another month moves it there (and into the summary above).
 */
@Composable
private fun SpendBars(months: List<MonthlySpend>, selected: Int, currency: String, onSelect: (Int) -> Unit) {
    val max = months.maxOf { it.cost }.coerceAtLeast(1.0)
    val summary = months.joinToString(", ") { "${monthName(it.year, it.month)} ${formatMoney(currency, it.cost)}" }
    Row(
        Modifier
            .fillMaxWidth()
            .height(132.dp)
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
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NearBlack, maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                val fraction = (m.cost / max).toFloat()
                Canvas(Modifier.width(24.dp).weight(1f)) {
                    val h = size.height * fraction
                    if (h > 0f) {
                        val r = 4.dp.toPx().coerceAtMost(h)
                        // Rounded data-end, square at the baseline.
                        val path = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = 0f, top = size.height - h, right = size.width, bottom = size.height,
                                    topLeftCornerRadius = CornerRadius(r, r),
                                    topRightCornerRadius = CornerRadius(r, r),
                                )
                            )
                        }
                        drawPath(path, if (isSelected) OliveDark else OliveMid)
                    }
                    // Baseline hairline.
                    drawLine(Hairline, Offset(-size.width, size.height), Offset(size.width * 2, size.height), 1.dp.toPx())
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    monthName(m.year, m.month),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) NearBlack else TextSecondary
                )
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
                    Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.export_csv), tint = White)
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
            .background(if (index % 2 == 0) SurfaceSelected else White)
            .padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(shortDateYear(entry.date), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
            val details = listOfNotNull(
                entry.odometer.takeIf { it > 0 }?.let { formatKm(it) },
                stringResource(if (entry.tankFull) R.string.refuel_full else R.string.refuel_partial)
            ).joinToString(" · ")
            Text(details, fontSize = 12.sp, color = TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${formatDecimal(entry.litres, 1)} L", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NearBlack)
            Text(formatMoney(currency, entry.cost), fontSize = 12.sp, color = TextSecondary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_refuel_title), tint = GrayIcon)
        }
    }
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

@Composable
private fun LevelChecksPage(fuel: FuelStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val msgDeleted = stringResource(R.string.level_check_deleted)
    val msgUndo = stringResource(R.string.undo)
    SubPage(
        title = stringResource(R.string.level_checks),
        onBack = onBack,
        emptyText = if (fuel.fuelLogs.isEmpty()) stringResource(R.string.level_checks_empty) else null,
    ) {
        itemsIndexed(fuel.fuelLogs, key = { _, log -> log.date }) { idx, log ->
            LevelCheckRow(log = log, index = idx, label = dateTime(context, log.date)) {
                fuel.deleteFuelLog(log.date)
                scope.launch {
                    val result = snackbar.showSnackbar(msgDeleted, actionLabel = msgUndo, withDismissAction = true)
                    if (result == SnackbarResult.ActionPerformed) fuel.restoreFuelLog(log)
                }
            }
        }
    }
}

@Composable
private fun LevelCheckRow(log: FuelLevelLog, index: Int, label: String, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (index % 2 == 0) SurfaceSelected else White)
            .padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
            if (log.odometer > 0) Text(formatKm(log.odometer), fontSize = 12.sp, color = TextSecondary)
        }
        Text("${log.levelPct.roundToInt()}%", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack)
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = GrayIcon)
        }
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
        Spacer(Modifier.height(10.dp))
        NumberField(
            price, { price = it; errorText = null },
            stringResource(R.string.refuel_price, fuel.currency),
            supporting = litres?.takeIf { it.isFinite() }?.let { stringResource(R.string.refuel_litres_calc, formatDecimal(it, 1)) }
                ?: if (fuel.latestPricePerLitre() != null) stringResource(R.string.trip_price_learned) else null
        )
        Spacer(Modifier.height(4.dp))
        NumberField(
            odometer, { odometer = it; errorText = null },
            stringResource(R.string.refuel_odometer),
            decimal = false,
            placeholder = fuel.currentOdo.takeIf { it > 0 }?.let { stringResource(R.string.refuel_odometer_hint, formatKm(it)) },
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { fullTank = !fullTank }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.refuel_full_tank), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
                Text(stringResource(R.string.refuel_full_tank_hint), fontSize = 12.sp, lineHeight = 16.sp, color = TextSecondary)
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
                o > 0 && fuel.currentOdo > 0 && o + 1.0 < fuel.currentOdo -> eOdoLow
                !fuel.logRefuel(litres = paidValue / priceValue, cost = paidValue, odometer = o, tankFull = fullTank) -> eGeneric
                else -> { onSaved(); null }
            }
        })
        QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
    }
}

@Composable
private fun TripCostSheet(fuel: FuelStore, onDismiss: () -> Unit) {
    val learnedConsumption = remember { fuel.averageL100km() }
    val learnedPrice = remember { fuel.latestPricePerLitre() }
    var distance by remember { mutableStateOf("") }
    var consumption by remember { mutableStateOf(learnedConsumption?.let { formatDecimal(it, 1) } ?: "") }
    var price by remember { mutableStateOf(learnedPrice?.let { formatDecimal(it, 3) } ?: "") }
    var roundTrip by remember { mutableStateOf(false) }

    fun positive(text: String): Double? = parseNumber(text)?.takeIf { it > 0 }
    val tripDistance = positive(distance)?.times(if (roundTrip) 2.0 else 1.0)
    val consumptionValue = positive(consumption)
    val litresNeeded = if (tripDistance != null && consumptionValue != null) tripDistance * consumptionValue / 100.0 else null
    val tripCost = positive(price)?.let { p -> litresNeeded?.times(p) }

    ParkedSheet(
        title = stringResource(R.string.trip_cost),
        subtitle = stringResource(R.string.trip_cost_subtitle),
        onDismiss = onDismiss
    ) {
        NumberField(distance, { distance = it }, stringResource(R.string.trip_distance))
        Spacer(Modifier.height(10.dp))
        NumberField(
            consumption, { consumption = it }, stringResource(R.string.trip_consumption),
            supporting = stringResource(if (learnedConsumption != null) R.string.trip_consumption_learned else R.string.trip_consumption_enter)
        )
        Spacer(Modifier.height(4.dp))
        NumberField(
            price, { price = it }, stringResource(R.string.trip_price, fuel.currency),
            supporting = if (learnedPrice != null) stringResource(R.string.trip_price_learned) else null
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Switch) { roundTrip = !roundTrip }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.trip_round), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack, modifier = Modifier.weight(1f))
            ParkedSwitch(checked = roundTrip, onCheckedChange = null)
        }
        Spacer(Modifier.height(14.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = SurfaceTint, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.trip_estimated), fontSize = 13.sp, color = TextSecondary)
                Text(
                    tripCost?.let { formatMoney(fuel.currency, it) } ?: "—",
                    fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = NearBlack
                )
                if (litresNeeded != null && tripDistance != null) {
                    Text("${formatKm(tripDistance)} · ~${formatDecimal(litresNeeded, 1)} L", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        QuietButton(stringResource(R.string.done), color = OliveDark, onClick = onDismiss)
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
                        fontSize = 13.sp, color = TextSecondary,
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
                    PrimaryButton(text = stringResource(R.string.service_add), icon = Icons.Filled.Add, onClick = { adding = true })
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
        ReminderStatus.Due -> ErrorRed
        ReminderStatus.Soon -> WarnAmber
        ReminderStatus.Ok -> OliveDark
    }
    Surface(onClick = onEdit, color = White) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(reminder.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NearBlack)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status is spelled out, never colour alone.
                    Icon(
                        when (p.status) {
                            ReminderStatus.Due -> Icons.Filled.Error
                            ReminderStatus.Soon -> Icons.Filled.Schedule
                            ReminderStatus.Ok -> Icons.Filled.CheckCircle
                        },
                        contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(statusText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                }
                Text(detail, fontSize = 13.sp, color = TextSecondary)
                Text(
                    stringResource(R.string.service_last_done, shortDateYear(reminder.lastDoneAt)),
                    fontSize = 12.sp, color = TextSecondary
                )
            }
            if (p.status != ReminderStatus.Ok) {
                OutlinedButton(onClick = onDone, shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.service_done), color = OliveDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    DividerRow()
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
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40); error = null },
            label = { Text(stringResource(R.string.service_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(km, { km = it; error = null }, stringResource(R.string.service_every_km), Modifier.weight(1f), decimal = false)
            NumberField(months, { months = it; error = null }, stringResource(R.string.service_every_months), Modifier.weight(1f), decimal = false)
        }
        Spacer(Modifier.height(10.dp))
        NumberField(
            lastOdo, { lastOdo = it }, stringResource(R.string.service_last_odo), decimal = false,
            supporting = stringResource(R.string.service_last_odo_hint)
        )
        ErrorText(error)
        Spacer(Modifier.height(14.dp))
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
            QuietButton(stringResource(R.string.delete), color = ErrorRed) { onDelete(existing) }
        }
        QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
    }
}
