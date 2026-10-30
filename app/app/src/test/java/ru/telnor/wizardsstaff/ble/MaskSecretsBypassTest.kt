package ru.telnor.wizardsstaff.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/*
 * Обходы маскировки PIN: строки, которые посох (ArduinoJson 7.4.3) примет как вход
 * или смену PIN, а maskSecrets пропустит в журнал как есть.
 *
 * Что ArduinoJson разбирает сверх строгого JSON (src/ArduinoJson/Json/JsonDeserializer.hpp):
 * - строки и ключи в одинарных кавычках (isQuote: ' и ");
 * - ключи без кавычек (parseNonQuotedString: буквы, цифры, _);
 * - \uXXXX в ключах и значениях (ARDUINOJSON_DECODE_UNICODE = 1 по умолчанию).
 * Экран логов шлёт набранное руками как есть (StaffViewModel.sendRaw), так что всё это
 * реально уходит на посох и реально пускает внутрь.
 *
 * Тест проверяет одно: настоящего PIN в строке журнала нет. Как именно строка
 * замаскирована, не важно.
 */
class MaskSecretsBypassTest {

    private val pin = "1234"
    private val newPin = "5678"

    private fun assertNoPin(line: String) {
        val masked = maskSecrets(line)
        assertFalse("PIN остался в журнале: $masked", masked.contains(pin))
        assertFalse("новый PIN остался в журнале: $masked", masked.contains(newPin))
    }

    // ---------- обходы, которые посох примет ----------

    @Test
    fun ключPinЧерезЮникодЭскейп() = assertNoPin("""{"cmd":"auth","\u0070in":"1234"}""")

    @Test
    fun имяКомандыЧерезЮникодЭскейп() = assertNoPin("""{"cmd":"\u0061uth","pin":"1234"}""")

    @Test
    fun ключCmdЧерезЮникодЭскейп() = assertNoPin("""{"\u0063md":"auth","pin":"1234"}""")

    @Test
    fun сменаPinСЭскейпамиВКлючах() =
        assertNoPin("""{"cmd":"pin","\u006fld":"1234","n\u0065w":"5678"}""")

    @Test
    fun одинарныеКавычки() = assertNoPin("""{'cmd':'auth','pin':'1234'}""")

    @Test
    fun значениеPinВОдинарныхКавычках() = assertNoPin("""{"cmd":"auth","pin":'1234'}""")

    @Test
    fun ключиБезКавычек() = assertNoPin("""{cmd:"auth",pin:"1234"}""")

    @Test
    fun сменаPinКлючиБезКавычек() = assertNoPin("""{cmd:"pin",old:"1234",new:"5678"}""")

    // Посох режет команды по закрывающей скобке, не глядя на кавычки (ble.h): для него
    // в каждой из этих строк две команды, и вторая — вход по PIN.

    @Test
    fun скобкаПослеОдинарнойКавычки() = assertNoPin("""'}{"cmd":"auth","pin":"1234"}""")

    @Test
    fun скобкаПослеДвойнойКавычки() = assertNoPin(""""}{"cmd":"auth","pin":"1234"}""")

    @Test
    fun скобкаВнутриСтрокиЗначения() = assertNoPin("""{"a":"x}{"cmd":"auth","pin":"1234"}""")

    @Test
    fun двоеточиеПослеЗначенияPin() = assertNoPin("""{"cmd":"auth","pin":"1234":1}""")

    // ---------- то, что уже закрыто: держим, чтобы не сломалось ----------

    @Test
    fun cmdНеПервымИПробелы() {
        assertNoPin("""{"pin":"1234","cmd":"auth"}""")
        assertNoPin("{\t\"pin\"\t:\t\"1234\" ,\t\"cmd\"\t:\t\"auth\"\t}")
    }

    @Test
    fun эскейпВЗначенииPin() = assertNoPin("""{"cmd":"auth","pin":"\u0031\u0032\u0033\u0034x1234"}""")

    @Test
    fun дубльПоляCmd() {
        // ArduinoJson берёт последнее значение ключа: это вход, хотя первым стоит info.
        assertNoPin("""{"cmd":"info","cmd":"auth","pin":"1234"}""")
    }

    @Test
    fun дваОбъектаВОднойСтроке() {
        // Посох режет команды по закрывающей скобке: вторая команда уйдёт отдельно.
        assertNoPin("""{"cmd":"info"}{"cmd":"auth","pin":"1234"}""")
    }

    @Test
    fun вложенныйОбъектТожеПрячется() {
        // Посох вложенный pin не читает, но набранный руками PIN в журнал попадать не должен.
        assertNoPin("""{"cmd":"auth","x":{"pin":"1234"}}""")
    }

    // ---------- ложные срабатывания ----------

    @Test
    fun ответыПосохаНеМеняются() {
        val lines = listOf(
            """{"ev":"log","msg":"неверный PIN, промах 1 подряд, пауза 30 с"}""",
            """{"ev":"log","msg":"PIN изменён с планшета"}""",
            """{"ev":"err","msg":"bad json"}""",
            """{"ev":"log","msg":"команда pin: \"new\" не годится"}""",
            """{"ev":"roll","id":5,"n":1,"d":20,"v":[20],"t":20,"ts":1761800000}""",
        )
        for (line in lines) assertEquals(line, maskSecrets(line))
    }

    @Test
    fun безCmdPinТожеПрячется() {
        // Без cmd посох ответит «нет поля cmd» и PIN не проверит, но набранный руками PIN
        // в журнал попадать не должен. Решение координатора 2026-10-30: прятать и такую строку.
        assertNoPin("""{"pin":"1234"}""")
    }
}
