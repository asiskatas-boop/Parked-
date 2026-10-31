package com.parked.app.ui.v8

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.parked.app.R
import com.parked.app.receiver.ParkingTimer
import com.parked.app.ui.LocalSnackbar
import com.parked.app.ui.SpotPhoto
import com.parked.app.ui.clockTime
import com.parked.app.util.Channels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Level sheet: floor, zone and space, as in the prototype. */
@Composable
fun ColumnScope.LevelSheetV8(level: String?, zone: String?, space: String?, onSave: (String?, String?, String?) -> Unit) {
    var l by remember { mutableStateOf(level.orEmpty()) }
    var z by remember { mutableStateOf(zone.orEmpty()) }
    var s by remember { mutableStateOf(space.orEmpty()) }
    SheetTitle(stringResource(R.string.tile_level))
    SheetSub(stringResource(R.string.v8_level_q))
    Spacer(Modifier.height(18.dp))
    FieldV8(l, { l = it }, stringResource(R.string.v8_floor_hint))
    Spacer(Modifier.height(10.dp))
    FieldV8(z, { z = it }, stringResource(R.string.v8_zone_hint))
    Spacer(Modifier.height(10.dp))
    FieldV8(s, { s = it }, stringResource(R.string.v8_space_hint))
    Spacer(Modifier.height(24.dp))
    PrimaryV8(stringResource(R.string.v8_save)) {
        onSave(l.trim().ifEmpty { null }, z.trim().ifEmpty { null }, s.trim().ifEmpty { null })
    }
}

/** Parking timer: four presets as navy buttons, or your own minutes. Same alarm logic as 0.7. */
@Composable
fun ColumnScope.TimerSheetV8(endsAt: Long?, onSet: (Long?) -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var custom by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    var pendingMinutes by remember { mutableStateOf<Int?>(null) }
    val errorText = stringResource(R.string.v8_err_minutes)
    val notifDenied = stringResource(R.string.timer_needs_notifications)

    fun commit(minutes: Int) = onSet(System.currentTimeMillis() + minutes * 60_000L)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val minutes = pendingMinutes
        pendingMinutes = null
        if (!granted) scope.launch { snackbar.showSnackbar(notifDenied) }
        if (minutes != null) commit(minutes)
    }
    fun start(minutes: Int) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsPermission) { pendingMinutes = minutes; notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else {
            if (!Channels.canPost(context, Channels.TIMER)) scope.launch { snackbar.showSnackbar(notifDenied) }
            commit(minutes)
        }
    }
    val exactAllowed = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() == true

    SheetTitle(stringResource(R.string.v8_timer_title))
    SheetSub(
        if (endsAt != null) stringResource(R.string.v8_timer_ends, clockTime(context, endsAt))
        else stringResource(R.string.v8_timer_sub)
    )
    Spacer(Modifier.height(24.dp))
    val presets = listOf(30, 60, 120, 180)
    GridV8(
        listOf(
            stringResource(R.string.v8_30min), stringResource(R.string.v8_1h),
            stringResource(R.string.v8_2h), stringResource(R.string.v8_3h),
        )
    ) { start(presets[it]) }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldV8(custom, { custom = it; error = null }, stringResource(R.string.v8_minutes_hint), Modifier.weight(1f), error = error, keyboard = KeyboardType.Number, shake = shake)
        Spacer(Modifier.width(10.dp))
        PrimaryV8(stringResource(R.string.v8_start), Modifier.width(90.dp), height = 38f) {
            val m = custom.trim().toIntOrNull()
            if (m == null || m !in 1..24 * 60) { error = errorText; shake++ } else start(m)
        }
    }
    if (!exactAllowed) {
        Text(stringResource(R.string.timer_inexact), style = designStyle(13.sp, FontWeight.Normal, Ink).copy(lineHeight = 17.sp), modifier = Modifier.padding(top = 14.dp))
        LinkV8(stringResource(R.string.timer_allow_exact), Modifier.offset((-10).dp)) {
            runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
        }
    }
    if (endsAt != null) Text(
        stringResource(R.string.timer_cancel),
        style = designStyle(15.sp, FontWeight.Light, ErrorRed),
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) { onSet(null) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/** The spot photo: the picture, Retake, Remove, and Done. */
@Composable
fun ColumnScope.PhotoSheetV8(path: String, onRetake: () -> Unit, onRemove: () -> Unit, onDone: () -> Unit) {
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) { bitmap = withContext(Dispatchers.IO) { SpotPhoto.load(path, 1400) } }
    SheetTitle(stringResource(R.string.v8_spot_photo))
    SheetSub(stringResource(R.string.v8_spot_photo_sub))
    Box(
        Modifier
            .padding(top = 18.dp)
            .fillMaxWidth()
            .height(196.dp)
            .clip(R15)
            .background(White)
    ) {
        bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.v8_spot_photo), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        LinkV8(stringResource(R.string.v8_retake), Modifier.offset((-10).dp), onClick = onRetake)
        Text(
            stringResource(R.string.v8_remove),
            style = designStyle(15.sp, FontWeight.Light, ErrorRed),
            modifier = Modifier
                .offset(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onRemove)
                .padding(horizontal = 10.dp, vertical = 14.dp),
        )
    }
    Spacer(Modifier.height(4.dp))
    PrimaryV8(stringResource(R.string.v8_done), onClick = onDone)
}
