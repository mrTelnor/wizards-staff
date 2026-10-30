package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.telnor.wizardsstaff.ui.theme.PosohDimens

/*
 * Экран «Инструкция»: как пользоваться посохом, подробнее, чем на его экране.
 * Тексты — в HelpContent.kt, здесь только разметка: столбец карточек с прокруткой.
 *
 * Цвета только из темы, без чисел из макетов (G62): экран должен читаться и в светлой,
 * и в тёмной теме. Положение прокрутки rememberScrollState переживает поворот сам.
 */

/** Потолок ширины столбца: на широком экране строки длиннее читать тяжело. */
private val HelpContentMaxWidth = 720.dp

/** Ширина колонки подписей: «Долго, 2 с», «Тест d20», «Раздел «Посох»». */
private val HelpLabelWidth = 136.dp

@Composable
fun HelpScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PosohDimens.screenPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = HelpContentMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceL),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(PosohDimens.railTouchTarget)) {
                    Icon(StaffIcons.Back, contentDescription = "Назад, в раздел «Посох»")
                }
                Spacer(Modifier.width(PosohDimens.spaceS))
                // weight, а не fillMaxWidth: иначе текст займёт всю строку вместе с кнопкой (G77).
                Text(
                    text = "Короткая версия есть на самом посохе: ОК, пункт «Инструкция».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            HelpContent.sections.forEach { HelpSectionCard(it) }
        }
    }
}

/** Карточка одного раздела инструкции. Оформление как у карточек экрана «Посох». */
@Composable
private fun HelpSectionCard(section: HelpSection) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(PosohDimens.spaceXl)) {
            Text(section.title, style = MaterialTheme.typography.titleLarge)
            section.items.forEach { item ->
                Spacer(Modifier.height(PosohDimens.spaceM))
                HelpItemRow(item)
            }
        }
    }
}

/**
 * Строка карточки: подпись в колонке постоянной ширины и пояснение на остаток строки.
 * Без подписи пояснение идёт на всю ширину карточки.
 */
@Composable
private fun HelpItemRow(item: HelpItem) {
    val scheme = MaterialTheme.colorScheme
    val label = item.label
    if (label == null) {
        Text(item.text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        return
    }
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
            modifier = Modifier.width(HelpLabelWidth),
        )
        Spacer(Modifier.width(PosohDimens.spaceM))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
