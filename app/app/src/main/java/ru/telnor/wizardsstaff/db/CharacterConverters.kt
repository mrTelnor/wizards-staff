package ru.telnor.wizardsstaff.db

import androidx.room.TypeConverter
import ru.telnor.wizardsstaff.rules.Ability
import ru.telnor.wizardsstaff.rules.ArmorCategory
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.rules.Save
import ru.telnor.wizardsstaff.rules.Skill
import ru.telnor.wizardsstaff.rules.WeaponTraining

/**
 * Карты листа в одну ячейку.
 *
 * Навыков восемнадцать, владений бронёй четыре, характеристик шесть — и все они всегда
 * читаются вместе с персонажем, целиком. Отдельная таблица на каждую карту дала бы
 * четыре соединения при каждом открытии листа и ни одного нового запроса, который
 * стало бы можно написать. Тот же довод, по которому значения костей в `rolls` лежат
 * строкой «2,4,5».
 *
 * Формат — «КЛЮЧ:ЗНАЧЕНИЕ» через запятую: `STR:14,DEX:12`. Имена перечислений, а не
 * их русские названия: переименование подписи в интерфейсе не должно портить базу.
 * Пустая карта — пустая строка.
 *
 * Неизвестный ключ при чтении пропускается. Это тот случай, когда старая база встретилась
 * с новой версией приложения, где навык переименован: потерять один навык лучше, чем
 * уронить приложение на открытии листа.
 */
class CharacterConverters {

    // --- общая механика ---

    /** Карта «перечисление → что-то» в строку. */
    private fun <K : Enum<K>, V> pack(map: Map<K, V>): String =
        map.entries.joinToString(",") { "${it.key.name}:${it.value}" }

    /** Строка обратно в карту. Ключи и значения, которые больше не существуют, пропускаются. */
    private fun <K : Enum<K>, V> unpack(
        text: String,
        key: (String) -> K?,
        value: (String) -> V?,
    ): Map<K, V> {
        if (text.isBlank()) return emptyMap()
        val result = LinkedHashMap<K, V>()
        for (part in text.split(",")) {
            val at = part.indexOf(':')
            if (at <= 0) continue
            val k = key(part.substring(0, at)) ?: continue
            val v = value(part.substring(at + 1)) ?: continue
            result[k] = v
        }
        return result
    }

    private fun rank(name: String): Rank? = Rank.entries.firstOrNull { it.name == name }

    private fun number(text: String): Int? = text.toIntOrNull()

    // --- характеристики ---

    @TypeConverter
    fun fromScores(map: Map<Ability, Int>): String = pack(map)

    @TypeConverter
    fun toScores(text: String): Map<Ability, Int> =
        unpack(text, { n -> Ability.entries.firstOrNull { it.name == n } }, ::number)

    @TypeConverter
    fun fromAbility(ability: Ability): String = ability.name

    @TypeConverter
    fun toAbility(name: String): Ability =
        Ability.entries.firstOrNull { it.name == name } ?: Ability.STR

    // --- степени владения ---

    @TypeConverter
    fun fromRank(value: Rank): String = value.name

    @TypeConverter
    fun toRank(name: String): Rank = rank(name) ?: Rank.UNTRAINED

    @TypeConverter
    fun fromArmorRanks(map: Map<ArmorCategory, Rank>): String = pack(map)

    @TypeConverter
    fun toArmorRanks(text: String): Map<ArmorCategory, Rank> =
        unpack(text, { n -> ArmorCategory.entries.firstOrNull { it.name == n } }, ::rank)

    @TypeConverter
    fun fromSaveRanks(map: Map<Save, Rank>): String = pack(map)

    @TypeConverter
    fun toSaveRanks(text: String): Map<Save, Rank> =
        unpack(text, { n -> Save.entries.firstOrNull { it.name == n } }, ::rank)

    @TypeConverter
    fun fromSkillRanks(map: Map<Skill, Rank>): String = pack(map)

    @TypeConverter
    fun toSkillRanks(text: String): Map<Skill, Rank> =
        unpack(text, { n -> Skill.entries.firstOrNull { it.name == n } }, ::rank)

    // --- бонусы предметов ---

    @TypeConverter
    fun fromSaveItems(map: Map<Save, Int>): String = pack(map)

    @TypeConverter
    fun toSaveItems(text: String): Map<Save, Int> =
        unpack(text, { n -> Save.entries.firstOrNull { it.name == n } }, ::number)

    @TypeConverter
    fun fromSkillItems(map: Map<Skill, Int>): String = pack(map)

    @TypeConverter
    fun toSkillItems(text: String): Map<Skill, Int> =
        unpack(text, { n -> Skill.entries.firstOrNull { it.name == n } }, ::number)

    // --- умения в оружии ---

    /**
     * Блок «умения в оружии» из бланка: пары вроде «Нож-звезда: экспертный». Название
     * там произвольное, поэтому разделитель внутри пары — двоеточие, а между парами
     * точка с запятой: запятая в названии встречается («без брони, лёгкая, средняя»),
     * а точка с запятой — нет.
     */
    @TypeConverter
    fun fromWeaponTraining(list: List<WeaponTraining>): String =
        list.joinToString(";") { "${it.what}:${it.rank.name}" }

    @TypeConverter
    fun toWeaponTraining(text: String): List<WeaponTraining> {
        if (text.isBlank()) return emptyList()
        return text.split(";").mapNotNull { part ->
            val at = part.lastIndexOf(':')
            if (at <= 0) return@mapNotNull null
            val value = rank(part.substring(at + 1)) ?: return@mapNotNull null
            WeaponTraining(part.substring(0, at), value)
        }
    }

    // --- перечисления списочных таблиц ---

    @TypeConverter
    fun fromFeatGroup(group: FeatGroup): String = group.name

    @TypeConverter
    fun toFeatGroup(name: String): FeatGroup =
        FeatGroup.entries.firstOrNull { it.name == name } ?: FeatGroup.GENERAL
}
