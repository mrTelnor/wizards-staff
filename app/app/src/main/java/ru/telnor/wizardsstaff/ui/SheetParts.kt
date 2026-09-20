package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.ui.theme.PosohDimens

/*
 * Кирпичи раздела «Персонажи», общие для всех четырёх вкладок: бейдж владения,
 * бросаемая строка, карточка-раздел и плитка с числом.
 *
 * Размеры и цвета — из макетов `docs/design/html`, состав — из SCREENS.md, раздел 3.
 * Там, где в `PosohTypography` есть роль, названная именно этим элементом (имя персонажа,
 * значение плитки, подпись над блоком), берётся роль. Свой размер указан только там,
 * где роли под него нет: модификатор характеристики и формула броска.
 */

/** Строка листа, которую можно бросить: испытание, навык, атака, урон. */
data class RollRow(
    val title: String,
    /** Вторая строка мелким: чем бросаем, какие признаки. */
    val detail: String,
    /** Готовая формула для показа: «1d20 + 11», «2d12 + 2». */
    val formula: String,
    val rank: Rank,
)

/**
 * Квадратик со степенью владения. Буква берётся у самой степени, чтобы подписи
 * в листе и на бейдже не разъехались: «И» изученный, «Э» экспертный, «М» мастерский.
 */
@Composable
fun RankBadge(rank: Rank, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val background = when (rank) {
        Rank.UNTRAINED -> scheme.surfaceVariant
        Rank.TRAINED -> scheme.surfaceContainerHigh
        Rank.EXPERT -> scheme.primaryContainer
        Rank.MASTER -> scheme.tertiaryContainer
        Rank.LEGENDARY -> scheme.tertiary
    }
    val content = when (rank) {
        Rank.UNTRAINED -> scheme.onSurfaceVariant
        Rank.TRAINED -> scheme.onSurface
        Rank.EXPERT -> scheme.onPrimaryContainer
        Rank.MASTER -> scheme.onTertiaryContainer
        Rank.LEGENDARY -> scheme.onTertiary
    }
    Box(
        modifier = modifier
            .size(22.dp)
            .background(background, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.badge,
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

/**
 * Бросаемая строка — основной кирпич раздела. Формула стоит справа отдельной колонкой:
 * так «+14» и «+9» встают в столбец, а не пляшут по ширине названия.
 *
 * Выбор действия (подсветка и взвод посоха) появится в шаге 4 задачи D2, пока строка
 * только показывает число.
 */
@Composable
fun RollRowItem(row: RollRow, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surface, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, scheme.outlineVariant), RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(row.rank)
        Spacer(Modifier.width(PosohDimens.spaceM))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleSmall)
            if (row.detail.isNotEmpty()) {
                Text(
                    text = row.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(PosohDimens.spaceS))
        Text(
            text = row.formula,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 18.sp),
        )
    }
}

/**
 * Карточка-раздел листа: «Атаки», «Испытания и восприятие», «Обученные навыки».
 * Подсказка рядом с заголовком необязательна.
 */
@Composable
fun SheetCard(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = PosohDimens.spaceM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                if (hint != null) {
                    Spacer(Modifier.width(PosohDimens.spaceS))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(PosohDimens.spaceS))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

/**
 * Плитка с числом: класс брони, здоровье, классовая СЛ, СЛ заклинаний, скорость.
 *
 * `suffix` — приписка мелким справа от числа: «фт» у скорости.
 *
 * Здоровье такой плиткой не рисуется: у него своя, со шкалой и кнопками, в «Обзоре».
 */
@Composable
fun SheetTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
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
            )
            Spacer(Modifier.height(PosohDimens.spaceXs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp),
                    maxLines = 1,
                )
                if (suffix != null) {
                    Spacer(Modifier.width(PosohDimens.spaceXs))
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
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
