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

    /*
     * Живые листы отличаются от сохранений пустым `saveName`. Поэтому почти во всех
     * запросах стоит `saveName IS NULL`: без него копии полезли бы в чип выбора
     * персонажа и в засев.
     */

    /**
     * Все живые листы, по порядку добавления. `@Transaction` обязателен: без него Room
     * читает персонажа и его части разными запросами, и между ними в базу успевает кто-то
     * записать — лист приедет наполовину старым.
     */
    @Transaction
    @Query("SELECT * FROM characters WHERE saveName IS NULL ORDER BY id")
    fun all(): Flow<List<FullCharacter>>

    /** Один лист. Null, если такого персонажа больше нет. */
    @Transaction
    @Query("SELECT * FROM characters WHERE id = :id")
    fun byId(id: Long): Flow<FullCharacter?>

    /** Сохранения одного персонажа, свежие сверху. */
    @Transaction
    @Query("SELECT * FROM characters WHERE saveOf = :id ORDER BY savedAt DESC")
    fun savesOf(id: Long): Flow<List<FullCharacter>>

    /** Сколько живых персонажей в базе. По нулю решается, нужен ли засев. */
    @Query("SELECT COUNT(*) FROM characters WHERE saveName IS NULL")
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

    /** ПЗ щита: от нуля до его максимума. Блок щитом отнимает их десятками. */
    @Query("UPDATE characters SET shieldHp = MAX(0, MIN(shieldMaxHp, shieldHp + :delta)) WHERE id = :id")
    suspend fun addShieldHp(id: Long, delta: Int)

    /** «Щит поднят» — галочка, как и «при смерти». */
    @Query("UPDATE characters SET shieldRaised = :raised WHERE id = :id")
    suspend fun setShieldRaised(id: Long, raised: Boolean)

    /**
     * Опыт. Здесь не прибавка, а готовое число: его набирают в поле целиком,
     * и гонки двух быстрых нажатий тут быть не может.
     */
    @Query("UPDATE characters SET xp = :xp WHERE id = :id")
    suspend fun setXp(id: Long, xp: Int)

    /** «При смерти» — галочка, а не счётчик: так попросил автор листа. */
    @Query("UPDATE characters SET dying = :dying WHERE id = :id")
    suspend fun setDying(id: Long, dying: Boolean)

    /**
     * «Полностью здоров»: ПЗ до максимума, ранения в ноль, «при смерти» снято.
     * Одним запросом, а не четырьмя — после отдыха всё это возвращается разом,
     * и промежуточных состояний вроде «здоров, но при смерти» быть не должно.
     *
     * Временные ПЗ обычно сбрасываются вместе со всем, но не всегда: их даёт заклинание
     * со своим сроком, и оно может пережить отдых. Поэтому решение принимает человек
     * в окне подтверждения, а запрос остаётся один.
     */
    @Query(
        "UPDATE characters SET currentHp = maxHp, " +
            "tempHp = CASE WHEN :keepTempHp THEN tempHp ELSE 0 END, " +
            "wounded = 0, dying = 0 WHERE id = :id"
    )
    suspend fun heal(id: Long, keepTempHp: Boolean)
}
