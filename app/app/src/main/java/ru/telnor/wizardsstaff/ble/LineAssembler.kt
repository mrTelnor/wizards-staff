package ru.telnor.wizardsstaff.ble

import java.io.ByteArrayOutputStream

/*
 * Сборка строк из кусков, которые приходят по BLE.
 *
 * Посох режет строку по размеру пакета, не глядя на буквы, и русская буква (в UTF-8 это
 * два байта) может оказаться разрезана между двумя кусками. Если превращать в текст каждый
 * кусок отдельно, обе половинки станут ромбиками, и склейка их уже не вернёт. Поэтому
 * копим БАЙТЫ до перевода строки и только законченную строку превращаем в текст.
 * Байт '\n' внутри многобайтовой буквы не встречается, так что резать по нему безопасно.
 *
 * Отдельный класс без Android: так его проверяет обычный JVM-тест.
 */

/** Сколько байт может занять одна строка, пока ждём перевода строки. */
const val MAX_LINE_BYTES = 4096

/** Итог одного куска: законченные строки и сколько слишком длинных строк выброшено. */
data class Assembled(val lines: List<String>, val dropped: Int)

class LineAssembler(private val maxLineBytes: Int = MAX_LINE_BYTES) {

    private val buffer = ByteArrayOutputStream()

    // Строка переросла предел: её начало выброшено, хвост пропускаем до перевода строки.
    // Иначе хвост разобрался бы как отдельная строка, а это заведомый мусор.
    private var skipping = false

    /**
     * Принимает кусок и отдаёт строки, которые в нём закончились. Пробелы и '\r'
     * по краям срезаются, пустые строки пропускаются. Хвост без '\n' ждёт следующего куска.
     */
    @Synchronized
    fun feed(chunk: ByteArray): Assembled {
        val lines = mutableListOf<String>()
        var dropped = 0
        for (byte in chunk) {
            if (byte == '\n'.code.toByte()) {
                if (!skipping) {
                    val line = buffer.toString(Charsets.UTF_8.name()).trim()
                    if (line.isNotEmpty()) lines += line
                }
                buffer.reset()
                skipping = false
                continue
            }
            if (skipping) continue
            if (buffer.size() >= maxLineBytes) {
                // Защита от мусора: без перевода строки буфер не должен расти бесконечно.
                buffer.reset()
                skipping = true
                dropped++
                continue
            }
            buffer.write(byte.toInt())
        }
        return Assembled(lines, dropped)
    }

    /** Забывает недособранную строку. Вызывать при разрыве связи. */
    @Synchronized
    fun reset() {
        buffer.reset()
        skipping = false
    }
}
