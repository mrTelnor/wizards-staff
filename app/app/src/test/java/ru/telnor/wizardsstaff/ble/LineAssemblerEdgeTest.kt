package ru.telnor.wizardsstaff.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/*
 * Края LineAssembler: границы предела, переполнение и что идёт после него,
 * одновременные вызовы из разных потоков (колбэки BLE и кнопка «Отключить»).
 */
class LineAssemblerEdgeTest {

    @Test
    fun наБайтБольшеПределаСтрокаВыброшена() {
        val got = LineAssembler(maxLineBytes = 16).feed(("a".repeat(17) + "\n").toByteArray())
        assertEquals(emptyList<String>(), got.lines)
        assertEquals(1, got.dropped)
    }

    @Test
    fun послеПереполненияСледующаяСтрокаВТомЖеКускеЦела() {
        val got = LineAssembler(maxLineBytes = 16)
            .feed(("a".repeat(100) + "\n{\"ev\":\"ok\"}\n").toByteArray())
        assertEquals(listOf("""{"ev":"ok"}"""), got.lines)
        assertEquals(1, got.dropped)
    }

    @Test
    fun переводСтрокиСразуЗаПереполнениемВОтдельномКуске() {
        val assembler = LineAssembler(maxLineBytes = 16)
        val first = assembler.feed("a".repeat(17).toByteArray())
        assertEquals(1, first.dropped)
        val second = assembler.feed("\n{\"ev\":\"ok\"}\n".toByteArray())
        assertEquals(listOf("""{"ev":"ok"}"""), second.lines)
        assertEquals(0, second.dropped)
    }

    @Test
    fun двеДлинныеСтрокиСчитаютсяДважды() {
        val got = LineAssembler(maxLineBytes = 8)
            .feed(("x".repeat(20) + "\n" + "y".repeat(20) + "\nok\n").toByteArray())
        assertEquals(listOf("ok"), got.lines)
        assertEquals(2, got.dropped)
    }

    @Test
    fun сбросВоВремяПропускаХвостаНеГлотаетСледующуюСтроку() {
        // Связь порвалась посреди слишком длинной строки. После переподключения первая
        // строка нового сеанса не должна приниматься за хвост старой.
        val assembler = LineAssembler(maxLineBytes = 16)
        assertEquals(1, assembler.feed("z".repeat(40).toByteArray()).dropped)
        assembler.reset()
        assertEquals(listOf("""{"ev":"ok"}"""), assembler.feed("{\"ev\":\"ok\"}\n".toByteArray()).lines)
    }

    @Test
    fun битыйUtf8НеРоняетСборку() {
        val bad = byteArrayOf(0xD0.toByte(), '\n'.code.toByte()) + "ok\n".toByteArray()
        val got = LineAssembler().feed(bad)
        assertEquals(2, got.lines.size)
        assertEquals("ok", got.lines[1])
    }

    @Test
    fun параллельныеКускиНеПеремешиваются() {
        // Каждый кусок — целая строка. При правильной блокировке строки не склеиваются
        // и ни одна не теряется, даже если reset зовут одновременно из другого потока.
        val assembler = LineAssembler()
        val threads = 8
        val perThread = 2_000
        val pool = Executors.newFixedThreadPool(threads + 1)
        val start = CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<String>())
        val futures = (0 until threads).map { t ->
            pool.submit {
                start.await()
                repeat(perThread) { i ->
                    results += assembler.feed("{\"t\":$t,\"i\":$i}\n".toByteArray()).lines
                }
            }
        }
        val resetter = pool.submit {
            start.await()
            repeat(5_000) { assembler.reset() }
        }
        start.countDown()
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        resetter.get(30, TimeUnit.SECONDS)
        pool.shutdown()

        assertEquals(threads * perThread, results.size)
        val expected = (0 until threads).flatMap { t -> (0 until perThread).map { "{\"t\":$t,\"i\":$it}" } }.toSet()
        assertEquals(expected, results.toSet())
    }
}
