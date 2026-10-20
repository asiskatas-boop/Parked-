package com.parked.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
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
        theme = "android:Theme.Material.NoActionBar",
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
        level = "-2", zone = "B", space = "14",
    )

    private val savedWeak = savedAuto.copy(
        monitoring = false, accuracy = 62f, level = null, zone = null, space = null, timerEndsAt = null,
    )

    private fun both(name: String, content: @Composable () -> Unit) {
        listOf("en" to "", "el" to "el").forEach { (tag, locale) ->
            paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(locale = locale.ifEmpty { null }))
            paparazzi.snapshot("${name}_$tag") {
                ParkedTheme { CompositionLocalProvider(LocalInlineSheets provides true) { content() } }
            }
        }
    }

    @Test fun intro() = both("1_intro") { OnboardingPage(onNext = {}) }

    @Test fun homeSaved() = both("2_home_saved") {
        HomeFrame(banner = null, gps = GpsReading(8f, now)) {
            SavedSpotContent(
                state = savedAuto, autoParkMode = AutoParkMode.Full, isAtCar = false, distance = 320.0,
                saving = false, onDirections = {}, onSave = {}, onShare = {}, onNote = {}, onPhoto = {},
                onTimer = {}, onGarage = {},
            )
        }
    }

    @Test fun homeWarning() = both("3_home_autopark_lost") {
        HomeFrame(banner = MapIssue.AutoParkLost, gps = GpsReading(62f, now)) {
            SavedSpotContent(
                state = savedWeak, autoParkMode = AutoParkMode.Full, isAtCar = false, distance = 1200.0,
                saving = false, onDirections = {}, onSave = {}, onShare = {}, onNote = {}, onPhoto = {},
                onTimer = {}, onGarage = {},
            )
        }
    }

    @Test fun homeBluetoothOff() = both("4_home_bluetooth_off") {
        HomeFrame(banner = MapIssue.BluetoothOff, gps = GpsReading(12f, now), showCar = false) {
            EmptySpotContent(saving = false, onSave = {})
        }
    }

    @Test fun garage() = both("5_garage_sheet") {
        SheetFrame {
            GarageSheet(
                level = "-2", zone = "B", space = null, hasPhoto = false,
                onPhoto = { _, _, _ -> }, onDismiss = {}, onSave = { _, _, _ -> },
            )
        }
    }

    @Test fun permissionLocation() = both("6_permission_location") {
        SheetFrame { PermissionStepSheet(step = PermStep.Location, onContinue = {}, onSkip = null, onDismiss = {}) }
    }

    @Test fun permissionNotifications() = both("7_permission_notifications") {
        SheetFrame { PermissionStepSheet(step = PermStep.Notifications, onContinue = {}, onSkip = {}, onDismiss = {}) }
    }
}

/** Home's layout: the map area above, the spot panel, the tab bar. */
@Composable
private fun HomeFrame(
    banner: MapIssue?,
    gps: GpsReading?,
    showCar: Boolean = true,
    panel: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Ink)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            FakeMap()
            if (showCar) {
                Image(
                    bitmap = makeParkedMarker(context).bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(48.dp)
                )
                Image(
                    bitmap = makeLiveMarker(context).bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).offset(x = 70.dp, y = 60.dp).size(20.dp)
                )
                MapButton(Ph.cornersOut, "", Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)) {}
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp)) {
                GpsSignal(gps = gps, preciseOverride = true)
                Spacer(Modifier.height(6.dp))
                OsmAttribution(Modifier)
            }
            if (banner != null) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 16.dp, end = 16.dp)) {
                    MapBanner(issue = banner, onDismiss = {}, onAction = {})
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp), content = panel)
        BottomNav(tab = AppTab.Home, onChange = {})
    }
}

/** A sheet as it sits at the bottom of a dimmed screen. */
@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Spacer(Modifier.weight(1f))
        content()
    }
}

/** Stand-in for the CARTO Dark Matter map: dark blocks and grey streets. */
@Composable
private fun FakeMap() {
    Canvas(Modifier.fillMaxSize().background(Color(0xFF111111))) {
        val street = Color(0xFF2B2B2B)
        val major = Color(0xFF3A3A3A)
        val w = size.width
        val h = size.height
        listOf(0.18f, 0.43f, 0.71f).forEach { y -> drawLine(street, Offset(0f, h * y), Offset(w, h * y + 40f), 14f) }
        listOf(0.22f, 0.58f, 0.86f).forEach { x -> drawLine(street, Offset(w * x, 0f), Offset(w * x - 60f, h), 14f) }
        drawLine(major, Offset(0f, h * 0.9f), Offset(w, h * 0.32f), 26f)
    }
}
