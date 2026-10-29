package ru.telnor.wizardsstaff.db

import androidx.room.Entity
import androidx.room.TypeConverter

/**
 * Один бросок. Он же запись в базе, он же то, что показывает лента: второй модели
 * заводить незачем, полей у броска немного и все они нужны обеим сторонам.
 *
 * Ключ — пара «номер посоха плюс номер броска». Номер броска сквозной и живёт во флеш
 * посоха, поэтому переживает его выключение; номер посоха нужен на случай, если посохов
 * станет больше одного, и на случай, если память посоха потрётся — тогда он придумает
 * себе новый номер, и приложение честно поймёт, что нумерация началась заново, а не
 * свалит старые броски с новыми в одну кучу.
 */
@Entity(tableName = "rolls", primaryKeys = ["staffId", "id"])
data class RollRecord(
    /** Постоянный номер посоха, он приходит в ответе на info. */
    val staffId: Long,
    /** Сквозной номер броска в этом посохе. */
    val id: Long,
    /** Адрес BLE посоха: пригодится, когда посохов станет несколько. */
    val mac: String,
    val count: Int,
    val sides: Int,
    val values: List<Int>,
    val total: Int,
    /** Байт признаков из истории посоха: удача или неудача (FLAG_FORTUNE, FLAG_MISFORTUNE), иначе ноль. */
    val flags: Int = 0,
    /**
     * Время по часам посоха, секунды. Ноль значит, что часы не выставлены: показывать
     * такое время нельзя, иначе бросок встанет в 1970 год. Тогда берётся receivedAt.
     */
    val staffTime: Long,
    /** Когда планшет получил бросок, миллисекунды. */
    val receivedAt: Long,
    /** Подпись человека: «атака по гоблину». */
    val note: String? = null,
    /** Ссылка на действие персонажа. Появится вместе с листами персонажей. */
    val actionId: Long? = null,
    /** Бросок помечен как не засчитанный. */
    val discarded: Boolean = false,
) {
    /** Чем этот бросок отличается от всех прочих. */
    val key: String get() = "$staffId-$id"

    /** Формула броска: 1d20, 3d6. */
    val formula: String get() = "${count}d$sides"

    /**
     * Одна кость, брошенная дважды: удача или неудача. Узнаётся по числу значений, а не по
     * flags: броски с удачей, пришедшие до того, как приложение научилось читать adv
     * (с 2026-10-29), лежат в базе с нулём во flags, и их тоже нельзя показывать суммой.
     */
    val twoRolls: Boolean get() = count == 1 && values.size == 2

    /** Слагаемые: «2 + 4 + 5», у удачи и неудачи оба броска «14, 7». У одной кости слагаемых нет. */
    val breakdown: String get() = when {
        twoRolls -> values.joinToString(", ")
        values.size > 1 -> values.joinToString(" + ")
        else -> ""
    }

    /** Какой из двух бросков удачи или неудачи пошёл в зачёт; -1, если бросок обычный. */
    val keptIndex: Int get() = if (twoRolls) values.indexOf(total) else -1

    /** Подпись режима для ленты; null у обычного броска и у старых записей, где режим неизвестен. */
    val modeLabel: String? get() = when (flags) {
        FLAG_FORTUNE -> "УДАЧА"
        FLAG_MISFORTUNE -> "НЕУДАЧА"
        else -> null
    }

    /**
     * Критический успех считается только по натуральной двадцатке на одном d20 — по итогу,
     * а не по первой кости: у неудачи «20, 3» в зачёт идёт тройка.
     */
    val critSuccess: Boolean get() = count == 1 && sides == 20 && total == 20

    /** Критический провал — натуральная единица там же. */
    val critFail: Boolean get() = count == 1 && sides == 20 && total == 1

    /** Знает ли посох, когда это было. Если нет, лента покажет время получения планшетом. */
    val staffTimeKnown: Boolean get() = staffTime > 0

    /** Время, которое показываем человеку, миллисекунды. */
    val shownAt: Long get() = if (staffTimeKnown) staffTime * 1000 else receivedAt

    companion object {
        /** Удача: из двух бросков в зачёт лучший. То же число в байте flags истории посоха. */
        const val FLAG_FORTUNE = 1

        /** Неудача: в зачёт худший. */
        const val FLAG_MISFORTUNE = 2
    }
}

/**
 * Значения костей в одну ячейку. Списками SQLite не умеет, а заводить отдельную таблицу
 * ради нескольких чисел — лишняя работа и лишние соединения при каждом чтении ленты.
 * Строка «2,4,5» читается человеком, если он заглянет в базу отладчиком.
 */
class RollConverters {

    @TypeConverter
    fun fromValues(values: List<Int>): String = values.joinToString(",")

    @TypeConverter
    fun toValues(text: String): List<Int> =
        if (text.isEmpty()) emptyList() else text.split(",").map { it.toInt() }
}
