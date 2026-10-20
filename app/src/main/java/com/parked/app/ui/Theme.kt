@file:OptIn(ExperimentalTextApi::class)

package com.parked.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parked.app.R

/*
 * 0.7 design: warm, flat and light (based on the MarketNest design system,
 * with its terracotta replaced by Parked green), with Rivian's status-first
 * layout and Revolut's one-big-number screens.
 *
 * Contrast on Bg (#FFFBF5): Text 16.6:1, TextMuted 7.3:1, Green 5.3:1,
 * white on Green 5.5:1, Danger 6.0:1, Warn 7.1:1.
 */

/** Page background: warm cream. */
val Bg = Color(0xFFFFFBF5)

/** Cards, sheets, the tab bar. */
val Surface = Color(0xFFFFFFFF)

/** Unselected chips, empty bar tracks. */
val SurfaceSunk = Color(0xFFF3EEE6)

/** 1 px card and row borders. */
val Border = Color(0xFFE7E5E4)

/** Field outlines and chip borders. */
val BorderStrong = Color(0xFFD6D3D1)

/** Primary text. */
val TextPrimary = Color(0xFF1C1917)

/** Secondary text. */
val TextMuted = Color(0xFF57534E)

/** Placeholders, disabled text, chevrons. */
val TextFaint = Color(0xFFA8A29E)

/** Parked green: every action, the active tab, the car pin. */
val Green = Color(0xFF3F7A22)

/** Pressed state of green buttons. */
val GreenDark = Color(0xFF2F5E19)

/** Tinted background for "on" and "saved" states. */
val GreenSoft = Color(0xFFEEF5E8)

/** Text and icons on Green. */
val OnGreen = Color(0xFFFFFFFF)

/** Forest green for small tags. */
val Forest = Color(0xFF365314)

/** Warm accents: the studio floor under the car, decorative fills. */
val Sand = Color(0xFFD4A373)
val SandSoft = Color(0xFFF6EBDD)

/** Errors and destructive actions. */
val Danger = Color(0xFFB91C1C)

/** Warnings such as a due service: text, and its chip background. */
val Warn = Color(0xFF92400E)
val WarnBg = Color(0xFFFEF3C7)

/** "OK" status: text and chip background. */
val Ok = Color(0xFF166534)
val OkBg = Color(0xFFDCFCE7)

/** Corner radii: 4 for buttons, chips and fields; 12 for cards and sheets. */
val RadiusSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
val RadiusCard = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)

/**
 * Type scale. Headings use Literata (a warm serif with full Greek), body text
 * uses Open Sans. Body copy is 15-17 sp with a line height of about 1.5.
 */
object TypeScale {
    val Caption = 13.sp
    val Supporting = 14.sp
    val Label = 15.sp
    val Body = 16.sp
    val Emphasis = 17.sp
    val Section = 21.sp
    val SheetTitle = 24.sp
    val PageTitle = 32.sp
    val Hero = 40.sp
    val BigNumber = 52.sp
    val Display = 44.sp
}

private fun literata(weight: FontWeight) = Font(
    R.font.literata, weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private fun openSans(weight: FontWeight) = Font(
    R.font.open_sans, weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Headings and big numbers. */
val HeadingFont = FontFamily(literata(FontWeight.Normal), literata(FontWeight.Medium), literata(FontWeight.SemiBold), literata(FontWeight.Bold))

/** Everything else. */
val BodyFont = FontFamily(openSans(FontWeight.Normal), openSans(FontWeight.Medium), openSans(FontWeight.SemiBold), openSans(FontWeight.Bold))

private fun parkedTypography(): Typography {
    val base = Typography()
    fun TextStyle.b() = copy(fontFamily = BodyFont)
    fun TextStyle.h() = copy(fontFamily = HeadingFont)
    return base.copy(
        displayLarge = base.displayLarge.h(),
        displayMedium = base.displayMedium.h(),
        displaySmall = base.displaySmall.h(),
        headlineLarge = base.headlineLarge.h(),
        headlineMedium = base.headlineMedium.h(),
        headlineSmall = base.headlineSmall.h(),
        titleLarge = base.titleLarge.b(),
        titleMedium = base.titleMedium.b(),
        titleSmall = base.titleSmall.b(),
        bodyLarge = base.bodyLarge.b(),
        bodyMedium = base.bodyMedium.b(),
        bodySmall = base.bodySmall.b(),
        labelLarge = base.labelLarge.b(),
        labelMedium = base.labelMedium.b(),
        labelSmall = base.labelSmall.b(),
    )
}

@Composable
fun ParkedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = OnGreen,
            primaryContainer = GreenSoft,
            onPrimaryContainer = Forest,
            secondary = Forest,
            onSecondary = OnGreen,
            background = Bg,
            onBackground = TextPrimary,
            surface = Surface,
            onSurface = TextPrimary,
            surfaceVariant = SurfaceSunk,
            onSurfaceVariant = TextMuted,
            surfaceContainerLowest = Surface,
            surfaceContainerLow = Surface,
            surfaceContainer = Surface,
            surfaceContainerHigh = Surface,
            surfaceContainerHighest = SurfaceSunk,
            outline = BorderStrong,
            outlineVariant = Border,
            inverseSurface = TextPrimary,
            inverseOnSurface = Bg,
            inversePrimary = GreenSoft,
            error = Danger,
            onError = OnGreen,
        ),
        typography = parkedTypography(),
        content = content
    )
}
