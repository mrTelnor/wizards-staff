package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import ru.telnor.wizardsstaff.rules.DamageType
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.ui.theme.PosohDimens

/*
 * Кирпичи раздела «Персонажи», общие для всех четырёх вкладок: бейдж владения,
 * подписанный кусочек блока, разделитель, кнопки-счётчики.
 *
 * Кегли, начертания и толщина рамок — по `docs/design/STYLE.md`. Там, где в теме есть
 * роль, названная именно этим элементом, берётся роль; свой размер указан только там,
 * где роли под него нет.
 */

/**
 * Квадратик с буквой: степень владения, тип урона. Общая форма, чтобы значки
 * в строке оружия выглядели одним рядом, а не набором разных наклеек.
 */
@Composable
fun LetterBadge(
    letter: String,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    Box(
        modifier = modifier.size(size).background(background, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = letter, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

/** Квадратик с типом урона: «Д» дробящее, «К» колющее, «Р» режущее. */
@Composable
fun DamageBadge(type: DamageType, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val scheme = MaterialTheme.colorScheme
    LetterBadge(type.badge, scheme.surfaceVariant, scheme.onSurface, modifier, size)
}

/**
 * Квадратик со степенью владения. Буква берётся у самой степени, чтобы подписи
 * в листе и на бейдже не разъехались: «И» изученный, «Э» экспертный, «М» мастерский.
 */
@Composable
fun RankBadge(rank: Rank, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val scheme = MaterialTheme.colorScheme
    val background = when (rank) {
        Rank.UNTRAINED -> scheme.surfaceVariant
        Rank.TRAINED -> scheme.surfaceContainerHigh
        Rank.EXPERT -> scheme.primaryContainer
        Rank.MASTER -> scheme.tertiaryContainer
        Rank.LEGENDARY -> scheme.tertiary
    }
    val content = when (rank) {
        Rank.UNTRAINED -> scheme.onSurface
        Rank.TRAINED -> scheme.onSurface
        Rank.EXPERT -> scheme.onPrimaryContainer
        Rank.MASTER -> scheme.onTertiaryContainer
        Rank.LEGENDARY -> scheme.onTertiary
    }
    LetterBadge(rank.badge, background, content, modifier, size)
}

/**
 * Чем бросают и насколько хорошо: характеристика слева, квадратик владения справа.
 * Подпись под числом в плитках испытаний и в блоке сложностей.
 */
@Composable
fun RankLine(ability: String, rank: Rank, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(
            text = ability,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(PosohDimens.spaceXs))
        RankBadge(rank, size = 18.dp)
    }
}

/**
 * Кружок с первой буквой имени вместо портрета. Рисунков персонажей у нас нет,
 * а пустой серый кружок в шапке выглядел бы недогруженной картинкой.
 */
@Composable
fun CharacterAvatar(name: String, size: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size.dp)
            .background(scheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = (size / 2).sp),
            color = scheme.onPrimaryContainer,
        )
    }
}

/**
 * Разделитель между частями одного блока: они об одном, но про разное. Тянется
 * на высоту самой высокой части, поэтому строка, в которой он стоит, должна быть
 * смерена `Modifier.height(IntrinsicSize.Min)`.
 */
@Composable
fun PartDivider(horizontal: Dp = PosohDimens.spaceS) {
    VerticalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.fillMaxHeight().padding(horizontal = horizontal),
    )
}

/**
 * Подписанный кусочек блока: надпись сверху, содержимое под ней. Из таких собраны
 * и блок жизни, и блок брони.
 */
@Composable
fun LabeledPart(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier) {
        // Надпись по середине своего кусочка: так по решению автора подписаны все блоки
        // листа, и глазу не приходится искать, к чему относится какая.
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(PosohDimens.spaceXs))
        content()
    }
}

/**
 * Рамка плитки, по которой можно будет нажать. Толще обычной: так плитка-кнопка
 * отличается от плитки, которая просто показывает число.
 */
val TileBorder = 2.dp

/** Обычный размер кнопки-счётчика. В тесных блоках её уменьшают своим `size`. */
val StepperSize = 34.dp

/**
 * Кегль знака на кнопке. По умолчанию средний; на стороне вызова ставится тот же,
 * каким набрано число, которое кнопка правит, — пара «число и кнопки» должна читаться
 * как одно целое, а не как число и приделанные к нему значки.
 */
val StepperFontSize = 15.sp

/**
 * Круглая кнопка счётчика: один шаг за нажатие. Для чисел, которые ходят
 * по единицам, — героизм, ранения.
 */
@Composable
fun TapStepperButton(
    label: String,
    enabled: Boolean,
    size: Dp = StepperSize,
    fontSize: TextUnit = StepperFontSize,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceContainer,
            contentColor = scheme.onSurface,
        ),
        border = stepperBorder(enabled),
    ) {
        StepperLabel(label, size, fontSize)
    }
}

/** Пауза перед тем, как удержание начнёт повторять шаги. */
private const val HOLD_START_MS = 400L

/** Начальная и самая быстрая пауза между шагами при удержании. */
private const val HOLD_STEP_MS = 120L
private const val HOLD_FAST_MS = 30L

/**
 * Кнопка счётчика, которая повторяет шаг при удержании: ПЗ бывает под сотню, и набирать
 * их по одному нажатию — занятие на весь ход.
 *
 * Первый шаг делается по отпусканию, а не по нажатию: палец, опустившийся на кнопку ради
 * прокрутки списка, не должен отнимать здоровье. Если его увели прокруткой, нажатие
 * отменяется и не считается вовсе.
 *
 * Чем дольше держат, тем быстрее идут шаги: от 120 мс к 30 мс. Так и единицу поймать можно,
 * и полсотни ПЗ отмотать за пару секунд.
 */
@Composable
fun HoldStepperButton(
    label: String,
    enabled: Boolean,
    size: Dp = StepperSize,
    fontSize: TextUnit = StepperFontSize,
    onStep: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Держим отдельно от состояния Compose: перерисовывать кнопку из-за этого незачем.
    val repeated = remember { booleanArrayOf(false) }

    Card(
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) scheme.surfaceContainer else scheme.surfaceContainerLow,
            contentColor = if (enabled) {
                scheme.onSurface
            } else {
                // Недоступная кнопка гаснет прозрачностью, а не серым цветом:
                // серого текста на листе больше нет.
                scheme.onSurface.copy(alpha = 0.38f)
            },
        ),
        border = stepperBorder(enabled),
        modifier = Modifier
            .size(size)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        repeated[0] = false
                        // Ждём: отпустили быстро — это обычное нажатие, оно уйдёт в onTap.
                        val released = withTimeoutOrNull(HOLD_START_MS) { tryAwaitRelease() }
                        if (released == null) {
                            repeated[0] = true
                            var pause = HOLD_STEP_MS
                            while (true) {
                                onStep()
                                val done = withTimeoutOrNull(pause) { tryAwaitRelease() }
                                if (done != null) break
                                pause = maxOf(HOLD_FAST_MS, pause - 10L)
                            }
                        }
                    },
                    onTap = { if (!repeated[0]) onStep() },
                )
            },
    ) {
        StepperLabel(label, size, fontSize)
    }
}

/**
 * Обводка кнопки-счётчика. Такая же, как у плиток, по которым нажимают: круглая
 * кнопка — тоже кнопка. Недоступная гаснет вместе со своим знаком.
 */
@Composable
private fun stepperBorder(enabled: Boolean): BorderStroke {
    val outline = MaterialTheme.colorScheme.outline
    return BorderStroke(TileBorder, if (enabled) outline else outline.copy(alpha = 0.38f))
}

/**
 * Знак на кнопке счётчика, по центру. Набран ролью числа: плюс и минус здесь работают
 * как цифры, а не как текст, и рядом с числом, которое они меняют, должны быть
 * одного кегля и начертания.
 */
@Composable
private fun StepperLabel(label: String, size: Dp, fontSize: TextUnit) {
    Box(Modifier.fillMaxWidth().height(size), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = fontSize),
            textAlign = TextAlign.Center,
        )
    }
}

/** Число со знаком: +2, −1, 0. Минус типографский, как в формулах бросков. */
fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${-value}"
    else -> "0"
}
