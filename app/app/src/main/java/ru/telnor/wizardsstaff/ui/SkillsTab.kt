package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.rules.Ability
import ru.telnor.wizardsstaff.rules.ArmorCategory
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.rules.Save
import ru.telnor.wizardsstaff.rules.Skill
import ru.telnor.wizardsstaff.rules.SkillModParts
import ru.telnor.wizardsstaff.rules.classDifficulty
import ru.telnor.wizardsstaff.rules.mod
import ru.telnor.wizardsstaff.rules.perceptionMod
import ru.telnor.wizardsstaff.rules.saveMod
import ru.telnor.wizardsstaff.rules.skillModParts
import ru.telnor.wizardsstaff.rules.spellAttackMod
import ru.telnor.wizardsstaff.rules.spellDifficulty
import ru.telnor.wizardsstaff.ui.theme.PosohDimens

/*
 * Вкладка «Навыки»: восемнадцать навыков по алфавиту, а под ними все остальные
 * владения персонажа — испытания, восприятие, сложности, доспехи и оружие.
 *
 * Здесь собрано всё, что описывается степенью владения. Раньше эти степени были
 * разбросаны: владение бронёй показывалось в блоке брони, испытаний — в плитках,
 * а править их было негде вовсе.
 *
 * Правка включается переключателем, а не живёт всегда. Причины две: за столом легко
 * сбить владение случайным касанием, а когда строка станет кнопкой броска (шаг 4),
 * внутри неё оказалась бы вторая кнопка, и палец попадал бы не туда.
 *
 * Ни одной новой таблицы и ни одного нового столбца: всё, что тут правится, уже лежит
 * в строке персонажа. Значит сохранение подхватывает это само — см. `CharacterParts.kt`.
 */

/** Что вкладка умеет менять. Карту меняет модель, сюда приходит «что и на что». */
data class SkillActions(
    val skillRank: (Skill, Rank) -> Unit = { _, _ -> },
    val skillItem: (Skill, Int) -> Unit = { _, _ -> },
    val armorRank: (ArmorCategory, Rank) -> Unit = { _, _ -> },
    val saveRank: (Save, Rank) -> Unit = { _, _ -> },
    val saveItem: (Save, Int) -> Unit = { _, _ -> },
    val perceptionRank: (Rank) -> Unit = {},
    val perceptionItem: (Int) -> Unit = {},
    val classDc: (Rank) -> Unit = {},
    val spellAttack: (Rank) -> Unit = {},
    val spellDc: (Rank) -> Unit = {},
    val weaponRank: (Int, Rank) -> Unit = { _, _ -> },
    val lore: (Int, String) -> Unit = { _, _ -> },
)

@Composable
fun SkillsTab(
    character: CharacterSheet,
    modifier: Modifier = Modifier,
    actions: SkillActions = SkillActions(),
) {
    val sheet = character.sheet
    val record = character.record
    // rememberSaveable: поворот планшета не должен выбрасывать из режима правки.
    var editing by rememberSaveable { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceS),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PosohDimens.screenPadding, vertical = PosohDimens.spaceM),
    ) {
        EditSwitch(editing) { editing = it }
        RankLegend()

        SectionTitle("Навыки")
        Skill.entries.forEach { skill ->
            SkillRow(
                title = record.skillTitle(skill),
                parts = sheet.skillModParts(skill),
                ability = skill.ability.short,
                rank = sheet.skills[skill] ?: Rank.UNTRAINED,
                item = sheet.skillItems[skill] ?: 0,
                editing = editing,
                onRank = { actions.skillRank(skill, it) },
                onItem = { actions.skillItem(skill, it) },
                lore = when (skill) {
                    Skill.LORE_1 -> 1 to record.lore1.orEmpty()
                    Skill.LORE_2 -> 2 to record.lore2.orEmpty()
                    else -> null
                },
                onLore = actions.lore,
            )
        }

        SectionTitle("Прочие владения")
        Save.entries.forEach { save ->
            ProficiencyRow(
                title = save.title,
                value = sheet.saveMod(save),
                ability = save.ability.short,
                rank = sheet.saves[save] ?: Rank.UNTRAINED,
                item = sheet.saveItems[save] ?: 0,
                editing = editing,
                onRank = { actions.saveRank(save, it) },
                onItem = { actions.saveItem(save, it) },
            )
        }
        ProficiencyRow(
            title = "Восприятие",
            value = sheet.perceptionMod(),
            ability = Ability.WIS.short,
            rank = sheet.perception,
            item = sheet.perceptionItem,
            editing = editing,
            onRank = { actions.perceptionRank(it) },
            onItem = { actions.perceptionItem(it) },
        )
        // У сложностей бонуса предмета нет: в лист его вписывать некуда, и правила
        // такой прибавки не дают.
        ProficiencyRow(
            title = "Классовая СЛ",
            value = sheet.classDifficulty(),
            ability = sheet.keyAbility.short,
            rank = sheet.classDc,
            item = null,
            editing = editing,
            onRank = { actions.classDc(it) },
        )
        ProficiencyRow(
            title = "Атака заклинанием",
            value = sheet.spellAttackMod(),
            ability = sheet.keyAbility.short,
            rank = sheet.spellAttack,
            item = null,
            editing = editing,
            onRank = { actions.spellAttack(it) },
        )
        ProficiencyRow(
            title = "СЛ заклинаний",
            value = sheet.spellDifficulty(),
            ability = sheet.keyAbility.short,
            rank = sheet.spellDc,
            item = null,
            editing = editing,
            onRank = { actions.spellDc(it) },
        )

        SectionTitle("Владение доспехами")
        ArmorCategory.entries.forEach { category ->
            PlainRankRow(
                title = category.title.replaceFirstChar { it.uppercase() },
                rank = sheet.armorRanks[category] ?: Rank.UNTRAINED,
                editing = editing,
                onRank = { actions.armorRank(category, it) },
            )
        }

        SectionTitle("Владение оружием")
        if (sheet.weaponTraining.isEmpty()) {
            Text(
                text = "В бланке этот блок не заполнен.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        sheet.weaponTraining.forEachIndexed { index, training ->
            PlainRankRow(
                title = training.what,
                rank = training.rank,
                editing = editing,
                onRank = { actions.weaponRank(index, it) },
            )
        }
    }
}

/** Переключатель режима правки. Выключен — вкладка только показывает. */
@Composable
private fun EditSwitch(editing: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = editing, onCheckedChange = onChange)
        Spacer(Modifier.width(PosohDimens.spaceS))
        Text(
            text = if (editing) "Правка включена" else "Править",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            fontWeight = if (editing) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Что означают квадратики и сколько даёт каждая степень. */
@Composable
private fun RankLegend() {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Одной строкой с вставленными в неё значками: так она переносится по словам,
        // а не обрезается. Пять отдельных колонок в узкую карточку не встают.
        Text(
            text = legendText(),
            inlineContent = legendBadges(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 22.sp),
            color = scheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PosohDimens.spaceM, vertical = PosohDimens.spaceS),
        )
    }
}

/**
 * Неразрывный пробел. Внутри блока легенды стоят только такие: обычный пробел —
 * это место, где строка может перенестись, а «[М] - мастерский, ур.» с уехавшим
 * на следующую строку «+6» читается как обрывок.
 */
private const val NB = "\u00A0"

/** «0» у нетренированного, «ур. +2» у изученного и так далее. */
private fun Rank.legend(): String = if (this == Rank.UNTRAINED) "0" else "ур.$NB+${bonus(0)}"

/** Место значка в строке легенды. */
private val LegendSlot = 21.sp

/**
 * «[значок] - изученный, ур. +2; …». Значки вставлены местами, которые заполнит `Text`.
 *
 * Единственный обычный пробел — тот, что стоит между блоками: только там строке
 * и разрешено переноситься.
 */
private fun legendText() = buildAnnotatedString {
    Rank.entries.forEachIndexed { index, rank ->
        if (index > 0) append(" ")
        appendInlineContent(rank.name, "[]")
        append("$NB-$NB${rank.title},$NB${rank.legend()}")
        if (index < Rank.entries.lastIndex) append(";")
    }
}

@Composable
private fun legendBadges(): Map<String, InlineTextContent> {
    val slot = Placeholder(LegendSlot, LegendSlot, PlaceholderVerticalAlign.Center)
    return Rank.entries.associate { rank ->
        rank.name to InlineTextContent(slot) { RankBadge(rank, size = 18.dp) }
    }
}

/**
 * Строка навыка: название, под ним разбор формулы, справа характеристика, владение,
 * бонус предмета и сам модификатор.
 *
 * `lore` не null у двух «Знаний»: в режиме правки название превращается в поле,
 * куда вписывают уточнение.
 */
@Composable
private fun SkillRow(
    title: String,
    parts: SkillModParts,
    ability: String,
    rank: Rank,
    item: Int,
    editing: Boolean,
    onRank: (Rank) -> Unit,
    onItem: (Int) -> Unit,
    lore: Pair<Int, String>? = null,
    onLore: (Int, String) -> Unit = { _, _ -> },
) {
    RowCard {
        Column(Modifier.weight(1f)) {
            if (editing && lore != null) {
                OutlinedTextField(
                    value = lore.second,
                    onValueChange = { onLore(lore.first, it) },
                    label = { Text("Знание о чём") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Text(
                text = breakdown(parts),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(PosohDimens.spaceS))
        RankCell(ability, rank, editing, onRank)
        Spacer(Modifier.width(PosohDimens.spaceM))
        ItemCell(item, editing, onItem)
        Spacer(Modifier.width(PosohDimens.spaceM))
        Total(parts.total)
    }
}

/**
 * Строка прочего владения: то же самое, но без разбора формулы — у испытаний
 * и сложностей он всегда одинаковый и ничего не объясняет.
 */
@Composable
private fun ProficiencyRow(
    title: String,
    value: Int,
    ability: String,
    rank: Rank,
    item: Int?,
    editing: Boolean,
    onRank: (Rank) -> Unit,
    onItem: (Int) -> Unit = {},
) {
    RowCard {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        RankCell(ability, rank, editing, onRank)
        Spacer(Modifier.width(PosohDimens.spaceM))
        if (item != null) ItemCell(item, editing, onItem) else ItemCell(null, false) {}
        Spacer(Modifier.width(PosohDimens.spaceM))
        Total(value)
    }
}

/** Строка владения без числа: доспехи и оружие — ими не бросают. */
@Composable
private fun PlainRankRow(title: String, rank: Rank, editing: Boolean, onRank: (Rank) -> Unit) {
    RowCard {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = rank.title,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(PosohDimens.spaceS))
        RankPicker(rank, editing, onRank)
    }
}

/** Общая оболочка строки: рамка кнопки, потому что строка ею станет в шаге 4. */
@Composable
private fun RowCard(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(TileBorder, scheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PosohDimens.spaceM, vertical = PosohDimens.spaceS),
            content = content,
        )
    }
}

/** Характеристика и квадратик владения. */
@Composable
private fun RankCell(ability: String, rank: Rank, editing: Boolean, onRank: (Rank) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = ability,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(PosohDimens.spaceXs))
        RankPicker(rank, editing, onRank)
    }
}

/**
 * Квадратик владения. В режиме правки нажимается и открывает выбор из пяти степеней;
 * вне режима это просто значок.
 */
@Composable
private fun RankPicker(rank: Rank, editing: Boolean, onRank: (Rank) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        RankBadge(
            rank = rank,
            modifier = if (editing) Modifier.clickable { open = true } else Modifier,
            size = if (editing) 26.dp else 18.dp,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Rank.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.badge}  ${option.title}") },
                    onClick = {
                        open = false
                        onRank(option)
                    },
                )
            }
        }
    }
}

/** Бонус предмета. В режиме правки — с кнопками, иначе просто число. */
@Composable
private fun ItemCell(item: Int?, editing: Boolean, onItem: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (item == null) "" else "предм",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = scheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(PosohDimens.spaceXs))
        if (editing && item != null) {
            TapStepperButton("−", enabled = item > 0, size = 26.dp, fontSize = 15.sp) {
                onItem(item - 1)
            }
            Spacer(Modifier.width(PosohDimens.spaceXs))
        }
        Text(
            text = item?.let { signed(it) } ?: "",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 15.sp),
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(26.dp),
        )
        if (editing && item != null) {
            Spacer(Modifier.width(PosohDimens.spaceXs))
            TapStepperButton("+", enabled = true, size = 26.dp, fontSize = 15.sp) {
                onItem(item + 1)
            }
        }
    }
}

/** Итоговое число строки. */
@Composable
private fun Total(value: Int) {
    Text(
        text = signed(value),
        style = MaterialTheme.typography.displaySmall.copy(fontSize = BigNumber),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(56.dp),
    )
}

/** Разбор модификатора словами: «1 ловкость + 10 владение». Нулевые не пишем. */
private fun breakdown(parts: SkillModParts): String = buildString {
    append("${parts.ability} характеристика")
    if (parts.proficiency != 0) append(" + ${parts.proficiency} владение")
    if (parts.item != 0) append(" + ${parts.item} предмет")
    // Штраф брони показывается только когда он есть: столбец с нулями у всех
    // восемнадцати навыков был бы шумом, а появившийся штраф проглядеть нельзя.
    if (parts.armor != 0) append(" − ${-parts.armor} броня")
}
