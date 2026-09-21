package ru.telnor.wizardsstaff.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/*
 * Сверка листа с сохранением. По ней решается, спрашивать ли «состояние не сохранено,
 * продолжить?», поэтому ошибка здесь стоит дорого в обе стороны: пропустишь разницу —
 * человек молча потеряет ход боя, найдёшь лишнюю — будешь спрашивать каждый раз.
 */
class CharacterSaveTest {

    private fun sheet(): CharacterSheet {
        val seed = silvrinSeed()
        return CharacterSheet(
            record = seed.character.copy(id = 1),
            weapons = seed.weapons.map { it.copy(characterId = 1) },
            feats = seed.feats.map { it.copy(characterId = 1) },
            items = seed.items.map { it.copy(characterId = 1) },
            spells = seed.spells.map { it.copy(characterId = 1) },
        )
    }

    /** Копия, какой её кладёт в базу `saveCopy`: свой номер, своя подпись. */
    private fun copyOf(sheet: CharacterSheet, id: Long = 2): CharacterSheet = CharacterSheet(
        record = sheet.record.copy(id = id, saveName = "снимок", savedAt = 1_700_000_000_000, saveOf = 1),
        weapons = sheet.weapons.map { it.copy(id = it.id + 100, characterId = id) },
        feats = sheet.feats.map { it.copy(id = it.id + 100, characterId = id) },
        items = sheet.items.map { it.copy(id = it.id + 100, characterId = id) },
        spells = sheet.spells.map { it.copy(id = it.id + 100, characterId = id) },
    )

    /**
     * Ловушка на будущее. Сохранение копирует ровно те части листа, что перечислены
     * в `saveCopy` и `sameAs`, — руками, потому что каждая лежит в своей таблице.
     * Появится шестая (скажем, заметки к навыкам или выученные заклинания по кругам) —
     * этот тест покраснеет и напомнит вписать её в оба места. Без него потеря была бы
     * молчаливой: сохранение просто не взяло бы новую часть, и никто бы не заметил
     * до первой загрузки.
     */
    @Test
    fun `частей листа ровно пять — иначе сохранение потеряет новую`() {
        val parts = CharacterSheet::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        assertEquals(
            "у листа изменился состав частей: впиши новую в CharacterRepository.saveCopy " +
                "и в CharacterSheet.sameAs, иначе сохранение её потеряет",
            setOf("record", "weapons", "feats", "items", "spells"),
            parts,
        )
    }

    @Test
    fun `лист совпадает сам с собой`() {
        assertTrue(sheet().sameAs(sheet()))
    }

    @Test
    fun `номера строк и подпись сохранения сравнению не мешают`() {
        val live = sheet()
        assertTrue(live.sameAs(copyOf(live)))
    }

    @Test
    fun `потерянные в бою ПЗ ломают совпадение`() {
        val live = sheet()
        val hurt = live.copy(record = live.record.copy(currentHp = live.record.currentHp - 1))
        assertFalse(hurt.sameAs(copyOf(live)))
    }

    @Test
    fun `поднятый щит ломает совпадение`() {
        val live = sheet()
        val raised = live.copy(record = live.record.copy(shieldRaised = true))
        assertFalse(raised.sameAs(copyOf(live)))
    }

    @Test
    fun `пропавшее оружие ломает совпадение`() {
        val live = sheet()
        val disarmed = live.copy(weapons = live.weapons.drop(1))
        assertFalse(disarmed.sameAs(copyOf(live)))
    }
}
