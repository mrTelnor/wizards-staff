package ru.telnor.wizardsstaff.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/*
 * Иконки приложения. Взяты из макетов (docs/design/html), там они нарисованы обычными
 * контурами SVG: та же толщина линии 1.8 и скруглённые концы.
 *
 * Своей библиотеки иконок не подключаем: нужных штук десяток, а material-icons-extended
 * тянет в приложение тысячи лишних.
 *
 * Цвет здесь чёрный только формально: Icon() перекрашивает иконку в нужный цвет сам.
 */

private fun strokeIcon(vararg pathData: String, strokeWidth: Float = 1.8f): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        pathData.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** Окружность строкой пути: в SVG макетов она записана тегом circle, а путь понимают все. */
private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${r * 2} 0a$r $r 0 1 0 ${-r * 2} 0"

object StaffIcons {

    /** Связь с посохом есть. */
    val Bluetooth: ImageVector = strokeIcon("M8 7.5 16 16.5 12 20V4l4 3.5L8 16.5")

    /** Связи нет: та же иконка, перечёркнутая. */
    val BluetoothOff: ImageVector = strokeIcon("M8 7.5 16 16.5 12 20V4l4 3.5L8 16.5", "M4 20 20 4")

    /** Раздел «Броски»: кость d20. */
    val Dice: ImageVector = strokeIcon("M12 2.5 21 7.5v9L12 21.5 3 16.5v-9z", "M12 6.8 16.3 15h-8.6z")

    /** Раздел «Персонажи». */
    val Person: ImageVector = strokeIcon(circle(12f, 8f, 3.5f), "M5 20c0-3.6 3.1-6 7-6s7 2.4 7 6")

    /** Раздел «Статистика». */
    val Chart: ImageVector = strokeIcon("M4 20V11", "M10 20V4", "M16 20v-6", "M3 20h18")

    /** Раздел «Посох»: навершие с шаром. */
    val Staff: ImageVector = strokeIcon("M12 9.5V21", circle(12f, 6f, 3.3f))

    /** Поиск посоха. */
    val Search: ImageVector = strokeIcon(circle(11f, 11f, 6.5f), "m16 16 5 5", strokeWidth = 2f)

    /** Закрыть, снять взвод. */
    val Close: ImageVector = strokeIcon("M6.5 6.5 17.5 17.5", "M17.5 6.5 6.5 17.5", strokeWidth = 1.9f)

    /** Заряд посоха. */
    val Battery: ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M5.5 7.5h10a3 3 0 0 1 3 3v3a3 3 0 0 1-3 3h-10a3 3 0 0 1-3-3v-3a3 3 0 0 1 3-3z"
            ).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        addPath(
            pathData = PathParser().parsePathString("M21.5 11v2").toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
        )
        // Заполненная часть внутри корпуса: показывает, что батарея не пустая.
        addPath(
            pathData = PathParser().parsePathString("M5.5 10.5h6v3h-6z").toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()

    /** Пояснение про геолокацию. */
    val Info: ImageVector = strokeIcon(circle(12f, 12f, 8.5f), "M12 11v5.5", "M12 7.8v.4")

    /** Вернуться назад. */
    val Back: ImageVector = strokeIcon("M19.5 12H5", "m11 5.5-6 6.5 6 6.5")

    /** Отправить команду посоху. */
    val Send: ImageVector = strokeIcon("M4 12h13", "m12 6.5 6 5.5-6 5.5")

    /** Сохранить журнал в файл. */
    val Save: ImageVector = strokeIcon("M12 4v10.5", "m7.5 11 4.5 4.5 4.5-4.5", "M5 19.5h14")

    /** Очистить журнал. */
    val Trash: ImageVector = strokeIcon("M5 7h14", "M9.5 7V4.8h5V7", "m7 7 1 12.2h8L17 7")

    /** Логи посоха. */
    val Logs: ImageVector = strokeIcon("M5 6h14", "M5 10h14", "M5 14h9", "M5 18h11")
}
