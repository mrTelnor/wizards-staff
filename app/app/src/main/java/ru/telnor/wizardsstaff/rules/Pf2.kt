package ru.telnor.wizardsstaff.rules

/*
 * Правила Pathfinder 2e, редакция Legacy (не Remaster): по ней собран персонаж автора.
 * Для чисел, которые считает лист, разницы между редакциями нет — она в содержании
 * (мировоззрение, названия традиций, состав черт), а не в формулах.
 *
 * Здесь только вывод чисел. Черты, меняющие сами правила, состояния и временные бонусы
 * не реализуются: это уже боевой трекер, а не лист.
 *
 * Всё в этом файле — чистые функции без единой зависимости от Android. Так их можно
 * проверить обычным тестом, что и сделано: Pf2Test сверяет вывод с листом Сильврина.
 */

/** Шесть характеристик. Трёхбуквенные имена — те же, что в листе и в макетах. */
enum class Ability(val short: String, val title: String) {
    STR("СИЛ", "Сила"),
    DEX("ЛВК", "Ловкость"),
    CON("ВЫН", "Выносливость"),
    INT("ИНТ", "Интеллект"),
    WIS("МДР", "Мудрость"),
    CHA("ХАР", "Харизма"),
}

/**
 * Степень владения. Буква — для бейджа в листе, см. SCREENS.md.
 *
 * Нетренированный — единственный, у кого бонус не зависит от уровня: он равен нулю.
 * Остальные прибавляют уровень персонажа к своей добавке. Это и есть та особенность
 * PF2e, из-за которой нетренированные навыки не растут вместе с героем.
 */
enum class Rank(val badge: String, val title: String, private val step: Int) {
    UNTRAINED("—", "нетренирован", 0),
    TRAINED("И", "изученный", 2),
    EXPERT("Э", "экспертный", 4),
    MASTER("М", "мастерский", 6),
    LEGENDARY("Л", "легендарный", 8);

    /** Бонус владения на этом уровне персонажа. */
    fun bonus(level: Int): Int = if (this == UNTRAINED) 0 else level + step
}

/** Модификатор характеристики: (значение − 10) пополам, вниз. У 7 это −2, а не −1. */
fun abilityMod(score: Int): Int = Math.floorDiv(score - 10, 2)

/** Больше трёх пунктов героизма правила держать не дают. */
const val HERO_POINTS_MAX = 3

/**
 * Выше трёх ранений не бывает: упав с ранением 3, персонаж получает «при смерти» 4,
 * а это уже смерть. Так что четвёртое ранение просто некому носить.
 */
const val WOUNDED_MAX = 3

/**
 * Надетая броня. Предел ловкости — потолок, выше которого ловкость в КБ не идёт;
 * ради него всё и затевалось, иначе при правке ловкости КБ рос бы без конца.
 *
 * Требование силы — значение, а не модификатор: так в редакции Legacy. Если сила
 * персонажа его достигает, штраф проверок не применяется вовсе, а штраф скорости
 * уменьшается на 5 футов. У Сильврина ровно поэтому деревянный нагрудник, а не
 * кольчуга: требования 14 он достигает, а 16 — нет.
 */
data class Armor(
    val name: String,
    val category: ArmorCategory,
    val acBonus: Int,
    val dexCap: Int,
    val checkPenalty: Int,      // отрицательное число или ноль
    val speedPenalty: Int,      // отрицательное число или ноль, в футах
    val strength: Int,          // требование силы
)

/** Категория брони: от неё зависит, какое владение бронёй идёт в КБ. */
enum class ArmorCategory(val title: String) {
    UNARMORED("без брони"),
    LIGHT("лёгкая"),
    MEDIUM("средняя"),
    HEAVY("тяжёлая"),
}

/** Навык: своя характеристика у каждого. Знание встречается дважды и с уточнением. */
enum class Skill(val title: String, val ability: Ability) {
    ACROBATICS("Акробатика", Ability.DEX),
    ARCANA("Мистицизм", Ability.INT),
    ATHLETICS("Атлетика", Ability.STR),
    CRAFTING("Ремесло", Ability.INT),
    DECEPTION("Обман", Ability.CHA),
    DIPLOMACY("Дипломатия", Ability.CHA),
    INTIMIDATION("Запугивание", Ability.CHA),
    LORE_1("Знание", Ability.INT),
    LORE_2("Знание", Ability.INT),
    MEDICINE("Медицина", Ability.WIS),
    NATURE("Природа", Ability.WIS),
    OCCULTISM("Оккультизм", Ability.INT),
    PERFORMANCE("Исполнение", Ability.CHA),
    RELIGION("Религия", Ability.WIS),
    SOCIETY("Общество", Ability.INT),
    STEALTH("Скрытность", Ability.DEX),
    SURVIVAL("Выживание", Ability.WIS),
    THIEVERY("Воровство", Ability.DEX);

    /** Штраф брони бьёт по проверкам силы и ловкости, остальных не касается. */
    val takesArmorPenalty: Boolean
        get() = ability == Ability.STR || ability == Ability.DEX
}

/**
 * Строка блока «умения в оружии» из бланка. Там вперемешку категории («простое»)
 * и отдельные оружия («нож-звезда»), и бланк устроен именно так: степень владения,
 * а рядом поле «какое именно». Поэтому и здесь просто пара «что» и «насколько»,
 * а не попытка свести одно к другому.
 *
 * На вывод чисел это не влияет: у каждого оружия своя степень записана в нём самом.
 * Блок нужен, чтобы показать его на вкладке «Черты и снаряжение».
 */
data class WeaponTraining(val what: String, val rank: Rank)

/** Испытание. */
enum class Save(val title: String, val ability: Ability) {
    FORTITUDE("Стойкость", Ability.CON),
    REFLEX("Реакция", Ability.DEX),
    WILL("Воля", Ability.WIS),
}

/**
 * Всё, из чего считаются числа листа. Это входные данные, а не итоги: поменял здесь
 * ловкость — и КБ, Реакция, Акробатика, Скрытность и Воровство пересчитаются сами.
 */
data class Sheet(
    val level: Int,
    val scores: Map<Ability, Int>,
    val keyAbility: Ability,
    val armor: Armor?,
    val armorRanks: Map<ArmorCategory, Rank>,
    val perception: Rank,
    val saves: Map<Save, Rank>,
    val skills: Map<Skill, Rank>,
    val classDc: Rank,
    val spellAttack: Rank,
    val spellDc: Rank,
    val baseSpeed: Int,
    /** Чем персонаж владеет из оружия, для показа. Пусто — блок в листе не заполнен. */
    val weaponTraining: List<WeaponTraining> = emptyList(),
    /** Бонусы предметов: у Сильврина их нет, но поля есть — иначе первый же амулет сломает вывод. */
    val perceptionItem: Int = 0,
    val saveItems: Map<Save, Int> = emptyMap(),
    val skillItems: Map<Skill, Int> = emptyMap(),
)

/** Модификатор характеристики по листу. */
fun Sheet.mod(ability: Ability): Int = abilityMod(scores[ability] ?: 10)

/** Достаёт ли персонаж до требования силы своей брони. От этого зависят штрафы. */
fun Sheet.meetsArmorStrength(): Boolean {
    val worn = armor ?: return true
    return (scores[Ability.STR] ?: 10) >= worn.strength
}

/** Штраф проверок от брони: ноль, если силы хватает или брони нет. */
fun Sheet.armorCheckPenalty(): Int =
    if (armor == null || meetsArmorStrength()) 0 else armor.checkPenalty

/** Скорость с учётом брони. Хватает силы — штраф уменьшается на 5 футов. */
fun Sheet.speed(): Int {
    val worn = armor ?: return baseSpeed
    val penalty = if (meetsArmorStrength()) minOf(worn.speedPenalty + 5, 0) else worn.speedPenalty
    return maxOf(baseSpeed + penalty, 5)
}

/**
 * Класс брони. Ловкость идёт в КБ не вся, а до предела надетой брони — без этого
 * потолка тяжёлый доспех давал бы ловкачу больше, чем позволяют правила.
 */
fun Sheet.armorClass(): Int {
    val category = armor?.category ?: ArmorCategory.UNARMORED
    val rank = armorRanks[category] ?: Rank.UNTRAINED
    val dex = if (armor == null) mod(Ability.DEX) else minOf(mod(Ability.DEX), armor.dexCap)
    return 10 + dex + rank.bonus(level) + (armor?.acBonus ?: 0)
}

/** Испытание: характеристика, владение, бонус предмета. */
fun Sheet.saveMod(save: Save): Int =
    mod(save.ability) + (saves[save] ?: Rank.UNTRAINED).bonus(level) + (saveItems[save] ?: 0)

/** Восприятие считается как испытание, только по мудрости. */
fun Sheet.perceptionMod(): Int =
    mod(Ability.WIS) + perception.bonus(level) + perceptionItem

/** Навык: характеристика, владение, предмет и штраф брони, если он есть. */
fun Sheet.skillMod(skill: Skill): Int {
    val penalty = if (skill.takesArmorPenalty) armorCheckPenalty() else 0
    return mod(skill.ability) +
        (skills[skill] ?: Rank.UNTRAINED).bonus(level) +
        (skillItems[skill] ?: 0) +
        penalty
}

/** Классовая СЛ: десятка, ключевая характеристика, владение. */
fun Sheet.classDifficulty(): Int = 10 + mod(keyAbility) + classDc.bonus(level)

/** Атака заклинанием. */
fun Sheet.spellAttackMod(): Int = mod(keyAbility) + spellAttack.bonus(level)

/** СЛ заклинаний — та же атака плюс десятка, как и всюду в этой системе. */
fun Sheet.spellDifficulty(): Int = 10 + mod(keyAbility) + spellDc.bonus(level)

/**
 * Оружие в руках. Кость урона и руны берутся из листа: черты вроде «Смертоносной
 * простоты» меняют кость по своим правилам, и выводить её мы не беремся.
 */
data class Weapon(
    val name: String,
    val rank: Rank,
    /** Кость урона одной штуки: «1d6». Руна удара умножает число костей, это уже учтено. */
    val damageDice: String,
    val potency: Int = 0,        // руна мощи: прибавка к броску атаки
    val finesse: Boolean = false, // фехтовальное: можно бить ловкостью вместо силы
    val traits: String = "",
)

/**
 * Бросок атаки этим оружием. Фехтовальным бьют той характеристикой, которая выше:
 * правила разрешают выбирать, а за столом никто не выбирает худшую.
 */
fun Sheet.attackMod(weapon: Weapon): Int {
    val ability = if (weapon.finesse) {
        maxOf(mod(Ability.STR), mod(Ability.DEX))
    } else {
        mod(Ability.STR)
    }
    return ability + weapon.rank.bonus(level) + weapon.potency
}

/** Прибавка к урону от силы. Формула урона целиком собирается вместе с костью оружия. */
fun Sheet.damageMod(weapon: Weapon): Int = mod(Ability.STR)

/** Формула урона для показа и для броска: «2d12 + 2». */
fun Sheet.damageFormula(weapon: Weapon): String {
    val bonus = damageMod(weapon)
    return if (bonus == 0) weapon.damageDice else "${weapon.damageDice} + $bonus"
}

/** Формула броска для показа: «1d20 + 13». Отрицательный модификатор пишется минусом. */
fun rollFormula(modifier: Int, die: String = "1d20"): String = when {
    modifier > 0 -> "$die + $modifier"
    modifier < 0 -> "$die − ${-modifier}"
    else -> die
}
