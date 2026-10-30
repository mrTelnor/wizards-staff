package ru.telnor.wizardsstaff.ble

import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Проверки сборки строк из BLE-кусков. Главная ловушка — русская буква занимает в UTF-8
 * два байта, и пакет может разрезать её пополам. Если превращать в текст каждый кусок
 * отдельно, вместо буквы получатся два ромбика.
 */
class LineAssemblerTest {

    private val russian = """{"ev":"log","msg":"взвод: ждём удара, кость d20 — «Стукни посохом!»"}"""

    /** Скармливает куски по очереди и собирает все строки и все выброшенные. */
    private fun LineAssembler.feedAll(vararg chunks: ByteArray): Assembled {
        val lines = mutableListOf<String>()
        var dropped = 0
        for (chunk in chunks) {
            val got = feed(chunk)
            lines += got.lines
            dropped += got.dropped
        }
        return Assembled(lines, dropped)
    }

    @Test
    fun русскаяСтрокаРазрезаннаяВЛюбомМестеСобирается() {
        val bytes = (russian + "\n").toByteArray(Charsets.UTF_8)
        for (cut in 0..bytes.size) {
            val got = LineAssembler().feedAll(bytes.copyOfRange(0, cut), bytes.copyOfRange(cut, bytes.size))
            assertEquals("разрез на байте $cut", listOf(russian), got.lines)
        }
    }

    @Test
    fun русскаяСтрокаПоОдномуБайту() {
        val bytes = (russian + "\n").toByteArray(Charsets.UTF_8)
        val got = LineAssembler().feedAll(*bytes.map { byteArrayOf(it) }.toTypedArray())
        assertEquals(listOf(russian), got.lines)
    }

    @Test
    fun несколькоСтрокВОдномКуске() {
        val chunk = "{\"ev\":\"bat\",\"mv\":3900}\n{\"ev\":\"log\",\"msg\":\"привет\"}\n".toByteArray()
        val got = LineAssembler().feed(chunk)
        assertEquals(listOf("""{"ev":"bat","mv":3900}""", """{"ev":"log","msg":"привет"}"""), got.lines)
        assertEquals(0, got.dropped)
    }

    @Test
    fun хвостБезПереводаСтрокиЖдётПродолжения() {
        val assembler = LineAssembler()
        val first = assembler.feed("{\"ev\":\"ok\"}\n{\"ev\":\"lo".toByteArray())
        assertEquals(listOf("""{"ev":"ok"}"""), first.lines)
        assertEquals(emptyList<String>(), assembler.feed("g\",\"msg\":\"ещё".toByteArray()).lines)
        assertEquals(listOf("""{"ev":"log","msg":"ещё"}"""), assembler.feed("\"}\n".toByteArray()).lines)
    }

    @Test
    fun пустыеСтрокиИПереводКареткиПропускаются() {
        val got = LineAssembler().feed("\n\r\n   \n{\"ev\":\"ok\"}\r\n\n".toByteArray())
        assertEquals(listOf("""{"ev":"ok"}"""), got.lines)
    }

    @Test
    fun сбросЗабываетНедособранное() {
        val assembler = LineAssembler()
        assembler.feed("{\"ev\":\"полови".toByteArray())
        assembler.reset()
        assertEquals(listOf("""{"ev":"ok"}"""), assembler.feed("{\"ev\":\"ok\"}\n".toByteArray()).lines)
    }

    @Test
    fun переполнениеВыбрасываетСтрокуЦеликомИНеЛомаетСледующие() {
        val assembler = LineAssembler(maxLineBytes = 16)
        // Длинная строка приходит в три куска; её хвост не должен разобраться как отдельная строка.
        val got = assembler.feedAll(
            "{\"ev\":\"log\",\"msg\":\"".toByteArray(),
            "очень длинная строка".toByteArray(),
            "\"}\n{\"ev\":\"ok\"}\n".toByteArray(),
        )
        assertEquals(listOf("""{"ev":"ok"}"""), got.lines)
        assertEquals(1, got.dropped)
    }

    @Test
    fun строкаРовноВПределПроходит() {
        val line = "a".repeat(16)
        val got = LineAssembler(maxLineBytes = 16).feed((line + "\n").toByteArray())
        assertEquals(listOf(line), got.lines)
        assertEquals(0, got.dropped)
    }

    @Test
    fun пределПоУмолчаниюСчитаетсяВБайтах() {
        // 2100 русских букв — это 4200 байт: больше предела, хотя символов меньше 4096.
        val got = LineAssembler().feed(("я".repeat(2100) + "\n{\"ev\":\"ok\"}\n").toByteArray())
        assertEquals(listOf("""{"ev":"ok"}"""), got.lines)
        assertEquals(1, got.dropped)
    }
}
