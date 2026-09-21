package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.telnor.wizardsstaff.db.CharacterRecord
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.db.silvrinSeed
import ru.telnor.wizardsstaff.rules.Ability
import ru.telnor.wizardsstaff.rules.HERO_POINTS_MAX
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.rules.Sheet
import ru.telnor.wizardsstaff.rules.WOUNDED_MAX
import ru.telnor.wizardsstaff.rules.XP_PER_LEVEL
import ru.telnor.wizardsstaff.rules.mod
import ru.telnor.wizardsstaff.rules.saveMod
import ru.telnor.wizardsstaff.ui.theme.PosohDimens
import ru.telnor.wizardsstaff.ui.theme.WizardsStaffTheme

/*
 * Вкладка «Обзор» листа персонажа.
 *
 * Порядок блоков: кто он такой, чем бросает, сколько в нём жизни, чем защищён, чем бьёт.
 * Класс брони со щитом идут отдельным блоком сразу за жизнью, см. `ArmorCard.kt`.
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
 * Крупное число листа: уровень, героизм, модификатор, ПЗ, КБ, счётчики состояния.
 * Тем же кеглем набраны знаки на кнопках, которые эти числа правят.
 */
val BigNumber = 26.sp

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
    /** Отдых. Аргумент — беречь ли временные ПЗ: их мог дать эффект, переживший отдых. */
    val heal: (Boolean) -> Unit = {},
    val shieldHp: (Int) -> Unit = {},
    val shieldRaised: (Boolean) -> Unit = {},
    /** Опыт вводят числом целиком, а не прибавкой: это поле ввода, а не счётчик. */
    val xp: (Int) -> Unit = {},
)

@Composable
fun CharacterOverview(
    character: CharacterSheet,
    modifier: Modifier = Modifier,
    actions: SheetActions = SheetActions(),
    onOpenSpells: () -> Unit = {},
) {
    val sheet = character.sheet

    val focus = LocalFocusManager.current

    BoxWithConstraints(modifier) {
        // На планшете в альбомной всё встаёт в ряд; в портретной и на телефоне плитки
        // защиты переносятся, а блок жизни делится надвое.
        val wide = maxWidth >= 900.dp

        Column(
            verticalArrangement = Arrangement.spacedBy(BlockGap),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // Тычок в пустое место листа снимает курсор с поля опыта и убирает
                // клавиатуру. Нажатия по кнопкам и полю сюда не доходят: их забирают
                // сами кнопки, а прокрутка отменяет жест.
                .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
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

            ArmorCard(character, actions)

            SavesRow(character)

            StatsCard(character, actions)

            AttackRows(character, onOpenSpells)
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = scheme.onSurface,
                )
            }
            Spacer(Modifier.width(PosohDimens.spaceM))

            // Выравнивание по левому краю, а не по правому: иначе плитка уровня встала бы
            // вровень с кнопками героизма и уехала вправо от его плитки.
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceS),
            ) {
                // Набран опыт на новый уровень — плитка загорается золотым и рядом
                // с числом встаёт «+». Сам уровень не растёт: почему, написано
                // в начале `StatsCard.kt`.
                val levelUp = record.xp >= XP_PER_LEVEL
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CornerTile(
                        label = "Уровень",
                        value = record.level.toString(),
                        background = if (levelUp) scheme.tertiaryContainer else scheme.surfaceVariant,
                        content = if (levelUp) scheme.onTertiaryContainer else scheme.onSurface,
                    )
                    if (levelUp) {
                        Spacer(Modifier.width(PosohDimens.spaceS))
                        LevelUpButton()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CornerTile(
                        label = "Героизм",
                        value = record.heroPoints.toString(),
                        background = scheme.tertiaryContainer,
                        content = scheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.width(PosohDimens.spaceS))
                    Column(verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceXs)) {
                        TapStepperButton(
                            label = "+",
                            enabled = record.heroPoints < HERO_POINTS_MAX,
                            fontSize = BigNumber,
                            onClick = { actions.heroPoints(1) },
                        )
                        TapStepperButton(
                            label = "−",
                            enabled = record.heroPoints > 0,
                            fontSize = BigNumber,
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
            Text(text = value, style = MaterialTheme.typography.displaySmall.copy(fontSize = BigNumber))
        }
    }
}

/**
 * Кнопка повышения уровня. Появляется рядом с уровнем, когда набрана тысяча опыта.
 *
 * Нажатие пока ничего не делает: страницы повышения ещё нет. Когда появится —
 * подключится сюда одной строкой.
 */
@Composable
private fun LevelUpButton() {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
        ),
        border = BorderStroke(TileBorder, scheme.tertiary),
        modifier = Modifier.size(StepperSize),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "+",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = BigNumber),
            )
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
        // Такая же рамка, как у испытаний: по плитке характеристики тоже можно будет
        // нажать — чтобы бросить чистую проверку этой характеристики.
        border = BorderStroke(
            width = TileBorder,
            color = if (key) scheme.primary else scheme.outline,
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
                color = if (key) scheme.onPrimaryContainer else scheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = signed(sheet.mod(ability)),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = BigNumber),
                maxLines = 1,
            )
            Text(
                // Ключевая подписана: по ней считаются классовая СЛ и магия.
                text = if (key) "$score · ключ." else score.toString(),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (key) scheme.onPrimaryContainer else scheme.onSurface,
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
        // По центру своего раздела, как и остальные надписи листа.
        Text(
            text = "ЗДОРОВЬЕ",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(PosohDimens.spaceXs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = record.currentHp.toString(),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = BigNumber),
                maxLines = 1,
            )
            Spacer(Modifier.width(PosohDimens.spaceXs))
            Text(
                text = "/ ${record.maxHp}",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = scheme.onSurface,
            )
            Spacer(Modifier.width(PosohDimens.spaceM))
            HealButton(actions.heal)
            Spacer(Modifier.weight(1f))
            HoldStepperButton("−", enabled = record.currentHp > 0, fontSize = BigNumber) {
                actions.hp(-1)
            }
            Spacer(Modifier.width(PosohDimens.spaceS))
            HoldStepperButton(
                label = "+",
                enabled = record.currentHp < record.maxHp,
                fontSize = BigNumber,
            ) { actions.hp(1) }
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

/**
 * Ширина, отведённая числу счётчика. Считана по трёхзначному значению: временные ПЗ
 * от заклинания бывают и за сотню, а прыгающие туда-сюда кнопки посреди боя хуже,
 * чем немного пустого места слева от нуля.
 */
private val CounterValueWidth = 44.dp

/**
 * Высота строки со значением. Одна на все три части, чтобы числа и галочка стояли
 * на одной линии: у галочки своя высота под палец, у счётчиков — своя, и без общей
 * высоты они разъехались бы по вертикали.
 */
private val StateRowHeight = PosohDimens.minTouchTarget

/**
 * Временные ПЗ, ранения и «при смерти». Три части одного блока: надписи в одну линию,
 * значения под ними тоже в одну.
 *
 * Разделителей между ними нет — по решению автора: в блоке брони они отделяют
 * разные сущности, а здесь всё это одно состояние в бою.
 */
@Composable
private fun StatePart(record: CharacterRecord, actions: SheetActions, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.Top, modifier = modifier) {
        LabeledPart(label = "Временные ПЗ", modifier = Modifier.weight(1f)) {
            Counter(
                value = record.tempHp,
                minus = record.tempHp > 0,
                // Временные ПЗ приходят десятками от заклинаний, поэтому с удержанием.
                plus = true,
                hold = true,
                onStep = actions.tempHp,
            )
        }
        LabeledPart(label = "Ранения", modifier = Modifier.weight(1f)) {
            Counter(
                value = record.wounded,
                minus = record.wounded > 0,
                plus = record.wounded < WOUNDED_MAX,
                // Ранений всего три, удержание тут ни к чему.
                hold = false,
                onStep = actions.wounded,
            )
        }
        LabeledPart(label = "При смерти", modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(StateRowHeight),
            ) {
                Checkbox(checked = record.dying, onCheckedChange = actions.dying)
                Text(
                    text = "Да",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
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

/** Число и пара кнопок к нему, по центру своей части. */
@Composable
private fun Counter(
    value: Int,
    minus: Boolean,
    plus: Boolean,
    hold: Boolean,
    onStep: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(StateRowHeight),
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = BigNumber),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(CounterValueWidth),
        )
        Spacer(Modifier.width(PosohDimens.spaceS))
        if (hold) {
            HoldStepperButton("−", enabled = minus, fontSize = BigNumber) { onStep(-1) }
            Spacer(Modifier.width(PosohDimens.spaceXs))
            HoldStepperButton("+", enabled = plus, fontSize = BigNumber) { onStep(1) }
        } else {
            TapStepperButton("−", enabled = minus, fontSize = BigNumber) { onStep(-1) }
            Spacer(Modifier.width(PosohDimens.spaceXs))
            TapStepperButton("+", enabled = plus, fontSize = BigNumber) { onStep(1) }
        }
    }
}

/**
 * «Полностью здоров»: возвращает персонажа в порядок после отдыха. Стоит сразу
 * за числами здоровья — то, что она чинит, начинается именно с них.
 *
 * Спрашивает подтверждение: нажать её посреди боя случайно — значит стереть всё
 * состояние разом, а отменить это нечем.
 */
@Composable
private fun HealButton(onHeal: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var ask by rememberSaveable { mutableStateOf(false) }

    OutlinedButton(
        onClick = { ask = true },
        shape = RoundedCornerShape(22.dp),
        // Цвет и рамка по правилам листа: текст чёрный, рамка как у плиток-кнопок.
        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
        border = BorderStroke(TileBorder, scheme.outline),
        contentPadding = PaddingValues(horizontal = PosohDimens.spaceL, vertical = 0.dp),
        modifier = modifier.heightIn(min = PosohDimens.minTouchTarget),
    ) {
        Text(
            text = "Полностью здоров",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            maxLines = 1,
        )
    }

    if (ask) {
        HealDialog(
            onDismiss = { ask = false },
            onConfirm = { keepTempHp ->
                ask = false
                onHeal(keepTempHp)
            },
        )
    }
}

/**
 * Подтверждение отдыха. Галочка про временные ПЗ снята по умолчанию: обычно они
 * уходят вместе со всем остальным, а вот заклинание с долгим сроком — случай редкий,
 * и о нём человек вспомнит сам.
 */
@Composable
private fun HealDialog(onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var keepTempHp by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Полностью здоров") },
        text = {
            Column {
                Text(
                    text = "Излечить персонажа полностью и сбросить его временные ПЗ?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(PosohDimens.spaceL))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.toggleable(
                        value = keepTempHp,
                        role = Role.Checkbox,
                    ) { keepTempHp = it },
                ) {
                    Checkbox(checked = keepTempHp, onCheckedChange = null)
                    Spacer(Modifier.width(PosohDimens.spaceS))
                    Text(
                        text = "Не сбрасывать временные ПЗ",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(keepTempHp) }) { Text("Да") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Нет") } },
    )
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
