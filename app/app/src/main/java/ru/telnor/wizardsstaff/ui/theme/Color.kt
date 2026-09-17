package ru.telnor.wizardsstaff.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Токены цвета приложения «Посох мага».
 * Светлая тема основная: играют при приглушённом свете, но планшет лежит на столе
 * среди карт и фигурок, и светлый интерфейс на нём читается лучше тёмного.
 * Тёмная тема — те же роли с другими значениями, разметка не меняется.
 *
 * Акцент (primary) — он же цвет подсветки шара посоха по умолчанию (синий из палитры посоха).
 * Критический успех живёт в роли tertiary (золото), критический провал — в роли error.
 */

// ---------- Светлая тема ----------

private val PrimaryLight = Color(0xFF2E7CD6)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val PrimaryContainerLight = Color(0xFFD9E8FB)
private val OnPrimaryContainerLight = Color(0xFF0C3A6B)

private val SecondaryLight = Color(0xFF51617A)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFE0E6F0)
private val OnSecondaryContainerLight = Color(0xFF2A3648)

private val TertiaryLight = Color(0xFF8A6100)            // крит. успех
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFFBEFD2)
private val OnTertiaryContainerLight = Color(0xFF4A3400)

private val ErrorLight = Color(0xFFC0392B)               // крит. провал
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFBDDD9)
private val OnErrorContainerLight = Color(0xFF5C1A13)

private val BackgroundLight = Color(0xFFEBEFF5)
private val OnBackgroundLight = Color(0xFF17202B)
private val SurfaceLight = Color(0xFFFFFFFF)
private val OnSurfaceLight = Color(0xFF17202B)
private val SurfaceVariantLight = Color(0xFFEFF3F8)
private val OnSurfaceVariantLight = Color(0xFF5C6874)
private val OutlineLight = Color(0xFFB9C3CF)
private val OutlineVariantLight = Color(0xFFDCE3EB)

private val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
private val SurfaceContainerLowLight = Color(0xFFF7F9FB)
private val SurfaceContainerLight = Color(0xFFEFF3F8)
private val SurfaceContainerHighLight = Color(0xFFE5EBF2)
private val SurfaceContainerHighestLight = Color(0xFFDDE4ED)

private val InverseSurfaceLight = Color(0xFF2A3542)
private val InverseOnSurfaceLight = Color(0xFFEDF1F6)
private val InversePrimaryLight = Color(0xFF8FBEF3)

// ---------- Тёмная тема ----------

private val PrimaryDark = Color(0xFF8FBEF3)
private val OnPrimaryDark = Color(0xFF06325C)
private val PrimaryContainerDark = Color(0xFF17497D)
private val OnPrimaryContainerDark = Color(0xFFD2E4FB)

private val SecondaryDark = Color(0xFFB6C4DA)
private val OnSecondaryDark = Color(0xFF223040)
private val SecondaryContainerDark = Color(0xFF38465A)
private val OnSecondaryContainerDark = Color(0xFFD6E0EE)

private val TertiaryDark = Color(0xFFF2C777)
private val OnTertiaryDark = Color(0xFF402D00)
private val TertiaryContainerDark = Color(0xFF4A3600)
private val OnTertiaryContainerDark = Color(0xFFFBE3B4)

private val ErrorDark = Color(0xFFF2A9A2)
private val OnErrorDark = Color(0xFF5C1A13)
private val ErrorContainerDark = Color(0xFF63211A)
private val OnErrorContainerDark = Color(0xFFFBDDD9)

private val BackgroundDark = Color(0xFF0F141A)
private val OnBackgroundDark = Color(0xFFE6ECF3)
private val SurfaceDark = Color(0xFF161C24)
private val OnSurfaceDark = Color(0xFFE6ECF3)
private val SurfaceVariantDark = Color(0xFF1C242E)
private val OnSurfaceVariantDark = Color(0xFFA3AFBD)
private val OutlineDark = Color(0xFF6B7886)
private val OutlineVariantDark = Color(0xFF2C3743)

private val SurfaceContainerLowestDark = Color(0xFF0C1116)
private val SurfaceContainerLowDark = Color(0xFF141A21)
private val SurfaceContainerDark = Color(0xFF1C242E)
private val SurfaceContainerHighDark = Color(0xFF232C38)
private val SurfaceContainerHighestDark = Color(0xFF2B3543)

private val InverseSurfaceDark = Color(0xFFE6ECF3)
private val InverseOnSurfaceDark = Color(0xFF1C242E)
private val InversePrimaryDark = Color(0xFF2E7CD6)

// ---------- Схемы Material 3 ----------

val PosohLightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    inversePrimary = InversePrimaryLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceTint = PrimaryLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = Color(0xFF000000),
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
)

val PosohDarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    inversePrimary = InversePrimaryDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceTint = PrimaryDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = Color(0xFF000000),
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
)

/**
 * Роли, которых нет в Material 3, но которые нужны этому приложению.
 *
 * critSuccessOutline / critFailOutline — обводка карточки крита в ленте:
 * заливки tertiaryContainer и errorContainer сами по себе слишком мягкие,
 * чтобы карточка выделялась при взгляде через стол.
 * connected — индикатор живой связи с посохом (не error и не primary: это состояние, а не действие).
 * staffOffline — тот же индикатор, когда посоха нет рядом.
 */
@Immutable
data class PosohExtraColors(
    val critSuccessOutline: Color,
    val critFailOutline: Color,
    val connected: Color,
    val staffOffline: Color,
)

val PosohExtraColorsLight = PosohExtraColors(
    critSuccessOutline = Color(0xFFE8CE93),
    critFailOutline = Color(0xFFEFB6AE),
    connected = Color(0xFF2E7D4F),
    staffOffline = Color(0xFF5C6874),
)

val PosohExtraColorsDark = PosohExtraColors(
    critSuccessOutline = Color(0xFF6B5100),
    critFailOutline = Color(0xFF8A3A30),
    connected = Color(0xFF6FD49B),
    staffOffline = Color(0xFFA3AFBD),
)

val LocalPosohExtraColors = staticCompositionLocalOf { PosohExtraColorsLight }
