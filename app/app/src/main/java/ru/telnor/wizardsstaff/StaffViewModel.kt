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

/**
 * Что происходит с входом на посох. Посох с PIN до входа отвечает только сокращёнными
 * сведениями, а через десять секунд молчания разрывает связь сам.
 */
sealed interface AuthState {
    /** Посох ещё не ответил на сведения: непонятно, нужен ли PIN. */
    data object Unknown : AuthState

    /** PIN на посохе не задан, входить некуда. */
    data object NotNeeded : AuthState

    /**
     * Нужен PIN. wrong — посох отверг тот, что мы назвали; waitSeconds — сколько
     * он ещё не будет принимать попытки, потому что счёл это подбором.
     */
    data class NeedPin(val wrong: Boolean = false, val waitSeconds: Int = 0) : AuthState

    /** Вход открыт, посох принимает все команды. */
    data object Open : AuthState
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

    // ---------- вход по PIN ----------

    private val pins = PinStore(application)

    private val _auth = MutableStateFlow<AuthState>(AuthState.Unknown)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    /** Открыто ли окно ввода PIN. Отдельно от состояния: окно можно закрыть, не входя. */
    private val _pinPrompt = MutableStateFlow(false)
    val pinPrompt: StateFlow<Boolean> = _pinPrompt.asStateFlow()

    /** Чем кончилась смена PIN. Показывается человеку и сбрасывается. */
    private val _pinChangeResult = MutableStateFlow<String?>(null)
    val pinChangeResult: StateFlow<String?> = _pinChangeResult.asStateFlow()

    /**
     * PIN, который человек только что набрал руками. Живёт до подтверждения посохом,
     * и намеренно переживает переподключение: пока его набирают, посох успевает
     * разорвать связь по таймауту, и набранное должно уйти в следующий же сеанс.
     */
    private var pendingPin: String? = null

    /** Новый PIN, ожидающий подтверждения командой pin: сохраняем только после «сделано». */
    private var pendingNewPin: String? = null

    /** Окно закрыли, не назвав PIN: не открывать его снова само на каждом переподключении. */
    private var pinPromptDismissed = false

    // Чем кончилась прошлая попытка входа. Держим отдельно от AuthState, потому что тот
    // обнуляется при каждом разрыве связи, а посох разрывает её каждые десять секунд.
    // Без этого сообщение «неверный PIN, ждать 60 с» пропадало бы через мгновение после
    // того, как человек его прочитал, — ровно когда оно нужнее всего.
    private var lastPinWrong = false
    private var lastPinWait = 0

    /** Часы и полные сведения запрашиваются один раз за сеанс, иначе получился бы круг. */
    private var doorOpened = false

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
                if (connected != wasConnected) {
                    if (connected) session++
                    // Дверь закрывается вместе со связью: посох забывает вход при разрыве,
                    // и в новом сеансе PIN придётся назвать заново.
                    doorOpened = false
                    // «Нужен PIN» при этом не забываем. Закрытый посох рвёт связь каждые
                    // десять секунд, и если сбрасывать это состояние на каждом разрыве,
                    // экран замигает между карточкой посоха и списком поиска.
                    if (_auth.value !is AuthState.NeedPin) _auth.value = AuthState.Unknown
                }
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
                // Посох с PIN до входа отдаёт сведения сокращённо: костей, ёмкости истории
                // и своего времени там нет вовсе. Разбирать такой ответ как полный нельзя:
                // расхождение часов стало бы «минус пятьдесят шесть лет», а список костей
                // опустел бы. Версия прошивки есть в обоих видах ответа.
                val short = event.pinSet && !event.authed
                _firmware.value = event.firmware
                if (!short) {
                    if (event.dice.isNotEmpty()) _dice.value = event.dice
                    if (event.armSeconds > 0) _armSeconds.value = event.armSeconds
                    _clockSkew.value = if (event.staffTime > 0) {
                        event.staffTime - System.currentTimeMillis() / 1000
                    } else {
                        null   // часы посоха не выставлены, сравнивать не с чем
                    }
                }
                afterInfo(event)
            }

            is StaffEvent.State ->
                _armed.value = if (event.armed) ArmedState(event.count, event.sides) else null

            is StaffEvent.Auth -> afterAuth(event)

            is StaffEvent.Error -> {
                _lastError.value = event.message
                // Посох отверг смену PIN: новый не сохраняем, говорим человеку почему.
                if (pendingNewPin != null) {
                    pendingNewPin = null
                    _pinChangeResult.value = "Посох отказал: ${event.message}"
                }
            }

            is StaffEvent.Ok -> {
                if (event.command == "pin") {
                    // Посох подтвердил смену. Только теперь запоминаем новый PIN:
                    // сохранить раньше значило бы при отказе остаться с чужим.
                    val address = connectedDevice.value?.address
                    pendingNewPin?.let { pin -> address?.let { pins.save(it, pin) } }
                    pendingNewPin = null
                    _pinChangeResult.value = "PIN изменён"
                }
            }

            // Строка монитора посоха ничего не меняет: в журнале обмена она уже видна.
            is StaffEvent.Log -> Unit
        }
    }

    // ---------- вход по PIN ----------

    /**
     * Ответ на сведения решает, что делать дальше. Это и есть вся развилка входа:
     * посох без защиты — идём работать; вход уже открыт — ничего не делаем;
     * PIN нужен и мы его знаем — называем сами; не знаем — спрашиваем человека.
     */
    private fun afterInfo(event: StaffEvent.Info) {
        if (!event.pinSet) {
            _auth.value = AuthState.NotNeeded
            openDoor()
            return
        }
        if (event.authed) {
            _auth.value = AuthState.Open
            return
        }
        val address = connectedDevice.value?.address
        val pin = pendingPin ?: address?.let { pins.get(it) }
        if (pin != null) {
            ble.send(StaffCommand.auth(pin))
        } else {
            _auth.value = AuthState.NeedPin(lastPinWrong, lastPinWait)
            if (!pinPromptDismissed) _pinPrompt.value = true
        }
    }

    /** Посох ответил на попытку входа. */
    private fun afterAuth(event: StaffEvent.Auth) {
        val address = connectedDevice.value?.address
        if (event.ok) {
            // Запоминаем только то, что посох принял. Сохранять раньше значило бы
            // держать в планшете заведомо неверный PIN.
            pendingPin?.let { pin -> address?.let { pins.save(it, pin) } }
            pendingPin = null
            lastPinWrong = false
            lastPinWait = 0
            pinPromptDismissed = false
            _pinPrompt.value = false
            _auth.value = AuthState.Open
            openDoor()
            return
        }

        // Не приняли. Сохранённый забываем: иначе приложение будет долбить им посох
        // при каждом переподключении и само наберёт промахов на часовую паузу.
        pendingPin = null
        address?.let { pins.forget(it) }
        lastPinWrong = true
        // Секунды — снимок на момент отказа, вживую они не тикают. Это честно: число
        // приходит от посоха и может только уменьшаться, а обещать точный отсчёт,
        // когда связь рвётся каждые десять секунд, значило бы врать.
        lastPinWait = event.waitSeconds
        _auth.value = AuthState.NeedPin(wrong = true, waitSeconds = event.waitSeconds)
        if (!pinPromptDismissed) _pinPrompt.value = true
    }

    /**
     * Дверь открыта: подводим часы и переспрашиваем сведения, потому что до входа
     * они приходили сокращёнными. Один раз за сеанс — иначе ответ на эти же сведения
     * снова привёл бы сюда, и приложение закружилось бы.
     */
    private fun openDoor() {
        if (doorOpened) return
        doorOpened = true
        ble.send(StaffCommand.time(System.currentTimeMillis() / 1000))
        ble.send(StaffCommand.info())
    }

    /** Человек набрал PIN в окне. */
    fun submitPin(pin: String) {
        pendingPin = pin
        // Пробуем заново: прошлый отказ больше не показываем, иначе окно врало бы
        // про неверный PIN ещё до того, как посох ответит про новый.
        lastPinWrong = false
        lastPinWait = 0
        _pinPrompt.value = false
        pinPromptDismissed = false
        // Если связи сейчас нет, посылать некуда: посох сам переподключится через
        // считаные секунды, и набранный PIN уйдёт в ответ на его сведения.
        if (connection.value == Connection.Connected) ble.send(StaffCommand.auth(pin))
    }

    /** Окно ввода закрыли, не назвав PIN. Само оно больше не откроется. */
    fun dismissPinPrompt() {
        _pinPrompt.value = false
        pinPromptDismissed = true
    }

    /** Открыть окно ввода по кнопке. */
    fun showPinPrompt() {
        pinPromptDismissed = false
        _pinPrompt.value = true
    }

    /** Сменить PIN посоха. Старый обязателен, его проверяет сам посох. */
    fun changePin(oldPin: String, newPin: String) {
        pendingNewPin = newPin
        _pinChangeResult.value = null
        ble.send(StaffCommand.changePin(oldPin, newPin))
    }

    /** Убрать показанный итог смены PIN. */
    fun clearPinChangeResult() {
        _pinChangeResult.value = null
    }

    /** PIN этого посоха, сохранённый в планшете. Нужен, чтобы подставить его в поле «старый». */
    fun savedPin(): String? = connectedDevice.value?.address?.let { pins.get(it) }

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
        _auth.value = AuthState.Unknown
        _pinPrompt.value = false
        pendingPin = null
        pinPromptDismissed = false
        lastPinWrong = false
        lastPinWait = 0
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
