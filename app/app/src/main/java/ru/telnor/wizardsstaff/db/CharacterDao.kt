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
}
