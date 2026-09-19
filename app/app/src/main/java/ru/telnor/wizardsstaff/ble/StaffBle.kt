package ru.telnor.wizardsstaff.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/*
 * Связь с посохом по Bluetooth Low Energy.
 *
 * Посох представляется сервисом Nordic UART: в характеристику RX мы пишем команды,
 * из характеристики TX приходят уведомления с событиями. Одно сообщение — одна строка
 * JSON с переводом строки на конце; строка приходит кусками по размеру пакета,
 * поэтому куски копятся в буфере до перевода строки.
 *
 * Разрешения проверяет тот, кто вызывает: экран «Посох» спрашивает их перед поиском.
 */

private const val TAG = "StaffBle"

private val NUS_SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
private val NUS_RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
private val NUS_TX: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/** Имя, под которым посох виден в эфире. */
const val STAFF_DEVICE_NAME = "WizardsStaff"

/** Сколько ищем устройства, миллисекунды. */
private const val SCAN_DURATION_MS = 12_000L

/** Состояние связи. */
enum class Connection { Disconnected, Scanning, Connecting, Connected, Lost }

/** Кто сказал строку в журнале обмена. */
enum class LogDirection {
    In,       // пришло с посоха
    Out,      // отправлено посоху
    System,   // событие самого приложения: подключились, потеряли связь
}

/** Одна строка журнала обмена, как в мониторе порта Arduino IDE. */
data class LogLine(val at: Long, val direction: LogDirection, val text: String)

/** Найденное в эфире устройство. */
data class FoundDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val isStaff: Boolean,
)

class StaffBle(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = manager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _connection = MutableStateFlow(Connection.Disconnected)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private val _devices = MutableStateFlow<List<FoundDevice>>(emptyList())
    val devices: StateFlow<List<FoundDevice>> = _devices.asStateFlow()

    private val _scanFinished = MutableStateFlow(false)
    val scanFinished: StateFlow<Boolean> = _scanFinished.asStateFlow()

    private val _connectedDevice = MutableStateFlow<FoundDevice?>(null)
    val connectedDevice: StateFlow<FoundDevice?> = _connectedDevice.asStateFlow()

    /** События с посоха: броски, заряд, состояние. */
    private val _events = MutableSharedFlow<StaffEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<StaffEvent> = _events.asSharedFlow()

    /** Журнал обмена: каждая строка в обе стороны. Нужен экрану логов. */
    private val _log = MutableSharedFlow<LogLine>(extraBufferCapacity = 256)
    val log: SharedFlow<LogLine> = _log.asSharedFlow()

    private fun writeLog(direction: LogDirection, text: String) {
        _log.tryEmit(LogLine(System.currentTimeMillis(), direction, text))
    }

    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var incoming = StringBuilder()
    private var wantConnection = false   // true, пока пользователь не нажал «Отключить»

    // Android выполняет одну операцию GATT за раз: если начать вторую, не дождавшись ответа
    // на первую, она молча пропадёт. Поэтому записи выстраиваются в очередь и идут по одной.
    private val pending = ArrayDeque<() -> Unit>()
    private var busy = false

    /** Включён ли Bluetooth в системе. */
    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    // ---------- поиск ----------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addDevice(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { addDevice(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "поиск не начался, код $errorCode")
            writeLog(LogDirection.System, "поиск не начался, код $errorCode")
            _connection.value = Connection.Disconnected
            _scanFinished.value = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDevice(result: ScanResult) {
        val record = result.scanRecord
        val name = record?.deviceName ?: runCatching { result.device.name }.getOrNull()
        val isStaff = name == STAFF_DEVICE_NAME ||
            record?.serviceUuids?.any { it.uuid == NUS_SERVICE } == true

        val device = FoundDevice(name, result.device.address, result.rssi, isStaff)
        // Посох показываем первым, остальные по силе сигнала: так его не приходится искать глазами.
        _devices.value = (_devices.value.filter { it.address != device.address } + device)
            .sortedWith(compareByDescending<FoundDevice> { it.isStaff }.thenByDescending { it.rssi })
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        stopScan()
        _devices.value = emptyList()
        _scanFinished.value = false
        _connection.value = Connection.Scanning
        writeLog(LogDirection.System, "ищем посох")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        // Без фильтров: пусть видно всё, что рядом, а посох сами пометим в списке.
        scanner.startScan(null, settings, scanCallback)

        mainHandler.postDelayed({
            if (_connection.value == Connection.Scanning) {
                stopScan()
                _connection.value = Connection.Disconnected
                _scanFinished.value = true
            }
        }, SCAN_DURATION_MS)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    // ---------- подключение ----------

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val device: BluetoothDevice = adapter?.getRemoteDevice(address) ?: return
        stopScan()
        wantConnection = true
        _connection.value = Connection.Connecting
        _connectedDevice.value = _devices.value.firstOrNull { it.address == address }
            ?: FoundDevice(null, address, 0, true)
        writeLog(LogDirection.System, "подключаемся к $address")
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        wantConnection = false
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        rxCharacteristic = null
        incoming = StringBuilder()
        clearQueue()
        _connection.value = Connection.Disconnected
        _connectedDevice.value = null
        writeLog(LogDirection.System, "отключились от посоха")
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    _connection.value = Connection.Connecting   // готовы будем после поиска сервисов
                    g.requestMtu(185)                           // больше пакет — меньше кусков в строке
                }

                BluetoothGatt.STATE_DISCONNECTED -> {
                    rxCharacteristic = null
                    incoming = StringBuilder()
                    clearQueue()
                    if (wantConnection) {
                        // Посох выключили или унесли. Просим систему подключиться, когда он вернётся.
                        writeLog(LogDirection.System, "связь потеряна, ждём посох")
                        _connection.value = Connection.Lost
                        runCatching { g.connect() }
                    } else {
                        _connection.value = Connection.Disconnected
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            g.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(NUS_SERVICE)
            if (service == null) {
                Log.w(TAG, "у устройства нет сервиса UART, это не посох")
                disconnect()
                return
            }
            rxCharacteristic = service.getCharacteristic(NUS_RX)
            val tx = service.getCharacteristic(NUS_TX)
            if (tx != null) {
                g.setCharacteristicNotification(tx, true)
                // Мало включить уведомления у себя: надо ещё попросить их у посоха,
                // записав признак в служебный дескриптор характеристики.
                tx.getDescriptor(CCCD)?.let { descriptor ->
                    enqueue {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            g.writeDescriptor(
                                descriptor,
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            g.writeDescriptor(descriptor)
                        }
                    }
                }
            }
            _connection.value = Connection.Connected
            writeLog(LogDirection.System, "посох на связи")
            // Спрашиваем сведения и на этом останавливаемся. Пока посох не впустил по PIN,
            // он отвечает только на эту команду, а всё остальное отвергает и через десять
            // секунд рвёт связь. Что делать дальше — решает StaffViewModel по ответу:
            // назвать сохранённый PIN, спросить его у человека или сразу подвести часы,
            // если посох без защиты.
            send(StaffCommand.info())
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == NUS_TX) receive(value)
        }

        // Ответы на наши записи: освобождают очередь для следующей команды.

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            operationDone()
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            operationDone()
        }

        @Deprecated("Нужен для Android 12 и старше: там значение приходит внутри характеристики")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == NUS_TX) receive(characteristic.value ?: return)
        }
    }

    // ---------- очередь операций ----------

    /** Ставит операцию в очередь и запускает, если линия свободна. */
    private fun enqueue(operation: () -> Unit) {
        synchronized(pending) {
            pending.addLast(operation)
            if (!busy) runNext()
        }
    }

    private fun runNext() {
        synchronized(pending) {
            if (busy) return
            val operation = pending.removeFirstOrNull() ?: return
            busy = true
            // Если ответ на операцию потеряется, очередь не должна встать навсегда.
            mainHandler.postDelayed(unstick, 3_000)
            operation()
        }
    }

    private val unstick = Runnable {
        Log.w(TAG, "ответ на операцию не пришёл, идём дальше")
        operationDone()
    }

    /** Операция завершена: снимаем занятость и берём следующую. */
    private fun operationDone() {
        mainHandler.removeCallbacks(unstick)
        synchronized(pending) { busy = false }
        runNext()
    }

    private fun clearQueue() {
        mainHandler.removeCallbacks(unstick)
        synchronized(pending) {
            pending.clear()
            busy = false
        }
    }

    // ---------- обмен строками ----------

    /** Складывает пришедшие куски и разбирает каждую законченную строку. */
    private fun receive(chunk: ByteArray) {
        incoming.append(String(chunk, Charsets.UTF_8))
        while (true) {
            val end = incoming.indexOf("\n")
            if (end < 0) break
            val line = incoming.substring(0, end).trim()
            incoming.delete(0, end + 1)
            if (line.isEmpty()) continue
            writeLog(LogDirection.In, line)
            val event = parseStaffEvent(line)
            if (event == null) {
                Log.w(TAG, "непонятная строка с посоха: $line")
            } else {
                _events.tryEmit(event)
            }
        }
        // Защита от мусора: если перевода строки долго нет, буфер не должен расти бесконечно.
        if (incoming.length > 4096) incoming = StringBuilder()
    }

    /** Отправляет команду посоху. Команды уходят по очереди, одна за другой. */
    @SuppressLint("MissingPermission")
    fun send(line: String) {
        val characteristic = rxCharacteristic ?: return
        val g = gatt ?: return
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        writeLog(LogDirection.Out, line)
        enqueue {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(
                    characteristic,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                )
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = bytes
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                g.writeCharacteristic(characteristic)
            }
        }
    }
}
