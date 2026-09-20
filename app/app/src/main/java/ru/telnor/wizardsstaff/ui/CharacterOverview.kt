package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Характеристики и здоровье подняты наверх намеренно: за столом смотрят чаще всего туда,
 * а здоровье ещё и правят по нескольку раз за бой.
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
        // защиты переносятся, а строка жизни делится надвое.
        val wide = maxWidth >= 900.dp
        val defenceColumns = if (wide) 4 else 2

        Column(
            verticalArrangement = Arrangement.spacedBy(BlockGap),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PosohDimens.screenPadding, vertical = PosohDimens.spaceM),
        ) {
            CharacterHeader(character, actions, wide)

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

            HealthRow(character, actions, wide)

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
 * Шапка: аватар, имя, кто он такой, уровень и героизм.
 *
 * На узком экране плитки уезжают под имя. В одну строку они там тоже влезают, но имени
 * остаётся полторы сотни точек, и «Сильврин Хвостозвон» разваливается на три строки.
 */
@Composable
private fun CharacterHeader(character: CharacterSheet, actions: SheetActions, wide: Boolean) {
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
        // Имя с аватаром и блок плиток: в одну строку на широком экране, в две на узком.
        val who: @Composable RowScope.() -> Unit = {
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
        }
        val counters: @Composable RowScope.() -> Unit = {
            CornerTile(
                label = "Уровень",
                value = record.level.toString(),
                background = scheme.surfaceVariant,
                content = scheme.onSurface,
            )
            Spacer(Modifier.width(BlockGap))

            // Героизм тратится и возвращается по нескольку раз за игру, поэтому правится
            // прямо здесь, а не в редакторе листа, которого ещё и нет.
            StepperButton("−", enabled = record.heroPoints > 0) { actions.heroPoints(-1) }
            Spacer(Modifier.width(PosohDimens.spaceS))
            CornerTile(
                label = "Героизм",
                value = record.heroPoints.toString(),
                background = scheme.tertiaryContainer,
                content = scheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(PosohDimens.spaceS))
            StepperButton("+", enabled = record.heroPoints < HERO_POINTS_MAX) {
                actions.heroPoints(1)
            }
        }

        Column(Modifier.padding(horizontal = 18.dp, vertical = PosohDimens.spaceM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                who()
                if (wide) {
                    Spacer(Modifier.width(PosohDimens.spaceM))
                    counters()
                }
            }
            if (!wide) {
                Spacer(Modifier.height(PosohDimens.spaceM))
                Row(verticalAlignment = Alignment.CenterVertically) { counters() }
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
        modifier = Modifier.width(92.dp).height(68.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().height(68.dp),
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
            Text(text = value, style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp))
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
 * Строка жизни: ПЗ со шкалой, временные ПЗ, ранения и «при смерти».
 *
 * Всё, что меняется в бою, собрано в одну строку и правится кнопками: лезть за этим
 * в редактор листа посреди хода — последнее, чего хочется за столом.
 */
@Composable
private fun HealthRow(character: CharacterSheet, actions: SheetActions, wide: Boolean) {
    val record = character.record

    val states: @Composable RowScope.() -> Unit = {
        CounterTile(
            label = "Врем. ПЗ",
            value = record.tempHp,
            canSubtract = record.tempHp > 0,
            canAdd = true,
            onChange = actions.tempHp,
            modifier = Modifier.weight(1f),
        )
        CounterTile(
            label = "Ранения",
            value = record.wounded,
            canSubtract = record.wounded > 0,
            canAdd = record.wounded < WOUNDED_MAX,
            onChange = actions.wounded,
            modifier = Modifier.weight(1f),
        )
        DyingTile(
            dying = record.dying,
            onChange = actions.dying,
            modifier = Modifier.weight(1f),
        )
    }

    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(BlockGap)) {
            HealthTile(record.currentHp, record.maxHp, actions.hp, Modifier.weight(2f))
            states()
        }
    } else {
        HealthTile(record.currentHp, record.maxHp, actions.hp, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(BlockGap)) { states() }
    }
}

/** Здоровье: текущие ПЗ из максимума, шкала и кнопки правки. */
@Composable
private fun HealthTile(current: Int, max: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = PosohDimens.spaceM, vertical = 10.dp)) {
            Text(
                text = "ЗДОРОВЬЕ",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(PosohDimens.spaceXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = current.toString(),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp),
                    maxLines = 1,
                )
                Spacer(Modifier.width(PosohDimens.spaceXs))
                Text(
                    text = "/ $max",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                StepperButton("−", enabled = current > 0) { onChange(-1) }
                Spacer(Modifier.width(PosohDimens.spaceS))
                StepperButton("+", enabled = current < max) { onChange(1) }
            }
            Spacer(Modifier.height(PosohDimens.spaceS))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(scheme.surfaceContainerHigh, RoundedCornerShape(3.dp)),
            ) {
                val fill = if (max > 0) (current.toFloat() / max).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier
                        .fillMaxWidth(fill)
                        .height(5.dp)
                        .background(scheme.primary, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

/** Плитка-счётчик: временные ПЗ и ранения. */
@Composable
private fun CounterTile(
    label: String,
    value: Int,
    canSubtract: Boolean,
    canAdd: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = PosohDimens.spaceM, vertical = 10.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(PosohDimens.spaceXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                StepperButton("−", enabled = canSubtract) { onChange(-1) }
                Spacer(Modifier.width(PosohDimens.spaceS))
                StepperButton("+", enabled = canAdd) { onChange(1) }
            }
        }
    }
}

/** «При смерти» — галочка. Отмеченная красит плитку: это состояние должно быть видно сразу. */
@Composable
private fun DyingTile(dying: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (dying) scheme.errorContainer else scheme.surface,
            contentColor = if (dying) scheme.onErrorContainer else scheme.onSurface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (dying) scheme.error.copy(alpha = 0.5f) else scheme.outlineVariant,
        ),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = PosohDimens.spaceM, vertical = 10.dp)) {
            Text(
                text = "ПРИ СМЕРТИ",
                style = MaterialTheme.typography.labelSmall,
                color = if (dying) scheme.onErrorContainer else scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Checkbox(checked = dying, onCheckedChange = onChange)
                Text(
                    text = if (dying) "да" else "нет",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Круглая кнопка счётчика. Такая же, как у числа костей на экране «Броски». */
@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceContainer,
            contentColor = scheme.onSurfaceVariant,
        ),
    ) {
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
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

/** Плитки защиты. Здоровья среди них больше нет: оно переехало в строку жизни. */
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
