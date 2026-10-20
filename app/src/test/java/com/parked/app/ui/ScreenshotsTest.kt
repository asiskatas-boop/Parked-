package com.parked.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.parked.app.data.FuelStore
import com.parked.app.data.ParkingState
import com.parked.app.data.ParkingStore
import com.parked.app.service.AutoParkMode
import org.junit.Rule
import org.junit.Test

/**
 * Real renders of the app's screens from the actual Compose code, for
 * previewing changes without a phone. The map is a stand-in drawing (tiles
 * need the network); everything else is the shipping UI.
 *
 * Run with: gradle :app:recordPaparazziDebug
 */
class ScreenshotsTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    private val now = System.currentTimeMillis()

    private val savedAuto = ParkingState(
        deviceName = "VW Golf",
        parkedLat = 37.9420, parkedLng = 23.6460,
        parkedAt = now - 38 * 60_000L,
        monitoring = true,
        timerEndsAt = now + 47 * 60_000L,
        source = ParkingStore.SOURCE_AUTO,
        accuracy = 8f,
        level = "-2", zone = null, space = "14",
        note = "Next to the blue pillar",
    )

    private val savedWeak = savedAuto.copy(
        monitoring = false, accuracy = 62f, level = null, zone = null, space = null, timerEndsAt = null, note = null,
        source = ParkingStore.SOURCE_MANUAL,
    )

    private fun both(name: String, content: @Composable () -> Unit) {
        listOf("en" to "", "el" to "el").forEach { (tag, locale) ->
            paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(locale = locale.ifEmpty { null }))
            paparazzi.snapshot("${name}_$tag") {
                ParkedTheme {
                    CompositionLocalProvider(
                        LocalInlineSheets provides true,
                        LocalSnackbar provides remember { SnackbarHostState() },
                    ) { content() }
                }
            }
        }
    }

    @Test fun intro() = both("1_intro") { OnboardingPage(onNext = {}) }

    @Test fun homeSaved() = both("2_home_saved") {
        HomeFrame(state = savedAuto, gps = GpsReading(8f, now)) { map ->
            SavedSpotContent(
                state = savedAuto, autoParkMode = AutoParkMode.Full, isAtCar = false, distance = 320.0,
                saving = false, timerEnds = savedAuto.timerEndsAt, map = map,
                onDirections = {}, onSave = {}, onShare = {}, onNote = {}, onPhoto = {},
                onTimer = {}, onGarage = {},
            )
        }
    }

    @Test fun homeWarning() = both("3_home_autopark_lost") {
        HomeFrame(state = savedWeak, banner = MapIssue.AutoParkLost, gps = GpsReading(62f, now)) { map ->
            SavedSpotContent(
                state = savedWeak, autoParkMode = AutoParkMode.Full, isAtCar = false, distance = 1240.0,
                saving = false, timerEnds = null, map = map,
                onDirections = {}, onSave = {}, onShare = {}, onNote = {}, onPhoto = {},
                onTimer = {}, onGarage = {},
            )
        }
    }

    @Test fun homeEmpty() = both("4_home_empty") {
        val empty = ParkingState(deviceName = "VW Golf", monitoring = true)
        HomeFrame(state = empty, gps = GpsReading(12f, now), showCar = false) { map ->
            EmptySpotContent(autoPark = true, saving = false, map = map, onSave = {})
        }
    }

    @Test fun car() = both("5_car") {
        val fuel = demoFuel()
        Column(Modifier.fillMaxSize().background(Bg)) {
            Box(Modifier.weight(1f)) { CarMain(fuel, "VW Golf", onOpen = {}) }
            BottomNav(tab = AppTab.Fuel, onChange = {})
        }
    }

    @Test fun carTall() = both("6_car_full") {
        val fuel = demoFuel()
        Box(Modifier.requiredHeight(2100.dp).fillMaxWidth().background(Bg)) { CarMain(fuel, "VW Golf", onOpen = {}) }
    }

    @Test fun fuelSheet() = both("7_fuel_level_sheet") {
        SheetFrame { FuelLevelSheet(fuel = demoFuel(), onDismiss = {}) }
    }

    @Test fun garage() = both("8_garage_sheet") {
        SheetFrame {
            GarageSheet(
                level = "-2", zone = "B", space = null, hasPhoto = false,
                onPhoto = { _, _, _ -> }, onDismiss = {}, onSave = { _, _, _ -> },
            )
        }
    }

    @Test fun permissionLocation() = both("9_permission_location") {
        SheetFrame { PermissionStepSheet(step = PermStep.Location, onContinue = {}, onSkip = null, onDismiss = {}) }
    }

    @Composable
    private fun demoFuel(): FuelStore {
        val context = LocalContext.current
        return remember {
            FuelStore(context).apply {
                if (refuels.isEmpty()) {
                    setOdometer(82_900.0)
                    updateTankCapacity(50.0)
                    logRefuel(litres = 40.0, cost = 71.20, odometer = 82_900.0, tankFull = true)
                    logRefuel(litres = 36.0, cost = 64.10, odometer = 83_420.0, tankFull = true)
                    setOdometer(83_456.0)
                    setAccent("Blue")
                    val day = 24 * 60 * 60_000L
                    addReminder("ΚΤΕΟ", null, 24, 0.0, now - (730 - 23) * day)
                    addReminder("Oil", 15_000, 12, 80_100.0, now - 120 * day)
                    addReminder("Tyres", 10_000, null, 75_000.0, now - 300 * day)
                }
                setLevel(62.0)
            }
        }
    }
}

/** Home's layout: the scrolling page on cream, then the tab bar. */
@Composable
private fun HomeFrame(
    state: ParkingState,
    gps: GpsReading?,
    banner: MapIssue? = null,
    showCar: Boolean = true,
    content: @Composable ColumnScope.(map: @Composable (Modifier, Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val map: @Composable (Modifier, Boolean) -> Unit = { mod, inCard ->
        MapCard(mod, inCard, online = true, showCenter = showCar, showBoth = showCar, onCenter = {}) {
            Box(Modifier.fillMaxSize()) {
                FakeMap(showPath = showCar)
                if (showCar) {
                    Image(
                        bitmap = makeParkedMarker(context).bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).offset(x = (-40).dp, y = (-18).dp).size(52.dp)
                    )
                }
                Image(
                    bitmap = makeLiveMarker(context).bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).offset(x = 70.dp, y = 40.dp).size(32.dp)
                )
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            if (banner != null) {
                MapBanner(issue = banner, onDismiss = {}, onAction = {})
                Spacer(Modifier.height(12.dp))
            }
            StatusChips(
                state = state, autoParkMode = AutoParkMode.Full, gps = gps, showGps = true,
                timerEnds = state.timerEndsAt, onTimer = {},
            )
            content(map)
        }
        BottomNav(tab = AppTab.Home, onChange = {})
    }
}

/** A sheet as it sits at the bottom of a dimmed screen. */
@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF9C9893))) {
        Spacer(Modifier.weight(1f))
        content()
    }
}

/** Stand-in for the CARTO Voyager map: warm blocks, white streets, a park. */
@Composable
private fun FakeMap(showPath: Boolean) {
    Canvas(Modifier.fillMaxSize().background(Color(0xFFF3EEE6))) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFFE3EDD9), Offset(w * 0.36f, h * 0.38f), androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.3f))
        drawRect(Color(0xFFD8E6EF), Offset(w * 0.84f, 0f), androidx.compose.ui.geometry.Size(w * 0.2f, h))
        val street = Color.White
        listOf(0.33f, 0.72f).forEach { y -> drawLine(street, Offset(0f, h * y), Offset(w, h * y), 18f) }
        listOf(0.34f, 0.62f).forEach { x -> drawLine(street, Offset(w * x, 0f), Offset(w * x, h), 18f) }
        drawLine(Color(0xFFFCE7C8), Offset(0f, h * 0.95f), Offset(w, h * 0.1f), 28f)
        if (showPath) {
            drawLine(
                Color(0xFF3F7A22), Offset(w / 2 + 70.dp.toPx(), h / 2 + 40.dp.toPx()), Offset(w / 2 - 40.dp.toPx(), h / 2 - 18.dp.toPx()),
                3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            )
        }
    }
}
