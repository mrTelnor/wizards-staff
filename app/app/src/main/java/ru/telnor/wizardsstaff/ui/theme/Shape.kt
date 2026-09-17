package ru.telnor.wizardsstaff.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/*
 * Радиусы и метрики разметки «Посоха мага».
 */

val PosohShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // чип формулы: 1d20, 2d12
    small = RoundedCornerShape(12.dp),       // поля ввода
    medium = RoundedCornerShape(16.dp),      // карточка ленты, плитка листа, кнопка действия
    large = RoundedCornerShape(20.dp),       // карточка активного действия, состояние посоха
    extraLarge = RoundedCornerShape(24.dp),  // карточка последнего броска
)

/** Радиус диалога — 28 dp, вне шкалы Shapes. */
val PosohDialogShape = RoundedCornerShape(28.dp)

/**
 * Отступы и размеры, повторяющиеся по экранам.
 * Шкала отступов: 4 · 8 · 12 · 16 · 20 · 24 · 32.
 */
@Immutable
object PosohDimens {
    // Отступы
    val spaceXs = 4.dp
    val spaceS = 8.dp
    val spaceM = 12.dp
    val spaceL = 16.dp
    val spaceXl = 20.dp
    val spaceXxl = 24.dp
    val spaceXxxl = 32.dp

    /** Поле экрана по горизонтали на планшете. */
    val screenPadding = 24.dp

    /** Внутреннее поле карточки. */
    val cardPadding = 16.dp

    /** Зазор между карточками ленты. Намеренно меньше сетки: лента должна читаться сплошным столбцом. */
    val feedItemGap = 9.dp

    // Каркас планшета (1097 × 686 dp, альбомная)
    val navigationRailWidth = 88.dp
    val topBarHeight = 64.dp

    /** Левая колонка экрана «Броски»: последний бросок, активное действие, состояние посоха. */
    val rollsLeftColumnWidth = 396.dp

    /** Ширина списка персонажей слева на экране «Персонажи». */
    val charactersListWidth = 252.dp

    /** Ширина колонки суммы в карточке ленты — чтобы суммы стояли ровной колонкой. */
    val feedSumColumnWidth = 78.dp

    // Цели нажатия
    val minTouchTarget = 44.dp          // минимум по всему приложению
    val railTouchTarget = 48.dp         // рейка и верхняя панель

    // Аватар персонажа
    val avatarSmall = 42.dp
    val avatarMedium = 46.dp
    val avatarLarge = 62.dp
}
