package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.rules.DamageType
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.rules.attackMod
import ru.telnor.wizardsstaff.rules.damageFormula
import ru.telnor.wizardsstaff.rules.rollFormula
import ru.telnor.wizardsstaff.rules.spellAttackMod
import ru.telnor.wizardsstaff.ui.theme.PosohDimens

/*
 * Атаки: на каждое оружие своя строка из двух кнопок — слева бросок атаки,
 * справа бросок урона.
 *
 * Две кнопки, а не одна строка с двумя числами: кости у них разные (атака всегда d20,
 * урон — что у оружия), и взводить посох они будут порознь.
 *
 * Кнопки делят строку поровну, с тем же зазором, что у испытаний и характеристик.
 * Нажатия пока нет: оно придёт в шаге 4 задачи D2.
 *
 * Владение показано только у атаки: бросок урона от него не зависит вовсе, и второй
 * такой же квадратик рядом сообщал бы неправду.
 *
 * Последняя строка — магическая атака. Пары у неё нет: урон у каждого заклинания свой,
 * и в лист его не впишешь. Поэтому правая кнопка не бросает, а ведёт на вкладку
 * «Магия», где заклинания и лежат.
 */

/**
 * Высота кнопки: под две строки названия и две строки пояснения. Названия оружия
 * длинные, в одну строку они не встают, а обрезанное название за столом бесполезно.
 */
private val ButtonHeight = 80.dp

/**
 * Ширина колонки с текстом. Задана числом, а не содержимым: так формулы всех кнопок
 * стоят на одном месте, а квадратики владения — в столбец, как в блоке испытаний.
 */
private val TextColumnWidth = 110.dp

/**
 * Размер значка в строке названия и место, которое он занимает в тексте.
 * Значки вставлены прямо в строку, поэтому переносятся вместе со словами.
 */
private val BadgeSize = 18.dp
private val BadgeSlot = 21.sp



@Composable
fun AttackRows(
    character: CharacterSheet,
    onOpenSpells: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheet = character.sheet
    val record = character.record
    Column(
        verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceS),
        modifier = modifier.fillMaxWidth(),
    ) {
        character.weapons.forEach { entry ->
            val weapon = entry.toWeapon()
            Row(
                horizontalArrangement = Arrangement.spacedBy(PosohDimens.spaceS),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                RollButton(
                    title = "Атака ${entry.shortName}",
                    formula = rollFormula(sheet.attackMod(weapon)),
                    detail = weapon.name,
                    rank = weapon.rank,
                    damage = weapon.damageType,
                    modifier = Modifier.weight(1f),
                )
                RollButton(
                    title = "Урон ${entry.shortName}",
                    formula = sheet.damageFormula(weapon),
                    // Короткие свойства лежат в базе своим столбцом; пусто —
                    // показываем полные, как у оружия, добавленного до версии 8.
                    detail = entry.shortTraits.ifBlank { weapon.traits },
                    rank = null,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Строка магии только у тех, кто колдует. У нетренированного в магической
        // атаке её показывать нечего: он ею не бросает вовсе.
        if (sheet.spellAttack != Rank.UNTRAINED) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PosohDimens.spaceS),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                RollButton(
                    title = "Атака заклинанием",
                    formula = rollFormula(sheet.spellAttackMod()),
                    detail = record.tradition,
                    rank = sheet.spellAttack,
                    modifier = Modifier.weight(1f),
                )
                RollButton(
                    title = "Урон заклинания",
                    // Стрелка вместо формулы: эта кнопка не бросает, а уводит туда,
                    // где у каждого заклинания свои кости.
                    formula = "→",
                    detail = "смотри в «Магии»",
                    rank = null,
                    onClick = onOpenSpells,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Кнопка одного броска: слева название и пояснение, справа формула.
 *
 * Колонка с текстом фиксированной ширины, формула сразу за ней: так между текстом
 * и числом остаётся один и тот же небольшой зазор, а сами формулы во всех кнопках
 * стоят на одном месте.
 */
@Composable
private fun RollButton(
    title: String,
    formula: String,
    detail: String,
    rank: Rank?,
    modifier: Modifier = Modifier,
    damage: DamageType? = null,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val colors = CardDefaults.cardColors(containerColor = scheme.surface)
    val border = BorderStroke(TileBorder, scheme.outline)
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PosohDimens.spaceM, vertical = PosohDimens.spaceS),
        ) {
            Column(Modifier.width(TextColumnWidth)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Значки идут сразу за названием оружия, а не колонкой справа:
                // они относятся к оружию, а не к самому броску, и вставлены прямо
                // в строку — тогда у длинного имени они переносятся вместе с ним.
                Text(
                    text = detailWithBadges(detail, rank, damage),
                    inlineContent = badgeContent(rank, damage),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(PosohDimens.spaceXs))
            Text(
                text = formula,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = BigNumber),
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }

    // Нажатие есть пока только у кнопки, ведущей в «Магию». Остальные получат его
    // в шаге 4 задачи D2, когда начнут взводить посох.
    val box = modifier.fillMaxHeight().heightIn(min = ButtonHeight)
    if (onClick == null) {
        Card(shape = shape, colors = colors, border = border, modifier = box) { content() }
    } else {
        Card(onClick = onClick, shape = shape, colors = colors, border = border, modifier = box) {
            content()
        }
    }
}

/** Название со значками в конце строки. */
private fun detailWithBadges(detail: String, rank: Rank?, damage: DamageType?) =
    buildAnnotatedString {
        append(detail)
        if (rank != null) {
            append(" ")
            appendInlineContent(RANK_SLOT, "[]")
        }
        if (damage != null) {
            append(" ")
            appendInlineContent(DAMAGE_SLOT, "[]")
        }
    }

private const val RANK_SLOT = "rank"
private const val DAMAGE_SLOT = "damage"

/** Чем заполняются места, оставленные значкам в строке. */
@Composable
private fun badgeContent(rank: Rank?, damage: DamageType?): Map<String, InlineTextContent> {
    val slot = Placeholder(BadgeSlot, BadgeSlot, PlaceholderVerticalAlign.Center)
    return buildMap {
        if (rank != null) put(RANK_SLOT, InlineTextContent(slot) { RankBadge(rank, size = BadgeSize) })
        if (damage != null) {
            put(DAMAGE_SLOT, InlineTextContent(slot) { DamageBadge(damage, size = BadgeSize) })
        }
    }
}
