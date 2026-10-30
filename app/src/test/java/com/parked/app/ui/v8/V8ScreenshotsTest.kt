package com.parked.app.ui.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.parked.app.data.FuelStore
import com.parked.app.ui.LocalSnackbar
import org.junit.Rule
import org.junit.Test

/**
 * 0.8 screens rendered 1:1 at the Figma frame size (393 px wide at 1x), so
 * they can be compared pixel by pixel with the Figma exports.
 * Run with: gradle :app:recordPaparazziDebug
 */
class V8ScreenshotsTest {
    private fun frame(h: Int) = DeviceConfig.PIXEL_6.copy(screenWidth = 393, screenHeight = h, xdpi = 160, ydpi = 160, density = Density.MEDIUM)

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = frame(971), theme = "android:Theme.Material.Light.NoActionBar", showSystemUi = false)

    private fun both(name: String, height: Int, tab: Int, content: @Composable () -> Unit) {
        listOf("en" to null, "el" to "el").forEach { (tag, locale) ->
            paparazzi.unsafeUpdateConfig(deviceConfig = frame(height).copy(locale = locale))
            paparazzi.snapshot("v8_${name}_$tag") {
                // Paparazzi has no Activity: give the screens a result registry and a back dispatcher.
                val owner = remember {
                    object : androidx.activity.result.ActivityResultRegistryOwner, androidx.activity.OnBackPressedDispatcherOwner {
                        override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                            override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {}
                        }
                        override val onBackPressedDispatcher = androidx.activity.OnBackPressedDispatcher()
                        override val lifecycle: androidx.lifecycle.Lifecycle get() = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
                    }
                }
                CompositionLocalProvider(
                    LocalSnackbar provides remember { SnackbarHostState() },
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides owner,
                    androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides owner,
                ) {
                    DesignScale {
                        Column(Modifier.fillMaxSize().background(White)) {
                            Box(Modifier.weight(1f)) { content() }
                            TabBarV8(tab, listOf("Home", "Car", "Settings")) {}
                        }
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

    @Composable
    private fun demoFuel(reminders: Boolean): FuelStore {
        val context = LocalContext.current
        return remember {
            FuelStore(PrefsContext(context)).apply {
                setOdometer(258_852.0)
                setCarColour(0xFFFF3700)
                if (reminders) {
                    addReminder("Oil change", 1_500, null, 258_592.0, System.currentTimeMillis())
                    addReminder("KTEO", null, 17, 0.0, System.currentTimeMillis())
                }
                setLevel(100.0)
            }
        }
    }

    @Test fun home_saved() = both("home_saved", 971, 0) { home(true, HomeBanner.Permission) }
    @Test fun home_empty() = both("home_empty", 971, 0) { home(false, HomeBanner.Permission) }
    @Test fun home_waiting() = both("home_waiting", 971, 0) { home(true, null, waiting = true) }

    @Test fun car() = both("car", 817, 1) { CarV8(demoFuel(false), "CE235BT", false) {} }
    @Test fun car_reminders() = both("car_reminders", 949, 1) { CarV8(demoFuel(true), "CE235BT", false) {} }
    @Test fun car_sheet_reminders() = both("car_sheet_reminders", 817, 1) {
        val f = demoFuel(false)
        Box { CarV8(f, "CE235BT", false) {}; SheetV8(true, {}) { RemindersSheet(f, emptyList(), {}, {}) } }
    }
    @Test fun car_sheet_fuel() = both("car_sheet_fuel", 817, 1) {
        val f = demoFuel(false)
        Box { CarV8(f, "CE235BT", false) {}; SheetV8(true, {}) { RefuelForm(f) {} } }
    }
    @Test fun car_sheet_level() = both("car_sheet_level", 817, 1) {
        val f = demoFuel(false)
        Box { CarV8(f, "CE235BT", false) {}; SheetV8(true, {}) { LevelForm(f) {} } }
    }
}
