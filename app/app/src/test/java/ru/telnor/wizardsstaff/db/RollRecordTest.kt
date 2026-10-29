package ru.telnor.wizardsstaff.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Проверки того, как лента понимает бросок: слагаемые, удача и неудача, двадцатка и единица.
 *
 * Главная ловушка — у удачи и неудачи итог не равен сумме значений: из двух бросков
 * в зачёт идёт один. Лента не должна показывать «20 + 3 = 3» и не должна подсвечивать
 * двадцатку, если она отброшена.
 */
class RollRecordTest {

    private fun roll(count: Int, values: List<Int>, total: Int, flags: Int = 0) = RollRecord(
        staffId = 1, id = 1, mac = "", count = count, sides = 20, values = values,
        total = total, flags = flags, staffTime = 0, receivedAt = 0,
    )

    @Test
    fun обычныйБросокСкладывается() {
        val r = roll(count = 3, values = listOf(2, 4, 5), total = 11)
        assertFalse(r.twoRolls)
        assertEquals("2 + 4 + 5", r.breakdown)
        assertEquals(-1, r.keptIndex)
        assertNull(r.modeLabel)
    }

    @Test
    fun удачаПоказываетОбаБроскаЧерезЗапятую() {
        val r = roll(count = 1, values = listOf(7, 18), total = 18, flags = RollRecord.FLAG_FORTUNE)
        assertTrue(r.twoRolls)
        assertEquals("7, 18", r.breakdown)
        assertEquals(1, r.keptIndex)
        assertEquals("УДАЧА", r.modeLabel)
    }

    @Test
    fun отброшеннаяДвадцаткаНеКрит() {
        val r = roll(count = 1, values = listOf(20, 3), total = 3, flags = RollRecord.FLAG_MISFORTUNE)
        assertFalse(r.critSuccess)
        assertFalse(r.critFail)
        assertEquals(1, r.keptIndex)
        assertEquals("НЕУДАЧА", r.modeLabel)
    }

    @Test
    fun двадцаткаВЗачётеКрит() {
        assertTrue(roll(count = 1, values = listOf(4, 20), total = 20, flags = RollRecord.FLAG_FORTUNE).critSuccess)
        assertTrue(roll(count = 1, values = listOf(20), total = 20).critSuccess)
        assertTrue(roll(count = 1, values = listOf(1, 9), total = 1, flags = RollRecord.FLAG_MISFORTUNE).critFail)
    }

    @Test
    fun стараяЗаписьБезФлаговВсёРавноНеСумма() {
        // Броски с удачей до того, как приложение научилось читать adv: flags ноль, значений два.
        val r = roll(count = 1, values = listOf(14, 7), total = 14)
        assertTrue(r.twoRolls)
        assertEquals("14, 7", r.breakdown)
        assertEquals(0, r.keptIndex)
        assertNull(r.modeLabel)   // режим уже не узнать
    }
}
