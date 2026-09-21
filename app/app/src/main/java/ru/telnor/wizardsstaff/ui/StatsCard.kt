package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.rules.FEET_PER_SQUARE
import ru.telnor.wizardsstaff.rules.XP_PER_LEVEL
import ru.telnor.wizardsstaff.rules.classDifficulty
import ru.telnor.wizardsstaff.rules.spellDifficulty
import ru.telnor.wizardsstaff.rules.speed
import ru.telnor.wizardsstaff.ui.theme.PosohDimens

/*
 * Блок «Сложности, скорость и опыт»: четыре части одной карточкой с разделителями,
 * как в блоке брони.
 *
 * Три части только показывают: обе СЛ и скорость выводит `Pf2.kt`. Правится одна —
 * опыт: его некому вывести, он просто копится от игры к игре.
 *
 * Уровень при тысяче опыта приложение не поднимает. Из уровня движок выводит всё —
 * КБ, испытания, навыки, атаки, обе СЛ, — а максимум ПЗ, владения и черты хранятся
 * и меняются по книге руками. Подними уровень кнопкой, и получится персонаж,
 * поднявшийся наполовину: броски уже нового уровня, здоровье и владения старого.
 * Поэтому плитка только загорается и говорит, что пора садиться за книгу.
 */

/** Ширина поля ввода опыта. Под четыре знака: больше тысячи опыт не копят. */
private val XpFieldWidth = 84.dp

/**
 * Высота поля ввода опыта. По ней же выровнены числа соседних частей: у опыта вокруг
 * числа рамка, и без общей высоты его число сидело бы ниже остальных трёх.
 */
private val ValueHeight = 40.dp

@Composable
fun StatsCard(character: CharacterSheet, actions: SheetActions, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val sheet = character.sheet

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        // Боковых полей у карточки нет, у разделителей — своих отступов: поля вокруг
        // частей поровну раскладывает `SpaceEvenly`, как в блоке брони.
        Column(Modifier.padding(vertical = PosohDimens.spaceS)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DifficultyPart(
                    label = "Классовая СЛ",
                    value = sheet.classDifficulty(),
                    rank = sheet.classDc,
                    ability = sheet.keyAbility.short,
                    modifier = Modifier.width(IntrinsicSize.Max),
                )
                PartDivider(0.dp)
                DifficultyPart(
                    label = "СЛ заклинаний",
                    value = sheet.spellDifficulty(),
                    rank = sheet.spellDc,
                    ability = sheet.keyAbility.short,
                    modifier = Modifier.width(IntrinsicSize.Max),
                )
                PartDivider(0.dp)
                SpeedPart(sheet.speed(), Modifier.width(IntrinsicSize.Max))
                PartDivider(0.dp)
                XpPart(character.record.xp, actions.xp, Modifier.width(IntrinsicSize.Max))
            }
        }
    }
}

/** Классовая СЛ или СЛ заклинаний: число, под ним ключевая характеристика и владение. */
@Composable
private fun DifficultyPart(
    label: String,
    value: Int,
    rank: ru.telnor.wizardsstaff.rules.Rank,
    ability: String,
    modifier: Modifier = Modifier,
) {
    LabeledPart(label, modifier) {
        BigValue(value.toString())
        RankLine(ability, rank, Modifier.fillMaxWidth())
    }
}

/** Скорость: футы крупно, под ними то же самое клетками поля. */
@Composable
private fun SpeedPart(feet: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    LabeledPart("Скорость", modifier) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().height(ValueHeight).padding(bottom = 4.dp),
        ) {
            Text(
                text = feet.toString(),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = BigNumber),
                color = scheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(PosohDimens.spaceXs))
            Text(
                text = "фт",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = scheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(
            // За столом ходят по клеткам, а не по футам: клетка это пять футов.
            text = squares(feet / FEET_PER_SQUARE),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** «1 клетка», «2 клетки», «6 клеток» — по-русски, как говорят за столом. */
private fun squares(count: Int): String {
    val word = when {
        count % 100 in 11..14 -> "клеток"
        count % 10 == 1 -> "клетка"
        count % 10 in 2..4 -> "клетки"
        else -> "клеток"
    }
    return "$count $word"
}

/**
 * Опыт: единственное поле листа, куда пишут руками. Только цифры — буквы и минус
 * в опыте смысла не имеют, а отдельная проверка с руганью была бы хуже, чем просто
 * не принимать их вовсе.
 *
 * На тысяче плитка загорается золотым. Уровень при этом не меняется: почему —
 * написано в начале файла.
 */
@Composable
private fun XpPart(xp: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    // rememberSaveable: поворот планшета пересоздаёт активность, и набранное
    // в поле не должно при этом пропадать.
    var text by rememberSaveable(xp == 0) { mutableStateOf(if (xp == 0) "" else xp.toString()) }
    val value = text.toIntOrNull() ?: 0
    val levelUp = value >= XP_PER_LEVEL
    val shape = RoundedCornerShape(10.dp)

    LabeledPart("Опыт", modifier) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BasicTextField(
                value = text,
                onValueChange = { entered ->
                    // Пять знаков с запасом: в PF2e опыт сбрасывается на каждой тысяче.
                    if (entered.length <= 5 && entered.all { it.isDigit() }) {
                        text = entered
                        onChange(entered.toIntOrNull() ?: 0)
                    }
                },
                singleLine = true,
                // «Готово» вместо перевода строки: поле однострочное, и по нему
                // курсор уходит, а клавиатура закрывается.
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    fontSize = BigNumber,
                    color = if (levelUp) scheme.onTertiaryContainer else scheme.onSurface,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(scheme.primary),
                decorationBox = { field ->
                    Box(
                        modifier = Modifier
                            .width(XpFieldWidth)
                            .height(ValueHeight)
                            .background(
                                color = if (levelUp) scheme.tertiaryContainer else scheme.surfaceContainerLow,
                                shape = shape,
                            )
                            .border(
                                width = if (levelUp) TileBorder else 1.dp,
                                color = if (levelUp) scheme.tertiary else scheme.outlineVariant,
                                shape = shape,
                            )
                            .padding(horizontal = PosohDimens.spaceXs),
                        contentAlignment = Alignment.Center,
                    ) { field() }
                },
            )
        }
        Text(
            text = if (levelUp) "Новый уровень!" else "до $XP_PER_LEVEL",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            fontWeight = if (levelUp) FontWeight.SemiBold else FontWeight.Normal,
            color = if (levelUp) scheme.tertiary else scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Крупное число части, по центру и на общей с опытом высоте. */
@Composable
private fun BigValue(value: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(ValueHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = BigNumber),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
