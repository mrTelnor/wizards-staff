package ru.telnor.wizardsstaff.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.telnor.wizardsstaff.rules.Ability
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.rules.SILVRIN
import ru.telnor.wizardsstaff.rules.SILVRIN_CLAWS
import ru.telnor.wizardsstaff.rules.SILVRIN_MAUL
import ru.telnor.wizardsstaff.rules.Save
import ru.telnor.wizardsstaff.rules.Skill
import ru.telnor.wizardsstaff.rules.WeaponTraining
import ru.telnor.wizardsstaff.rules.armorClass
import ru.telnor.wizardsstaff.rules.skillMod

/*
 * Проверки засева и хранения листа.
 *
 * Главная из них — что лист, попавший в базу, даёт движку ровно те же входные данные,
 * что и эталон `SILVRIN`, на котором стоят проверки правил. Без неё две копии листа
 * разошлись бы молча: Pf2Test проверяет эталон, а на экране показывается база.
 */
class SilvrinSeedTest {

    private val seed = silvrinSeed()

    // ---------- засев ----------

    @Test
    fun `лист из базы даёт движку тот же лист, что и эталон`() {
        assertEquals(SILVRIN, seed.character.toSheet())
    }

    @Test
    fun `броня находится в справочнике по имени`() {
        // Имя хранится строкой, и опечатка в нём означала бы КБ без брони — 21 вместо 24.
        val armor = seed.character.toSheet().armor
        assertNotNull(armor)
        assertEquals("Деревянный нагрудник", armor?.name)
        assertEquals(24, seed.character.toSheet().armorClass())
    }

    @Test
    fun `числа, которые движок не выводит, взяты из бланка`() {
        val c = seed.character
        assertEquals(8, c.level)
        assertEquals(900, c.xp)
        assertEquals(1, c.heroPoints)
        assertEquals(82, c.maxHp)
        assertEquals(36, c.currentHp)
        assertEquals("К ядам", c.resistances)
        assertEquals("Всеобщий, аммуррун", c.languages)
        assertEquals(1, c.shieldAc)
        assertEquals(10, c.shieldHardness)
        assertEquals(3, c.gold)
        assertEquals(668, c.silver)
        assertEquals(2, c.bulk)
        assertEquals(12, c.maxBulk)
        assertEquals(4, c.cantripRank)
    }

    @Test
    fun `оружие переезжает в базу без потерь и в порядке бланка`() {
        assertEquals(2, seed.weapons.size)
        assertEquals(SILVRIN_CLAWS, seed.weapons[0].toWeapon())
        assertEquals(SILVRIN_MAUL, seed.weapons[1].toWeapon())
        assertEquals(listOf(0, 1), seed.weapons.map { it.position })
        // Короткие имена подставляются в «Атака …» и «Урон …» на вкладке «Обзор».
        assertEquals(listOf("когтями", "молотом"), seed.weapons.map { it.shortName })
    }

    @Test
    fun `черты разложены по колонкам вкладки`() {
        assertEquals(16, seed.feats.size)
        assertEquals(5, seed.feats.count { it.featGroup == FeatGroup.ANCESTRY })
        assertEquals(6, seed.feats.count { it.featGroup == FeatGroup.CLASS })
        assertEquals(3, seed.feats.count { it.featGroup == FeatGroup.SKILL })
        assertEquals(2, seed.feats.count { it.featGroup == FeatGroup.GENERAL })
        // Порядок сквозной: по нему репозиторий раскладывает то, что Room вернул как попало.
        assertEquals(seed.feats.indices.toList(), seed.feats.map { it.position })
    }

    @Test
    fun `заклинание без броска на посох не уходит`() {
        val spell = seed.spells.single()
        assertEquals("Наставление", spell.name)
        assertTrue(spell.isCantrip)
        assertEquals(false, spell.hasRoll)
    }

    @Test
    fun `знание показывается с уточнением из бланка`() {
        assertEquals("Знание (Ламашту)", seed.character.skillTitle(Skill.LORE_1))
        assertEquals("Знание", seed.character.skillTitle(Skill.LORE_2))
        assertEquals("Медицина", seed.character.skillTitle(Skill.MEDICINE))
    }

    // ---------- хранение карт в одной ячейке ----------

    private val converters = CharacterConverters()

    @Test
    fun `карта навыков переживает запись и чтение`() {
        val packed = converters.fromSkillRanks(SILVRIN.skills)
        assertEquals(SILVRIN.skills, converters.toSkillRanks(packed))
    }

    @Test
    fun `карты характеристик, испытаний и брони переживают запись и чтение`() {
        assertEquals(SILVRIN.scores, converters.toScores(converters.fromScores(SILVRIN.scores)))
        assertEquals(SILVRIN.saves, converters.toSaveRanks(converters.fromSaveRanks(SILVRIN.saves)))
        assertEquals(
            SILVRIN.armorRanks,
            converters.toArmorRanks(converters.fromArmorRanks(SILVRIN.armorRanks)),
        )
        assertEquals(emptyMap<Save, Int>(), converters.toSaveItems(converters.fromSaveItems(emptyMap())))
    }

    @Test
    fun `умение в оружии переживает запятую внутри названия`() {
        // «без брони, лёгкая, средняя» — строка из бланка, и запятая в ней своя.
        val training = listOf(
            WeaponTraining("без брони, лёгкая, средняя", Rank.TRAINED),
            WeaponTraining("Нож-звезда", Rank.EXPERT),
        )
        assertEquals(training, converters.toWeaponTraining(converters.fromWeaponTraining(training)))
    }

    @Test
    fun `испорченная ячейка не роняет лист, а теряет только непонятное`() {
        // Так выглядит база, пережившая переименование навыка: ключа больше нет.
        val map = converters.toSkillRanks("MEDICINE:EXPERT,ЧЕГО-ТО:EXPERT,STEALTH:НЕПОНЯТНО")
        assertEquals(mapOf(Skill.MEDICINE to Rank.EXPERT), map)
        assertEquals(emptyMap<Ability, Int>(), converters.toScores(""))
    }

    // ---------- вывод по листу из базы ----------

    @Test
    fun `по листу из базы выводятся те же числа, что в бумажном бланке`() {
        val sheet = seed.character.toSheet()
        assertEquals(16, sheet.skillMod(Skill.MEDICINE))
        assertEquals(14, sheet.skillMod(Skill.ATHLETICS))
        assertEquals(11, sheet.skillMod(Skill.STEALTH))
    }
}
