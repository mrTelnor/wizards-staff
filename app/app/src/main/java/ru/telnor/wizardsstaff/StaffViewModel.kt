package ru.telnor.wizardsstaff

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.telnor.wizardsstaff.ble.Connection
import ru.telnor.wizardsstaff.ble.FoundDevice
import ru.telnor.wizardsstaff.ble.LogDirection
import ru.telnor.wizardsstaff.ble.LogLine
import ru.telnor.wizardsstaff.ble.StaffBle
import ru.telnor.wizardsstaff.ble.StaffCommand
import ru.telnor.wizardsstaff.ble.StaffEvent

/*
 * Состояние приложения: связь с посохом, лента бросков, взвод.
 *
 * Броски пока живут только в памяти и пропадают при закрытии приложения — база Room
 * появится следующим шагом (задача C2 в docs/ПЛАН-APP.md) вместе с подписями и догрузкой
 * пропущенных бросков с посоха.
 */

/** Один бросок в ленте. */
data class RollRecord(
    /** Номер броска в посохе. После перезагрузки посоха нумерация начинается заново. */
    val id: Long,
    /** Номер сеанса связи: вместе с id даёт ключ, уникальный в пределах работы приложения. */
    val session: Int,
    val count: Int,
    val sides: Int,
    val values: List<Int>,
    val total: Int,
    /** Время планшета в момент получения: часы посоха могут быть не выставлены. */
    val receivedAt: Long,
    val discarded: Boolean = false,
) {
    /** Чем этот бросок отличается от всех прочих в ленте. */
    val key: String get() = "$session-$id"

    /** Формула броска: 1d20, 3d6. */
    val formula: String get() = "${count}d$sides"

    /** Слагаемые: «2 + 4 + 5». У одной кости слагаемых нет. */
    val breakdown: String get() = if (values.size > 1) values.joinToString(" + ") else ""

    /** Критический успех считается только по натуральной двадцатке на одном d20. */
    val critSuccess: Boolean get() = count == 1 && sides == 20 && values.firstOrNull() == 20

    /** Критический провал — натуральная единица там же. */
    val critFail: Boolean get() = count == 1 && sides == 20 && values.firstOrNull() == 1
}

/** Сколько строк журнала держим в памяти: дальше старые вытесняются. */
private const val MAX_LOG_LINES = 2000

/** Взвод: какой бросок сделает следующий удар об пол. */
data class ArmedState(val count: Int, val sides: Int) {
    val formula: String get() = "${count}d$sides"
}

class StaffViewModel(application: Application) : AndroidViewModel(application) {

    private val ble = StaffBle(application)

    val connection: StateFlow<Connection> = ble.connection
    val devices: StateFlow<List<FoundDevice>> = ble.devices
    val scanFinished: StateFlow<Boolean> = ble.scanFinished
    val connectedDevice: StateFlow<FoundDevice?> = ble.connectedDevice

    private val _rolls = MutableStateFlow<List<RollRecord>>(emptyList())
    val rolls: StateFlow<List<RollRecord>> = _rolls.asStateFlow()

    private val _batteryPercent = MutableStateFlow<Int?>(null)
    val batteryPercent: StateFlow<Int?> = _batteryPercent.asStateFlow()

    private val _firmware = MutableStateFlow<String?>(null)
    val firmware: StateFlow<String?> = _firmware.asStateFlow()

    /** Через сколько секунд у подключённого посоха спадает взвод. Ноль — посох ещё не сказал. */
    private val _armSeconds = MutableStateFlow(0)
    val armSeconds: StateFlow<Int> = _armSeconds.asStateFlow()

    private val _dice = MutableStateFlow(listOf(2, 4, 6, 8, 10, 12, 20, 100))
    val dice: StateFlow<List<Int>> = _dice.asStateFlow()

    private val _armed = MutableStateFlow<ArmedState?>(null)
    val armed: StateFlow<ArmedState?> = _armed.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _logLines = MutableStateFlow<List<LogLine>>(emptyList())
    val logLines: StateFlow<List<LogLine>> = _logLines.asStateFlow()

    /** На сколько секунд часы посоха расходятся с планшетом. null — пока не проверяли. */
    private val _clockSkew = MutableStateFlow<Long?>(null)
    val clockSkew: StateFlow<Long?> = _clockSkew.asStateFlow()

    // Каждое новое подключение — новый сеанс. Посох после перезагрузки нумерует броски
    // заново с единицы, и без этого счётчика свежий бросок №1 выглядел бы повтором старого
    // и не попадал бы в ленту.
    private var session = 0

    init {
        viewModelScope.launch {
            ble.events.collect { event -> onEvent(event) }
        }
        viewModelScope.launch {
            var wasConnected = false
            ble.connection.collect { state ->
                val connected = state == Connection.Connected
                if (connected && !wasConnected) session++
                wasConnected = connected
            }
        }
        viewModelScope.launch {
            ble.log.collect { line ->
                val lines = _logLines.value + line
                _logLines.value = if (lines.size > MAX_LOG_LINES) {
                    lines.takeLast(MAX_LOG_LINES)
                } else {
                    lines
                }
            }
        }
    }

    private fun onEvent(event: StaffEvent) {
        when (event) {
            is StaffEvent.Roll -> {
                val record = RollRecord(
                    id = event.id,
                    session = session,
                    count = event.count,
                    sides = event.sides,
                    values = event.values,
                    total = event.total,
                    receivedAt = System.currentTimeMillis(),
                )
                // Посох может прислать один бросок дважды: дубли отсеиваем в пределах сеанса.
                if (_rolls.value.none { it.key == record.key }) {
                    _rolls.value = listOf(record) + _rolls.value
                }
            }

            is StaffEvent.Battery -> _batteryPercent.value = event.percent

            is StaffEvent.Info -> {
                _firmware.value = event.firmware
                if (event.dice.isNotEmpty()) _dice.value = event.dice
                if (event.armSeconds > 0) _armSeconds.value = event.armSeconds
                _clockSkew.value = if (event.staffTime > 0) {
                    event.staffTime - System.currentTimeMillis() / 1000
                } else {
                    null   // часы посоха не выставлены, сравнивать не с чем
                }
            }

            is StaffEvent.State ->
                _armed.value = if (event.armed) ArmedState(event.count, event.sides) else null

            is StaffEvent.Error -> _lastError.value = event.message
            // Строка монитора посоха и ответ «сделано» ничего не меняют:
            // в журнале обмена они уже видны как есть.
            is StaffEvent.Log -> Unit
            is StaffEvent.Ok -> Unit
        }
    }

    // ---------- связь ----------

    fun isBluetoothOn(): Boolean = ble.isBluetoothOn()

    fun startScan() = ble.startScan()

    fun stopScan() = ble.stopScan()

    fun connect(address: String) = ble.connect(address)

    fun disconnect() {
        ble.disconnect()
        _batteryPercent.value = null
        _firmware.value = null
        _armSeconds.value = 0
        _armed.value = null
        _clockSkew.value = null
    }

    /** Отправляет посоху строку как есть: экран логов позволяет писать команды руками. */
    fun sendRaw(text: String) {
        val line = text.trim()
        if (line.isEmpty()) return
        ble.send(line)
    }

    /** Чистит журнал обмена. На посохе при этом ничего не меняется. */
    fun clearLog() {
        _logLines.value = emptyList()
    }

    /** Подводит часы посоха по планшету и сразу спрашивает, что из этого вышло. */
    fun syncTime() {
        ble.send(StaffCommand.time(System.currentTimeMillis() / 1000))
        ble.send(StaffCommand.info())
    }

    // ---------- взвод ----------

    /** Взводит посох: следующий удар об пол бросит count костей по sides граней. */
    fun arm(count: Int, sides: Int) = ble.send(StaffCommand.arm(count, sides))

    /** Снимает взвод. */
    fun disarm() = ble.send(StaffCommand.disarm())

    // ---------- лента ----------

    /** Отмечает бросок как случайный: он остаётся в ленте, но не считается. */
    fun toggleDiscarded(key: String) {
        _rolls.value = _rolls.value.map {
            if (it.key == key) it.copy(discarded = !it.discarded) else it
        }
    }

    fun clearError() { _lastError.value = null }

    override fun onCleared() {
        ble.stopScan()
        ble.disconnect()
        super.onCleared()
    }
}
