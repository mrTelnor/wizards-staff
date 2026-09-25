package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.telnor.wizardsstaff.db.CatalogFeat
import ru.telnor.wizardsstaff.db.CharacterFeatRecord
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.db.FeatGroup
import ru.telnor.wizardsstaff.db.findByRussian
import ru.telnor.wizardsstaff.ui.theme.PosohDimens

/*
 * Вкладка «Черты и знания»: черты и особенности по группам, а под ними языки
 * и сопротивления.
 *
 * Плашки идут по две в ряд: шестнадцать черт в один столбец превратили бы вкладку
 * в длинную ленту, а ей ещё нести знания.
 *
 * Рамка у плашки в две точки, хотя нажатие ещё не сделано: по нажатию будет
 * открываться описание черты, и по правилу из `docs/design/STYLE.md` то, что станет
 * кнопкой позже, обводится как кнопка сразу.
 *
 * Описание берётся не из листа, а из справочника `assets/feats.json`, по русскому
 * названию. Справочник неполон и полным не будет: черт в Legacy больше тысячи.
 * Поэтому нажимается только та плашка, для которой описание нашлось, — у остальных
 * тонкая рамка, и по ней сразу видно, что смотреть нечего.
 */

/** Сколько плашек в ряду. */
private const val COLUMNS = 2

/**
 * Высота плашки под две строки названия. Задана всем, а не только длинным: плашки
 * разной высоты в одном ряду выглядят как разные по важности, хотя черты равны.
 */
private val PlateHeight = 56.dp

@Composable
fun FeatsTab(
    character: CharacterSheet,
    catalog: List<CatalogFeat> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val record = character.record
    // Храним название, а не саму черту: так открытое окно переживает поворот планшета.
    var shown by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceS),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PosohDimens.screenPadding, vertical = PosohDimens.spaceM),
    ) {
        FeatGroup.entries.forEach { group ->
            val feats = character.feats(group)
            // Пустую группу не показываем вовсе: заголовок без единой плашки
            // выглядел бы как не догрузившийся раздел.
            if (feats.isEmpty()) return@forEach

            SectionTitle(group.title)
            feats.chunked(COLUMNS).forEach { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PosohDimens.spaceS),
                    // Общая высота ряда: длинное название в две строки не должно
                    // делать соседнюю плашку ниже себя.
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    pair.forEach { feat ->
                        FeatPlate(
                            feat = feat,
                            described = catalog.findByRussian(feat.name) != null,
                            onClick = { shown = feat.name },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Нечётная черта в конце группы остаётся половинной ширины,
                    // а не растягивается на весь ряд.
                    repeat(COLUMNS - pair.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        SectionTitle("Языки")
        InfoCard(record.languages)

        SectionTitle("Сопротивления")
        InfoCard(record.resistances)
    }

    shown?.let { name ->
        catalog.findByRussian(name)?.let { feat ->
            FeatDialog(feat) { shown = null }
        }
    }
}

/*
 * Кегли окна с описанием черты — своё правило, по решению автора от 2026-09-22,
 * и пока оно относится ТОЛЬКО к этому окну. Остальной лист живёт по кеглям
 * из `docs/design/STYLE.md`, где весь текст двенадцатый.
 *
 * Причина разницы: лист читают мельком и через стол, а описание черты — вдумчиво
 * и вблизи, целым абзацем книжного текста.
 */

/** Заголовок окна: жирный, 24. */
private val DialogTitle = 24.sp

/** Любой текст окна: обычный, 20. */
private val DialogText = 20.sp

/** Докуда окно растёт, прежде чем содержимое начнёт прокручиваться. */
private val DialogMaxHeight = 420.dp

/** Ширина полосы прокрутки и самый короткий ползунок, который ещё виден. */
private val ScrollBarWidth = 4.dp
private val ScrollThumbMin = 28.dp

/**
 * Окно с описанием черты: уровень и признаки, потом источник с требованиями,
 * потом сам текст правила.
 *
 * Текст взят с pf2.ru дословно, без пересказа — по нему принимают решения за столом.
 */
@Composable
private fun FeatDialog(feat: CatalogFeat, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyMedium.copy(fontSize = DialogText, lineHeight = 26.sp)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = feat.russian,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = DialogTitle),
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
        },
        text = {
            val scroll = rememberScrollState()
            Box(Modifier.heightIn(max = DialogMaxHeight)) {
                Column(
                    Modifier
                        .verticalScroll(scroll)
                        // Место под полосу: иначе длинная строка уходит под ползунок.
                        .padding(end = PosohDimens.spaceM),
                ) {
                    Text(
                        text = listOfNotNull(
                            feat.level?.let { "Черта $it" },
                            feat.traits.takeIf { it.isNotEmpty() }?.joinToString(", "),
                            feat.english,
                        ).joinToString(" · "),
                        style = body,
                        color = scheme.onSurface,
                    )
                    if (feat.header.isNotBlank()) {
                        Spacer(Modifier.height(PosohDimens.spaceM))
                        Text(feat.header, style = body, color = scheme.onSurface)
                    }
                    Spacer(Modifier.height(PosohDimens.spaceM))
                    Text(feat.text, style = body, color = scheme.onSurface)
                }
                ScrollBar(
                    scroll,
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(ScrollBarWidth),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

/**
 * Полоса прокрутки с ползунком. Своей в Compose под Android нет, поэтому рисуется.
 *
 * Нужна не для того, чтобы ей тянули — пальцем прокручивают сам текст, — а чтобы было
 * видно, что текст не кончился. У длинных черт описание не влезает, и без полосы окно
 * выглядит так, будто правило просто обрывается на полуслове.
 *
 * Когда текст помещается целиком, полосы нет вовсе: пустой жёлоб сообщал бы неправду.
 */
@Composable
private fun ScrollBar(state: ScrollState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier) {
        val hidden = state.maxValue.toFloat()
        if (hidden <= 0f) return@Canvas

        val track = size.height
        // Длина ползунка — доля видимого от всего текста, но не короче заметного.
        val thumb = (track * track / (track + hidden)).coerceAtLeast(ScrollThumbMin.toPx())
        val offset = (track - thumb) * (state.value / hidden)
        val radius = CornerRadius(size.width / 2)

        drawRoundRect(color = scheme.outlineVariant, size = Size(size.width, track), cornerRadius = radius)
        drawRoundRect(
            color = scheme.outline,
            topLeft = Offset(0f, offset),
            size = Size(size.width, thumb),
            cornerRadius = radius,
        )
    }
}

/**
 * Плашка черты: название слева, тег происхождения мелким справа.
 *
 * `described` — нашлось ли описание в справочнике. Нашлось — плашка нажимается
 * и обведена как кнопка; нет — тонкая рамка и нажимать нечего. Так видно заранее,
 * а не после пустого нажатия.
 */
@Composable
private fun FeatPlate(
    feat: CharacterFeatRecord,
    described: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val colors = CardDefaults.cardColors(containerColor = scheme.surface)
    val border = if (described) {
        BorderStroke(TileBorder, scheme.outline)
    } else {
        BorderStroke(1.dp, scheme.outlineVariant)
    }
    val box = modifier.fillMaxHeight().heightIn(min = PlateHeight)
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PosohDimens.spaceM, vertical = PosohDimens.spaceS),
        ) {
            Text(
                text = feat.name,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(PosohDimens.spaceS))
            Text(
                text = feat.tag,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = scheme.onSurface,
            )
        }
    }

    if (described) {
        Card(onClick = onClick, shape = shape, colors = colors, border = border, modifier = box) {
            content()
        }
    } else {
        Card(shape = shape, colors = colors, border = border, modifier = box) { content() }
    }
}

/**
 * Строка со сведениями: языки, сопротивления. Рамка тонкая — нажимать тут нечего,
 * это не кнопка.
 */
@Composable
private fun InfoCard(text: String) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text.ifBlank { "не заполнено" },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = scheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PosohDimens.spaceM, vertical = PosohDimens.spaceS),
        )
    }
}
