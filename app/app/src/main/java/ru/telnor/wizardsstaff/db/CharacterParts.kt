package ru.telnor.wizardsstaff.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.telnor.wizardsstaff.rules.DamageType
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.rules.Weapon

/*
 * Списочные части листа: оружие, черты, снаряжение и заклинания. У каждой свой ряд строк
 * на персонажа, поэтому они и вынесены в отдельные таблицы, в отличие от карт навыков
 * и владений — те всегда читаются вместе с персонажем и лежат в его строке.
 *
 * Отдельной таблицы действий нет намеренно: атаки, испытания, навыки и восприятие движок
 * выводит из листа сам. Своими строками остаются только заклинания — их не вывести ниоткуда.
 *
 * Удаление персонажа уносит его части с собой (CASCADE): осиротевшая черта не нужна никому,
 * а найти её потом можно было бы только запросом по всей таблице.
 */

/** Оружие в руках персонажа. Отдельная строка на каждое, порядок как в бланке. */
@Entity(
    tableName = "character_weapons",
    foreignKeys = [
        ForeignKey(
            entity = CharacterRecord::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("characterId")],
)
data class CharacterWeaponRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    val name: String,
    /**
     * Короткое имя для строк листа: «молотом», «когтями». Подставляется в «Атака …»
     * и «Урон …», поэтому и стоит в творительном падеже. Вывести его из полного имени
     * нельзя: «Двуручный молот кошачьей ярости +1» сокращается только головой.
     */
    val shortName: String,
    val rank: Rank,
    /** Кость урона одной штуки: «2d12». Руна удара уже учтена в числе костей. */
    val damageDice: String,
    val potency: Int,
    val finesse: Boolean,
    val traits: String,
    /**
     * Те же свойства короткими словами: «безоруж., быстр., фехт.». Хранятся рядом
     * с полными по той же причине, что и короткое имя оружия: сократить «фехтовальное»
     * до «фехт.» может только человек, вывести это неоткуда.
     *
     * Пусто — показываются полные: так ведёт себя оружие, добавленное до версии 8.
     */
    @ColumnInfo(defaultValue = "")
    val shortTraits: String = "",
    /**
     * Чем бьёт: дробящее, колющее или режущее. Умолчание нужно базе — столбец
     * добавлен в версии 7 поверх уже записанного оружия.
     */
    @ColumnInfo(defaultValue = "BLUDGEONING")
    val damageType: DamageType = DamageType.BLUDGEONING,
    val position: Int,
) {
    /** Оружие в том виде, в каком его понимает движок правил. */
    fun toWeapon(): Weapon = Weapon(
        name = name,
        rank = rank,
        damageDice = damageDice,
        potency = potency,
        finesse = finesse,
        traits = traits,
        damageType = damageType,
    )
}

/** Колонка, в которой черта показывается на вкладке «Черты и снаряжение». */
enum class FeatGroup(val title: String) {
    ANCESTRY("Народ и родословная"),
    CLASS("Классовые черты"),
    SKILL("Черты навыков"),
    GENERAL("Общие черты"),
}

/**
 * Черта или особенность. Тег — это то, что написано на плашке мелким шрифтом:
 * «народ», «родословная 1», «черта 5», «классовая 8». Хранится строкой, а не номером
 * уровня: в бланке рядом с чертами народа уровня нет вовсе, а у классовых особенностей
 * он двойной («1–2»).
 */
@Entity(
    tableName = "character_feats",
    foreignKeys = [
        ForeignKey(
            entity = CharacterRecord::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("characterId")],
)
data class CharacterFeatRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    /**
     * Колонка вкладки. Поле названо не `group`: это слово занято в SQL, и запрос с ним
     * пришлось бы брать в кавычки — сейчас таких запросов нет, но мина осталась бы лежать.
     */
    val featGroup: FeatGroup,
    val name: String,
    val tag: String,
    val position: Int,
)

/**
 * Предмет снаряжения. Вес хранится строкой: в правилах он бывает не только числом,
 * но и буквой «Л» — лёгкий, и прочерком, когда веса нет.
 */
@Entity(
    tableName = "character_items",
    foreignKeys = [
        ForeignKey(
            entity = CharacterRecord::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("characterId")],
)
data class CharacterItemRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    val name: String,
    /** Вторая строка карточки: тип и свойства. Пусто, если в бланке ничего не сказано. */
    val details: String,
    val bulk: String,
    val position: Int,
)

/**
 * Заклинание. Круг ноль — заговор: в правилах у него есть круг, но в листе он показывается
 * отдельным блоком «Заговоры».
 *
 * `hasRoll` отделяет то, что уходит на посох, от того, что лежит для памяти: «Наставление»
 * броска не требует и в ленту попадать не должно.
 */
@Entity(
    tableName = "character_spells",
    foreignKeys = [
        ForeignKey(
            entity = CharacterRecord::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("characterId")],
)
data class CharacterSpellRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    val name: String,
    /** Круг заклинания, ноль — заговор. */
    val rank: Int,
    /** Сколько действий занимает: «1 действие». Пусто, если в листе не записано. */
    val actions: String,
    /** Как часто можно: «Неограниченно раз». */
    val frequency: String,
    val hasRoll: Boolean,
    val position: Int,
) {
    /** Заговор показывается в своём блоке и круга при себе не пишет. */
    val isCantrip: Boolean get() = rank == 0
}
