package com.parked.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.parked.app.R

// Brand
val OliveDark = Color(0xFF2B4A23)
val OliveMid = Color(0xFF4E8F27)
val LimeBright = Color(0xFF6EC436)

// Neutrals
val White = Color(0xFFFFFFFF)
val NearBlack = Color(0xFF08090B)
val SurfaceTint = Color(0xFFF5F7F3)
val SurfaceSelected = Color(0xFFE8F3E0)
val Hairline = Color(0xFFDDE2D9)
val GrayIcon = Color(0xFF4D4D4D)

/**
 * Secondary text. The previous #909090 measured 3.2:1 on white, below the
 * WCAG AA minimum of 4.5:1 for body-size text; this measures 5.1:1.
 */
val TextSecondary = Color(0xFF6B6F68)

// Status
val ErrorRed = Color(0xFFB91C1C)
val WarnAmber = Color(0xFF8A5A00)

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

private fun interTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = InterFamily),
        displayMedium = base.displayMedium.copy(fontFamily = InterFamily),
        displaySmall = base.displaySmall.copy(fontFamily = InterFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = InterFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = InterFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = InterFamily),
        titleLarge = base.titleLarge.copy(fontFamily = InterFamily),
        titleMedium = base.titleMedium.copy(fontFamily = InterFamily),
        titleSmall = base.titleSmall.copy(fontFamily = InterFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = InterFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = InterFamily),
        bodySmall = base.bodySmall.copy(fontFamily = InterFamily),
        labelLarge = base.labelLarge.copy(fontFamily = InterFamily),
        labelMedium = base.labelMedium.copy(fontFamily = InterFamily),
        labelSmall = base.labelSmall.copy(fontFamily = InterFamily),
    )
}

@Composable
fun ParkedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = OliveDark,
            onPrimary = White,
            secondary = LimeBright,
            onSecondary = NearBlack,
            background = White,
            onBackground = NearBlack,
            surface = White,
            onSurface = NearBlack,
            surfaceVariant = Color(0xFFF5F5F5),
            onSurfaceVariant = TextSecondary,
            surfaceContainerLow = White,
            inverseSurface = NearBlack,
            inverseOnSurface = White,
            inversePrimary = LimeBright,
            error = ErrorRed,
        ),
        typography = interTypography(),
        content = content
    )
}
