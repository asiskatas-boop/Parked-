package com.parked.app.ui.v8

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parked.app.R
import com.parked.app.ui.Haptics
import com.parked.app.util.hasBluetoothPermission
import com.parked.app.util.hasLocationPermission
import kotlinx.coroutines.delay

private enum class Page { Start, How, Oops, AllSet }

private val DotGrey = Color(0xFFF0F0F0)
private val ResendBlue = Color(0xFFA5C3DF)
private val OopsDot = Color(0xFF4B7192)

/**
 * First launch, 0.8 design (four Figma frames, 393 x 971): Get started, How
 * it works (asks for the permissions on Next), Oops (when one was refused),
 * All set (white, then it turns into the gradient). The frame scales to fit
 * the screen, so nothing is squeezed on short phones.
 *
 * [onFinish] gets true when location and Bluetooth were allowed, so the app
 * can go on to choose the car.
 */
@Composable
fun OnboardingV8(initialPage: Int = 0, onFinish: (granted: Boolean) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var page by rememberSaveable { mutableStateOf(Page.entries[initialPage]) }
    var forward by remember { mutableStateOf(true) }
    var asked by rememberSaveable { mutableIntStateOf(0) }
    fun go(p: Page) { forward = p.ordinal >= page.ordinal; page = p }
    fun granted() = hasLocationPermission(context) && hasBluetoothPermission(context)

    val perms = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        asked++
        go(if (granted()) Page.AllSet else Page.Oops)
    }
    fun ask() {
        if (granted()) { go(Page.AllSet); return }
        // After a second refusal Android shows nothing more: open the app's settings instead.
        if (asked >= 2) runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        } else runCatching { launcher.launch(perms) }.onFailure { go(Page.Oops) }
    }
    // Back from Android's settings with the permissions allowed: carry on.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && page == Page.Oops && granted()) go(Page.AllSet)
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(White).systemBarsPadding()) {
        val outer = LocalDensity.current
        val fit = minOf(maxWidth.value / 393f, maxHeight.value / 971f)
        val gradient by animateFloatAsState(
            if (page == Page.AllSet) 1f else 0f, tween(300, delayMillis = 1300, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)), label = "gradient",
        )
        // The gradient fills the whole screen behind the frame.
        Image(
            painterResource(R.drawable.onboarding_gradient), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = gradient },
        )
        CompositionLocalProvider(LocalDensity provides Density(outer.density * fit, outer.fontScale.coerceIn(0.85f, 1.1f))) {
            Box(Modifier.align(Alignment.Center).size(393.dp, 971.dp)) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val d = if (forward) 1 else -1
                        (fadeIn(tween(300)) + slideInHorizontally(tween(450, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))) { d * it / 11 })
                            .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { -d * it / 16 })
                    },
                    label = "onboarding",
                ) { p ->
                    Box(Modifier.fillMaxSize()) {
                        when (p) {
                            Page.Start -> StartPage()
                            Page.How -> HowPage()
                            Page.Oops -> OopsPage(onResend = { Haptics.tick(view); ask() })
                            Page.AllSet -> AllSetPage(gradient)
                        }
                    }
                }
                // Dots and the button (Figma y 777 / 838, or 679 / 740 on "How it works").
                val how = page == Page.How
                Dots(
                    active = when (page) { Page.Start -> 0; Page.How, Page.Oops -> 1; Page.AllSet -> 2 },
                    oops = page == Page.Oops, onGradient = gradient,
                    modifier = Modifier.offset(137.dp, if (how) 679.dp else 777.dp),
                )
                Box(Modifier.offset(45.dp, if (how) 740.dp else 838.dp)) {
                    BigButton(
                        stringResource(
                            when (page) {
                                Page.Start -> R.string.v8_ob_get_started
                                Page.How, Page.Oops -> R.string.v8_ob_next
                                Page.AllSet -> R.string.v8_ob_go_map
                            }
                        )
                    ) {
                        Haptics.tick(view)
                        when (page) {
                            Page.Start -> go(Page.How)
                            Page.How -> ask()
                            Page.Oops -> go(Page.AllSet)
                            Page.AllSet -> onFinish(granted())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredText(text: String, baseline: Float, size: Float, weight: FontWeight, color: Color = Black) {
    Text(
        text,
        style = designStyle(size.sp, weight, color).copy(textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth().offset(y = topForBaseline(baseline, size)),
    )
}

@Composable
private fun StartPage() {
    // Logo: the car and "Parked!" with a navy "!" (Figma y 66-92).
    Row(Modifier.fillMaxWidth().offset(y = topForBaseline(92f, 35f)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(DesignIcons.TabCar, null, tint = Navy, modifier = Modifier.size(29.82.dp, 26.51.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString { append("Parked"); withStyle(SpanStyle(color = Navy)) { append("!") } },
            style = designStyle(35.sp, FontWeight.Bold, Black),
        )
    }
    Box(Modifier.fillMaxWidth().semantics { heading() }) {
        CenteredText(stringResource(R.string.v8_ob_forget_1), 424f, 28f, FontWeight.Bold)
        CenteredText(stringResource(R.string.v8_ob_forget_2), 458f, 28f, FontWeight.Bold)
        CenteredText(stringResource(R.string.v8_ob_me_too), 492f, 28f, FontWeight.Bold, Navy)
    }
}

@Composable
private fun HowPage() {
    CenteredText(stringResource(R.string.v8_ob_how), 78f, 28f, FontWeight.Bold)
    CenteredText(stringResource(R.string.v8_ob_three_steps), 135f, 21f, FontWeight.Light)
    // Three steps: bold title, then light text, 32 between lines (Figma x 46).
    val steps = listOf(
        Triple(R.string.v8_ob_a_title, R.string.v8_ob_a_body, 199f),
        Triple(R.string.v8_ob_b_title, R.string.v8_ob_b_body, 299f),
        Triple(R.string.v8_ob_c_title, R.string.v8_ob_c_body, 457f),
    )
    steps.forEach { (t, b, base) ->
        Column(Modifier.offset(46.dp, topForBaseline(base, 20f)).width(335.dp)) {
            Text(stringResource(t), style = designStyle(20.sp, FontWeight.Bold, Black).copy(lineHeight = 32.sp))
            Text(stringResource(b), style = designStyle(20.sp, FontWeight.Light, Black).copy(lineHeight = 32.sp))
        }
    }
    Text(
        buildAnnotatedString {
            append(stringResource(R.string.v8_ob_spots_1)); append(" ")
            withStyle(SpanStyle(color = Navy)) { append(stringResource(R.string.v8_ob_spots_always)) }
            append(" "); append(stringResource(R.string.v8_ob_spots_2))
        },
        style = designStyle(16.sp, FontWeight.Bold, Black),
        modifier = Modifier.offset(46.dp, topForBaseline(613f, 16f)),
    )
    // "Also" box (Figma 29,854 - 363,925, radius 14).
    Box(
        Modifier
            .offset(29.dp, 854.dp)
            .size(334.dp, 71.dp)
            .background(CardGrey, RoundedCornerShape(14.dp))
            .padding(start = 18.dp, end = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.v8_ob_also)) }
                append(" "); append(stringResource(R.string.v8_ob_also_body))
            },
            style = designStyle(13.sp, FontWeight.Normal, Ink).copy(lineHeight = 22.sp),
        )
    }
}

@Composable
private fun OopsPage(onResend: () -> Unit) {
    val view = LocalView.current
    val shake = remember { Animatable(0f) }
    // "Oops": the text shakes once, with a slight wobble, and the phone buzzes.
    LaunchedEffect(Unit) {
        delay(260)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) view.context.getSystemService(android.os.Vibrator::class.java)
                ?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40, 60, 30), -1))
        }
        shake.animateTo(0f, keyframes {
            durationMillis = 620
            -14f at 75; 12f at 175; -9f at 270; 6f at 370; -3f at 470; 0f at 620
        })
    }
    Box(Modifier.fillMaxSize().graphicsLayer { translationX = shake.value * density; rotationZ = shake.value * 0.085f }) {
        CenteredText(stringResource(R.string.v8_ob_oops), 387f, 28f, FontWeight.Bold)
        CenteredText(stringResource(R.string.v8_ob_oops_1), 437f, 19f, FontWeight.Normal)
        CenteredText(stringResource(R.string.v8_ob_oops_2), 469f, 19f, FontWeight.Normal)
        Box(
            Modifier
                .offset(117.dp, 557.dp)
                .size(158.dp, 55.dp)
                .pressable(RoundedCornerShape(14.dp), onClick = onResend)
                .background(ResendBlue, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.v8_ob_resend), style = designStyle(15.sp, FontWeight.Normal, Black)) }
    }
}

@Composable
private fun AllSetPage(gradient: Float) {
    // Black text on white turns white on the gradient.
    val c = androidx.compose.ui.graphics.lerp(Black, White, gradient)
    CenteredText(stringResource(R.string.v8_ob_all_set), 387f, 28f, FontWeight.Bold, c)
    Text(
        buildAnnotatedString {
            append(stringResource(R.string.v8_ob_next_time)); append(" ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Parked!") }
            append("\n"); append(stringResource(R.string.v8_ob_saves_itself))
        },
        style = designStyle(21.sp, FontWeight.Normal, c).copy(textAlign = TextAlign.Center, lineHeight = 32.sp),
        modifier = Modifier.fillMaxWidth().offset(y = topForBaseline(438f, 21f) - 3.dp),
    )
    CenteredText(stringResource(R.string.v8_ob_save_anytime), 524f, 15f, FontWeight.Normal, c)
}

@Composable
private fun Dots(active: Int, oops: Boolean, onGradient: Float, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(31.dp)) {
        repeat(3) { i ->
            val target = when {
                i == active && oops -> OopsDot
                i == active -> Navy
                else -> androidx.compose.ui.graphics.lerp(DotGrey, White, onGradient)
            }
            val col by androidx.compose.animation.animateColorAsState(target, tween(300), label = "dot$i")
            Box(Modifier.size(19.dp).background(col, CircleShape))
        }
    }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(302.dp, 72.dp)
            .pressable(R15, onClick = onClick)
            .background(Navy, R15),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(text, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "btn") {
            Text(it, style = designStyle(22.sp, FontWeight.SemiBold, White), maxLines = 1)
        }
    }
}
