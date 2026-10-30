package com.parked.app.ui.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import org.junit.Rule
import org.junit.Test

/**
 * 0.8 screens rendered at exactly the Figma frame size (393 dp wide at 3x =
 * 1179 px), so they can be compared pixel by pixel with the Figma exports.
 * Run with: gradle :app:recordPaparazziDebug
 */
class V8ScreenshotsTest {
    private val figma = DeviceConfig.PIXEL_6.copy(screenWidth = 1179, screenHeight = 2913, xdpi = 480, ydpi = 480, density = Density.XXHIGH)

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = figma, theme = "android:Theme.Material.Light.NoActionBar", showSystemUi = false)

    private fun both(name: String, content: @Composable () -> Unit) {
        listOf("en" to null, "el" to "el").forEach { (tag, locale) ->
            paparazzi.unsafeUpdateConfig(deviceConfig = figma.copy(locale = locale))
            paparazzi.snapshot("v8_${name}_$tag") {
                DesignScale {
                    Box(Modifier.fillMaxSize().background(White)) {
                        content()
                        Box(Modifier.align(Alignment.BottomStart)) { TabBarV8(0, listOf("Home", "Car", "Settings")) {} }
                    }
                }
            }
        }
    }

    @Composable
    private fun home(hasSpot: Boolean, banner: HomeBanner?, waiting: Boolean = false) = HomeV8(
        loaded = true, hasSpot = hasSpot, banner = banner, autoParkOn = false,
        parkedLabel = "Parked today at 13:25", distanceLabel = "22 m", waitingForGps = waiting,
        saving = false, note = null, showRecenter = true,
        map = { Box(it.background(CardGrey)) },
        onBannerAction = {}, onAutoPark = {}, onRecenter = {}, onDirections = {}, onSaveSpot = {},
        onShare = {}, onLevel = {}, onPhoto = {}, onTime = {}, onNote = {},
    )

    @Test fun home_saved() = both("home_saved") { home(true, HomeBanner.Permission) }
    @Test fun home_nobanner() = both("home_nobanner") { home(true, null) }
    @Test fun home_empty() = both("home_empty") { home(false, HomeBanner.Permission) }
    @Test fun home_waiting() = both("home_waiting") { home(true, HomeBanner.Permission, waiting = true) }
}
