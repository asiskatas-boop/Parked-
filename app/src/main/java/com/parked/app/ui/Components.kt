package com.parked.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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

val HeaderGradient: Brush
    get() = Brush.linearGradient(
        colors = listOf(OliveDark, OliveMid, LimeBright),
        start = Offset.Zero,
        end = Offset(900f, 900f)
    )

/**
 * Main call to action. Uses a minimum height rather than a fixed one so the
 * label is never clipped at large font sizes.
 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LimeBright, contentColor = NearBlack),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OliveDark, contentColor = White),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text, fontSize = 15.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Bottom sheet used by every form. It is a real modal: it covers the bottom
 * navigation, closes on back and outside tap, and moves above the keyboard.
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
        containerColor = White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NearBlack,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, lineHeight = 18.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** Full-screen sub-page with a back button, used for histories and lists. */
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    emptyText: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(White)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(HeaderGradient)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = White)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                title,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = White,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            actions()
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (emptyText != null) {
                item {
                    Box(Modifier.fillMaxWidth().height(260.dp).padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(emptyText, color = TextSecondary, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    }
                }
            } else {
                content()
            }
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
}

/** Tappable row with an icon, a title, a supporting line and a chevron. */
@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceTint,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = OliveDark)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NearBlack)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = TextSecondary)
            }
            if (trailing != null) trailing() else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = OliveDark)
            }
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)
    ) { Column(content = content) }
}

@Composable
fun ParkedSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = White,
            checkedTrackColor = OliveMid,
            uncheckedThumbColor = White,
            uncheckedTrackColor = Color(0xFFBDBDBD),
            uncheckedBorderColor = Color(0xFF9E9E9E),
        )
    )
}

/** The whole row toggles, not only the small switch, for a larger touch target. */
@Composable
fun ToggleRow(label: String, checked: Boolean, supporting: String? = null, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleableRow(checked, onToggle)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack)
            if (supporting != null) Text(supporting, fontSize = 12.sp, lineHeight = 16.sp, color = TextSecondary)
        }
        ParkedSwitch(checked = checked, onCheckedChange = null)
    }
}

private fun Modifier.toggleableRow(checked: Boolean, onToggle: (Boolean) -> Unit): Modifier =
    this.then(
        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
    )

@Composable
fun TextRow(label: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NearBlack, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = OliveDark, textAlign = TextAlign.End)
        }
    }
}

@Composable
fun DividerRow() {
    HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
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
        containerColor = White,
        title = { Text(title, fontWeight = FontWeight.ExtraBold, color = NearBlack) },
        text = { Text(message, color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) ErrorRed else OliveDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = OliveDark) }
        }
    )
}

@Composable
fun SectionTitle(text: String, color: Color = NearBlack, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        modifier = modifier.semantics { heading() }
    )
}

@Composable
fun ErrorText(text: String?) {
    if (text == null) return
    Spacer(Modifier.height(6.dp))
    Text(text, fontSize = 13.sp, color = ErrorRed)
}

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
        placeholder = placeholder?.let { { Text(it, color = TextSecondary) } },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (decimal) androidx.compose.ui.text.input.KeyboardType.Decimal
            else androidx.compose.ui.text.input.KeyboardType.Number
        ),
        modifier = modifier.fillMaxWidth()
    )
}
