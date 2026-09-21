package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.rules.Ability
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.rules.Save
import ru.telnor.wizardsstaff.rules.initiativeMod
import ru.telnor.wizardsstaff.rules.perceptionMod
import ru.telnor.wizardsstaff.rules.saveMod
import ru.telnor.wizardsstaff.ui.theme.PosohDimens

/*
 * Ряд «Испытания»: стойкость, реакция, воля, восприятие и инициатива.
 *
 * Пять отдельных плиток с зазором, а не одна карточка с разделителями: каждая из них
 * станет кнопкой, которая взводит посох на этот бросок (шаг 4 задачи D2), и выглядеть
 * они должны кнопками уже сейчас. Отсюда и рамка в две точки вместо обычной одной.
 *
 * Нажатия пока нет намеренно: мёртвая кнопка хуже её отсутствия.
 *
 * Все пять чисел выводит `Pf2.kt`. Хранится из них только прибавка к инициативе —
 * её даёт черта, а черты движок не понимает.
 */

@Composable
fun SavesRow(character: CharacterSheet, modifier: Modifier = Modifier) {
    val sheet = character.sheet
    // IntrinsicSize.Min и fillMaxHeight у плиток: все пять одной высоты, даже если
    // в какой-то строка внизу окажется короче.
    Row(
        horizontalArrangement = Arrangement.spacedBy(PosohDimens.spaceS),
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
    ) {
        Save.entries.forEach { save ->
            RollTile(
                label = save.title,
                value = signed(sheet.saveMod(save)),
                rank = sheet.saves[save] ?: Rank.UNTRAINED,
                detail = save.ability.short,
                modifier = Modifier.weight(1f),
            )
        }
        RollTile(
            label = "Восприятие",
            value = signed(sheet.perceptionMod()),
            rank = sheet.perception,
            detail = Ability.WIS.short,
            modifier = Modifier.weight(1f),
        )
        RollTile(
            label = "Инициат",
            // Прибавка от черты в число входит, но отдельной строкой не показана:
            // бонусы черт и предметов будут собраны на своей странице.
            value = signed(sheet.initiativeMod(character.record.initiativeBonus)),
            // Владения у инициативы своего не бывает: бросают её Восприятием,
            // им же и подписываем.
            rank = sheet.perception,
            detail = Ability.WIS.short,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Плитка одного броска: надпись, число и мелкая строка под ним — характеристика,
 * которой бросают, и квадратик владения справа от неё.
 */
@Composable
private fun RollTile(
    label: String,
    value: String,
    rank: Rank,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(TileBorder, scheme.outline),
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = PosohDimens.spaceS),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = BigNumber),
                color = scheme.onSurface,
                maxLines = 1,
            )
            RankLine(detail, rank)
        }
    }
}
