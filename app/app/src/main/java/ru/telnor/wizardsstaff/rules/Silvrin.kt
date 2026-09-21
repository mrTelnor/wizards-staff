package ru.telnor.wizardsstaff.rules

/*
 * Сильврин Хвостозвон — персонаж автора, по нему проверяется движок вывода.
 *
 * Здесь лежат ИСХОДНЫЕ данные из бланка (docs/Сильврин Хвостозвон-1(2).pdf):
 * характеристики, степени владения, надетая броня, оружие. Ни одного итогового
 * числа: КБ, испытания, навыки и СЛ считает Pf2.kt. Сверка с листом — в Pf2Test.
 */

/**
 * Когти котолюда. Изученное владение — по строке «безоружное» из блока умений.
 * Фехтовальное, но сила у Сильврина выше ловкости, так что бьёт силой.
 * Кость 1d6 взята из листа как есть: её задаёт черта, а черты мы не выводим.
 */
val SILVRIN_CLAWS = Weapon(
    name = "Когти",
    rank = Rank.TRAINED,
    damageDice = "1d6",
    finesse = true,
    traits = "безоружно, быстрое, фехтовальное",
    // В бланке у первой строки оружия стоит галочка «режущее».
    damageType = DamageType.SLASHING,
)

/**
 * Двуручный молот +1. Оружие особое, и владение им у Сильврина
 * изученное — отсюда бонус 10 на восьмом уровне. Две кости урона дала руна удара.
 */
val SILVRIN_MAUL = Weapon(
    name = "Двуручный молот +1",
    rank = Rank.TRAINED,
    damageDice = "2d12",
    potency = 1,
    traits = "толкающее",
    // Галочек у второй строки бланка не стоит, а молот по книге дробящий.
    damageType = DamageType.BLUDGEONING,
)

/** Лист Сильврина: только входные данные. */
val SILVRIN: Sheet = Sheet(
    level = 8,
    scores = mapOf(
        Ability.STR to 14,
        Ability.DEX to 12,
        Ability.CON to 12,
        Ability.INT to 12,
        Ability.WIS to 18,
        Ability.CHA to 12,
    ),
    keyAbility = Ability.WIS,
    armor = WOODEN_BREASTPLATE_ITEM.armor,   // из справочника, а не копией чисел
    armorRanks = mapOf(
        ArmorCategory.UNARMORED to Rank.TRAINED,
        ArmorCategory.LIGHT to Rank.TRAINED,
        ArmorCategory.MEDIUM to Rank.TRAINED,
        ArmorCategory.HEAVY to Rank.UNTRAINED,
    ),
    perception = Rank.EXPERT,
    saves = mapOf(
        Save.FORTITUDE to Rank.EXPERT,
        Save.REFLEX to Rank.TRAINED,
        Save.WILL to Rank.EXPERT,
    ),
    skills = mapOf(
        Skill.ACROBATICS to Rank.TRAINED,
        Skill.ATHLETICS to Rank.EXPERT,
        Skill.DIPLOMACY to Rank.EXPERT,
        Skill.LORE_1 to Rank.TRAINED,
        Skill.MEDICINE to Rank.EXPERT,
        Skill.OCCULTISM to Rank.TRAINED,
        Skill.RELIGION to Rank.TRAINED,
        Skill.STEALTH to Rank.TRAINED,
        Skill.SURVIVAL to Rank.TRAINED,
        // Мистицизм, Ремесло, Обман, Запугивание, Знание 2, Природа, Исполнение,
        // Общество и Воровство остаются нетренированными и в карту не попадают.
    ),
    classDc = Rank.TRAINED,
    spellAttack = Rank.EXPERT,
    spellDc = Rank.EXPERT,
    baseSpeed = 30,
    // Блок «умения в оружии» из бланка. Отсюда берутся степени обоих оружий Сильврина:
    // двуручный молот — особое оружие, а когти считаются по безоружному владению.
    weaponTraining = listOf(
        WeaponTraining("Особое", Rank.TRAINED),
        WeaponTraining("Нож-звезда", Rank.EXPERT),
        WeaponTraining("Безоружное", Rank.TRAINED),
    ),
)
