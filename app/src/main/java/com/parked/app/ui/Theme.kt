@file:OptIn(ExperimentalTextApi::class)

package com.parked.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.parked.app.R

/*
 * 0.4 design: dark only, one accent. Contrast on Ink (#000000):
 * Paper 21:1, Muted 7.4:1, Accent 7.0:1, Danger 7.6:1, Warn 12:1.
 * Black text on Accent (the active switch track) is 3:1; it carries no text.
 */

/** Page background. */
val Ink = Color(0xFF000000)

/** Raised panels: the car panel, sheets, dialogs, the tab bar. */
val Panel = Color(0xFF1A1A1A)

/** Input fields and pressed rows. */
val Field = Color(0xFF262626)

/** Hairline dividers and field outlines. */
val Line = Color(0xFF333333)

/** Primary text and the primary button. */
val Paper = Color(0xFFFFFFFF)

/** Secondary text. 7.4:1 on Ink, 6.2:1 on Panel. */
val Muted = Color(0xFF9A9A9A)

/** The single accent: arrows, active tab, distance, progress, switches. */
val Accent = Color(0xFFFF7500)

/** Errors and destructive actions. */
val Danger = Color(0xFFFF6B5E)

/** Warnings such as a due service. */
val Warn = Color(0xFFFFC14D)

/**
 * Type scale. Every text size comes from here. Body copy is 15-17 sp with a
 * line height of at least 1.5; headings are large and light, as in the
 * reference.
 */
object TypeScale {
    val Caption = 13.sp
    val Supporting = 14.sp
    val Label = 15.sp
    val Body = 16.sp
    val Emphasis = 17.sp
    val Section = 20.sp
    val SheetTitle = 26.sp
    val PageTitle = 34.sp
    val Hero = 40.sp
    val BigNumber = 56.sp
    val Display = 60.sp
}

/** Sofia Sans, one variable file with a 1-1000 weight axis and full Greek. */
private fun sofia(weight: FontWeight) = Font(
    R.font.sofia_sans,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val SofiaSans = FontFamily(
    sofia(FontWeight.Light),
    sofia(FontWeight.Normal),
    sofia(FontWeight.Medium),
    sofia(FontWeight.SemiBold),
    sofia(FontWeight.Bold),
)

private fun sofiaTypography(): Typography {
    val base = Typography()
    fun TextStyle.s() = copy(fontFamily = SofiaSans)
    return base.copy(
        displayLarge = base.displayLarge.s(),
        displayMedium = base.displayMedium.s(),
        displaySmall = base.displaySmall.s(),
        headlineLarge = base.headlineLarge.s(),
        headlineMedium = base.headlineMedium.s(),
        headlineSmall = base.headlineSmall.s(),
        titleLarge = base.titleLarge.s(),
        titleMedium = base.titleMedium.s(),
        titleSmall = base.titleSmall.s(),
        bodyLarge = base.bodyLarge.s(),
        bodyMedium = base.bodyMedium.s(),
        bodySmall = base.bodySmall.s(),
        labelLarge = base.labelLarge.s(),
        labelMedium = base.labelMedium.s(),
        labelSmall = base.labelSmall.s(),
    )
}

@Composable
fun ParkedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Ink,
            secondary = Paper,
            onSecondary = Ink,
            background = Ink,
            onBackground = Paper,
            surface = Ink,
            onSurface = Paper,
            surfaceVariant = Field,
            onSurfaceVariant = Muted,
            surfaceContainerLowest = Ink,
            surfaceContainerLow = Panel,
            surfaceContainer = Panel,
            surfaceContainerHigh = Panel,
            surfaceContainerHighest = Field,
            outline = Line,
            outlineVariant = Line,
            inverseSurface = Paper,
            inverseOnSurface = Ink,
            inversePrimary = Accent,
            error = Danger,
            onError = Ink,
        ),
        typography = sofiaTypography(),
        content = content
    )
}
