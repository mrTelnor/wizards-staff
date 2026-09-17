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
    val id: Long,
    val count: Int,
    val sides: Int,
    val values: List<Int>,
    val total: Int,
    /** Время планшета в момент получения: часы посоха могут быть не выставлены. */
    val receivedAt: Long,
    val discarded: Boolean = false,
) {
    /** Формула броска: 1d20, 3d6. */
    val formula: String get() = "${count}d$sides"

    /** Слагаемые: «2 + 4 + 5». У одного кубика слагаемых нет. */
    val breakdown: String get() = if (values.size > 1) values.joinToString(" + ") else ""

    /** Критический успех считается только по натуральной двадцатке на одном d20. */
    val critSuccess: Boolean get() = count == 1 && sides == 20 && values.firstOrNull() == 20

    /** Критический провал — натуральная единица там же. */
    val critFail: Boolean get() = count == 1 && sides == 20 && values.firstOrNull() == 1
}

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

    private val _dice = MutableStateFlow(listOf(2, 4, 6, 8, 10, 12, 20, 100))
    val dice: StateFlow<List<Int>> = _dice.asStateFlow()

    private val _armed = MutableStateFlow<ArmedState?>(null)
    val armed: StateFlow<ArmedState?> = _armed.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        viewModelScope.launch {
            ble.events.collect { event -> onEvent(event) }
        }
    }

    private fun onEvent(event: StaffEvent) {
        when (event) {
            is StaffEvent.Roll -> {
                val record = RollRecord(
                    id = event.id,
                    count = event.count,
                    sides = event.sides,
                    values = event.values,
                    total = event.total,
                    receivedAt = System.currentTimeMillis(),
                )
                // Посох может прислать бросок повторно при переподключении: по id отсеиваем дубли.
                if (_rolls.value.none { it.id == record.id }) {
                    _rolls.value = listOf(record) + _rolls.value
                }
            }

            is StaffEvent.Battery -> _batteryPercent.value = event.percent

            is StaffEvent.Info -> {
                _firmware.value = event.firmware
                if (event.dice.isNotEmpty()) _dice.value = event.dice
            }

            is StaffEvent.State ->
                _armed.value = if (event.armed) ArmedState(event.count, event.sides) else null

            is StaffEvent.Error -> _lastError.value = event.message
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
        _armed.value = null
    }

    // ---------- взвод ----------

    /** Взводит посох: следующий удар об пол бросит count кубиков по sides граней. */
    fun arm(count: Int, sides: Int) = ble.send(StaffCommand.arm(count, sides))

    /** Снимает взвод. */
    fun disarm() = ble.send(StaffCommand.disarm())

    // ---------- лента ----------

    /** Отмечает бросок как случайный: он остаётся в ленте, но не считается. */
    fun toggleDiscarded(id: Long) {
        _rolls.value = _rolls.value.map { if (it.id == id) it.copy(discarded = !it.discarded) else it }
    }

    fun clearError() { _lastError.value = null }

    override fun onCleared() {
        ble.stopScan()
        ble.disconnect()
        super.onCleared()
    }
}
