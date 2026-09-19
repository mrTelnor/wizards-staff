package ru.telnor.wizardsstaff.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Запросы к ленте бросков. SQL здесь проверяется при сборке — ради этого и брали Room. */
@Dao
interface RollDao {

    /**
     * Добавляет бросок. Повтор молча пропускается: ключ у записи составной, и посох
     * присылает один и тот же бросок дважды — свежим событием и потом в догрузке истории.
     * Пропустить, а не перезаписать, важно: у уже лежащей записи может быть подпись
     * или пометка «не засчитан», и догрузка не должна их стирать.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(roll: RollRecord)

    /**
     * Свежие сверху. Сортируем по времени посоха, когда оно известно, иначе по времени
     * получения планшетом: у бросков, сделанных при невыставленных часах, `staffTime`
     * нулевой, и по нему они уехали бы в самый низ, в 1970 год.
     *
     * Секунды посоха переводим в миллисекунды, чтобы сравнивать с временем планшета.
     */
    @Query(
        """
        SELECT * FROM rolls
        ORDER BY CASE WHEN staffTime > 0 THEN staffTime * 1000 ELSE receivedAt END DESC,
                 id DESC
        LIMIT :limit
        """
    )
    fun recent(limit: Int): Flow<List<RollRecord>>

    /** Сколько бросков в базе всего: лента показывает это в заголовке и знает, есть ли что догружать. */
    @Query("SELECT COUNT(*) FROM rolls")
    fun total(): Flow<Int>

    @Query("UPDATE rolls SET note = :note WHERE staffId = :staffId AND id = :id")
    suspend fun setNote(staffId: Long, id: Long, note: String?)

    @Query("UPDATE rolls SET discarded = :discarded WHERE staffId = :staffId AND id = :id")
    suspend fun setDiscarded(staffId: Long, id: Long, discarded: Boolean)

    @Query("SELECT discarded FROM rolls WHERE staffId = :staffId AND id = :id")
    suspend fun isDiscarded(staffId: Long, id: Long): Boolean?

    /**
     * Самый большой номер броска этого посоха. С него начинается догрузка: всё, что
     * посох накопил позже, планшет пропустил. Null значит, что от этого посоха записей
     * нет вовсе — тогда просим всю историю с нуля.
     */
    @Query("SELECT MAX(id) FROM rolls WHERE staffId = :staffId")
    suspend fun lastId(staffId: Long): Long?
}
