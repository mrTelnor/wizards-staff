package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.db.CharacterWeaponRecord
import ru.telnor.wizardsstaff.db.silvrinSeed
import ru.telnor.wizardsstaff.rules.Ability
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.rules.Save
import ru.telnor.wizardsstaff.rules.Sheet
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
 * Вкладка «Обзор» листа персонажа: шапка, защита, характеристики, атаки и испытания.
 * Макет — docs/design/png/Character.png, состав — SCREENS.md, раздел 3.
 *
 * Ни одно число здесь не хранится: всё, что показано, считает `Pf2.kt` по листу из базы.
 */

/** Зазор между блоками листа. В макете 10, это между шагами шкалы отступов. */
private val BlockGap = 10.dp

@Composable
fun CharacterOverview(character: CharacterSheet, modifier: Modifier = Modifier) {
    val sheet = character.sheet

    BoxWithConstraints(modifier) {
        // На планшете в альбомной все плитки встают в ряд, в портретной и на телефоне —
        // по три и по две, иначе число в плитке пришлось бы сжимать до нечитаемого.
        val wide = maxWidth >= 900.dp
        val defenceColumns = if (wide) 5 else if (maxWidth >= 600.dp) 3 else 2
        val abilityColumns = if (wide) 6 else if (maxWidth >= 600.dp) 3 else 2

        Column(
            verticalArrangement = Arrangement.spacedBy(BlockGap),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PosohDimens.screenPadding, vertical = PosohDimens.spaceM),
        ) {
            CharacterHeader(character)

            TileGrid(defenceTiles(character, sheet), defenceColumns) { tile ->
                SheetTile(
                    label = tile.label,
                    value = tile.value,
                    suffix = tile.suffix,
                    fill = tile.fill,
                    modifier = Modifier.weight(1f),
                )
            }

            TileGrid(Ability.entries, abilityColumns) { ability ->
                AbilityTile(
                    ability = ability,
                    sheet = sheet,
                    key = ability == sheet.keyAbility,
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

/** Шапка: аватар, имя, кто он такой, уровень и героизм. */
@Composable
private fun CharacterHeader(character: CharacterSheet) {
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
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
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
            CornerTile("Уровень", record.level.toString(), scheme.surfaceVariant, scheme.onSurface)
            Spacer(Modifier.width(BlockGap))
            CornerTile(
                label = "Героизм",
                value = record.heroPoints.toString(),
                background = scheme.tertiaryContainer,
                content = scheme.onTertiaryContainer,
            )
        }
    }
}

/** Маленькая плитка в шапке: уровень и героизм. */
@Composable
private fun CornerTile(
    label: String,
    value: String,
    background: Color,
    content: Color,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = background, contentColor = content),
        modifier = Modifier.width(68.dp).height(52.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp),
            )
        }
    }
}

/** Плитка характеристики: модификатор крупно, само значение под ним. */
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
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = ability.title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (key) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = signed(sheet.mod(ability)),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 22.sp),
            )
            Text(
                // Ключевая характеристика подписана: по ней считаются классовая СЛ и магия.
                text = if (key) "$score · ключевая" else score.toString(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = if (key) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                maxLines = 1,
            )
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
 * иначе три плитки в ряду из пяти растянулись бы на всю ширину и стали бы шире соседних.
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
private data class TileData(
    val label: String,
    val value: String,
    val suffix: String? = null,
    val fill: Float? = null,
)

/** Пять плиток защиты. Все числа, кроме здоровья, считает движок. */
private fun defenceTiles(character: CharacterSheet, sheet: Sheet): List<TileData> {
    val record = character.record
    return listOf(
        TileData("Класс брони", sheet.armorClass().toString()),
        TileData(
            label = "Здоровье",
            value = record.currentHp.toString(),
            suffix = "/ ${record.maxHp}",
            fill = if (record.maxHp > 0) record.currentHp.toFloat() / record.maxHp else 0f,
        ),
        TileData("Классовая СЛ", sheet.classDifficulty().toString()),
        TileData("СЛ заклинаний", sheet.spellDifficulty().toString()),
        TileData("Скорость", sheet.speed().toString(), suffix = "фт"),
    )
}

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
 * Превью для Android Studio. Лист берётся из засева, поэтому в макете видны настоящие
 * числа Сильврина, а не выдуманные: КБ 24, Воля +16, атака молотом 1d20 + 13.
 *
 * Узкое превью не для телефона (его артбордов пока нет), а чтобы видеть, что плитки
 * переносятся, а колонки складываются в одну, когда места мало.
 */

/** Лист Сильврина без базы: тот же засев, только с выданным номером. */
private fun previewCharacter(): CharacterSheet {
    val seed = silvrinSeed()
    return CharacterSheet(
        record = seed.character.copy(id = 1),
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

@Preview(name = "Обзор, узкий экран", widthDp = 400, heightDp = 900)
@Composable
private fun CharacterOverviewNarrowPreview() {
    WizardsStaffTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CharacterOverview(previewCharacter())
        }
    }
}
