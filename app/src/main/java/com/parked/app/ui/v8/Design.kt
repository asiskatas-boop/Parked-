@file:OptIn(ExperimentalTextApi::class)

package com.parked.app.ui.v8

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parked.app.R

/*
 * 0.8 design (Figma, frames 393 wide). Values here are copied from the Figma
 * file; screens place things at the Figma coordinates, so they match it.
 */

/** Navy: buttons, links, the active tab. White on it 8.4:1. */
val Navy = Color(0xFF225079)
/** Titles. */
val Black = Color(0xFF000000)
/** Body text and button labels. */
val Ink = Color(0xFF15110E)
/** Cards, map, sheets, the GPS banner. */
val CardGrey = Color(0xFFD9D9D9)
/** Inactive tab icons, card outlines, switch track outline. */
val IconGrey = Color(0xFFACACAC)
/** Notes outline, switch knob when off. */
val LineGrey = Color(0xFF909090)
/** Car colour picker panel. */
val PickerGrey = Color(0xFF7F7F7F)
/** Fuel bar. */
val FuelGreen = Color(0xFF6EC436)
/** Small accent dots (due soon, waiting for GPS). Not for text: 2.9:1 on white. */
val Accent = Color(0xFFCE84AD)
/** Error rings and messages. */
val ErrorRed = Color(0xFFB42318)
val White = Color(0xFFFFFFFF)

/** Placeholders: Ink at half strength, as in the Figma file. */
val Placeholder = Ink.copy(alpha = 0.5f)

/** Car colours offered by the colour picker (Figma). */
val CarColours = listOf(
    0xFFEFCD33, 0xFFFD876E, 0xFFFF3700, 0xFF582159, 0xFFFE77F6,
    0xFFFFFFFF, 0xFF0437C0, 0xFF47EBEC, 0xFF22C17A, 0xFF212121,
).map { Color(it) }

/** Corner radius of buttons, cards and fields. */
val R15 = androidx.compose.foundation.shape.RoundedCornerShape(15.dp)

private fun inter(weight: FontWeight) = Font(
    R.font.inter, weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Inter 3.19 (variable), with Greek. */
val Inter = FontFamily(
    inter(FontWeight.Light), inter(FontWeight.Normal), inter(FontWeight.Medium),
    inter(FontWeight.SemiBold), inter(FontWeight.Bold),
)

/** Width of the Figma frames. */
const val DESIGN_WIDTH = 393f

/**
 * Scales dp so the screen is exactly [DESIGN_WIDTH] wide: the design is laid
 * out once, at Figma's size, and looks the same on every phone width.
 * Text size follows the user's setting, up to 115%, so large text can't break
 * the layout.
 */
@Composable
fun DesignScale(content: @Composable () -> Unit) {
    BoxWithConstraints {
        val outer = LocalDensity.current
        val factor = maxWidth.value / DESIGN_WIDTH
        val scaled = Density(outer.density * factor, outer.fontScale.coerceIn(0.85f, 1.15f))
        CompositionLocalProvider(LocalDensity provides scaled) { content() }
    }
}

/** Inter's ascent (hhea) as a share of the font size: top of the line box to the baseline. */
private const val ASCENT = 0.96875f
/** Ascent + descent: the natural line height Figma uses for "auto". */
private const val LINE = 1.20996f

/** Text style with Figma's line box (no extra font padding). */
fun designStyle(size: TextUnit, weight: FontWeight, color: Color, letterSpacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = Inter,
    fontSize = size,
    fontWeight = weight,
    color = color,
    letterSpacing = letterSpacing,
    lineHeight = size * LINE,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
)

/** Top of a text line whose baseline sits at [baseline] (both in design dp). */
fun topForBaseline(baseline: Float, size: Float): Dp = (baseline - size * ASCENT).dp

/**
 * One line of text placed like in Figma: [x] is the left edge and [baseline]
 * the baseline, relative to the parent box.
 */
@Composable
fun FigmaText(
    text: String,
    x: Float,
    baseline: Float,
    size: Float,
    weight: FontWeight,
    color: Color = Ink,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = designStyle(size.sp, weight, color),
        maxLines = 1,
        softWrap = false,
        modifier = modifier.offset(x.dp, topForBaseline(baseline, size)),
    )
}
