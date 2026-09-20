package ru.telnor.wizardsstaff.db

import ru.telnor.wizardsstaff.rules.ArmorCategory
import ru.telnor.wizardsstaff.rules.SILVRIN
import ru.telnor.wizardsstaff.rules.SILVRIN_CLAWS
import ru.telnor.wizardsstaff.rules.SILVRIN_MAUL
import ru.telnor.wizardsstaff.rules.Weapon

/*
 * Сильврин Хвостозвон для первого запуска.
 *
 * Откуда числа. Всё, из чего считается лист, взято из `SILVRIN` — того самого эталона,
 * на котором стоят тринадцать проверок движка. Ни одно из них здесь не повторяется
 * значением: перепиши характеристику в двух местах — и однажды они разойдутся молча,
 * а тест этого не заметит, потому что проверяет он эталон, а показывается база.
 *
 * Остальное — паспорт, ПЗ, деньги, черты, снаряжение — из заполненного бланка
 * `docs/Сильврин Хвостозвон-1(2).pdf`: у него заполняемые поля, и значения читаются
 * оттуда как есть. Макеты в `docs/design` для этого не годятся: они рисовались по листу
 * шестого уровня, там ПЗ 56/56, КБ 23 и кольчуга вместо деревянного доспеха.
 */

/** Персонаж со всеми частями до записи в базу: номера ему ещё не выдала база. */
data class CharacterSeed(
    val character: CharacterRecord,
    val weapons: List<CharacterWeaponRecord>,
    val feats: List<CharacterFeatRecord>,
    val items: List<CharacterItemRecord>,
    val spells: List<CharacterSpellRecord>,
)

/** Лист Сильврина целиком. */
fun silvrinSeed(): CharacterSeed {
    val character = CharacterRecord(
        name = "Сильврин Хвостозвон",
        ancestry = "Когтистый котолюд",
        background = "Благословлённый",
        className = "Жрец-капеллан",
        deity = "Дезна",
        alignment = "Хаотично-нейтральный",
        // В бланке размер выбирается из списка и записан по-английски («Medium»).
        size = "Средний",

        level = SILVRIN.level,
        xp = 900,
        heroPoints = 1,

        scores = SILVRIN.scores,
        keyAbility = SILVRIN.keyAbility,
        // Имя, а не копия шести чисел: по нему броня находится в справочнике.
        armorName = SILVRIN.armor?.name,
        armorRanks = SILVRIN.armorRanks,
        perception = SILVRIN.perception,
        perceptionItem = SILVRIN.perceptionItem,
        saves = SILVRIN.saves,
        saveItems = SILVRIN.saveItems,
        skills = SILVRIN.skills,
        skillItems = SILVRIN.skillItems,
        classDc = SILVRIN.classDc,
        spellAttack = SILVRIN.spellAttack,
        spellDc = SILVRIN.spellDc,
        baseSpeed = SILVRIN.baseSpeed,
        weaponTraining = SILVRIN.weaponTraining,

        lore1 = "Ламашту",
        lore2 = null,

        maxHp = 82,
        currentHp = 36,
        resistances = "К ядам",
        languages = "Всеобщий, аммуррун",
        shieldAc = 1,
        shieldHardness = 10,

        platinum = 0,
        gold = 3,
        silver = 668,
        copper = 0,
        bulk = 2,
        encumberedAt = 7,
        maxBulk = 12,

        tradition = "Сакральная",
        preparedCaster = true,
        cantripRank = 4,
    )

    val weapons = listOf(SILVRIN_CLAWS, SILVRIN_MAUL).mapIndexed { at, weapon ->
        weapon.toRecord(position = at)
    }

    // Порядок внутри колонки — как в бланке: сверху то, что получено раньше.
    val feats = buildFeats()

    val items = listOf(
        CharacterItemRecord(
            characterId = 0,
            name = "Деревянный доспех",
            // Тип берётся у надетой брони, чтобы не разойтись с тем, что считает КБ.
            details = (SILVRIN.armor?.category ?: ArmorCategory.UNARMORED).title,
            bulk = "2",
            position = 0,
        ),
        CharacterItemRecord(
            characterId = 0,
            name = "Зелье лечения 3d8+10, 2 шт.",
            details = "",
            // В бланке вес у зелий не проставлен, и придумывать его мы не беремся.
            bulk = "—",
            position = 1,
        ),
    )

    val spells = listOf(
        CharacterSpellRecord(
            characterId = 0,
            name = "Наставление",
            rank = 0,
            // Число действий — единственное, что взято из макета: в бланке такого поля нет.
            actions = "1 действие",
            frequency = "Неограниченно раз",
            // Броска не требует, поэтому на посох не уходит и в ленте не появляется.
            hasRoll = false,
            position = 0,
        ),
    )

    return CharacterSeed(character, weapons, feats, items, spells)
}

/** Оружие из правил в строку таблицы. */
private fun Weapon.toRecord(position: Int) = CharacterWeaponRecord(
    characterId = 0,
    name = name,
    rank = rank,
    damageDice = damageDice,
    potency = potency,
    finesse = finesse,
    traits = traits,
    position = position,
)

/**
 * Черты и особенности из бланка. Тег — то, что написано на плашке мелким: у черт народа
 * уровня в бланке нет вовсе, у классовых особенностей он двойной, поэтому тег строкой.
 */
private fun buildFeats(): List<CharacterFeatRecord> {
    val rows = listOf(
        Triple(FeatGroup.ANCESTRY, "Сумеречное зрение", "народ"),
        Triple(FeatGroup.ANCESTRY, "Приземление на ноги", "народ"),
        Triple(FeatGroup.ANCESTRY, "Когтистый котолюд", "родословная 1"),
        Triple(FeatGroup.ANCESTRY, "Знания котолюдов", "черта 1"),
        Triple(FeatGroup.ANCESTRY, "Легкоступ", "черта 5"),

        Triple(FeatGroup.CLASS, "Смертоносная простота", "особенность 1–2"),
        Triple(FeatGroup.CLASS, "Блок щитом", "классовая 1"),
        Triple(FeatGroup.CLASS, "Совместное лечение", "классовая 2"),
        Triple(FeatGroup.CLASS, "Улучшенное совместное лечение", "классовая 4"),
        Triple(FeatGroup.CLASS, "Лечащие руки", "классовая 6"),
        Triple(FeatGroup.CLASS, "Лучистая подпитка", "классовая 8"),

        Triple(FeatGroup.SKILL, "Медицина в бою", "навык 2"),
        Triple(FeatGroup.SKILL, "Дежурный медик", "навык 4"),
        Triple(FeatGroup.SKILL, "Непрерывное восстановление", "навык 6"),

        Triple(FeatGroup.GENERAL, "Невероятная инициатива", "общая 3"),
        Triple(FeatGroup.GENERAL, "Прозорливое планирование", "общая 7"),
    )
    return rows.mapIndexed { at, (group, name, tag) ->
        CharacterFeatRecord(characterId = 0, featGroup = group, name = name, tag = tag, position = at)
    }
}
