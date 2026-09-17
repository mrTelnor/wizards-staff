package ru.telnor.wizardsstaff.ui.theme

import androidx.compose.material3.Typography
import ru.telnor.wizardsstaff.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Типографика «Посоха мага».
 *
 * Literata — цифры результата и имена персонажей. Серифная антиква: крупная цифра,
 *   набранная ей, читается через стол и даёт «книжный» оттенок без готики и пергамента.
 * Onest — весь интерфейс. Гротеск с честной кириллицей и узкими цифрами.
 *
 * Оба шрифта лежат в res/font. Нужны файлы (Google Fonts, SIL OFL):
 *   res/font/literata_semibold.ttf   (600)
 *   res/font/literata_bold.ttf       (700)
 *   res/font/onest_regular.ttf       (400)
 *   res/font/onest_medium.ttf        (500)
 *   res/font/onest_semibold.ttf      (600)
 *   res/font/onest_bold.ttf          (700)
 * Альтернатива — downloadable fonts через GoogleFont.Provider; тогда
 * заменить Font(R.font.…) на Font(googleFont, provider, weight).
 *
 * Все цифры выводить с FeatureSettings("tnum") — в ленте суммы должны стоять
 * в колонку, иначе строки «прыгают» при прокрутке.
 */

private val Literata = FontFamily(
    Font(R.font.literata_semibold, FontWeight.SemiBold),
    Font(R.font.literata_bold, FontWeight.Bold),
)

private val Onest = FontFamily(
    Font(R.font.onest_regular, FontWeight.Normal),
    Font(R.font.onest_medium, FontWeight.Medium),
    Font(R.font.onest_semibold, FontWeight.SemiBold),
    Font(R.font.onest_bold, FontWeight.Bold),
)

private val TabularFigures = "tnum"

private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

val PosohTypography = Typography(

    // Результат последнего броска. Главный элемент экрана «Броски»:
    // ведущий должен прочитать его с полутора метров.
    displayLarge = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.Bold,
        fontSize = 130.sp,
        lineHeight = 133.sp,
        letterSpacing = (-0.035).em,
        fontFeatureSettings = TabularFigures,
        lineHeightStyle = TightLineHeight,
    ),

    // Сумма броска в ленте.
    displayMedium = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = TabularFigures,
        lineHeightStyle = TightLineHeight,
    ),

    // Значения в плитках листа персонажа: КБ, ПЗ, Класс. СЛ, Восприятие, Скорость.
    displaySmall = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 35.sp,
        fontFeatureSettings = TabularFigures,
    ),

    headlineLarge = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.01).em,
    ),

    // Заголовок пустого состояния и диалога.
    headlineMedium = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.01).em,
    ),

    // Заголовок экрана в верхней панели планшета.
    headlineSmall = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.01).em,
    ),

    // Имя персонажа.
    titleLarge = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.015).em,
    ),

    // Заголовок карточки, название действия.
    titleMedium = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),

    // Подпись броска в ленте.
    titleSmall = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),

    bodyLarge = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),

    // Слагаемые броска, пояснения.
    bodyMedium = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TabularFigures,
    ),

    // Время броска, адрес устройства.
    bodySmall = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = TabularFigures,
    ),

    // Текст кнопки.
    labelLarge = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),

    // Подпись пункта навигационной рейки, чип формулы.
    labelMedium = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),

    // Надпись над блоком: «ПОСЛЕДНИЙ БРОСОК», «АКТИВНОЕ ДЕЙСТВИЕ», «КЛАСС БРОНИ».
    // Выводить в верхнем регистре на стороне вызова (text.uppercase()).
    labelSmall = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.07.em,
    ),
)
