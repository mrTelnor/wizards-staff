package ru.telnor.wizardsstaff.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Проверки того, что PIN не попадает в журнал обмена: журнал видно на экране,
 * его сохраняют в файл, а строки, которые не разобрались, идут ещё и в logcat.
 *
 * PIN в тестах выдуманные. Сверка идёт с точной строкой, которую сама собрала бы
 * StaffCommand со звёздочками вместо PIN: так видно, что JSON остался целым.
 */
class MaskSecretsTest {

    @Test
    fun входПрячетPin() {
        val masked = maskSecrets(StaffCommand.auth("1234"))
        assertEquals(StaffCommand.auth(MASKED_SECRET), masked)
        assertFalse(masked.contains("1234"))
    }

    @Test
    fun сменаPinПрячетСтарыйИНовый() {
        val masked = maskSecrets(StaffCommand.changePin("1234", "5678"))
        assertEquals(StaffCommand.changePin(MASKED_SECRET, MASKED_SECRET), masked)
        assertFalse(masked.contains("1234"))
        assertFalse(masked.contains("5678"))
    }

    @Test
    fun звёздочекВсегдаЧетыре() {
        // Длину PIN журнал выдавать не должен: и короткий, и длинный выглядят одинаково.
        assertEquals("""{"cmd":"auth","pin":"****"}""", maskSecrets("""{"cmd":"auth","pin":"1"}"""))
        assertEquals(
            """{"cmd":"auth","pin":"****"}""",
            maskSecrets("""{"cmd":"auth","pin":"123456789012"}"""),
        )
        assertEquals("""{"cmd":"auth","pin":"****"}""", maskSecrets("""{"cmd":"auth","pin":""}"""))
    }

    @Test
    fun набранноеРукамиТожеПрячется() {
        // Экран логов позволяет писать команды руками: пробелы, другой порядок полей, PIN числом.
        assertEquals(
            """{ "cmd" : "auth", "pin" : "****" }""",
            maskSecrets("""{ "cmd" : "auth", "pin" : "1234" }"""),
        )
        assertEquals(
            """{"new":"****","old":"****","cmd":"pin"}""",
            maskSecrets("""{"new":"5678","old":"1234","cmd":"pin"}"""),
        )
        assertEquals("""{"cmd":"auth","pin":"****"}""", maskSecrets("""{"cmd":"auth","pin":1234}"""))
        // Незакрытый JSON посох отвергнет, но в журнал он попадёт всё равно.
        assertEquals("""{"cmd":"auth","pin":"****"""", maskSecrets("""{"cmd":"auth","pin":"1234""""))
        assertEquals("""{"cmd":"auth","pin":"****"""", maskSecrets("""{"cmd":"auth","pin":"1234"""))
    }

    @Test
    fun кавычкаВнутриЗначенияНеОставляетХвоста() {
        val masked = maskSecrets("""{"cmd":"auth","pin":"12\"34"}""")
        assertEquals("""{"cmd":"auth","pin":"****"}""", masked)
    }

    @Test
    fun прочиеКомандыНеМеняются() {
        val lines = listOf(
            StaffCommand.info(),
            StaffCommand.arm(3, 6),
            StaffCommand.disarm(),
            StaffCommand.time(1_761_800_000),
            StaffCommand.hist(42),
            """{"cmd":"map","dice":[4,6,8,10,12,20,100,2]}""",
        )
        for (line in lines) assertEquals(line, maskSecrets(line))
    }

    @Test
    fun готовыеСтрокиДляЖурналаБезPin() {
        assertEquals("""{"cmd":"auth","pin":"****"}""", StaffCommand.authForLog())
        assertEquals("""{"cmd":"pin","old":"****","new":"****"}""", StaffCommand.changePinForLog())
        // Повторная маскировка в writeLog их не портит.
        assertEquals(StaffCommand.authForLog(), maskSecrets(StaffCommand.authForLog()))
        assertEquals(StaffCommand.changePinForLog(), maskSecrets(StaffCommand.changePinForLog()))
    }

    @Test
    fun ключOldИлиNewПрячутсяВЛюбойКоманде() {
        // С запасом: поле old или new в чужой команде тоже прячется, лишние звёздочки не страшны.
        assertEquals("""{"cmd":"rename","new":"****"}""", maskSecrets("""{"cmd":"rename","new":"Посох"}"""))
    }

    @Test
    fun приСрабатыванииПрячутсяВсеЗначенияКромеCmd() {
        assertEquals(
            """{"cmd":"auth","pin":"****","x":"****","n":"****","ok":"****"}""",
            maskSecrets("""{"cmd":"auth","pin":"1234","x":"12.34","n":-1.5e3,"ok":true}"""),
        )
        assertEquals("""{'cmd':'auth','pin':'****'}""", maskSecrets("""{'cmd':'auth','pin':'1234'}"""))
    }

    @Test
    fun ременьПрячетЦифрыЕслиТочныйРазборПромахнулся() {
        // Слово pin есть, а ключа, понятного разбору, нет: цифры уходят в звёздочки.
        assertEquals("pin ****", maskOutgoing("pin 1234"))
        // Цифры эскейпа тоже уходят: ремень грубый, зато без исключений.
        assertEquals(
            "{\"cmd\":\"x\",\"note\":\"\\u****in ****\"}",
            maskOutgoing("{\"cmd\":\"x\",\"note\":\"\\u0070in 1234\"}"),
        )
        // Точный разбор сработал — ремень не нужен, строка как у maskSecrets.
        assertEquals(maskSecrets(StaffCommand.auth("1234")), maskOutgoing(StaffCommand.auth("1234")))
    }

    @Test
    fun настоящиеЭскейпыВКлючахИЗначениях() {
        // Эскейпы в ключах, командах и значениях, плюс ключ в верхнем регистре (PIN).
        // Записаны обычными строками с \\u; в """…""" Kotlin \uXXXX не раскрывает, так что
        // эскейп-тесты MaskSecretsBypassTest проверяют то же самое.
        val lines = listOf(
            "{\"cmd\":\"auth\",\"\\u0070in\":\"1234\"}",
            "{\"cmd\":\"\\u0061uth\",\"pin\":\"1234\"}",
            "{\"\\u0063md\":\"auth\",\"pin\":\"1234\"}",
            "{\"cmd\":\"pin\",\"\\u006fld\":\"1234\",\"n\\u0065w\":\"5678\"}",
            "{\"cmd\":\"auth\",\"pin\":\"\\u0031\\u0032\\u0033\\u0034\"}",
            "{\"cmd\":\"auth\",\"\\u0050IN\":\"1234\"}",
        )
        for (line in lines) {
            assertTrue(line, line.contains("\\u"))
            val masked = maskSecrets(line)
            assertFalse(masked, masked.contains("1234") || masked.contains("5678"))
            assertFalse(masked, masked.contains("\\u0031"))
        }
    }

    @Test
    fun ременьНеТрогаетОбычныеКоманды() {
        val lines = listOf(StaffCommand.arm(3, 6), StaffCommand.hist(42), StaffCommand.time(1_761_800_000))
        for (line in lines) assertEquals(line, maskOutgoing(line))
        // Слово внутри другого слова — не повод: opinion и pinset не трогаем.
        assertEquals("""{"cmd":"x","pinset":1,"m":"opinion 5"}""", maskOutgoing("""{"cmd":"x","pinset":1,"m":"opinion 5"}"""))
    }

    @Test
    fun ответыПосохаТочнаяМаскировкаНеТрогает() {
        // Для входящих строк ремня нет: цифры в тексте посоха остаются.
        val line = """{"ev":"log","msg":"неверный PIN, промах 3 подряд, пауза 30 с"}"""
        assertEquals(line, maskSecrets(line))
    }

    @Test
    fun дробноеЧислоПрячетсяЦеликом() {
        assertEquals("""{"cmd":"auth","pin":"****"}""", maskSecrets("""{"cmd":"auth","pin":12.34}"""))
        assertEquals("""{"cmd":"auth","pin":"****"}""", maskSecrets("""{"cmd":"auth","pin":1.2e+34}"""))
    }

    @Test
    fun строкаБезPinНеМеняется() {
        val lines = listOf(
            """{"ev":"auth","ok":false,"wait":30}""",
            """{"ev":"info","fw":"0.21.0","staffId":7,"pinset":true}""",
            """{"ev":"err","msg":"pin rejected"}""",
            """{"ev":"ok","cmd":"pin"}""",
            "подключаемся к 3C:0F:02:A3:F9:8A",
            "",
        )
        for (line in lines) assertEquals(line, maskSecrets(line))
    }

    @Test
    fun ответПосохаНаСменуНеСчитаетсяКомандой() {
        // В ответе «ok» поле cmd тоже есть, но значений PIN в нём нет, и строка цела.
        val line = """{"ev":"ok","cmd":"pin"}"""
        assertEquals(line, maskSecrets(line))
        assertTrue(maskSecrets(line).contains("\"cmd\":\"pin\""))
    }
}
