package ru.telnor.wizardsstaff.db

import android.content.Context
import kotlinx.coroutines.flow.Flow
import ru.telnor.wizardsstaff.ble.StaffEvent

/**
 * Между посохом и базой. Здесь событие с посоха превращается в запись: событие знает
 * только номер броска, а ключ в базе составной, и номер посоха приходится приписывать
 * отдельно — его приложение узнаёт из ответа на info.
 */
class RollRepository(context: Context) {

    private val dao = StaffDatabase.open(context).rolls()

    /** Лента: свежие сверху, не больше limit штук. Обновляется сама при каждой записи. */
    fun recent(limit: Int): Flow<List<RollRecord>> = dao.recent(limit)

    /** Сколько бросков в базе всего. */
    fun total(): Flow<Int> = dao.total()

    /**
     * Записывает бросок, пришедший с посоха. Повтор молча пропускается, см. RollDao.add.
     * Если посох ещё не назвался (staffId нулевой), запись не делаем: ключ вышел бы
     * общим для всех посохов, и броски разных посохов начали бы затирать друг друга.
     */
    suspend fun add(event: StaffEvent.Roll, staffId: Long, mac: String): Boolean {
        if (staffId == 0L) return false
        dao.add(
            RollRecord(
                staffId = staffId,
                id = event.id,
                mac = mac,
                count = event.count,
                sides = event.sides,
                values = event.values,
                total = event.total,
                staffTime = event.staffTime,
                receivedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    /** Последний известный номер броска этого посоха. Ноль, если записей от него нет. */
    suspend fun lastId(staffId: Long): Long = dao.lastId(staffId) ?: 0L

    /** Подпись к броску. Пустая строка стирает подпись, а не хранится пустой. */
    suspend fun setNote(key: RollKey, note: String?) {
        dao.setNote(key.staffId, key.id, note?.takeIf { it.isNotBlank() })
    }

    /** Переключает пометку «не засчитан». */
    suspend fun toggleDiscarded(key: RollKey) {
        val now = dao.isDiscarded(key.staffId, key.id) ?: return
        dao.setDiscarded(key.staffId, key.id, !now)
    }
}

/** Ключ броска, разобранный обратно из строки вида «2748104938-57». */
data class RollKey(val staffId: Long, val id: Long) {

    companion object {
        /** Возвращает null, если строка не похожа на ключ: лучше промолчать, чем упасть. */
        fun parse(key: String): RollKey? {
            val parts = key.split("-")
            if (parts.size != 2) return null
            val staffId = parts[0].toLongOrNull() ?: return null
            val id = parts[1].toLongOrNull() ?: return null
            return RollKey(staffId, id)
        }
    }
}
