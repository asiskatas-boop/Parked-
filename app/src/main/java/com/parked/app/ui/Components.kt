package com.parked.app.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parked.app.R

/** One snackbar host for the whole app, so every confirmation looks the same. */
val LocalSnackbar = compositionLocalOf<SnackbarHostState> { error("No snackbar host") }

/** True in screenshot tests: sheets draw inline instead of in a window. */
val LocalInlineSheets = compositionLocalOf { false }

/** Small green arrow for the end of a row. */
@Composable
fun AccentArrow() {
    Icon(Ph.arrowRight, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
}

/**
 * Phosphor icons (regular weight, one stroke everywhere), bundled as vector
 * drawables under res/drawable/ph_*.xml. Tint them in Compose.
 */
object Ph {
    val arrowRight @Composable get() = ImageVector.vectorResource(R.drawable.ph_arrow_right)
    val arrowLeft @Composable get() = ImageVector.vectorResource(R.drawable.ph_arrow_left)
    val arrowUpRight @Composable get() = ImageVector.vectorResource(R.drawable.ph_arrow_up_right)
    val bell @Composable get() = ImageVector.vectorResource(R.drawable.ph_bell)
    val bluetooth @Composable get() = ImageVector.vectorResource(R.drawable.ph_bluetooth)
    val calendar @Composable get() = ImageVector.vectorResource(R.drawable.ph_calendar)
    val camera @Composable get() = ImageVector.vectorResource(R.drawable.ph_camera)
    val car @Composable get() = ImageVector.vectorResource(R.drawable.ph_car)
    val carProfile @Composable get() = ImageVector.vectorResource(R.drawable.ph_car_profile)
    val check @Composable get() = ImageVector.vectorResource(R.drawable.ph_check)
    val checkCircle @Composable get() = ImageVector.vectorResource(R.drawable.ph_check_circle)
    val clockCounter @Composable get() = ImageVector.vectorResource(R.drawable.ph_clock_counter)
    val cornersOut @Composable get() = ImageVector.vectorResource(R.drawable.ph_corners_out)
    val export @Composable get() = ImageVector.vectorResource(R.drawable.ph_export)
    val gasPump @Composable get() = ImageVector.vectorResource(R.drawable.ph_gas_pump)
    val gauge @Composable get() = ImageVector.vectorResource(R.drawable.ph_gauge)
    val info @Composable get() = ImageVector.vectorResource(R.drawable.ph_info)
    val mapPin @Composable get() = ImageVector.vectorResource(R.drawable.ph_map_pin)
    val navigation @Composable get() = ImageVector.vectorResource(R.drawable.ph_navigation)
    val note @Composable get() = ImageVector.vectorResource(R.drawable.ph_note)
    val plus @Composable get() = ImageVector.vectorResource(R.drawable.ph_plus)
    val road @Composable get() = ImageVector.vectorResource(R.drawable.ph_road)
    val share @Composable get() = ImageVector.vectorResource(R.drawable.ph_share)
    val sliders @Composable get() = ImageVector.vectorResource(R.drawable.ph_sliders)
    val squaresFour @Composable get() = ImageVector.vectorResource(R.drawable.ph_squares_four)
    val timer @Composable get() = ImageVector.vectorResource(R.drawable.ph_timer)
    val trash @Composable get() = ImageVector.vectorResource(R.drawable.ph_trash)
    val trayArrowDown @Composable get() = ImageVector.vectorResource(R.drawable.ph_tray_arrow_down)
    val trayArrowUp @Composable get() = ImageVector.vectorResource(R.drawable.ph_tray_arrow_up)
    val warning @Composable get() = ImageVector.vectorResource(R.drawable.ph_warning)
    val wrench @Composable get() = ImageVector.vectorResource(R.drawable.ph_wrench)
    val x @Composable get() = ImageVector.vectorResource(R.drawable.ph_x)
    val caretRight @Composable get() = ImageVector.vectorResource(R.drawable.ph_caret_right)
    val house @Composable get() = ImageVector.vectorResource(R.drawable.ph_house)
    val houseFill @Composable get() = ImageVector.vectorResource(R.drawable.ph_house_fill)
    val carFill @Composable get() = ImageVector.vectorResource(R.drawable.ph_car_fill)
    val gearSix @Composable get() = ImageVector.vectorResource(R.drawable.ph_gear_six)
    val gearSixFill @Composable get() = ImageVector.vectorResource(R.drawable.ph_gear_six_fill)
    val crosshair @Composable get() = ImageVector.vectorResource(R.drawable.ph_crosshair)
    val stack @Composable get() = ImageVector.vectorResource(R.drawable.ph_stack)
    val shareNetwork @Composable get() = ImageVector.vectorResource(R.drawable.ph_share_network)
    val notePencil @Composable get() = ImageVector.vectorResource(R.drawable.ph_note_pencil)
    val cloudUp @Composable get() = ImageVector.vectorResource(R.drawable.ph_cloud_arrow_up)
    val download @Composable get() = ImageVector.vectorResource(R.drawable.ph_download_simple)
    val globe @Composable get() = ImageVector.vectorResource(R.drawable.ph_globe)
    val clock @Composable get() = ImageVector.vectorResource(R.drawable.ph_clock)
    val checkCircleFill @Composable get() = ImageVector.vectorResource(R.drawable.ph_check_circle_fill)
    val navigationFill @Composable get() = ImageVector.vectorResource(R.drawable.ph_navigation_arrow_fill)
}

/**
 * Motion: springs instead of fixed durations, so movement can be interrupted
 * and always settles naturally (Material's standard spring values). Spatial
 * springs move things; effect springs fade and recolour, without bounce.
 */
object Motion {
    fun <T> spatial() = androidx.compose.animation.core.spring<T>(dampingRatio = 0.9f, stiffness = 700f)
    fun <T> spatialFast() = androidx.compose.animation.core.spring<T>(dampingRatio = 0.9f, stiffness = 1400f)
    fun <T> effects() = androidx.compose.animation.core.spring<T>(dampingRatio = 1f, stiffness = 1600f)
}

/** Haptics, used sparingly: a firm tick for confirmations, a light one for toggles. */
object Haptics {
    fun confirm(view: android.view.View) {
        view.performHapticFeedback(
            if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
            else android.view.HapticFeedbackConstants.VIRTUAL_KEY
        )
    }
    fun tick(view: android.view.View) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }
}

/** Shrinks slightly while pressed, like Revolut's buttons and cards. */
fun Modifier.pressScale(interaction: androidx.compose.foundation.interaction.InteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.spatialFast(), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Large serif page title. */
@Composable
fun PageTitle(text: String, color: Color = TextPrimary, modifier: Modifier = Modifier) {
    Text(
        text,
        fontFamily = HeadingFont,
        fontSize = TypeScale.PageTitle,
        lineHeight = TypeScale.PageTitle * 1.15f,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier.semantics { heading() }
    )
}

/** Small uppercase label above a big number ("THE CAR", "OCTOBER"). */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = TextMuted) {
    Text(
        upperNoAccents(text), modifier = modifier, color = color,
        fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
    )
}

/**
 * Uppercase without accents: Greek capitals drop their tonos ("ΤΟ ΑΥΤΟΚΙΝΗΤΟ
 * ΣΟΥ", not "ΑΥΤΟΚΊΝΗΤΌ"). Only the Greek tonos is removed; the dialytika stays.
 */
fun upperNoAccents(text: String): String =
    java.text.Normalizer.normalize(text.uppercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("(?<=[\\u0370-\\u03FF])\\u0301+"), "")
        .let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFC) }

/** The one big number on a screen (distance, spend, fuel), Revolut-style. */
@Composable
fun BigNumber(text: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.TextUnit = TypeScale.BigNumber, color: Color = TextPrimary) {
    Text(
        text, modifier = modifier, color = color,
        fontFamily = HeadingFont, fontWeight = FontWeight.SemiBold,
        fontSize = size, lineHeight = size * 1.1f, letterSpacing = (-0.5).sp,
    )
}

/** Main call to action: green, full width, label centred. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp).pressScale(interaction),
        shape = RadiusSmall,
        colors = ButtonDefaults.buttonColors(
            containerColor = Green,
            contentColor = OnGreen,
            disabledContainerColor = SurfaceSunk,
            disabledContentColor = TextFaint,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = TypeScale.Label, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Second-level action: green outline. */
@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 48.dp).pressScale(interaction),
        shape = RadiusSmall,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Green),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Green),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = TypeScale.Label, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Text link ("All", "History", "Later"). */
@Composable
fun LinkButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Green,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RadiusSmall)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = TypeScale.Label, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, color: Color = TextMuted, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RadiusSmall) {
        Text(text, fontSize = TypeScale.Body, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** White card with a warm 1 dp border and no shadow. */
@Composable
fun ParkedCard(modifier: Modifier = Modifier, color: Color = Surface, border: Color = Border, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RadiusCard)
            .background(color)
            .border(1.dp, border, RadiusCard),
        content = content
    )
}

/** Status chip: neutral, on (green), warn (amber), ok. */
enum class ChipKind { Neutral, On, Warn, Ok }

@Composable
fun StatusChip(text: String, kind: ChipKind = ChipKind.Neutral, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val (bg, fg, line) = when (kind) {
        ChipKind.Neutral -> Triple(Surface, TextPrimary, BorderStrong)
        ChipKind.On -> Triple(GreenSoft, Green, Green)
        ChipKind.Warn -> Triple(WarnBg, Warn, Color(0xFFFCD34D))
        ChipKind.Ok -> Triple(OkBg, Ok, OkBg)
    }
    Row(
        modifier
            .heightIn(min = 32.dp)
            .clip(RadiusSmall)
            .background(bg)
            .border(1.dp, line, RadiusSmall)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            icon != null -> { Icon(icon, contentDescription = null, tint = if (kind == ChipKind.Neutral) Green else fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
            kind == ChipKind.On -> { Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Green)); Spacer(Modifier.width(6.dp)) }
        }
        Text(text, fontSize = TypeScale.Caption, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

/**
 * Bottom sheet used by every form: white, rounded top, a real modal that
 * covers the tab bar, closes on back and outside tap, moves above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkedSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    @Composable
    fun Body(scrollable: Boolean) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.imePadding().verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                title,
                fontFamily = HeadingFont,
                fontSize = TypeScale.SheetTitle,
                lineHeight = TypeScale.SheetTitle * 1.25f,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = TextMuted)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
    val handle = @Composable {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(BorderStrong))
        }
    }
    if (LocalInlineSheets.current) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Surface)) {
            handle()
            Body(scrollable = false)
        }
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface,
        contentColor = TextPrimary,
        scrimColor = TextPrimary.copy(alpha = 0.38f),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        dragHandle = handle,
    ) { Body(scrollable = true) }
}

/** Full-screen sub-page: back arrow, serif title, then a list on cream. */
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    emptyText: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Ph.arrowLeft, contentDescription = stringResource(R.string.cd_back), tint = TextPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        PageTitle(title, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            // The empty message goes above the content, not instead of it, so
            // actions such as "Add reminder" stay reachable on an empty page.
            if (emptyText != null) {
                item {
                    Text(
                        emptyText,
                        fontSize = TypeScale.Body,
                        lineHeight = 24.sp,
                        color = TextMuted,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }
            content()
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
}

/** Chevron at the end of a tappable row. */
@Composable
fun RowChevron() {
    Icon(Ph.caretRight, contentDescription = null, tint = TextFaint, modifier = Modifier.size(18.dp))
}

/**
 * Tappable row inside a card: green icon, title, supporting line, chevron.
 * Rows in one card are separated with [DividerRow].
 */
@Composable
fun ActionRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    iconTint: Color = Green,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = TypeScale.Label, lineHeight = 21.sp, color = TextPrimary)
            if (subtitle != null) Text(subtitle, fontSize = TypeScale.Caption, lineHeight = 19.sp, color = TextMuted)
        }
        Spacer(Modifier.width(12.dp))
        if (trailing != null) trailing() else RowChevron()
    }
}

/** A white card of rows. Rows inside separate with [DividerRow]. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ParkedCard(modifier, content = content)
}

/** Green pill switch with a white knob; springs between states. */
@Composable
fun ParkedSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val track by animateColorAsState(if (checked) Green else BorderStrong, Motion.effects(), label = "track")
    val offset by animateDpAsState(if (checked) 18.dp else 0.dp, Motion.spatialFast(), label = "knob")
    val base = if (onCheckedChange != null) {
        modifier
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch) { Haptics.tick(view); onCheckedChange(it) }
    } else modifier
    Box(base, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(track)
                .padding(3.dp)
        ) {
            Box(Modifier.offset(x = offset).size(20.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Surface))
        }
    }
}

/** The whole row toggles, not only the switch, for a larger touch target. */
@Composable
fun ToggleRow(label: String, checked: Boolean, supporting: String? = null, icon: ImageVector? = null, onToggle: (Boolean) -> Unit) {
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch) { Haptics.tick(view); onToggle(it) }
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontSize = TypeScale.Label, lineHeight = 21.sp, color = TextPrimary)
            if (supporting != null) Text(supporting, fontSize = TypeScale.Caption, lineHeight = 19.sp, color = TextMuted)
        }
        ParkedSwitch(checked = checked, onCheckedChange = null)
    }
}

/** Label on the left, current value and a chevron on the right. */
@Composable
fun TextRow(label: String, value: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(label, fontSize = TypeScale.Label, lineHeight = 21.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = TypeScale.Supporting, color = TextMuted, textAlign = TextAlign.End)
        Spacer(Modifier.width(8.dp))
        RowChevron()
    }
}

@Composable
fun DividerRow(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = Border, thickness = 1.dp)
}

@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = true,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RadiusCard,
        title = { Text(title, fontFamily = HeadingFont, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) },
        text = { Text(message, fontSize = TypeScale.Body, lineHeight = 24.sp, color = TextMuted) },
        confirmButton = {
            TextButton(onClick = onConfirm, shape = RadiusSmall, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(confirmLabel, fontSize = TypeScale.Body, color = if (destructive) Danger else Green, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RadiusSmall, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.cancel), fontSize = TypeScale.Body, color = TextMuted, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

/** Serif section heading, optionally with a link on the right ("All"). */
@Composable
fun SectionTitle(text: String, color: Color = TextPrimary, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            fontFamily = HeadingFont,
            fontSize = TypeScale.Section,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (action != null) LinkButton(action, onClick = onAction)
    }
}

@Composable
fun ErrorText(text: String?) {
    if (text == null) return
    Spacer(Modifier.height(8.dp))
    Text(text, fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = Danger)
}

/** White fields with a warm outline and a green focus outline. Shared by every form. */
@Composable
fun parkedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Surface,
    unfocusedContainerColor = Surface,
    disabledContainerColor = SurfaceSunk,
    errorContainerColor = Surface,
    focusedBorderColor = Green,
    unfocusedBorderColor = BorderStrong,
    errorBorderColor = Danger,
    focusedLabelColor = Green,
    unfocusedLabelColor = TextMuted,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Green,
    focusedPlaceholderColor = TextFaint,
    unfocusedPlaceholderColor = TextFaint,
    focusedSupportingTextColor = TextMuted,
    unfocusedSupportingTextColor = TextMuted,
    focusedTrailingIconColor = Green,
    unfocusedTrailingIconColor = TextMuted,
    selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
        handleColor = Green,
        backgroundColor = Green.copy(alpha = 0.25f),
    ),
)

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    placeholder: String? = null,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        shape = RadiusSmall,
        colors = parkedFieldColors(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = BodyFont, fontSize = TypeScale.Body),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (decimal) androidx.compose.ui.text.input.KeyboardType.Decimal
            else androidx.compose.ui.text.input.KeyboardType.Number
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Moving into a sub-page slides it in from the end edge on a spring; going
 * back reverses it.
 */
fun pageTransition(forward: Boolean): ContentTransform {
    val slide = Motion.spatial<androidx.compose.ui.unit.IntOffset>()
    return if (forward) {
        (slideInHorizontally(slide) { it / 4 } + fadeIn(Motion.effects()))
            .togetherWith(slideOutHorizontally(slide) { -it / 10 } + fadeOut(Motion.effects()))
    } else {
        (slideInHorizontally(slide) { -it / 10 } + fadeIn(Motion.effects()))
            .togetherWith(slideOutHorizontally(slide) { it / 4 } + fadeOut(Motion.effects()))
    }
}
