package ru.besuglovs.fitness.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.cos
import ru.besuglovs.fitness.ble.HeartRateStatus
import ru.besuglovs.fitness.ble.ScannedDevice

@Composable
fun HeartRateWidget(
    bpm: Int?,
    status: HeartRateStatus,
    deviceName: String?,
    recordedCount: Int,
    devices: List<ScannedDevice>,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectDevice: (ScannedDevice) -> Unit,
    onScanAgain: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            pendingAction?.invoke()
        }
        pendingAction = null
    }

    fun connectWithPermissions(action: () -> Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    val connected = status == HeartRateStatus.CONNECTED
    val lost = status == HeartRateStatus.LOST
    val statusText = when (status) {
        HeartRateStatus.DISCONNECTED -> "Пульсометр не подключён"
        HeartRateStatus.LOST -> "Связь с пульсометром потеряна"
        HeartRateStatus.SCANNING -> "Поиск пульсометра H808S…"
        HeartRateStatus.CONNECTING -> "Подключение…"
        HeartRateStatus.CONNECTED ->
            (deviceName ?: "Пульсометр") + " · записано: $recordedCount"
    }

    val alarmBlink = rememberInfiniteTransition(label = "hrAlarmBlink")
    val alarmProgress by alarmBlink.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hrAlarmBlinkProgress"
    )
    val blinkAlpha = if (lost) 0.25f + 0.75f * alarmProgress else 1f
    val blinkCos = ((1f - cos(alarmProgress * (2f * PI.toFloat()))) / 2f).let {
        if (lost) it else 0f
    }
    val alarmColor = MaterialTheme.colorScheme.error
    val heartTint = when {
        connected -> alarmColor
        lost -> alarmColor.copy(alpha = blinkAlpha)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bpmColor = when {
        lost -> alarmColor.copy(alpha = blinkAlpha)
        else -> MaterialTheme.colorScheme.onSurface
    }

    var showPicker by remember { mutableStateOf(false) }
    LaunchedEffect(status) {
        if (status == HeartRateStatus.SCANNING) showPicker = true
    }

    if (showPicker) {
        val sortedDevices = devices.sortedByDescending { it.rssi }
        AlertDialog(
            onDismissRequest = {
                showPicker = false
                onDisconnect()
            },
            title = { Text("Выберите пульсометр") },
            text = {
                if (sortedDevices.isEmpty()) {
                    Text("Устройства не найдены. Нажмите «Искать снова».")
                } else {
                    Column {
                        sortedDevices.forEach { device ->
                            TextButton(
                                onClick = {
                                    showPicker = false
                                    onSelectDevice(device)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        device.name ?: "Без имени",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "${device.address} · RSSI ${device.rssi}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { connectWithPermissions(onScanAgain) }) {
                    Text("Искать снова")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPicker = false
                    onDisconnect()
                }) {
                    Text("Отмена")
                }
            }
        )
    }

    Card(
        modifier = modifier,
        border = if (lost) {
            BorderStroke(3.dp, alarmColor.copy(alpha = 0.4f + 0.6f * blinkCos))
        } else {
            null
        },
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (lost) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = heartTint
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (bpm != null) "$bpm уд/мин" else "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = bpmColor
                    )
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (lost) FontWeight.Bold else FontWeight.Normal,
                        color = if (lost) alarmColor.copy(alpha = blinkAlpha)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = {
                    if (connected) onDisconnect() else connectWithPermissions(onConnect)
                }) {
                    Text(if (connected) "Отключить" else "Подключить")
                }
            }
            if (connected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, bottom = 4.dp)
                ) {
                    TextButton(onClick = onForget) {
                        Text("Забыть датчик")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { connectWithPermissions(onScanAgain) }) {
                        Text("Найти другой")
                    }
                }
            }
        }
    }
}