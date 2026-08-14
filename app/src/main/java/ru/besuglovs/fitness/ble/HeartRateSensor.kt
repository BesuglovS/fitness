package ru.besuglovs.fitness.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

enum class HeartRateStatus { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int
)

/**
 * Клиент BLE-пульсометра H808S (COOSPO). Использует стандартный сервис
 * Heart Rate (0x180D) и характеристику Heart Rate Measurement (0x2A37).
 */
class HeartRateSensor(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("heart_rate_sensor", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(HeartRateStatus.DISCONNECTED)
    val status: StateFlow<HeartRateStatus> = _status.asStateFlow()

    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _readings = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    val readings: SharedFlow<Int> = _readings.asSharedFlow()

    private val _discoveredDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ScannedDevice>> = _discoveredDevices.asStateFlow()

    @Volatile
    private var autoReconnectInProgress = false

    @Volatile
    private var wasConnected = false

    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private var timeoutRunnable: Runnable? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isAvailable(): Boolean = adapter != null && adapter.isEnabled

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (adapter == null || !adapter.isEnabled) return@withContext false
        if (!hasRequiredPermissions()) return@withContext false

        val savedMac = prefs.getString(KEY_LAST_MAC, null)
        if (savedMac != null) {
            val device = runCatching { adapter.getRemoteDevice(savedMac) }.getOrNull()
            if (device != null) {
                autoReconnectInProgress = true
                connectToDevice(device)
                return@withContext true
            }
        }
        scanForDevices()
        true
    }

    suspend fun connect(address: String): Boolean = withContext(Dispatchers.IO) {
        if (adapter == null || !adapter.isEnabled) return@withContext false
        if (!hasRequiredPermissions()) return@withContext false
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        if (device == null) return@withContext false
        prefs.edit().putString(KEY_LAST_MAC, address).apply()
        connectToDevice(device)
        true
    }

    fun scanForDevices() {
        _discoveredDevices.value = emptyList()
        startScan()
    }

    fun cancelScan() {
        stopScan()
        _status.value = HeartRateStatus.DISCONNECTED
        _discoveredDevices.value = emptyList()
    }

    fun forgetLastDevice() {
        prefs.edit().remove(KEY_LAST_MAC).apply()
    }

    fun disconnect() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        stopScan()
        autoReconnectInProgress = false
        wasConnected = false
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _status.value = HeartRateStatus.DISCONNECTED
        _bpm.value = null
        _deviceName.value = null
        _discoveredDevices.value = emptyList()
    }

    private fun hasRequiredPermissions(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return required.all {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startScan() {
        stopScan()
        val scanner = adapter?.bluetoothLeScanner ?: run {
            _status.value = HeartRateStatus.DISCONNECTED
            return
        }
        _status.value = HeartRateStatus.SCANNING
        _deviceName.value = null
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = result.scanRecord?.deviceName ?: device.name
                val isTarget = name?.contains("h808", ignoreCase = true) == true ||
                    result.scanRecord?.serviceUuids?.any { it.uuid == HEART_RATE_SERVICE_UUID } == true
                if (!isTarget) return
                val address = device.address
                val current = _discoveredDevices.value
                if (current.none { it.address == address }) {
                    _discoveredDevices.value = current + ScannedDevice(
                        address = address,
                        name = name,
                        rssi = result.rssi
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _status.value = HeartRateStatus.DISCONNECTED
            }
        }
        scanCallback = callback
        scanner.startScan(callback)
        timeoutRunnable = Runnable {
            stopScan()
        }.also { mainHandler.postDelayed(it, SCAN_TIMEOUT_MS) }
    }

    private fun stopScan() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        val cb = scanCallback ?: return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(cb) }
        scanCallback = null
    }

    private fun connectToDevice(device: BluetoothDevice) {
        wasConnected = false
        _status.value = HeartRateStatus.CONNECTING
        _deviceName.value = device.name ?: "H808S"
        gatt?.disconnect()
        gatt?.close()
        gatt = device.connectGatt(appContext, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    wasConnected = true
                    autoReconnectInProgress = false
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    this@HeartRateSensor.gatt?.close()
                    this@HeartRateSensor.gatt = null
                    val failedReconnect = autoReconnectInProgress && !wasConnected
                    autoReconnectInProgress = false
                    wasConnected = false
                    _bpm.value = null
                    if (failedReconnect) {
                        prefs.edit().remove(KEY_LAST_MAC).apply()
                        startScan()
                    } else {
                        _status.value = HeartRateStatus.DISCONNECTED
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                gatt.disconnect()
                return
            }
            val service = gatt.getService(HEART_RATE_SERVICE_UUID)
                ?: run { gatt.disconnect(); return }
            val characteristic = service.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
                ?: run { gatt.disconnect(); return }
            gatt.setCharacteristicNotification(characteristic, true)
            characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)?.let { descriptor ->
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid != HEART_RATE_MEASUREMENT_UUID) return
            val value = parseHeartRate(characteristic.value) ?: return
            _bpm.value = value
            _readings.tryEmit(value)
            if (_status.value != HeartRateStatus.CONNECTED) {
                _status.value = HeartRateStatus.CONNECTED
            }
        }
    }

    private fun parseHeartRate(data: ByteArray?): Int? {
        if (data == null || data.size < 2) return null
        val flags = data[0].toInt() and 0xFF
        return if (flags and 0x01 == 0) {
            data[1].toInt() and 0xFF
        } else {
            if (data.size < 3) null
            else (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        }
    }

    companion object {
        private const val KEY_LAST_MAC = "last_mac"
        private const val SCAN_TIMEOUT_MS = 15000L
        val HEART_RATE_SERVICE_UUID: UUID =
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}