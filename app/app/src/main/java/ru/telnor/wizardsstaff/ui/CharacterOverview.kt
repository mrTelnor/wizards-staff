package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import ru.telnor.wizardsstaff.db.CharacterRecord
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.db.CharacterWeaponRecord
import ru.telnor.wizardsstaff.db.silvrinSeed
import ru.telnor.wizardsstaff.rules.Ability
import ru.telnor.wizardsstaff.rules.HERO_POINTS_MAX
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.rules.Save
import ru.telnor.wizardsstaff.rules.Sheet
import ru.telnor.wizardsstaff.rules.WOUNDED_MAX
import ru.telnor.wizardsstaff.rules.armorClass
import ru.telnor.wizardsstaff.rules.attackMod
import ru.telnor.wizardsstaff.rules.classDifficulty
import ru.telnor.wizardsstaff.rules.damageFormula
import ru.telnor.wizardsstaff.rules.mod
import ru.telnor.wizardsstaff.rules.perceptionMod
import ru.telnor.wizardsstaff.rules.rollFormula
import ru.telnor.wizardsstaff.rules.saveMod
import ru.telnor.wizardsstaff.rules.speed
import ru.telnor.wizardsstaff.rules.spellDifficulty
import ru.telnor.wizardsstaff.ui.theme.PosohDimens
import ru.telnor.wizardsstaff.ui.theme.WizardsStaffTheme

/*
 * Вкладка «Обзор» листа персонажа.
 *
 * Порядок блоков: кто он такой, чем бросает, сколько в нём жизни, чем защищён, чем бьёт.
 * Характеристики и жизнь подняты наверх намеренно: за столом смотрят чаще всего туда,
 * а жизнь ещё и правят по нескольку раз за бой.
 *
 * Ни одно выведенное число здесь не хранится: КБ, СЛ, скорость, испытания и атаки считает
 * `Pf2.kt`. Из базы приходит только то, что не выводится: ПЗ, временные ПЗ, ранения,
 * «при смерти», героизм и уровень.
 */

/** Зазор между блоками листа. В макете 10, это между шагами шкалы отступов. */
private val BlockGap = 10.dp

/**
 * Что лист умеет менять. Правки уходят в базу прибавкой, а границы держит она же,
 * поэтому здесь только «на сколько», без «до скольки».
 */
data class SheetActions(
    val heroPoints: (Int) -> Unit = {},
    val hp: (Int) -> Unit = {},
    val tempHp: (Int) -> Unit = {},
    val wounded: (Int) -> Unit = {},
    val dying: (Boolean) -> Unit = {},
    val heal: () -> Unit = {},
)

@Composable
fun CharacterOverview(
    character: CharacterSheet,
    modifier: Modifier = Modifier,
    actions: SheetActions = SheetActions(),
) {
    val sheet = character.sheet

    BoxWithConstraints(modifier) {
        // На планшете в альбомной всё встаёт в ряд; в портретной и на телефоне плитки
        // защиты переносятся, а блок жизни делится надвое.
        val wide = maxWidth >= 900.dp
        val defenceColumns = if (wide) 4 else 2

        Column(
            verticalArrangement = Arrangement.spacedBy(BlockGap),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PosohDimens.screenPadding, vertical = PosohDimens.spaceM),
        ) {
            CharacterHeader(character, actions)

            // Шесть характеристик всегда одной строкой: разорванный ряд читается хуже,
            // а места хватает даже в портретной — плитка узкая, в ней три коротких строки.
            Row(horizontalArrangement = Arrangement.spacedBy(PosohDimens.spaceS)) {
                Ability.entries.forEach { ability ->
                    AbilityTile(
                        ability = ability,
                        sheet = sheet,
                        key = ability == sheet.keyAbility,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            LifeCard(character.record, actions, wide)

            TileGrid(defenceTiles(sheet), defenceColumns) { tile ->
                SheetTile(
                    label = tile.label,
                    value = tile.value,
                    suffix = tile.suffix,
                    modifier = Modifier.weight(1f),
                )
            }

            val attacks = character.weapons.flatMap { weaponRows(sheet, it) }
            val saves = saveRows(sheet)

            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(PosohDimens.spaceM)) {
                    if (attacks.isNotEmpty()) RowsCard("Атаки", attacks, Modifier.weight(1f))
                    RowsCard("Испытания и восприятие", saves, Modifier.weight(1f))
                }
            } else {
                if (attacks.isNotEmpty()) RowsCard("Атаки", attacks, Modifier.fillMaxWidth())
                RowsCard("Испытания и восприятие", saves, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Шапка: аватар, имя, кто он такой, а справа столбиком уровень и героизм.
 *
 * Кнопки героизма стоят справа от его плитки, одна над другой, и шагают по одному пункту
 * за нажатие: пунктов всего три, удержание тут только мешало бы.
 */
@Composable
private fun CharacterHeader(character: CharacterSheet, actions: SheetActions) {
    val scheme = MaterialTheme.colorScheme
    val record = character.record
    // Мировоззрение и размер в шапке идут строчными: это не названия, а описание.
    val subtitle = listOf(
        record.ancestry,
        record.className,
        record.deity,
        record.alignment.lowercase(),
        "${record.size.lowercase()} размер",
    ).filter { it.isNotBlank() }.joinToString(" · ")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = PosohDimens.spaceM),
        ) {
            CharacterAvatar(record.name, size = 54)
            Spacer(Modifier.width(PosohDimens.spaceL))
            Column(Modifier.weight(1f)) {
                Text(record.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(PosohDimens.spaceM))

            // Выравнивание по левому краю, а не по правому: иначе плитка уровня встала бы
            // вровень с кнопками героизма и уехала вправо от его плитки.
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceS),
            ) {
                CornerTile(
                    label = "Уровень",
                    value = record.level.toString(),
                    background = scheme.surfaceVariant,
                    content = scheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CornerTile(
                        label = "Героизм",
                        value = record.heroPoints.toString(),
                        background = scheme.tertiaryContainer,
                        content = scheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.width(PosohDimens.spaceS))
                    Column(verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceXs)) {
                        StepperButton(
                            label = "+",
                            enabled = record.heroPoints < HERO_POINTS_MAX,
                            onClick = { actions.heroPoints(1) },
                        )
                        StepperButton(
                            label = "−",
                            enabled = record.heroPoints > 0,
                            onClick = { actions.heroPoints(-1) },
                        )
                    }
                }
            }
        }
    }
}

/** Плитка в шапке: уровень и героизм. С обводкой, как остальные плитки листа. */
@Composable
private fun CornerTile(label: String, value: String, background: Color, content: Color) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = background, contentColor = content),
        border = BorderStroke(1.dp, content.copy(alpha = 0.25f)),
        modifier = Modifier.width(96.dp).height(62.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().height(62.dp),
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
            Text(text = value, style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp))
        }
    }
}

/** Плитка характеристики: трёхбуквенное имя, модификатор крупно, само значение мелким. */
@Composable
private fun AbilityTile(ability: Ability, sheet: Sheet, key: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val score = sheet.scores[ability] ?: 10
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (key) scheme.primaryContainer else scheme.surface,
            contentColor = if (key) scheme.onPrimaryContainer else scheme.onSurface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (key) scheme.primary.copy(alpha = 0.45f) else scheme.outlineVariant,
        ),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            Text(
                // Трёхбуквенное имя: «ВЫНОСЛИВОСТЬ» в узкую плитку не встаёт.
                text = ability.short,
                style = MaterialTheme.typography.labelSmall,
                color = if (key) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = signed(sheet.mod(ability)),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp),
                maxLines = 1,
            )
            Text(
                // Ключевая подписана: по ней считаются классовая СЛ и магия.
                text = if (key) "$score · ключ." else score.toString(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = if (key) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Жизнь одним блоком: ПЗ со шкалой, временные ПЗ, ранения, «при смерти» и кнопка
 * «Полностью здоров».
 *
 * Всё это меняется в бою и меняется вместе, поэтому и собрано в одну карточку: четыре
 * отдельные заставляли бы глаза прыгать по экрану в середине хода.
 */
@Composable
private fun LifeCard(record: CharacterRecord, actions: SheetActions, wide: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = PosohDimens.spaceL, vertical = PosohDimens.spaceM)) {
            if (wide) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HealthPart(record, actions, Modifier.weight(1f))
                    LifeDivider()
                    StatePart(record, actions, Modifier.weight(1f))
                }
            } else {
                HealthPart(record, actions, Modifier.fillMaxWidth())
                Spacer(Modifier.height(PosohDimens.spaceM))
                StatePart(record, actions, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Разделитель между частями блока жизни: они в одной карточке, но про разное. */
@Composable
private fun LifeDivider() {
    VerticalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier
            .height(62.dp)
            .padding(horizontal = PosohDimens.spaceL),
    )
}

/** Текущие ПЗ, шкала и кнопки. */
@Composable
private fun HealthPart(record: CharacterRecord, actions: SheetActions, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Text(
            text = "ЗДОРОВЬЕ",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(PosohDimens.spaceXs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = record.currentHp.toString(),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp),
                maxLines = 1,
            )
            Spacer(Modifier.width(PosohDimens.spaceXs))
            Text(
                text = "/ ${record.maxHp}",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(PosohDimens.spaceM))
            HealButton(actions.heal)
            Spacer(Modifier.weight(1f))
            HoldStepperButton("−", enabled = record.currentHp > 0) { actions.hp(-1) }
            Spacer(Modifier.width(PosohDimens.spaceS))
            HoldStepperButton("+", enabled = record.currentHp < record.maxHp) { actions.hp(1) }
        }
        Spacer(Modifier.height(PosohDimens.spaceS))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(scheme.surfaceContainerHigh, RoundedCornerShape(3.dp)),
        ) {
            val fill = if (record.maxHp > 0) {
                (record.currentHp.toFloat() / record.maxHp).coerceIn(0f, 1f)
            } else {
                0f
            }
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .height(6.dp)
                    .background(scheme.primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}

/** Временные ПЗ, ранения и «при смерти». */
@Composable
private fun StatePart(record: CharacterRecord, actions: SheetActions, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        LifeItem(label = "Врем. ПЗ", modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.tempHp.toString(),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                // Временные ПЗ приходят десятками от заклинаний, поэтому тоже с удержанием.
                HoldStepperButton("−", enabled = record.tempHp > 0) { actions.tempHp(-1) }
                Spacer(Modifier.width(PosohDimens.spaceXs))
                HoldStepperButton("+", enabled = true) { actions.tempHp(1) }
            }
        }
        Spacer(Modifier.width(PosohDimens.spaceM))
        LifeItem(label = "Ранения", modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.wounded.toString(),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                // Ранений всего три, удержание тут ни к чему.
                StepperButton("−", enabled = record.wounded > 0) { actions.wounded(-1) }
                Spacer(Modifier.width(PosohDimens.spaceXs))
                StepperButton("+", enabled = record.wounded < WOUNDED_MAX) { actions.wounded(1) }
            }
        }
        Spacer(Modifier.width(PosohDimens.spaceM))
        LifeItem(label = "При смерти", modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = PosohDimens.minTouchTarget),
            ) {
                Checkbox(checked = record.dying, onCheckedChange = actions.dying)
                Text(
                    text = "Да",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (record.dying) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * «Полностью здоров»: одно нажатие возвращает персонажа в порядок после отдыха.
 * Стоит сразу за числами здоровья — то, что она чинит, начинается именно с них.
 */
@Composable
private fun HealButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = PosohDimens.spaceL, vertical = 0.dp),
        modifier = modifier.heightIn(min = PosohDimens.minTouchTarget),
    ) {
        Text("Полностью здоров", style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Подписанный кусочек блока жизни: надпись сверху, содержимое под ней. */
@Composable
private fun LifeItem(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(PosohDimens.spaceXs))
        content()
    }
}

/** Круглая кнопка счётчика. Такая же, как у числа костей на экране «Броски». */
@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceContainer,
            contentColor = scheme.onSurfaceVariant,
        ),
    ) {
        StepperLabel(label)
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
private fun HoldStepperButton(label: String, enabled: Boolean, onStep: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Держим отдельно от состояния Compose: перерисовывать кнопку из-за этого незачем.
    val repeated = remember { booleanArrayOf(false) }

    Card(
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) scheme.surfaceContainer else scheme.surfaceContainerLow,
            contentColor = if (enabled) {
                scheme.onSurfaceVariant
            } else {
                scheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
        ),
        modifier = Modifier
            .size(34.dp)
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
        StepperLabel(label)
    }
}

/** Знак на кнопке счётчика, по центру. */
@Composable
private fun StepperLabel(label: String) {
    Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

/** Карточка со списком бросаемых строк. */
@Composable
private fun RowsCard(title: String, rows: List<RollRow>, modifier: Modifier = Modifier) {
    SheetCard(title = title, modifier = modifier) {
        rows.forEach { RollRowItem(it) }
    }
}

/**
 * Раскладывает плитки рядами по `columns` штук. Последний ряд добивается пустотой,
 * иначе две плитки в ряду из четырёх растянулись бы на всю ширину.
 */
@Composable
private fun <T> TileGrid(
    items: List<T>,
    columns: Int,
    tile: @Composable RowScope.(T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BlockGap)) {
        items.chunked(columns).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(BlockGap)) {
                chunk.forEach { tile(it) }
                repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Данные одной плитки защиты. */
private data class TileData(val label: String, val value: String, val suffix: String? = null)

/** Плитки защиты. Здоровья среди них нет: оно живёт в блоке жизни. */
private fun defenceTiles(sheet: Sheet): List<TileData> = listOf(
    TileData("Класс брони", sheet.armorClass().toString()),
    TileData("Классовая СЛ", sheet.classDifficulty().toString()),
    TileData("СЛ заклинаний", sheet.spellDifficulty().toString()),
    TileData("Скорость", sheet.speed().toString(), suffix = "фт"),
)

/**
 * Две строки на каждое оружие: бросок атаки и бросок урона. Кость у них разная,
 * поэтому это именно две строки, а не одна с двумя числами.
 */
private fun weaponRows(sheet: Sheet, record: CharacterWeaponRecord): List<RollRow> {
    val weapon = record.toWeapon()
    return listOf(
        RollRow(
            title = "Атака ${record.shortName}",
            detail = weapon.name,
            formula = rollFormula(sheet.attackMod(weapon)),
            rank = weapon.rank,
        ),
        RollRow(
            title = "Урон ${record.shortName}",
            detail = weapon.traits,
            formula = sheet.damageFormula(weapon),
            rank = weapon.rank,
        ),
    )
}

/** Три испытания и восприятие: бросаются одинаково, поэтому и показываются вместе. */
private fun saveRows(sheet: Sheet): List<RollRow> {
    val saves = Save.entries.map { save ->
        val rank = sheet.saves[save] ?: Rank.UNTRAINED
        RollRow(
            title = save.title,
            detail = "${save.ability.title} · ${rank.title}",
            formula = rollFormula(sheet.saveMod(save)),
            rank = rank,
        )
    }
    val perception = RollRow(
        title = "Восприятие",
        detail = "${Ability.WIS.title} · ${sheet.perception.title}",
        formula = rollFormula(sheet.perceptionMod()),
        rank = sheet.perception,
    )
    return saves + perception
}

/** Модификатор со знаком: +2, −1, 0. Минус типографский, как в формулах. */
private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${-value}"
    else -> "0"
}

/*
 * Превью для Android Studio. Лист берётся из засева, поэтому видны настоящие числа
 * Сильврина, а не выдуманные: КБ 24, Воля +16, атака молотом 1d20 + 13.
 *
 * Кнопки в превью ничего не делают: правки уходят в базу, а базы в превью нет.
 */

/** Лист Сильврина без базы: тот же засев, только с выданным номером. */
private fun previewCharacter(): CharacterSheet {
    val seed = silvrinSeed()
    return CharacterSheet(
        record = seed.character.copy(id = 1, tempHp = 8, wounded = 1),
        weapons = seed.weapons,
        feats = seed.feats,
        items = seed.items,
        spells = seed.spells,
    )
}

@Preview(name = "Обзор, планшет", widthDp = 1009, heightDp = 622)
@Composable
private fun CharacterOverviewPreview() {
    WizardsStaffTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CharacterOverview(previewCharacter())
        }
    }
}

@Preview(name = "Обзор, узкий экран", widthDp = 512, heightDp = 1100)
@Composable
private fun CharacterOverviewNarrowPreview() {
    WizardsStaffTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CharacterOverview(previewCharacter())
        }
    }
}
