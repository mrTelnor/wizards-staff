package ru.telnor.wizardsstaff.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/*
 * Тема «Посоха мага». Заменяет Theme.kt из шаблона Android Studio.
 *
 * Динамический цвет (dynamicColor) выключен намеренно: акцент приложения —
 * это цвет подсветки шара посоха, он должен совпадать с железом, а не с обоями планшета.
 *
 * Роли surfaceContainer / surfaceContainerLow / surfaceContainerHigh появились
 * в material3 1.2.0 — если сборка на более старой версии, поднять зависимость,
 * иначе Color.kt не скомпилируется.
 */

@Composable
fun WizardsStaffTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) PosohDarkColors else PosohLightColors
    val extraColors = if (darkTheme) PosohExtraColorsDark else PosohExtraColorsLight

    CompositionLocalProvider(LocalPosohExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PosohTypography,
            shapes = PosohShapes,
            content = content,
        )
    }
}

/** Доступ к ролям цвета, которых нет в Material 3: PosohTheme.extraColors.connected и т. д. */
object PosohTheme {
    val extraColors: PosohExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPosohExtraColors.current
}
