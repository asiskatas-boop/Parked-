package com.parked.app.ui

import androidx.compose.animation.ContentTransform
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

/**
 * Phosphor icons (light weight, one stroke everywhere), bundled as vector
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
    val warning @Composable get() = ImageVector.vectorResource(R.drawable.ph_warning)
    val wrench @Composable get() = ImageVector.vectorResource(R.drawable.ph_wrench)
    val x @Composable get() = ImageVector.vectorResource(R.drawable.ph_x)
}

/** Large, light page title, as in the reference. */
@Composable
fun PageTitle(text: String, color: Color = Paper, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = TypeScale.PageTitle,
        lineHeight = TypeScale.PageTitle * 1.1f,
        fontWeight = FontWeight.Normal,
        color = color,
        letterSpacing = (-0.4).sp,
        modifier = modifier.semantics { heading() }
    )
}

/** The orange arrow that ends every call to action and every navigating row. */
@Composable
fun AccentArrow(modifier: Modifier = Modifier, icon: ImageVector = Ph.arrowRight) {
    Icon(icon, contentDescription = null, tint = Accent, modifier = modifier.size(20.dp))
}

/**
 * Main call to action: a white rectangle with the label on the left and an
 * orange arrow after it. Minimum height rather than fixed, so large font
 * sizes never clip the label.
 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Paper,
            contentColor = Ink,
            disabledContainerColor = Field,
            disabledContentColor = Muted,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(text, fontSize = TypeScale.Emphasis, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(10.dp))
            AccentArrow()
        }
    }
}

/**
 * Second-level action: an outlined rectangle. Used where two actions sit side
 * by side and neither is the main one.
 */
@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Paper),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = TypeScale.Label, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

/** Text link with an orange arrow, as in the reference ("Αργότερα →"). */
@Composable
fun LinkButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Paper,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = TypeScale.Emphasis, color = color)
        Spacer(Modifier.width(10.dp))
        AccentArrow(icon = icon ?: Ph.arrowRight)
    }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, color: Color = Muted, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RectangleShape) {
        Text(text, fontSize = TypeScale.Body, color = color)
    }
}

/**
 * Bottom sheet used by every form. It is a real modal: it covers the tab bar,
 * closes on back and outside tap, and moves above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkedSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel,
        contentColor = Paper,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RectangleShape,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 32.dp, height = 3.dp).background(Line))
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Text(
                title,
                fontSize = TypeScale.SheetTitle,
                lineHeight = TypeScale.SheetTitle * 1.15f,
                fontWeight = FontWeight.Normal,
                color = Paper,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, fontSize = TypeScale.Body, lineHeight = 24.sp, color = Muted)
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

/** Full-screen sub-page: back arrow, large title, then a list. */
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    emptyText: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Ph.arrowLeft, contentDescription = stringResource(R.string.cd_back), tint = Paper, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        PageTitle(title, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            // The empty message goes above the content, not instead of it, so
            // actions such as "Add reminder" stay reachable on an empty page.
            if (emptyText != null) {
                item {
                    Text(
                        emptyText,
                        fontSize = TypeScale.Body,
                        lineHeight = 24.sp,
                        color = Muted,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)
                    )
                }
            }
            content()
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
}

/**
 * Tappable list row: icon, title, supporting line, orange arrow, hairline
 * underneath. Rows stack into a list with no cards.
 */
@Composable
fun ActionRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Paper, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = TypeScale.Emphasis, lineHeight = 24.sp, color = Paper)
                if (subtitle != null) Text(subtitle, fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = Muted)
            }
            Spacer(Modifier.width(12.dp))
            if (trailing != null) trailing() else AccentArrow()
        }
        DividerRow()
    }
}

/** A group of rows between hairlines. Rows inside separate with [DividerRow]. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        DividerRow()
        content()
        DividerRow()
    }
}

/**
 * Square switch, as in the reference: orange track and black knob when on,
 * dark track and grey knob when off. 48 dp tall touch area when interactive.
 */
@Composable
fun ParkedSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) Accent else Field, tween(180), label = "track")
    val knob by animateColorAsState(if (checked) Ink else Muted, tween(180), label = "knob")
    val offset by animateDpAsState(if (checked) 20.dp else 0.dp, tween(180, easing = FastOutSlowInEasing), label = "knob")
    val base = if (onCheckedChange != null) {
        modifier
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    } else modifier
    Box(base, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 48.dp, height = 28.dp)
                .background(track)
                .then(if (checked) Modifier else Modifier.border(1.dp, Line))
                .padding(3.dp)
        ) {
            Box(Modifier.offset(x = offset).size(22.dp).background(knob))
        }
    }
}

/** The whole row toggles, not only the switch, for a larger touch target. */
@Composable
fun ToggleRow(label: String, checked: Boolean, supporting: String? = null, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontSize = TypeScale.Emphasis, lineHeight = 24.sp, color = Paper)
            if (supporting != null) Text(supporting, fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = Muted)
        }
        ParkedSwitch(checked = checked, onCheckedChange = null)
    }
}

/** Label on the left, current value and an orange arrow on the right. */
@Composable
fun TextRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = TypeScale.Emphasis, lineHeight = 24.sp, color = Paper, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = TypeScale.Body, color = Muted, textAlign = TextAlign.End)
        Spacer(Modifier.width(12.dp))
        AccentArrow()
    }
}

@Composable
fun DividerRow(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = Line, thickness = 1.dp)
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
        containerColor = Panel,
        shape = RectangleShape,
        title = { Text(title, fontSize = 24.sp, fontWeight = FontWeight.Normal, color = Paper) },
        text = { Text(message, fontSize = TypeScale.Body, lineHeight = 24.sp, color = Muted) },
        confirmButton = {
            TextButton(onClick = onConfirm, shape = RectangleShape, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(confirmLabel, fontSize = TypeScale.Body, color = if (destructive) Danger else Accent, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RectangleShape, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.cancel), fontSize = TypeScale.Body, color = Paper)
            }
        }
    )
}

@Composable
fun SectionTitle(text: String, color: Color = Paper, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = TypeScale.Section,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
        color = color,
        modifier = modifier.semantics { heading() }
    )
}

@Composable
fun ErrorText(text: String?) {
    if (text == null) return
    Spacer(Modifier.height(8.dp))
    Text(text, fontSize = TypeScale.Supporting, lineHeight = 21.sp, color = Danger)
}

/** Square dark fields with an orange focus outline. Shared by every form. */
@Composable
fun parkedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Field,
    unfocusedContainerColor = Field,
    disabledContainerColor = Field,
    errorContainerColor = Field,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Line,
    errorBorderColor = Danger,
    focusedLabelColor = Accent,
    unfocusedLabelColor = Muted,
    focusedTextColor = Paper,
    unfocusedTextColor = Paper,
    cursorColor = Accent,
    focusedPlaceholderColor = Muted,
    unfocusedPlaceholderColor = Muted,
    focusedSupportingTextColor = Muted,
    unfocusedSupportingTextColor = Muted,
    focusedTrailingIconColor = Paper,
    unfocusedTrailingIconColor = Muted,
    selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
        handleColor = Accent,
        backgroundColor = Accent.copy(alpha = 0.35f),
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
        shape = RectangleShape,
        colors = parkedFieldColors(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = SofiaSans, fontSize = TypeScale.Emphasis),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (decimal) androidx.compose.ui.text.input.KeyboardType.Decimal
            else androidx.compose.ui.text.input.KeyboardType.Number
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Moving into a sub-page slides it in from the end edge; going back reverses it.
 * Short and eased so it reads as navigation, not decoration.
 */
fun pageTransition(forward: Boolean): ContentTransform {
    val spec = tween<androidx.compose.ui.unit.IntOffset>(300, easing = FastOutSlowInEasing)
    return if (forward) {
        (slideInHorizontally(spec) { it / 4 } + fadeIn(tween(240, delayMillis = 40)))
            .togetherWith(slideOutHorizontally(spec) { -it / 10 } + fadeOut(tween(150)))
    } else {
        (slideInHorizontally(spec) { -it / 10 } + fadeIn(tween(240, delayMillis = 40)))
            .togetherWith(slideOutHorizontally(spec) { it / 4 } + fadeOut(tween(150)))
    }
}
