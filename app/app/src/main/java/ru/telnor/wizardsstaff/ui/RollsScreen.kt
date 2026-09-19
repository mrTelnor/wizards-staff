package ru.telnor.wizardsstaff.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.telnor.wizardsstaff.ArmedState
import ru.telnor.wizardsstaff.db.RollRecord
import ru.telnor.wizardsstaff.ble.Connection
import ru.telnor.wizardsstaff.ui.theme.PosohDimens
import ru.telnor.wizardsstaff.ui.theme.PosohTheme
import kotlinx.coroutines.delay
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Главный экран во время игры: последний бросок, взвод, состояние посоха и лента бросков.
 * Разметка по макету docs/design/png/Main.png.
 *
 * Макет нарисован для планшета в альбомной ориентации (1097 dp). В портретной ориентации
 * ширины на две колонки не хватает, поэтому там всё складывается в один столбец:
 * сначала карточки, под ними лента.
 *
 * Пока нет листов персонажей, место карточки «активное действие» занимает карточка взвода:
 * кость выбирается здесь и уходит на посох, удар об пол бросает именно её.
 */

/** С какой ширины экрана помещаются две колонки. */
private val TwoColumnWidth = 900.dp

/** Сколько крутится барабан результата, миллисекунды. */
private const val ROLL_SPIN_MS = 1100L

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(millis: Long): String = timeFormat.format(Date(millis))

/**
 * Время броска для показа. Посох знает своё время только когда у него выставлены часы;
 * если нет, берём время получения планшетом и честно помечаем звёздочкой, чтобы
 * «19:05» не выглядело замером, которого никто не делал.
 */
private fun rollTime(roll: RollRecord): String =
    if (roll.staffTimeKnown) formatTime(roll.shownAt) else formatTime(roll.receivedAt) + "*"

@Composable
fun RollsScreen(
    rolls: List<RollRecord>,
    armed: ArmedState?,
    dice: List<Int>,
    connection: Connection,
    batteryPercent: Int?,
    onArm: (Int, Int) -> Unit,
    onDisarm: () -> Unit,
    onToggleDiscarded: (String) -> Unit,
    onSaveNote: (String, String?) -> Unit,
    onLoadMore: () -> Unit,
    onGoToStaff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Какому броску сейчас пишут подпись. Окно живёт тут, а не в каркасе приложения:
    // подпись имеет смысл только на этом экране, и таскать состояние наружу незачем.
    var noteTarget by remember { mutableStateOf<RollRecord?>(null) }

    noteTarget?.let { target ->
        NoteDialog(
            roll = target,
            onSave = { text ->
                onSaveNote(target.key, text)
                noteTarget = null
            },
            onDismiss = { noteTarget = null },
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= TwoColumnWidth) {
            Row(Modifier.fillMaxSize().padding(PosohDimens.screenPadding)) {
                // Прокрутка обязательна: в альбомной ориентации планшета экран низкий,
                // и карточки «последний бросок» плюс «взвод» с сеткой из восьми костей
                // в высоту не помещаются - нижний ряд костей оказывался за краем экрана
                // и нажать его было нельзя. Сжимать содержимое нечем, поэтому колонка
                // прокручивается. В портретной ориентации прокручивать обычно нечего.
                Column(
                    modifier = Modifier
                        .width(PosohDimens.rollsLeftColumnWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceL),
                ) {
                    SideCards(rolls, armed, dice, connection, batteryPercent,
                        onArm, onDisarm, onToggleDiscarded, onGoToStaff)
                }

                Spacer(Modifier.width(PosohDimens.spaceXl))

                Column(Modifier.weight(1f).fillMaxSize()) {
                    FeedHeader(rolls)
                    if (rolls.isEmpty()) {
                        EmptyFeed(connection)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(PosohDimens.feedItemGap)
                        ) {
                            items(rolls, key = { it.key }) { roll ->
                                FeedCard(
                                    roll = roll,
                                    newest = roll.key == rolls.first().key,
                                    onLongClick = { noteTarget = roll },
                                    modifier = Modifier.animateItem(),
                                )
                                if (roll.key == rolls.last().key) {
                                    LaunchedEffect(roll.key) { onLoadMore() }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(PosohDimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceM),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceL)) {
                        SideCards(rolls, armed, dice, connection, batteryPercent,
                            onArm, onDisarm, onToggleDiscarded, onGoToStaff)
                    }
                }
                item { FeedHeader(rolls) }
                if (rolls.isEmpty()) {
                    item { EmptyFeed(connection) }
                } else {
                    items(rolls, key = { it.key }) { roll ->
                        FeedCard(
                            roll = roll,
                            newest = roll.key == rolls.first().key,
                            onLongClick = { noteTarget = roll },
                            modifier = Modifier.animateItem(),
                        )
                        // Домотали до последней карточки - просим следующую сотню.
                        if (roll.key == rolls.last().key) {
                            LaunchedEffect(roll.key) { onLoadMore() }
                        }
                    }
                }
            }
        }
    }
}

/** Три карточки, одинаковые в обеих раскладках. */
@Composable
private fun ColumnScope.SideCards(
    rolls: List<RollRecord>,
    armed: ArmedState?,
    dice: List<Int>,
    connection: Connection,
    batteryPercent: Int?,
    onArm: (Int, Int) -> Unit,
    onDisarm: () -> Unit,
    onToggleDiscarded: (String) -> Unit,
    onGoToStaff: () -> Unit,
) {
    LastRollCard(rolls.firstOrNull(), onToggleDiscarded)
    ArmCard(armed, dice, connection == Connection.Connected, onArm, onDisarm)
    StaffStateCard(connection, armed, batteryPercent, onGoToStaff)
}

@Composable
private fun FeedHeader(rolls: List<RollRecord>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "ЛЕНТА",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (rolls.isNotEmpty()) {
            val from = formatTime(rolls.last().shownAt)
            val to = formatTime(rolls.first().shownAt)
            Text(
                text = if (from == to) "сегодня, $to" else "сегодня, $from — $to",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- карточки ----------

/**
 * Последний бросок крупно: его должно быть видно через стол.
 * Сумма стоит по центру, под ней — что выпало на каждой кости; формула в шапке.
 */
@Composable
private fun LastRollCard(roll: RollRecord?, onToggleDiscarded: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val extra = PosohTheme.extraColors

    val background = when {
        roll == null -> scheme.surface
        roll.critSuccess -> scheme.tertiaryContainer
        roll.critFail -> scheme.errorContainer
        else -> scheme.surface
    }
    val content = when {
        roll == null -> scheme.onSurface
        roll.critSuccess -> scheme.onTertiaryContainer
        roll.critFail -> scheme.onErrorContainer
        else -> scheme.onSurface
    }
    val border = when {
        roll == null -> scheme.outlineVariant
        roll.critSuccess -> extra.critSuccessOutline
        roll.critFail -> extra.critFailOutline
        else -> scheme.outlineVariant
    }
    val title = when {
        roll == null -> "ПОСЛЕДНИЙ БРОСОК"
        roll.critSuccess -> "КРИТИЧЕСКИЙ УСПЕХ"
        roll.critFail -> "КРИТИЧЕСКИЙ ПРОВАЛ"
        else -> "ПОСЛЕДНИЙ БРОСОК"
    }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = background, contentColor = content),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (roll == null) scheme.onSurfaceVariant else content.copy(alpha = 0.75f),
                    )
                    if (roll != null) {
                        Spacer(Modifier.width(PosohDimens.spaceS))
                        FormulaChip(roll.formula, content)
                    }
                }
                if (roll != null) {
                    Text(
                        text = rollTime(roll),
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.75f),
                    )
                }
            }

            Spacer(Modifier.height(PosohDimens.spaceS))

            if (roll == null) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.displayLarge,
                    color = scheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Бросков ещё не было",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val spinning = RollingTotal(roll, content)

                if (roll.breakdown.isNotEmpty()) {
                    Spacer(Modifier.height(PosohDimens.spaceM))
                    Text(
                        // Пока барабан крутится, слагаемые прячем: иначе по ним виден результат.
                        text = if (spinning) "…" else roll.breakdown,
                        fontSize = 19.sp,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(PosohDimens.spaceL))
                HorizontalDivider(color = scheme.outlineVariant)
                Spacer(Modifier.height(PosohDimens.spaceM))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = if (roll.discarded) "Не считается" else "Бросок с посоха",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Бросок №${roll.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = 0.7f),
                        )
                    }
                    FilledIconButton(
                        onClick = { onToggleDiscarded(roll.key) },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = scheme.surfaceContainer,
                            contentColor = scheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector = StaffIcons.Close,
                            contentDescription = if (roll.discarded) {
                                "Вернуть бросок в счёт"
                            } else {
                                "Отметить, что бросок не считается"
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Барабан результата: свежий бросок сначала «крутится», как барабан игрового автомата,
 * и только потом останавливается на выпавшей сумме. Секунда ожидания — это и есть
 * маленькое представление, ради которого посох вообще затевался.
 *
 * Возвращает true, пока барабан крутится: карточка по этому признаку прячет слагаемые.
 */
@Composable
private fun RollingTotal(roll: RollRecord, content: Color): Boolean {
    var shown by remember(roll.key) { mutableIntStateOf(roll.total) }
    var spinning by remember(roll.key) { mutableStateOf(false) }

    LaunchedEffect(roll.key) {
        // Крутим только по-настоящему свежий бросок. Иначе барабан заводился бы заново
        // при каждом повороте планшета и возврате на экран.
        if (System.currentTimeMillis() - roll.receivedAt > 2000) return@LaunchedEffect

        spinning = true
        val smallest = roll.count                 // на всех костях выпали единицы
        val largest = roll.count * roll.sides     // на всех выпал максимум
        val startedAt = System.currentTimeMillis()
        var step = 45L

        while (System.currentTimeMillis() - startedAt < ROLL_SPIN_MS) {
            shown = Random.nextInt(smallest, largest + 1)
            delay(step)
            step = (step * 1.18).toLong().coerceAtMost(220)   // барабан постепенно замедляется
        }

        shown = roll.total
        spinning = false
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                // Пока крутится — числа пролетают снизу вверх, как в окошке автомата.
                // Остановка мягче: последнее число выезжает наполовину и замирает.
                if (spinning) {
                    (slideInVertically { it } + fadeIn(tween(60)))
                        .togetherWith(slideOutVertically { -it } + fadeOut(tween(60)))
                } else {
                    (slideInVertically { it / 2 } + fadeIn(tween(220)))
                        .togetherWith(fadeOut(tween(120)))
                }
            },
            label = "барабан броска",
        ) { value ->
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = if (spinning) content.copy(alpha = 0.45f) else content,
                textAlign = TextAlign.Center,
                maxLines = 1,
                textDecoration = if (roll.discarded) TextDecoration.LineThrough else null,
            )
        }
    }

    return spinning
}

/** Взвод: выбор кости и команда посоху. Пока нет кнопок на посохе, это единственный способ. */
@Composable
private fun ArmCard(
    armed: ArmedState?,
    dice: List<Int>,
    connected: Boolean,
    onArm: (Int, Int) -> Unit,
    onDisarm: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var count by rememberSaveable { mutableIntStateOf(1) }
    var sides by rememberSaveable { mutableIntStateOf(20) }

    if (armed != null) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(PosohDimens.spaceL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(PosohDimens.avatarMedium)
                        .background(scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = StaffIcons.Staff,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(PosohDimens.spaceM))
                Column(Modifier.weight(1f)) {
                    Text("ПОСОХ ВЗВЕДЁН", style = MaterialTheme.typography.labelSmall)
                    Text(armed.formula, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Ждёт удара об пол",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
                IconButton(onClick = onDisarm, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = StaffIcons.Close,
                        contentDescription = "Снять взвод",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        return
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(PosohDimens.spaceL)) {
            Text(
                text = "ВЗВОД",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(PosohDimens.spaceXs))
            Text(
                text = "Посох не взведён",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Выбери кость — удар об пол бросит её",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(PosohDimens.spaceM))

            // Кости в два ряда по четыре: строка из восьми на планшете влезает,
            // но на узком экране разъезжается, а перенос строк здесь проще колонки.
            dice.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = PosohDimens.spaceS),
                    horizontalArrangement = Arrangement.spacedBy(PosohDimens.spaceS),
                ) {
                    row.forEach { value ->
                        DiceButton(
                            label = "d$value",
                            selected = value == sides,
                            onClick = { sides = value },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(PosohDimens.spaceXs))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Костей",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(PosohDimens.spaceS))
                CountStepper(count = count, onChange = { count = it })
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onArm(count, sides) },
                    enabled = connected,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text("Взвести ${count}d$sides")
                }
            }

            if (!connected) {
                Spacer(Modifier.height(PosohDimens.spaceS))
                Text(
                    text = "Посох не подключён: взводить нечего",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Кнопка выбора кости. */
@Composable
private fun DiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
            contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        ),
        border = if (selected) BorderStroke(1.dp, scheme.primary) else null,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Счётчик количества костей: от одной до десяти, больше посох не бросает. */
@Composable
private fun CountStepper(count: Int, onChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton("−", enabled = count > 1, onClick = { onChange(count - 1) })
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = scheme.onSurface,
            modifier = Modifier.width(32.dp),
        )
        StepperButton("+", enabled = count < 10, onClick = { onChange(count + 1) })
    }
}

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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Строка состояния посоха: связь, взвод, заряд. Нажатие ведёт в раздел «Посох». */
@Composable
private fun StaffStateCard(
    connection: Connection,
    armed: ArmedState?,
    batteryPercent: Int?,
    onGoToStaff: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val connected = connection == Connection.Connected

    val title = when {
        !connected -> "Нет связи с посохом"
        armed != null -> "Посох взведён · ${armed.formula}"
        else -> "Посох в покое"
    }
    val subtitle = when {
        connection == Connection.Lost -> "Потерялся: нажми, чтобы открыть раздел «Посох»"
        !connected -> "Нажми, чтобы найти и подключить посох"
        armed != null -> "Ждёт удара об пол"
        else -> "Взведи его, чтобы бросить кость"
    }

    Card(
        onClick = onGoToStaff,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, if (connected) scheme.outlineVariant else scheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(PosohDimens.spaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(scheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = StaffIcons.Staff,
                    contentDescription = null,
                    tint = if (connected) scheme.primary else PosohTheme.extraColors.staffOffline,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(PosohDimens.spaceM))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (batteryPercent != null) {
                Icon(
                    imageVector = StaffIcons.Battery,
                    contentDescription = "Заряд посоха",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(PosohDimens.spaceXs))
                Text(
                    text = "$batteryPercent %",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------- лента ----------

/** Чип формулы: 1d20, 3d6. */
@Composable
private fun FormulaChip(formula: String, contentColor: Color) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text = formula,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.85f),
            maxLines = 1,
        )
    }
}

/**
 * Карточка броска в ленте. Высота не задана жёстко: у броска десятью костями
 * строка слагаемых длинная и должна переноситься, а не обрезаться.
 */
@Composable
private fun FeedCard(
    roll: RollRecord,
    newest: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = PosohTheme.extraColors

    // Свежий бросок выделяется только рамкой: заливка светлым цветом в тёмной теме
    // превращала карточку в белое пятно с нечитаемым текстом.
    val background = when {
        roll.critSuccess -> scheme.tertiaryContainer
        roll.critFail -> scheme.errorContainer
        else -> scheme.surface
    }
    val content = when {
        roll.critSuccess -> scheme.onTertiaryContainer
        roll.critFail -> scheme.onErrorContainer
        else -> scheme.onSurface
    }
    val border = when {
        roll.critSuccess -> extra.critSuccessOutline
        roll.critFail -> extra.critFailOutline
        newest -> scheme.primary
        else -> scheme.outlineVariant
    }
    val borderWidth = if (newest) 2.dp else 1.dp
    val label = when {
        roll.critSuccess -> "КРИТ. УСПЕХ"
        roll.critFail -> "КРИТ. ПРОВАЛ"
        else -> null
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = background, contentColor = content),
        border = BorderStroke(borderWidth, border),
        // Долгое нажатие вместо кнопки: подпись нужна не каждому броску, а лишняя
        // иконка на каждой карточке засоряла бы ленту, ради которой сюда и смотрят.
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .padding(horizontal = PosohDimens.spaceL, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PosohDimens.spaceM),
        ) {
            Text(
                text = roll.total.toString(),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.End,
                maxLines = 1,
                textDecoration = if (roll.discarded) TextDecoration.LineThrough else null,
                modifier = Modifier.widthIn(min = PosohDimens.feedSumColumnWidth),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FormulaChip(roll.formula, content)
                    if (label != null) {
                        Spacer(Modifier.width(PosohDimens.spaceS))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                    if (roll.discarded) {
                        Spacer(Modifier.width(PosohDimens.spaceS))
                        Text(
                            text = "НЕ СЧИТАЕТСЯ",
                            style = MaterialTheme.typography.labelSmall,
                            color = content.copy(alpha = 0.6f),
                        )
                    }
                }
                if (roll.breakdown.isNotEmpty()) {
                    Text(
                        text = roll.breakdown,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                roll.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = rollTime(roll),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

/**
 * Подпись к броску: «атака по гоблину». Открывается долгим нажатием на карточку.
 * Пустая строка стирает подпись — отдельной кнопки «убрать» не нужно.
 */
@Composable
private fun NoteDialog(roll: RollRecord, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by remember(roll.key) { mutableStateOf(roll.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подпись к броску") },
        text = {
            Column {
                Text(
                    text = "${roll.formula} = ${roll.total}" +
                        if (roll.breakdown.isNotEmpty()) " (${roll.breakdown})" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(PosohDimens.spaceL))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Зачем бросали") },
                    placeholder = { Text("атака по гоблину") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Бросков ещё нет. */
@Composable
private fun EmptyFeed(connection: Connection) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Icon(
                imageVector = StaffIcons.Dice,
                contentDescription = null,
                tint = scheme.outline,
                modifier = Modifier.size(104.dp),
            )
            Spacer(Modifier.height(PosohDimens.spaceXl))
            Text(
                text = "Бросков ещё нет",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(PosohDimens.spaceM))
            Text(
                text = if (connection == Connection.Connected) {
                    "Взведи посох кнопкой выше и ударь им об пол. " +
                        "Результат появится здесь через долю секунды — записывать ничего не нужно."
                } else {
                    "Сначала подключи посох в разделе «Посох». " +
                        "Приложение работает и без него, но записывать пока нечего."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
