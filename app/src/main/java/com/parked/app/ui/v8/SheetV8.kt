package com.parked.app.ui.v8

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parked.app.ui.Haptics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The 0.8 bottom sheet: grey, rounded, with a grab handle; the screen behind
 * dims. Springs up, drags down to close, Back closes. Place it last inside the
 * screen's root Box so it sits above the content and under the tab bar.
 */
@Composable
fun SheetV8(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x47111827))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
    }
    if (visible) BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) { it },
            exit = slideOutVertically(tween(260)) { it },
        ) {
            val drag = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            val view = LocalView.current
            Column(
                Modifier
                    .imePadding()
                    .offset { IntOffset(0, drag.value.roundToInt()) }
                    .fillMaxWidth()
                    .clip(R15)
                    .background(CardGrey)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } },
                        onDragStopped = { v ->
                            if (drag.value > 260f || v > 1600f) { Haptics.tick(view); onDismiss(); drag.snapTo(0f) }
                            else drag.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                        },
                    )
                    .padding(start = 41.dp, end = 41.dp, bottom = 33.dp)
            ) {
                // Grab handle (36 x 4, 8 from the top).
                Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.size(36.dp, 4.dp).background(IconGrey, RoundedCornerShape(2.dp)))
                }
                content()
            }
        }
    }
}

/** Sheet title: 28 bold, baseline 49 from the sheet top (Figma "Add fuel"). */
@Composable
fun SheetTitle(text: String) {
    Text(
        text,
        style = designStyle(28.sp, FontWeight.Bold, Black),
        modifier = Modifier.padding(top = 10.dp).semantics { heading() },
    )
}

/** One line under the title (13 regular), 38 below the title's baseline. */
@Composable
fun SheetSub(text: String) {
    Text(text, style = designStyle(13.sp, FontWeight.Normal, Ink).copy(lineHeight = 17.sp), modifier = Modifier.padding(start = 2.dp, top = 15.dp))
}

/** Navy button, 41 high (Figma "Save"). */
@Composable
fun PrimaryV8(text: String, modifier: Modifier = Modifier, height: Float = 41f, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .pressable(R15) { Haptics.tick(view); onClick() }
            .background(Navy, R15),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = designStyle(16.sp, FontWeight.SemiBold, White), maxLines = 1) }
}

/** Text link (15 light, navy), e.g. "Edit". */
@Composable
fun LinkV8(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        style = designStyle(15.sp, FontWeight.Light, Navy),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp),
    )
}

/**
 * White field, 38 high (Figma "How much money you paid"): placeholder at half
 * strength, a navy ring while typing, a red ring and a short message on error.
 */
@Composable
fun FieldV8(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    prefix: String? = null,
    suffix: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    shake: Int = 0,
) {
    var focused by remember { mutableStateOf(false) }
    val ring by animateColorAsState(
        when { error != null -> ErrorRed; focused -> Navy; else -> Color.Transparent }, tween(180), label = "ring",
    )
    val offset = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake == 0) return@LaunchedEffect
        for (x in listOf(-5f, 4f, -3f, 2f, 0f)) offset.animateTo(x, tween(70))
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .graphicsLayer { translationX = offset.value * density }
            .background(White, R15)
            .border(2.dp, ring, R15)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefix != null) {
                Text(prefix, style = designStyle(13.sp, FontWeight.Light, Placeholder), maxLines = 1)
                Spacer(Modifier.width(10.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = designStyle(13.sp, FontWeight.Light, Ink).copy(textAlign = if (prefix != null) TextAlign.End else TextAlign.Start),
                cursorBrush = SolidColor(Navy),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && prefix == null) Text(placeholder, style = designStyle(13.sp, FontWeight.Light, Placeholder), maxLines = 1)
                        inner()
                    }
                },
            )
            if (suffix != null) {
                Spacer(Modifier.width(6.dp))
                Text(suffix, style = designStyle(13.sp, FontWeight.Light, Ink), maxLines = 1)
            }
        }
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Text(
                error.orEmpty(),
                style = designStyle(12.sp, FontWeight.Medium, ErrorRed),
                modifier = Modifier.background(White).padding(start = 6.dp),
            )
        }
    }
}

/** Switch (44 x 24): white track with a grey knob when off, navy with a white knob when on. */
@Composable
fun SwitchV8(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val view = LocalView.current
    val knob by animateDpAsState(if (checked) 23.dp else 3.dp, spring(dampingRatio = 0.55f, stiffness = 600f), label = "knob")
    val track by animateColorAsState(if (checked) Navy else White, tween(250), label = "track")
    val dot by animateColorAsState(if (checked) White else LineGrey, tween(250), label = "dot")
    Box(
        Modifier
            .size(44.dp, 24.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(role = Role.Switch, onClickLabel = label) { Haptics.tick(view); onChange(!checked) }
            .semantics { stateDescription = if (checked) "on" else "off" }
    ) {
        Box(Modifier.offset(knob, 3.dp).size(18.dp).background(dot, CircleShape))
    }
}

/** 2-column grid of navy buttons, 49 high (Figma maintenance sheet). */
@Composable
fun GridV8(items: List<String>, onClick: (Int) -> Unit) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEachIndexed { col, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(49.dp)
                            .pressable(R15) { Haptics.tick(view); onClick(row * 2 + col) }
                            .background(Navy, R15),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = designStyle(16.sp, FontWeight.SemiBold, White), maxLines = 1) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** White option rows (48 high) with a radio on the right. */
@Composable
fun OptionsV8(items: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit) {
    val view = LocalView.current
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { i, (title, sub) ->
            val on = i == selected
            val fill by animateColorAsState(if (on) Navy else Color.Transparent, tween(200), label = "radio")
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .pressable(R15, Role.RadioButton) { Haptics.tick(view); onSelect(i) }
                    .background(White, R15)
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = designStyle(13.sp, FontWeight.Bold, Ink), maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Text(sub, style = designStyle(13.sp, FontWeight.Light, Ink), maxLines = 1, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(fill).border(2.dp, if (on) Navy else IconGrey, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(DesignIcons.Check, null, tint = White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Outlined row like the Figma "New reminder" row: 3 px grey inside border, 58 high. */
@Composable
fun RowCardV8(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val view = LocalView.current
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .pressable(R15) { Haptics.tick(view); onClick() }
            .border(3.dp, CardGrey, R15)
            .padding(start = 17.dp, end = 19.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
