package ru.telnor.wizardsstaff.ble

import org.json.JSONObject

/*
 * Протокол обмена с посохом. Одно сообщение — одна строка JSON, в конце перевод строки.
 * Описание протокола целиком: docs/ПЛАН-APP.md, раздел «Протокол».
 *
 * Разбираем встроенным org.json: сообщений всего пять видов, ради них не стоит
 * подключать библиотеку сериализации и плагин к сборке.
 */

/** Событие, пришедшее с посоха. */
sealed interface StaffEvent {

    /** Бросок: сколько костей, какие, что выпало, сумма, время посоха (0, если часы не выставлены). */
    data class Roll(
        val id: Long,
        val count: Int,
        val sides: Int,
        val values: List<Int>,
        val total: Int,
        val staffTime: Long,
    ) : StaffEvent

    /** Заряд: милливольты и проценты. */
    data class Battery(val millivolts: Int, val percent: Int) : StaffEvent

    /**
     * Сведения о посохе при подключении. staffTime — время его часов, 0 если не выставлены.
     * armSeconds — через сколько секунд спадает взвод. Берём у посоха, а не храним у себя:
     * иначе подсказка в приложении разъедется с прошивкой, как уже было с версией и историей.
     */
    data class Info(
        val firmware: String,
        val historySize: Int,
        val dice: List<Int>,
        val staffTime: Long,
        val armSeconds: Int,
        /** Задан ли на посохе PIN. Если нет, посох отвечает всем подряд. */
        val pinSet: Boolean,
        /** Открыт ли вход в этом сеансе связи. */
        val authed: Boolean,
    ) : StaffEvent

    /**
     * Итог попытки входа. waitSeconds — сколько ещё секунд посох не принимает попытки:
     * он считает промахи и после каждого третьего берёт паузу. Ноль значит «пробуй сейчас».
     */
    data class Auth(val ok: Boolean, val waitSeconds: Int) : StaffEvent

    /** Чего посох сейчас ждёт: покой или взведён на такой-то бросок. */
    data class State(val armed: Boolean, val count: Int, val sides: Int) : StaffEvent

    /** Посох не понял команду. */
    data class Error(val message: String) : StaffEvent

    /** Строка из монитора посоха: срабатывания датчика, спавший взвод. */
    data class Log(val message: String) : StaffEvent

    /** Ответ «сделано» на команду вроде time или map. */
    data class Ok(val command: String) : StaffEvent
}

/** Разбирает строку с посоха. Возвращает null, если строка не JSON или событие незнакомое. */
fun parseStaffEvent(line: String): StaffEvent? {
    val json = try {
        JSONObject(line)
    } catch (e: Exception) {
        return null
    }

    return when (json.optString("ev")) {
        "roll" -> {
            val array = json.optJSONArray("v")
            val values = buildList {
                if (array != null) for (i in 0 until array.length()) add(array.optInt(i))
            }
            StaffEvent.Roll(
                id = json.optLong("id"),
                count = json.optInt("n", values.size),
                sides = json.optInt("d"),
                values = values,
                total = json.optInt("t", values.sum()),
                staffTime = json.optLong("ts"),
            )
        }

        "bat" -> StaffEvent.Battery(json.optInt("mv"), json.optInt("pct"))

        "info" -> {
            val array = json.optJSONArray("dice")
            val dice = buildList {
                if (array != null) for (i in 0 until array.length()) add(array.optInt(i))
            }
            StaffEvent.Info(
                firmware = json.optString("fw"),
                historySize = json.optInt("hist"),
                dice = dice,
                staffTime = json.optLong("ts"),
                armSeconds = json.optInt("arm"),
                // До входа посох отдаёт сведения сокращённо: этих двух полей хватает,
                // чтобы понять, нужен ли PIN и не открыт ли вход уже.
                pinSet = json.optBoolean("pinset"),
                authed = json.optBoolean("authed"),
            )
        }

        "state" -> {
            val armed = json.optString("st") == "armed"
            StaffEvent.State(armed, json.optInt("n", 1), json.optInt("d", 20))
        }

        "auth" -> StaffEvent.Auth(json.optBoolean("ok"), json.optInt("wait"))

        "log" -> StaffEvent.Log(json.optString("msg"))
        "err" -> StaffEvent.Error(json.optString("msg"))
        "ok" -> StaffEvent.Ok(json.optString("cmd"))
        else -> null
    }
}

/** Команды посоху. Каждая уходит одной строкой. */
object StaffCommand {

    /** Спросить версию прошивки и назначение кнопок. */
    fun info(): String = """{"cmd":"info"}"""

    /** Взвести посох: следующий удар об пол бросит count костей по sides граней. */
    fun arm(count: Int, sides: Int): String = """{"cmd":"arm","n":$count,"d":$sides}"""

    /** Снять взвод. */
    fun disarm(): String = """{"cmd":"disarm"}"""

    /** Выставить часы посоха по времени планшета, секунды UTC. */
    fun time(epochSeconds: Long): String = """{"cmd":"time","epoch":$epochSeconds}"""

    /**
     * Вход по PIN. Пока он не назван, посох отвечает только сокращённым info,
     * а через десять секунд разрывает связь сам.
     */
    fun auth(pin: String): String = """{"cmd":"auth","pin":"$pin"}"""

    /** Смена PIN. Старый обязателен, даже когда вход уже открыт. */
    fun changePin(oldPin: String, newPin: String): String =
        """{"cmd":"pin","old":"$oldPin","new":"$newPin"}"""
}
