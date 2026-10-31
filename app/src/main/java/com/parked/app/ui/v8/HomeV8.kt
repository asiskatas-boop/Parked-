package com.parked.app.ui.v8

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parked.app.R
import com.parked.app.ui.Haptics

/** What the grey banner at the top asks for. Null: no banner. */
enum class HomeBanner { Permission, LocationOff, BluetoothOff, BluetoothRefused, AutoParkLost }

/**
 * Home, 0.8 design. Positions are the Figma coordinates (frame 393 wide);
 * comments give the Figma y of each element.
 */
@Composable
fun HomeV8(
    loaded: Boolean,
    hasSpot: Boolean,
    banner: HomeBanner?,
    autoParkOn: Boolean,
    /** Goes up each time the user refuses to turn Bluetooth on: the banner shakes. */
    bannerShake: Int = 0,
    parkedLabel: String?,
    distanceLabel: String?,
    waitingForGps: Boolean,
    saving: Boolean,
    note: String?,
    showRecenter: Boolean,
    map: @Composable (Modifier) -> Unit,
    onBannerAction: () -> Unit,
    onAutoPark: () -> Unit,
    onRecenter: () -> Unit,
    onDirections: () -> Unit,
    onSaveSpot: () -> Unit,
    onShare: () -> Unit,
    onLevel: () -> Unit,
    onPhoto: () -> Unit,
    onTime: () -> Unit,
    onNote: (String?) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(White)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // GPS banner (y 16-83). Folds away when the problem is fixed.
        AnimatedVisibility(
            visible = banner != null,
            enter = fadeIn(tween(220)) + expandVertically(spring(dampingRatio = 0.9f, stiffness = 500f)),
            exit = fadeOut(tween(200)) + shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 500f)),
        ) {
            var last by remember { mutableStateOf(banner ?: HomeBanner.Permission) }
            if (banner != null) last = banner
            Banner(last, bannerShake, onBannerAction)
        }
        val top by animateFloatAsState(if (banner != null) 27f else 16f, spring(dampingRatio = 0.9f, stiffness = 500f), label = "top")
        Spacer(Modifier.height(top.dp))

        // AutoPark row (Figma y 110) and the title (baseline 186.33).
        // When AutoPark is on there's nothing to say: the row folds away and the title moves up.
        val lift by animateFloatAsState(if (autoParkOn) 36f else 0f, spring(dampingRatio = 0.9f, stiffness = 500f), label = "lift")
        Box(Modifier.fillMaxWidth().height((108f - lift).dp)) {
            val apAlpha by animateFloatAsState(if (autoParkOn) 0f else 1f, tween(200), label = "apRow")
            if (apAlpha > 0f) Row(
                Modifier
                    .offset(24.dp, (-8).dp)
                    .graphicsLayer { alpha = apAlpha }
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onAutoPark)
                    .padding(start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DesignIcons.AutoParkCar, null, tint = Black, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(if (autoParkOn) R.string.v8_autopark_on else R.string.v8_autopark_off),
                    style = designStyle(15.sp, FontWeight.Light, Black),
                )
            }
            AnimatedContent(
                targetState = hasSpot || !loaded,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "title",
                modifier = Modifier.offset(y = (-lift).dp).semantics { heading() },
            ) { saved ->
                FigmaText(
                    stringResource(if (saved) R.string.v8_car_here else R.string.v8_no_spot),
                    x = 29f, baseline = 76.33f, size = 28f, weight = FontWeight.Bold, color = Black,
                )
            }
        }

        // Map card (y 218-566, radius 14) with the recenter button.
        Box(
            Modifier
                .padding(start = 23.dp)
                .size(346.dp, 348.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CardGrey)
        ) {
            map(Modifier.fillMaxSize())
            Text(
                stringResource(R.string.osm_attribution),
                style = designStyle(9.sp, FontWeight.Normal, Ink.copy(alpha = 0.7f)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(White.copy(alpha = 0.8f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            if (showRecenter) RecenterButton(Modifier.align(Alignment.TopStart).offset(287.dp, 288.dp), onRecenter)
        }

        if (!loaded) return@Column
        AnimatedContent(
            targetState = hasSpot,
            transitionSpec = { fadeIn(tween(250, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            label = "homeBody",
        ) { saved ->
            if (saved) SavedBody(parkedLabel, distanceLabel, waitingForGps, note, onDirections, onSaveSpot, onShare, onLevel, onPhoto, onTime, onNote)
            else EmptyBody(saving, onSaveSpot)
        }
    }
}

@Composable
private fun Banner(issue: HomeBanner, shakeKey: Int, onAction: () -> Unit) {
    val (l1, l2, action) = when (issue) {
        HomeBanner.Permission -> Triple(R.string.v8_banner_gps_1, R.string.v8_banner_gps_2, R.string.v8_turn_on)
        HomeBanner.LocationOff -> Triple(R.string.v8_banner_loc_1, R.string.v8_banner_loc_2, R.string.v8_turn_on)
        HomeBanner.BluetoothOff -> Triple(R.string.v8_banner_bt_1, R.string.v8_banner_bt_2, R.string.v8_turn_on)
        HomeBanner.BluetoothRefused -> Triple(R.string.v8_banner_bt_oops_1, R.string.v8_banner_bt_oops_2, R.string.v8_turn_on)
        HomeBanner.AutoParkLost -> Triple(R.string.v8_banner_lost_1, R.string.v8_banner_lost_2, R.string.v8_fix)
    }
    // Same shake as the onboarding's "Oops!".
    val shake = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        shake.animateTo(0f, androidx.compose.animation.core.keyframes {
            durationMillis = 620
            -14f at 75; 12f at 175; -9f at 270; 6f at 370; -3f at 470; 0f at 620
        })
    }
    Box(Modifier.fillMaxWidth().height(83.dp)) {
        Box(
            Modifier
                .offset(24.dp, 16.dp)
                .graphicsLayer { translationX = shake.value * density }
                .size(344.dp, 67.dp)
                .clip(R15)
                .background(CardGrey)
                .clickable(role = Role.Button, onClick = onAction)
        ) {
            Icon(DesignIcons.Pin, null, tint = Black, modifier = Modifier.offset(15.dp, 24.dp).size(16.dp, 20.dp))
            FigmaText(stringResource(l1), x = 46f, baseline = 29f, size = 15f, weight = FontWeight.Light, color = Black)
            FigmaText(stringResource(l2), x = 46f, baseline = 47f, size = 15f, weight = FontWeight.Light, color = Black)
            // "Turn on" sits 20 from the right edge (Figma x 302-348).
            Text(
                stringResource(action),
                style = designStyle(13.sp, FontWeight.Medium, Navy),
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).offset((-20).dp, topForBaseline(39f, 13f)),
            )
        }
    }
}

@Composable
private fun RecenterButton(modifier: Modifier, onClick: () -> Unit) {
    val view = LocalView.current
    var pings by remember { mutableIntStateOf(0) }
    val ring = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(pings) {
        if (pings == 0) return@LaunchedEffect
        ring.snapTo(0f); ring.animateTo(1f, tween(700))
    }
    Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer {
                    val s = 1f + 0.7f * ring.value
                    scaleX = s; scaleY = s; alpha = if (ring.value in 0.001f..0.999f) 0.25f * (1f - ring.value) else 0f
                }
                .clip(CircleShape)
                .background(Navy)
        )
        Box(
            Modifier
                .size(44.dp)
                .pressable(CircleShape) { Haptics.tick(view); pings++; onClick() }
                .background(White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DesignIcons.Recenter, stringResource(R.string.map_center_car), tint = Black, modifier = Modifier.size(24.dp))
        }
    }
}

/** Shrinks a little while pressed and springs back: the press feel of every button. */
@Composable
fun Modifier.pressable(shape: androidx.compose.ui.graphics.Shape, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.965f else 1f,
        if (pressed) tween(120) else spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .clickable(interactionSource = interaction, indication = null, role = role, onClick = onClick)
}

@Composable
private fun SavedBody(
    parkedLabel: String?,
    distanceLabel: String?,
    waitingForGps: Boolean,
    note: String?,
    onDirections: () -> Unit,
    onSaveSpot: () -> Unit,
    onShare: () -> Unit,
    onLevel: () -> Unit,
    onPhoto: () -> Unit,
    onTime: () -> Unit,
    onNote: (String?) -> Unit,
) {
    val view = LocalView.current
    Column {
        // "Parked today at 13:25" and the distance (baseline 600, 34 under the map).
        // One row, so a long label and "GPS…" can never overlap: the label gives way.
        val waitingCd = stringResource(R.string.v8_waiting_gps)
        Box(Modifier.fillMaxWidth().height(53.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 25.dp, end = 28.dp).offset(y = topForBaseline(34f, 15f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    parkedLabel ?: stringResource(R.string.parked_recently),
                    style = designStyle(15.sp, FontWeight.Light, Black), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(0.001f).widthIn(min = 12.dp))
                AnimatedContent(
                    targetState = waitingForGps,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "distance",
                ) { waiting ->
                    if (waiting) Row(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = waitingCd },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PulsingDot(); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.v8_waiting_gps_short), style = designStyle(15.sp, FontWeight.Light, Black), maxLines = 1)
                    } else if (distanceLabel != null) {
                        Text(distanceLabel, style = designStyle(15.sp, FontWeight.Light, Black), maxLines = 1)
                    }
                }
            }
        }
        // Go to my car (y 619-668).
        Box(
            Modifier
                .padding(start = 25.dp)
                .size(344.dp, 49.dp)
                .pressable(R15) { Haptics.tick(view); onDirections() }
                .background(Navy, R15),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.v8_go_to_car), style = designStyle(16.sp, FontWeight.SemiBold, White), maxLines = 1)
        }
        Spacer(Modifier.height(14.dp))
        // Save this spot / Share spot (y 682-731).
        Row(Modifier.padding(start = 25.dp)) {
            OutlineButton(DesignIcons.SaveSpot, 18f, 18f, 10f, stringResource(R.string.v8_save_spot), 12f, Modifier.size(168.dp, 49.dp), onSaveSpot)
            Spacer(Modifier.width(8.dp))
            OutlineButton(DesignIcons.Share, 16f, 21f, 7f, stringResource(R.string.v8_share_spot), 13f, Modifier.size(168.dp, 49.dp), onShare)
        }
        Spacer(Modifier.height(11.dp))
        // Level / Photo / Time (y 742-791).
        Row(Modifier.padding(start = 25.dp)) {
            OutlineButton(DesignIcons.Level, 19.83f, 19.6f, 7.07f, stringResource(R.string.tile_level), 13f, Modifier.size(104.dp, 49.dp), onLevel)
            Spacer(Modifier.width(16.dp))
            OutlineButton(DesignIcons.AddPhoto, 22f, 20f, 10f, stringResource(R.string.v8_photo), 13f, Modifier.size(104.dp, 49.dp), onPhoto)
            Spacer(Modifier.width(16.dp))
            OutlineButton(DesignIcons.Time, 20f, 20f, 7f, stringResource(R.string.v8_time), 13f, Modifier.size(104.dp, 49.dp), onTime)
        }
        Spacer(Modifier.height(11.dp))
        NoteField(note, onNote)
        // Room under the last row, as in Figma (851 to the tab bar at 890).
        Spacer(Modifier.height(39.dp))
    }
}

@Composable
private fun EmptyBody(saving: Boolean, onSaveSpot: () -> Unit) {
    val view = LocalView.current
    Column {
        // Two lines of help (baselines 597 and 615), then the button (y 635).
        Text(
            stringResource(R.string.v8_empty_help),
            style = designStyle(15.sp, FontWeight.Light, Black).copy(lineHeight = 18.sp),
            modifier = Modifier.padding(start = 25.dp, end = 30.dp, top = 17.dp).widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(15.dp))
        Box(
            Modifier
                .padding(start = 25.dp)
                .size(344.dp, 49.dp)
                .pressable(R15) { Haptics.tick(view); onSaveSpot() }
                .background(Navy, R15),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (saving) R.string.saving else R.string.v8_save_spot),
                style = designStyle(16.sp, FontWeight.SemiBold, White), maxLines = 1,
            )
        }
        Spacer(Modifier.height(39.dp))
    }
}

/** White button with a 1 px navy outline: icon and label centred, like the Figma buttons. */
@Composable
private fun OutlineButton(
    icon: ImageVector,
    iconW: Float,
    iconH: Float,
    gap: Float,
    label: String,
    size: Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Row(
        modifier
            .pressable(R15) { Haptics.tick(view); onClick() }
            .border(1.dp, Navy, R15),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Black, modifier = Modifier.size(iconW.dp, iconH.dp))
        Spacer(Modifier.width(gap.dp))
        Text(label, style = designStyle(size.sp, FontWeight.Bold, Ink), maxLines = 1)
    }
}

/** The note, typed in place (y 802-851, grey outline). Saved when the keyboard closes. */
@Composable
private fun NoteField(note: String?, onNote: (String?) -> Unit) {
    var text by remember(note) { mutableStateOf(note.orEmpty()) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val outline = if (focused) Navy else LineGrey
    val width by animateFloatAsState(if (focused) 2f else 1f, tween(180), label = "noteRing")
    Box(
        Modifier
            .padding(start = 25.dp)
            .size(344.dp, 49.dp)
            .border(width.dp, outline, R15)
    ) {
        Icon(DesignIcons.Notes, null, tint = Black, modifier = Modifier.offset(39.dp, 18.dp).size(22.dp, 15.07.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = designStyle(13.sp, FontWeight.Normal, Ink),
            cursorBrush = SolidColor(Navy),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier
                .offset(76.dp, topForBaseline(30f, 13f))
                .width(250.dp)
                .onFocusChanged {
                    if (focused && !it.isFocused && text.trim() != note.orEmpty().trim()) onNote(text.trim().ifEmpty { null })
                    focused = it.isFocused
                },
            decorationBox = { inner ->
                if (text.isEmpty()) Text(stringResource(R.string.v8_note_hint), style = designStyle(13.sp, FontWeight.Normal, Ink), maxLines = 1)
                inner()
            },
        )
    }
}

@Composable
private fun PulsingDot() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable<Float>(tween(800), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Box(Modifier.size(8.dp).graphicsLayer { alpha = a; scaleX = 0.7f + 0.3f * a; scaleY = 0.7f + 0.3f * a }.background(Accent, CircleShape))
}

/** The 0.8 tab bar: three icons, navy when active, at the Figma positions (bar 81 high). */
@Composable
fun TabBarV8(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    val view = LocalView.current
    val icons = listOf(DesignIcons.TabHome to (39f to 27f), DesignIcons.TabCar to (182f to 27f), DesignIcons.TabSettings to (324f to 26f))
    Box(
        Modifier
            .fillMaxWidth()
            .background(White)
            .navigationBarsPadding()
            .height(81.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, role = Role.Tab,
                            onClickLabel = label,
                        ) { if (i != selected) { Haptics.tick(view); onSelect(i) } }
                        .semantics { contentDescription = label; this.selected = i == selected }
                )
            }
        }
        icons.forEachIndexed { i, (icon, pos) ->
            val tint by androidx.compose.animation.animateColorAsState(if (i == selected) Navy else IconGrey, tween(250), label = "tab$i")
            Icon(
                icon, null, tint = tint,
                modifier = Modifier
                    .offset(pos.first.dp, pos.second.dp)
                    .size(icon.defaultWidth, icon.defaultHeight),
            )
        }
    }
}
