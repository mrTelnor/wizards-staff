package ru.telnor.wizardsstaff.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Персонаж со всеми своими частями, как их отдаёт Room.
 *
 * Порядок строк внутри списков Room не обещает: `@Relation` не умеет ORDER BY. Поэтому
 * наружу это уходит не как есть, а через `CharacterRepository`, который раскладывает
 * списки по полю `position`.
 */
data class FullCharacter(
    @Embedded val character: CharacterRecord,
    @Relation(parentColumn = "id", entityColumn = "characterId")
    val weapons: List<CharacterWeaponRecord>,
    @Relation(parentColumn = "id", entityColumn = "characterId")
    val feats: List<CharacterFeatRecord>,
    @Relation(parentColumn = "id", entityColumn = "characterId")
    val items: List<CharacterItemRecord>,
    @Relation(parentColumn = "id", entityColumn = "characterId")
    val spells: List<CharacterSpellRecord>,
)

/** Запросы к листам персонажей. */
@Dao
interface CharacterDao {

    /**
     * Все листы, по порядку добавления. `@Transaction` обязателен: без него Room читает
     * персонажа и его части разными запросами, и между ними в базу успевает кто-то
     * записать — лист приедет наполовину старым.
     */
    @Transaction
    @Query("SELECT * FROM characters ORDER BY id")
    fun all(): Flow<List<FullCharacter>>

    /** Один лист. Null, если такого персонажа больше нет. */
    @Transaction
    @Query("SELECT * FROM characters WHERE id = :id")
    fun byId(id: Long): Flow<FullCharacter?>

    /** Сколько персонажей в базе. По нулю решается, нужен ли засев. */
    @Query("SELECT COUNT(*) FROM characters")
    suspend fun count(): Int

    /** Возвращает номер, который база дала новому персонажу: по нему привязываются части. */
    @Insert
    suspend fun add(character: CharacterRecord): Long

    @Insert
    suspend fun addWeapons(weapons: List<CharacterWeaponRecord>)

    @Insert
    suspend fun addFeats(feats: List<CharacterFeatRecord>)

    @Insert
    suspend fun addItems(items: List<CharacterItemRecord>)

    @Insert
    suspend fun addSpells(spells: List<CharacterSpellRecord>)

    /*
     * Счётчики листа меняются прибавкой прямо в запросе, а не «прочитали, посчитали,
     * записали». Иначе два быстрых нажатия на «+» успели бы прочитать одно и то же
     * число и прибавить к нему по единице — одно нажатие пропало бы.
     *
     * Границы тоже здесь: за них не должно вывести ни одно нажатие, откуда бы оно ни
     * пришло. MIN и MAX в SQLite с двумя аргументами — это «меньшее» и «большее»,
     * а не подсчёт по всему столбцу.
     */

    /** Пункты героизма: от нуля до предела правил. */
    @Query("UPDATE characters SET heroPoints = MAX(0, MIN(:limit, heroPoints + :delta)) WHERE id = :id")
    suspend fun addHeroPoints(id: Long, delta: Int, limit: Int)

    /** Текущие ПЗ: от нуля до максимума этого персонажа. */
    @Query("UPDATE characters SET currentHp = MAX(0, MIN(maxHp, currentHp + :delta)) WHERE id = :id")
    suspend fun addHp(id: Long, delta: Int)

    /** Временные ПЗ: сверху не ограничены, их даёт заклинание или зелье. */
    @Query("UPDATE characters SET tempHp = MAX(0, tempHp + :delta) WHERE id = :id")
    suspend fun addTempHp(id: Long, delta: Int)

    /** Ранения: от нуля до предела правил. */
    @Query("UPDATE characters SET wounded = MAX(0, MIN(:limit, wounded + :delta)) WHERE id = :id")
    suspend fun addWounded(id: Long, delta: Int, limit: Int)

    /** «При смерти» — галочка, а не счётчик: так попросил автор листа. */
    @Query("UPDATE characters SET dying = :dying WHERE id = :id")
    suspend fun setDying(id: Long, dying: Boolean)

    /**
     * «Полностью здоров»: ПЗ до максимума, временные ПЗ и ранения в ноль, «при смерти»
     * снято. Одним запросом, а не пятью — после ночного отдыха всё это возвращается
     * разом, и промежуточных состояний вроде «здоров, но при смерти» быть не должно.
     */
    @Query(
        "UPDATE characters SET currentHp = maxHp, tempHp = 0, wounded = 0, dying = 0 WHERE id = :id"
    )
    suspend fun heal(id: Long)
}
