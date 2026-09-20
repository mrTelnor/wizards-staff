package ru.telnor.wizardsstaff.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.telnor.wizardsstaff.rules.Ability
import ru.telnor.wizardsstaff.rules.ArmorCategory
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.rules.Save
import ru.telnor.wizardsstaff.rules.Sheet
import ru.telnor.wizardsstaff.rules.Skill
import ru.telnor.wizardsstaff.rules.WeaponTraining
import ru.telnor.wizardsstaff.rules.armorByName

/**
 * Персонаж в базе.
 *
 * Главное правило: здесь лежат ВХОДНЫЕ данные, а не итоги. КБ, классовой СЛ, восприятия,
 * скорости и модификаторов навыков в таблице нет — их считает `Pf2.kt` по этим полям.
 * Вторая копия итогов разошлась бы с первой же правкой ловкости, и разошлась бы молча.
 *
 * Исключение — то, что движок вывести не может и что просто записано в бланке: максимум
 * и текущие ПЗ (они зависят от класса, народа и черт, а черты мы не моделируем), героизм,
 * опыт, щит, сопротивления, языки, деньги и нагрузка.
 */
@Entity(tableName = "characters")
data class CharacterRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Паспорт: строка под именем в шапке листа.
    val name: String,
    val ancestry: String,
    val background: String,
    val className: String,
    val deity: String,
    val alignment: String,
    val size: String,

    val level: Int,
    val xp: Int,
    val heroPoints: Int,

    // Входные данные движка.
    val scores: Map<Ability, Int>,
    val keyAbility: Ability,
    /** Имя надетой брони в справочнике `ArmorCatalog`. Null — брони нет вовсе. */
    val armorName: String?,
    val armorRanks: Map<ArmorCategory, Rank>,
    val perception: Rank,
    val perceptionItem: Int,
    val saves: Map<Save, Rank>,
    val saveItems: Map<Save, Int>,
    val skills: Map<Skill, Rank>,
    val skillItems: Map<Skill, Int>,
    val classDc: Rank,
    val spellAttack: Rank,
    val spellDc: Rank,
    val baseSpeed: Int,
    val weaponTraining: List<WeaponTraining>,

    /** Уточнения к двум «Знаниям»: в бланке это поле рядом с навыком, вроде «Ламашту». */
    val lore1: String?,
    val lore2: String?,

    // Невыводимое: просто числа из бланка.
    val maxHp: Int,
    val currentHp: Int,
    val resistances: String,
    val languages: String,
    /** Щит: КБ, который он даёт, и его твёрдость. Ноль — щита нет. */
    val shieldAc: Int,
    val shieldHardness: Int,

    val platinum: Int,
    val gold: Int,
    val silver: Int,
    val copper: Int,
    /** Набранный объём и две границы: с какого числа нагружен и какое предельное. */
    val bulk: Int,
    val encumberedAt: Int,
    val maxBulk: Int,

    // Магия. Числа атаки заклинанием и СЛ считает движок, здесь только что за магия.
    val tradition: String,
    val preparedCaster: Boolean,
    val cantripRank: Int,
) {
    /** Название навыка с уточнением: «Знание (Ламашту)». У обычных навыков уточнения нет. */
    fun skillTitle(skill: Skill): String {
        val detail = when (skill) {
            Skill.LORE_1 -> lore1
            Skill.LORE_2 -> lore2
            else -> null
        }
        return if (detail.isNullOrBlank()) skill.title else "${skill.title} ($detail)"
    }
}

/**
 * Собирает из записи входные данные для движка правил.
 *
 * Броня берётся из справочника по имени, а не копией шести чисел: опечатка в пределе
 * ловкости или требовании силы молча испортила бы КБ, скорость и половину навыков.
 * Если имя в справочнике не найдено, считаем, что брони нет — так ведёт себя и пустое
 * поле. Случиться это может только если из справочника уберут предмет, на который
 * кто-то уже сослался.
 */
fun CharacterRecord.toSheet(): Sheet = Sheet(
    level = level,
    scores = scores,
    keyAbility = keyAbility,
    armor = armorName?.let { armorByName(it)?.armor },
    armorRanks = armorRanks,
    perception = perception,
    saves = saves,
    skills = skills,
    classDc = classDc,
    spellAttack = spellAttack,
    spellDc = spellDc,
    baseSpeed = baseSpeed,
    weaponTraining = weaponTraining,
    perceptionItem = perceptionItem,
    saveItems = saveItems,
    skillItems = skillItems,
)
