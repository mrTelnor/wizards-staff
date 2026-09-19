package ru.telnor.wizardsstaff.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Сверка движка вывода с бумажным листом Сильврина (docs/Сильврин Хвостозвон-1(2).pdf).
 *
 * Смысл этих проверок не в том, чтобы поймать опечатку в коде, а в том, чтобы поймать
 * неверно понятое правило. Поэтому здесь ни одного числа, придуманного нами: все взяты
 * из заполненного бланка, а движок считает их сам из характеристик и владений.
 *
 * Если после правки правил тест покраснеет — значит, лист и приложение разошлись,
 * и разбираться надо до того, как числа уедут на игровой стол.
 */
class Pf2Test {

    // ---------- модификаторы характеристик ----------

    @Test
    fun `модификатор считается вниз и у нечётных значений`() {
        assertEquals(-2, abilityMod(7))
        assertEquals(-1, abilityMod(9))
        assertEquals(0, abilityMod(10))
        assertEquals(0, abilityMod(11))
        assertEquals(1, abilityMod(12))
        assertEquals(2, abilityMod(14))
        assertEquals(4, abilityMod(18))
    }

    @Test
    fun `у нетренированного бонус не зависит от уровня`() {
        assertEquals(0, Rank.UNTRAINED.bonus(1))
        assertEquals(0, Rank.UNTRAINED.bonus(20))
        assertEquals(10, Rank.TRAINED.bonus(8))
        assertEquals(12, Rank.EXPERT.bonus(8))
        assertEquals(14, Rank.MASTER.bonus(8))
        assertEquals(16, Rank.LEGENDARY.bonus(8))
    }

    // ---------- числа из листа ----------

    @Test
    fun `защита и скорость как в листе`() {
        assertEquals(24, SILVRIN.armorClass())
        assertEquals(30, SILVRIN.speed())
        assertEquals(16, SILVRIN.perceptionMod())
    }

    @Test
    fun `испытания как в листе`() {
        assertEquals(13, SILVRIN.saveMod(Save.FORTITUDE))
        assertEquals(11, SILVRIN.saveMod(Save.REFLEX))
        assertEquals(16, SILVRIN.saveMod(Save.WILL))
    }

    @Test
    fun `сложности как в листе`() {
        assertEquals(24, SILVRIN.classDifficulty())
        assertEquals(16, SILVRIN.spellAttackMod())
        assertEquals(26, SILVRIN.spellDifficulty())
    }

    @Test
    fun `все восемнадцать навыков как в листе`() {
        val expected = mapOf(
            Skill.ACROBATICS to 11,
            Skill.ARCANA to 1,
            Skill.ATHLETICS to 14,
            Skill.CRAFTING to 1,
            Skill.DECEPTION to 1,
            Skill.DIPLOMACY to 13,
            Skill.INTIMIDATION to 1,
            Skill.LORE_1 to 11,
            Skill.LORE_2 to 1,
            Skill.MEDICINE to 16,
            Skill.NATURE to 4,
            Skill.OCCULTISM to 11,
            Skill.PERFORMANCE to 1,
            Skill.RELIGION to 14,
            Skill.SOCIETY to 1,
            Skill.STEALTH to 11,
            Skill.SURVIVAL to 14,
            Skill.THIEVERY to 1,
        )
        for ((skill, mod) in expected) {
            assertEquals("навык ${skill.title}", mod, SILVRIN.skillMod(skill))
        }
        assertEquals("проверены все навыки", Skill.entries.size, expected.size)
    }

    @Test
    fun `оружие как в листе`() {
        assertEquals(12, SILVRIN.attackMod(SILVRIN_CLAWS))
        assertEquals(13, SILVRIN.attackMod(SILVRIN_MAUL))
        assertEquals("1d6 + 2", SILVRIN.damageFormula(SILVRIN_CLAWS))
        assertEquals("2d12 + 2", SILVRIN.damageFormula(SILVRIN_MAUL))
    }

    // ---------- ради чего всё затевалось: поменял характеристику, изменилось везде ----------

    @Test
    fun `рост ловкости двигает КБ, Реакцию и ловкие навыки`() {
        val quicker = SILVRIN.copy(scores = SILVRIN.scores + (Ability.DEX to 14))   // +2
        assertEquals(25, quicker.armorClass())          // ловкость дошла ровно до предела брони
        assertEquals(12, quicker.saveMod(Save.REFLEX))
        assertEquals(12, quicker.skillMod(Skill.ACROBATICS))
        assertEquals(12, quicker.skillMod(Skill.STEALTH))
        // Мудрые навыки не шелохнулись: ловкость к ним отношения не имеет.
        assertEquals(16, quicker.skillMod(Skill.MEDICINE))
    }

    @Test
    fun `выше предела брони ловкость в КБ не идёт`() {
        val nimble = SILVRIN.copy(scores = SILVRIN.scores + (Ability.DEX to 18))    // +4
        // В КБ засчитаются только +2: предел деревянного нагрудника.
        assertEquals(25, nimble.armorClass())
        // А вот в Реакцию и Акробатику идёт вся ловкость, там потолка нет.
        assertEquals(14, nimble.saveMod(Save.REFLEX))
        assertEquals(14, nimble.skillMod(Skill.ACROBATICS))
    }

    @Test
    fun `нехватка силы включает штрафы брони`() {
        val weaker = SILVRIN.copy(scores = SILVRIN.scores + (Ability.STR to 12))    // требование 14
        assertFalse(weaker.meetsArmorStrength())
        assertEquals(-2, weaker.armorCheckPenalty())
        assertEquals(25, weaker.speed())                          // штраф брони перестал гаситься
        assertEquals(11, weaker.skillMod(Skill.ATHLETICS))        // 1 сила + 12 владение − 2 штраф
        assertEquals(9, weaker.skillMod(Skill.ACROBATICS))        // 1 + 10 − 2
        assertEquals(16, weaker.skillMod(Skill.MEDICINE))         // мудрый навык штраф не задевает
    }

    @Test
    fun `у Сильврина силы ровно хватает на его броню`() {
        assertTrue(SILVRIN.meetsArmorStrength())
        assertEquals(0, SILVRIN.armorCheckPenalty())
    }

    @Test
    fun `кольчуга была бы Сильврину не по силам`() {
        // Ради этого он и носит деревянный нагрудник: требование 14 достаёт, 16 — нет.
        val inChainMail = SILVRIN.copy(armor = CHAIN_MAIL.armor)
        assertFalse(inChainMail.meetsArmorStrength())
        assertEquals(25, inChainMail.armorClass())              // на единицу больше
        assertEquals(25, inChainMail.speed())                   // но медленнее
        assertEquals(12, inChainMail.skillMod(Skill.ATHLETICS)) // и со штрафом
    }

    @Test
    fun `формула броска пишется знаком`() {
        assertEquals("1d20 + 13", rollFormula(13))
        assertEquals("1d20", rollFormula(0))
        assertEquals("1d20 − 2", rollFormula(-2))
    }
}
