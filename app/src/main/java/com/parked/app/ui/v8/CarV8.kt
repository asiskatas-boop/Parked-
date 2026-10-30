package com.parked.app.ui.v8

import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parked.app.R
import com.parked.app.data.FuelStore
import com.parked.app.data.ParkingStore
import com.parked.app.data.ReminderStatus
import com.parked.app.data.ServiceReminder
import com.parked.app.ui.Haptics
import com.parked.app.ui.LocalSnackbar
import com.parked.app.ui.SpotPhoto
import com.parked.app.ui.carColours
import com.parked.app.ui.formatDecimal
import com.parked.app.ui.formatKm
import com.parked.app.ui.formatMoney
import com.parked.app.ui.parseNumber
import com.parked.app.ui.shortDate
import com.parked.app.ui.shortDateYear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import kotlin.math.roundToInt

private enum class CarSheet { None, Reminders, ReminderForm, Reminder, Refuel, Level, Odometer, DeleteRefuel }

/**
 * The Car tab, 0.8 design (Figma frame "Car", 393 x 817). Coordinates in the
 * comments are Figma's.
 */
@Composable
fun CarV8(fuel: FuelStore, deviceName: String?, openServiceRequest: Boolean, onServiceRequestHandled: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf(CarSheet.None) }
    var formName by remember { mutableStateOf<String?>(null) }
    var openReminder by remember { mutableStateOf<ServiceReminder?>(null) }
    var deleteRefuelAt by remember { mutableStateOf<Long?>(null) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    fun toast(text: String) = scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) }

    LaunchedEffect(openServiceRequest) {
        if (openServiceRequest) { sheet = CarSheet.Reminders; onServiceRequestHandled() }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "car").apply { mkdirs() }
                    val file = File(dir, "car_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                    file.absolutePath
                }.getOrNull()
            }
            if (path != null) fuel.setCarPhoto(path)
        }
    }
    fun choosePhoto() = runCatching { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val legacy = carColours.firstOrNull { it.first == fuel.accentName }?.second ?: Color(0xFFFF3700)
    val carColour = fuel.carColour?.let { Color(it.toInt()) } ?: legacy
    val sorted = fuel.reminders.sortedByDescending { fuel.progress(it).status.ordinal }
    val rowsShift = sorted.size * 66f

    Box(Modifier.fillMaxSize().background(White)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // ---- Name, odometer + colour dot, photo (y 0-320) ----
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                FigmaText(
                    deviceName ?: stringResource(R.string.v8_my_car),
                    x = 37f, baseline = 58f, size = 28f, weight = FontWeight.Bold, color = Black,
                    modifier = Modifier.semantics { heading() },
                )
                Row(
                    Modifier.offset(37.dp, topForBaseline(87f, 13f) - 14.dp).height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        fuel.currentOdo.takeIf { it > 0 }?.let { formatKm(it) } ?: stringResource(R.string.v8_set_km),
                        style = designStyle(13.sp, FontWeight.Normal, Black),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button) { sheet = CarSheet.Odometer },
                    )
                    Spacer(Modifier.width(9.dp))
                    // The car's colour; tap for the colour picker.
                    val cdColour = stringResource(R.string.v8_car_colour)
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { pickerOpen = !pickerOpen }
                            .semantics { contentDescription = cdColour },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        val pop by animateFloatAsState(if (pickerOpen) 1.3f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "dot")
                        Box(
                            Modifier
                                .graphicsLayer { scaleX = pop; scaleY = pop }
                                .size(10.dp)
                                .background(carColour, CircleShape)
                                .then(if (carColour.luminanceIsWhite()) Modifier.border(1.dp, IconGrey, CircleShape) else Modifier)
                        )
                    }
                }
                CarPhotoCard(
                    path = fuel.carPhotoPath,
                    modifier = Modifier.offset(35.dp, 116.dp).size(322.dp, 204.dp),
                    onPick = { choosePhoto() },
                )
                ColourPicker(
                    open = pickerOpen,
                    selected = carColour,
                    modifier = Modifier.offset(143.dp, 70.dp),
                    onPick = { c ->
                        fuel.setCarColour(c.toArgbLong())
                        scope.launch { kotlinx.coroutines.delay(160); pickerOpen = false }
                    },
                )
            }

            // ---- Fuel level (label baseline 360, card 380-486) ----
            Box(Modifier.fillMaxWidth().height(60.dp)) {
                FigmaText(stringResource(R.string.v8_fuel_level), x = 35f, baseline = 40f, size = 15f, weight = FontWeight.Light, color = Black)
                LinkV8(stringResource(R.string.v8_edit), Modifier.align(Alignment.TopEnd).offset((-33).dp, topForBaseline(40f, 15f) - 14.dp)) { sheet = CarSheet.Level }
            }
            FuelCard(fuel, Modifier.padding(start = 35.dp).size(322.dp, 106.dp))

            // ---- Service & KTEO (baseline 518, rows from 536) ----
            Box(Modifier.fillMaxWidth().height(50.dp)) {
                FigmaText(stringResource(R.string.v8_service_kteo), x = 35f, baseline = 32f, size = 15f, weight = FontWeight.Light, color = Black)
            }
            Column(Modifier.padding(start = 35.dp).width(322.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sorted.forEach { r ->
                    RowCardV8(onClick = { openReminder = r; sheet = CarSheet.Reminder }) {
                        Text(r.name, style = designStyle(13.sp, FontWeight.Bold, Ink), maxLines = 1, modifier = Modifier.weight(1f))
                        ReminderStatusText(fuel, r)
                    }
                }
                RowCardV8(onClick = { sheet = CarSheet.Reminders }) {
                    Icon(DesignIcons.Plus, null, tint = Ink, modifier = Modifier.size(16.35.dp, 15.04.dp))
                    Spacer(Modifier.width(8.65.dp))
                    Text(stringResource(R.string.v8_new_reminder), style = designStyle(13.sp, FontWeight.Bold, Ink), maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.v8_reminder_kinds), style = designStyle(13.sp, FontWeight.Normal, Ink), maxLines = 1)
                }
            }

            // ---- Fuel history (baseline 626, row 644-702) ----
            Box(Modifier.fillMaxWidth().height(50.dp)) {
                FigmaText(stringResource(R.string.v8_fuel_history), x = 35f, baseline = 32f, size = 15f, weight = FontWeight.Light, color = Black)
            }
            RowCardV8(Modifier.padding(start = 35.dp).width(322.dp), onClick = { sheet = CarSheet.Refuel }) {
                Icon(DesignIcons.Plus, null, tint = Ink, modifier = Modifier.size(16.35.dp, 15.04.dp))
                Spacer(Modifier.width(8.65.dp))
                Text(stringResource(R.string.v8_add_fuel), style = designStyle(13.sp, FontWeight.Bold, Ink), maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (fuel.refuels.isNotEmpty()) Text(
                    stringResource(R.string.v8_see_all),
                    style = designStyle(13.sp, FontWeight.Normal, Ink),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { showHistory = true }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(34.dp))
        }

        // Tap anywhere else to close the colour picker.
        if (pickerOpen) Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { pickerOpen = false } }
        )

        FuelHistoryPage(
            visible = showHistory,
            fuel = fuel,
            onBack = { showHistory = false },
            onDelete = { deleteRefuelAt = it; sheet = CarSheet.DeleteRefuel },
        )

        // ---- Sheets ----
        SheetV8(sheet == CarSheet.Reminders, onDismiss = { sheet = CarSheet.None }) {
            RemindersSheet(fuel, sorted,
                onAdd = { name -> formName = name; sheet = CarSheet.ReminderForm },
                onOpen = { r -> openReminder = r; sheet = CarSheet.Reminder })
        }
        SheetV8(sheet == CarSheet.ReminderForm, onDismiss = { sheet = CarSheet.None }) {
            ReminderForm(fuel, formName) { saved -> sheet = CarSheet.None; if (saved) toast(context.getString(R.string.v8_reminder_saved)) }
        }
        SheetV8(sheet == CarSheet.Reminder, onDismiss = { sheet = CarSheet.None }) {
            openReminder?.let { r ->
                ReminderDetail(fuel, r,
                    onDone = { fuel.markReminderDone(r.id); sheet = CarSheet.None; toast(context.getString(R.string.v8_reminder_done)) },
                    onDelete = { fuel.deleteReminder(r.id); sheet = CarSheet.None; toast(context.getString(R.string.v8_reminder_deleted)) })
            }
        }
        SheetV8(sheet == CarSheet.Refuel, onDismiss = { sheet = CarSheet.None }) {
            RefuelForm(fuel) { sheet = CarSheet.None; toast(context.getString(R.string.refuel_saved)) }
        }
        SheetV8(sheet == CarSheet.Level, onDismiss = { sheet = CarSheet.None }) {
            LevelForm(fuel) { sheet = CarSheet.None; toast(context.getString(R.string.fuel_level_saved)) }
        }
        SheetV8(sheet == CarSheet.Odometer, onDismiss = { sheet = CarSheet.None }) {
            OdometerForm(fuel) { sheet = CarSheet.None; toast(context.getString(R.string.v8_odometer_saved)) }
        }
        SheetV8(sheet == CarSheet.DeleteRefuel, onDismiss = { sheet = CarSheet.None }) {
            SheetTitle(stringResource(R.string.v8_delete_refuel))
            SheetSub(stringResource(R.string.v8_delete_refuel_body))
            Spacer(Modifier.height(24.dp))
            PrimaryV8(stringResource(R.string.v8_delete)) {
                deleteRefuelAt?.let { fuel.deleteRefuel(it) }
                sheet = CarSheet.None
                toast(context.getString(R.string.v8_refuel_deleted))
            }
        }
    }
}

private fun Color.luminanceIsWhite() = red > 0.95f && green > 0.95f && blue > 0.95f
private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

@Composable
private fun CarPhotoCard(path: String?, modifier: Modifier, onPick: () -> Unit) {
    val view = LocalView.current
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) { bitmap = path?.let { withContext(Dispatchers.IO) { SpotPhoto.load(it, 1200) } } }
    val shown by animateFloatAsState(if (bitmap != null) 1f else 0f, tween(450), label = "photo")
    val cd = stringResource(R.string.v8_car_photo)
    Box(
        modifier
            .pressable(R15) { Haptics.tick(view); onPick() }
            .background(CardGrey, R15)
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        // Add-photo icon, 69 x 69, centred (Figma 161.5, 183.5).
        Icon(DesignIcons.AddImage, null, tint = Black, modifier = Modifier.size(69.dp).graphicsLayer { alpha = 1f - shown })
        bitmap?.let { b ->
            Image(
                b.asImageBitmap(), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown; scaleX = 1.04f - 0.04f * shown; scaleY = scaleX },
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(44.dp)
                    .graphicsLayer { alpha = shown; scaleX = 0.8f + 0.2f * shown; scaleY = scaleX }
                    .background(White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(DesignIcons.Pencil, stringResource(R.string.v8_change_photo), tint = Black, modifier = Modifier.size(22.dp)) }
        }
    }
}

/** Colour picker (Figma: 166 x 75, #7F7F7F, two rows of five 21 px dots). */
@Composable
private fun ColourPicker(open: Boolean, selected: Color, modifier: Modifier, onPick: (Color) -> Unit) {
    val view = LocalView.current
    AnimatedVisibility(
        open,
        modifier = modifier,
        enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.6f, transformOrigin = TransformOrigin(-0.08f, 0.16f)),
        exit = fadeOut(tween(140)) + scaleOut(tween(200), targetScale = 0.6f, transformOrigin = TransformOrigin(-0.08f, 0.16f)),
    ) {
        Box(Modifier.size(166.dp, 75.dp).background(PickerGrey, RoundedCornerShape(14.dp))) {
            CarColours.forEachIndexed { k, c ->
                val on = c == selected
                val ring by animateColorAsState(if (on) White else Color.Transparent, tween(200), label = "ring$k")
                Box(
                    Modifier
                        .offset((21.8f + (k % 5) * 30.7f - 14.5f).dp, ((if (k < 5) 22.5f else 53f) - 14.5f).dp)
                        .size(29.dp)
                        .border(2.dp, ring, CircleShape)
                        .padding(4.dp)
                        .pressable(CircleShape, Role.RadioButton) { Haptics.tick(view); onPick(c) }
                        .background(c, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun FuelCard(fuel: FuelStore, modifier: Modifier) {
    val set = fuel.levelUpdatedAt > 0L
    val pct = fuel.estimateLevelPct().coerceIn(0.0, 100.0)
    val range = fuel.estimatedRangeKm()?.let { it * pct / 100.0 }
    // Fills and counts up each time the tab opens.
    val still = androidx.compose.ui.platform.LocalInspectionMode.current
    val anim = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(pct, set) {
        if (still) return@LaunchedEffect
        anim.snapTo(0f); anim.animateTo(1f, tween(900, easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)))
    }
    val shownPct = if (set) pct * anim.value else 0.0
    Box(modifier.background(CardGrey, R15)) {
        FigmaText(if (set) "${shownPct.roundToInt()}%" else "–", x = 21f, baseline = 43f, size = 28f, weight = FontWeight.Bold, color = Black)
        Text(
            when {
                !set -> stringResource(R.string.v8_fuel_not_set)
                range != null -> formatKm(range * anim.value)
                else -> ""
            },
            style = designStyle(15.sp, FontWeight.Light, Black), maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd).offset((-23).dp, topForBaseline(38f, 15f)),
        )
        // Bar (Figma 56,444 - 335,464): green up to the level, E and F in ink.
        Box(Modifier.offset(21.dp, 64.dp).size(279.dp, 20.dp)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((shownPct / 100.0).toFloat().coerceIn(0f, 1f))
                    .background(FuelGreen, RoundedCornerShape(10.dp))
            )
            FigmaText("E", x = 9f, baseline = 14f, size = 10f, weight = FontWeight.SemiBold, color = Ink)
            FigmaText("F", x = 263f, baseline = 14f, size = 10f, weight = FontWeight.SemiBold, color = Ink)
        }
    }
}

@Composable
private fun ReminderStatusText(fuel: FuelStore, r: ServiceReminder) {
    val p = fuel.progress(r)
    val text = when {
        p.status == ReminderStatus.Due -> stringResource(R.string.v8_due_now)
        p.kmLeft != null -> stringResource(R.string.v8_due_in, formatKm(p.kmLeft))
        p.dueAt != null -> shortDateYear(p.dueAt)
        else -> ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (p.status == ReminderStatus.Soon) { Box(Modifier.size(8.dp).background(Accent, CircleShape)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = designStyle(13.sp, if (p.status == ReminderStatus.Due) FontWeight.Medium else FontWeight.Normal, if (p.status == ReminderStatus.Due) ErrorRed else Ink), maxLines = 1)
    }
}

// ---------------------------------------------------------------- sheets

@Composable
internal fun ColumnScope.RemindersSheet(fuel: FuelStore, list: List<ServiceReminder>, onAdd: (String?) -> Unit, onOpen: (ServiceReminder) -> Unit) {
    SheetTitle(stringResource(R.string.v8_maintenance))
    if (list.isEmpty()) SheetSub(stringResource(R.string.v8_no_reminders))
    else Column(Modifier.padding(top = 14.dp)) {
        list.take(4).forEach { r ->
            Row(
                Modifier.fillMaxWidth().height(24.dp).clickable { onOpen(r) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.name, style = designStyle(13.sp, FontWeight.Bold, Ink), maxLines = 1, modifier = Modifier.weight(1f))
                ReminderStatusText(fuel, r)
            }
        }
    }
    Spacer(Modifier.height(32.dp))
    PrimaryV8(stringResource(R.string.v8_add_reminder), height = 49f) { onAdd(null) }
    Spacer(Modifier.height(17.dp))
    val kinds = listOf(
        stringResource(R.string.v8_kind_oil), stringResource(R.string.v8_kind_service),
        stringResource(R.string.v8_kind_tyres), stringResource(R.string.v8_kind_kteo),
    )
    GridV8(kinds) { onAdd(kinds[it]) }
}

@Composable
private fun ColumnScope.ReminderForm(fuel: FuelStore, preset: String?, onDone: (saved: Boolean) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(preset.orEmpty()) }
    var km by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<Long?>(null) }
    var errName by remember { mutableStateOf<String?>(null) }
    var errKm by remember { mutableStateOf<String?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    val msgName = stringResource(R.string.v8_err_name)
    val msgKm = stringResource(R.string.v8_err_km)
    val msgNeed = stringResource(R.string.v8_err_km_or_date)
    val msgOdo = stringResource(R.string.v8_err_odo_first)

    SheetTitle(preset ?: stringResource(R.string.v8_new_reminder))
    SheetSub(stringResource(R.string.v8_reminder_sub))
    Spacer(Modifier.height(18.dp))
    if (preset == null) {
        FieldV8(name, { name = it; errName = null }, stringResource(R.string.v8_reminder_name_hint), error = errName, shake = shake)
        Spacer(Modifier.height(10.dp))
    }
    FieldV8(km, { km = it; errKm = null }, stringResource(R.string.v8_due_km_hint), error = errKm, keyboard = KeyboardType.Number, shake = shake)
    Spacer(Modifier.height(10.dp))
    // Date: a field that opens the system date picker.
    Box(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(White, R15)
            .clip(R15)
            .clickable(role = Role.Button) {
                val c = Calendar.getInstance().apply { date?.let { timeInMillis = it } ?: add(Calendar.MONTH, 12) }
                DatePickerDialog(context, { _, y, m, d ->
                    date = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }.timeInMillis; errKm = null
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(stringResource(R.string.v8_due_date_hint), style = designStyle(13.sp, FontWeight.Light, Placeholder))
        date?.let { Text(shortDateYear(it), style = designStyle(13.sp, FontWeight.Light, Ink), modifier = Modifier.align(Alignment.CenterEnd)) }
    }
    Spacer(Modifier.height(24.dp))
    PrimaryV8(stringResource(R.string.v8_save)) {
        val n = (preset ?: name).trim()
        val kmValue = km.takeIf { it.isNotBlank() }?.let { parseNumber(it.replace(".", "").replace(" ", "")) }
        errName = if (n.isEmpty()) msgName else null
        errKm = when {
            km.isNotBlank() && (kmValue == null || kmValue <= 0) -> msgKm
            km.isNotBlank() && fuel.currentOdo <= 0 -> msgOdo
            km.isNotBlank() && kmValue != null && kmValue <= fuel.currentOdo -> msgKm
            km.isBlank() && date == null -> msgNeed
            else -> null
        }
        if (errName != null || errKm != null) { shake++; return@PrimaryV8 }
        val now = System.currentTimeMillis()
        val intervalKm = kmValue?.let { (it - fuel.currentOdo).roundToInt().coerceAtLeast(1) }
        val intervalMonths = date?.let { d ->
            val a = Calendar.getInstance(); val b = Calendar.getInstance().apply { timeInMillis = d }
            ((b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 12 + b.get(Calendar.MONTH) - a.get(Calendar.MONTH)).coerceAtLeast(1)
        }
        fuel.addReminder(n, intervalKm, intervalMonths, fuel.currentOdo, now)
        onDone(true)
    }
}

@Composable
private fun ColumnScope.ReminderDetail(fuel: FuelStore, r: ServiceReminder, onDone: () -> Unit, onDelete: () -> Unit) {
    SheetTitle(r.name)
    Row(Modifier.padding(start = 2.dp, top = 15.dp)) { ReminderStatusText(fuel, r) }
    Spacer(Modifier.height(24.dp))
    PrimaryV8(stringResource(R.string.v8_mark_done), onClick = onDone)
    Text(
        stringResource(R.string.v8_delete),
        style = designStyle(15.sp, FontWeight.Light, ErrorRed),
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onDelete)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
internal fun ColumnScope.RefuelForm(fuel: FuelStore, onSaved: () -> Unit) {
    var paid by remember { mutableStateOf("") }
    var price by remember { mutableStateOf(fuel.latestPricePerLitre()?.let { formatDecimal(it, 2) } ?: "") }
    var full by remember { mutableStateOf(false) }
    var errPaid by remember { mutableStateOf<String?>(null) }
    var errPrice by remember { mutableStateOf<String?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    val enterAmount = stringResource(R.string.v8_err_enter_amount); val checkAmount = stringResource(R.string.v8_err_check_amount)
    val enterPrice = stringResource(R.string.v8_err_enter_price); val checkPrice = stringResource(R.string.v8_err_check_price)

    SheetTitle(stringResource(R.string.v8_add_fuel))
    SheetSub(stringResource(R.string.v8_refuel_sub))
    Spacer(Modifier.height(18.dp))
    FieldV8(paid, { paid = it; errPaid = null }, stringResource(R.string.v8_paid_hint), error = errPaid, keyboard = KeyboardType.Decimal, shake = shake)
    Spacer(Modifier.height(10.dp))
    FieldV8(price, { price = it; errPrice = null }, stringResource(R.string.v8_price_hint), error = errPrice, keyboard = KeyboardType.Decimal, shake = shake)
    // "Filled up?" with its switch (baseline 635, switch right-aligned).
    Row(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 0.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.v8_filled_up), style = designStyle(13.sp, FontWeight.Normal, Ink), modifier = Modifier.weight(1f).padding(start = 2.dp))
        SwitchV8(full, stringResource(R.string.v8_filled_up)) { full = it }
    }
    Spacer(Modifier.height(18.dp))
    PrimaryV8(stringResource(R.string.v8_save)) {
        val a = parseNumber(paid); val p = parseNumber(price)
        errPaid = when { paid.isBlank() -> enterAmount; a == null || a <= 0 || a > 500 -> checkAmount; else -> null }
        errPrice = when { price.isBlank() -> enterPrice; p == null || p < 0.5 || p > 5 -> checkPrice; else -> null }
        if (errPaid != null || errPrice != null) { shake++; return@PrimaryV8 }
        if (fuel.logRefuel(litres = a!! / p!!, cost = a, odometer = fuel.currentOdo, tankFull = full)) onSaved()
        else { errPaid = checkAmount; shake++ }
    }
}

@Composable
internal fun ColumnScope.LevelForm(fuel: FuelStore, onSaved: () -> Unit) {
    val view = LocalView.current
    var pct by remember { mutableIntStateOf(((fuel.estimateLevelPct() / 5).roundToInt() * 5).coerceIn(0, 100)) }
    var tank by remember { mutableStateOf(fuel.tankCapacity.takeIf { it > 0 }?.let { formatDecimal(it, 0) } ?: "") }
    var errTank by remember { mutableStateOf<String?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    val checkTank = stringResource(R.string.v8_err_tank)
    val range = fuel.estimatedRangeKm()

    SheetTitle(stringResource(R.string.v8_fuel_level))
    SheetSub(stringResource(R.string.v8_level_sub))
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.Bottom) {
        Text("$pct%", style = designStyle(28.sp, FontWeight.Bold, Black), modifier = Modifier.weight(1f))
        if (range != null) Text(formatKm(range * pct / 100.0), style = designStyle(15.sp, FontWeight.Light, Black))
    }
    // Drag along the bar; steps of 5% with a tick of haptics on each quarter.
    BoxWithConstraints(
        Modifier
            .padding(top = 14.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(Unit) {
                fun set(x: Float) {
                    val v = ((x / size.width * 100f) / 5f).roundToInt().coerceIn(0, 20) * 5
                    if (v != pct) { pct = v; if (v % 25 == 0) Haptics.tick(view) }
                }
                detectTapGestures { set(it.x) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    val v = ((change.position.x / size.width * 100f) / 5f).roundToInt().coerceIn(0, 20) * 5
                    if (v != pct) { pct = v; if (v % 25 == 0) Haptics.tick(view) }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(20.dp).background(White, RoundedCornerShape(10.dp)))
        Box(Modifier.fillMaxWidth(pct / 100f).height(20.dp).background(FuelGreen, RoundedCornerShape(10.dp)))
        val x by androidx.compose.animation.core.animateDpAsState((w - 30.dp) * (pct / 100f), spring(dampingRatio = 0.8f, stiffness = 900f), label = "thumb")
        Box(Modifier.offset(x).size(30.dp).background(White, CircleShape).border(2.dp, Navy, CircleShape))
    }
    FieldV8(tank, { tank = it; errTank = null }, "", prefix = stringResource(R.string.v8_tank_size), suffix = "L", error = errTank, keyboard = KeyboardType.Number, shake = shake)
    Spacer(Modifier.height(24.dp))
    PrimaryV8(stringResource(R.string.v8_save)) {
        val t = parseNumber(tank)
        if (tank.isNotBlank() && (t == null || t < 10 || t > 150)) { errTank = checkTank; shake++; return@PrimaryV8 }
        fuel.setLevel(pct.toDouble())
        if (t != null) fuel.updateTankCapacity(t)
        onSaved()
    }
}

@Composable
private fun ColumnScope.OdometerForm(fuel: FuelStore, onSaved: () -> Unit) {
    var km by remember { mutableStateOf(fuel.currentOdo.takeIf { it > 0 }?.roundToInt()?.toString() ?: "") }
    var err by remember { mutableStateOf<String?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    val check = stringResource(R.string.v8_err_km)
    SheetTitle(stringResource(R.string.odometer))
    SheetSub(stringResource(R.string.v8_odo_sub))
    Spacer(Modifier.height(18.dp))
    FieldV8(km, { km = it; err = null }, stringResource(R.string.v8_odo_hint), suffix = "km", error = err, keyboard = KeyboardType.Number, shake = shake)
    Spacer(Modifier.height(24.dp))
    PrimaryV8(stringResource(R.string.v8_save)) {
        val v = parseNumber(km.replace(".", "").replace(" ", ""))
        if (v == null || v < 1 || v > 2_000_000) { err = check; shake++; return@PrimaryV8 }
        fuel.setOdometer(v)
        onSaved()
    }
}

// ---------------------------------------------------------------- fuel history page

@Composable
private fun FuelHistoryPage(visible: Boolean, fuel: FuelStore, onBack: () -> Unit, onDelete: (Long) -> Unit) {
    if (visible) BackHandler(onBack = onBack)
    AnimatedVisibility(
        visible,
        enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it },
        exit = slideOutHorizontally(tween(280)) { it },
    ) {
        val month = Calendar.getInstance()
        val thisMonth = fuel.refuels.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.date }
            c.get(Calendar.YEAR) == month.get(Calendar.YEAR) && c.get(Calendar.MONTH) == month.get(Calendar.MONTH)
        }
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
            Text(stringResource(R.string.v8_fuel_history), style = designStyle(28.sp, FontWeight.Bold, Black), modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            Text(
                stringResource(R.string.v8_this_month, thisMonth.size, formatMoney(fuel.currency, thisMonth.sumOf { it.cost })),
                style = designStyle(15.sp, FontWeight.Light, Black),
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            )
            fuel.refuels.forEach { r ->
                val parts = listOfNotNull(
                    "${formatDecimal(r.litres, 1)} L",
                    if (r.litres > 0) "${formatDecimal(r.cost / r.litres, 2)} ${fuel.currency}/L" else null,
                    if (r.tankFull) stringResource(R.string.v8_filled_up_short) else null,
                )
                RowCardV8(Modifier.padding(bottom = 8.dp).height(64.dp), onClick = { onDelete(r.date) }) {
                    Column(Modifier.weight(1f)) {
                        Text(shortDateYear(r.date), style = designStyle(13.sp, FontWeight.Bold, Ink), maxLines = 1)
                        Text(parts.joinToString(" · "), style = designStyle(13.sp, FontWeight.Light, Ink), maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                    }
                    Text(formatMoney(fuel.currency, r.cost), style = designStyle(13.sp, FontWeight.Normal, Ink), maxLines = 1)
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier
            .padding(top = 20.dp)
            .offset((-10).dp)
            .size(44.dp)
            .pressable(CircleShape) { Haptics.tick(view); onBack() },
        contentAlignment = Alignment.Center,
    ) { Icon(DesignIcons.Back, stringResource(R.string.v8_back), tint = Black, modifier = Modifier.size(24.dp)) }
}
