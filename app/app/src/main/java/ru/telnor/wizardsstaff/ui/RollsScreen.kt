package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.telnor.wizardsstaff.ArmedState
import ru.telnor.wizardsstaff.RollRecord
import ru.telnor.wizardsstaff.ble.Connection
import ru.telnor.wizardsstaff.ui.theme.PosohDimens
import ru.telnor.wizardsstaff.ui.theme.PosohTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Главный экран во время игры: слева последний бросок, взвод и состояние посоха,
 * справа лента бросков. Разметка по макету docs/design/png/Main.png.
 *
 * Пока нет листов персонажей, место карточки «активное действие» занимает карточка взвода:
 * кубик выбирается здесь и уходит на посох, удар об пол бросает именно его.
 */

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(millis: Long): String = timeFormat.format(Date(millis))

@Composable
fun RollsScreen(
    rolls: List<RollRecord>,
    armed: ArmedState?,
    dice: List<Int>,
    connection: Connection,
    batteryPercent: Int?,
    onArm: (Int, Int) -> Unit,
    onDisarm: () -> Unit,
    onToggleDiscarded: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize().padding(PosohDimens.screenPadding)) {

        Column(
            modifier = Modifier.width(PosohDimens.rollsLeftColumnWidth).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceL),
        ) {
            LastRollCard(rolls.firstOrNull(), onToggleDiscarded)
            ArmCard(armed, dice, connection == Connection.Connected, onArm, onDisarm)
            StaffStateCard(connection, armed, batteryPercent)
        }

        Spacer(Modifier.width(PosohDimens.spaceXl))

        Column(Modifier.weight(1f).fillMaxSize()) {
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
                    val from = formatTime(rolls.last().receivedAt)
                    val to = formatTime(rolls.first().receivedAt)
                    Text(
                        text = if (from == to) "сегодня, $to" else "сегодня, $from — $to",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (rolls.isEmpty()) {
                EmptyFeed(connection)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(PosohDimens.feedItemGap)) {
                    items(rolls, key = { it.id }) { roll ->
                        FeedCard(
                            roll = roll,
                            newest = roll.id == rolls.first().id,
                            onClick = { onToggleDiscarded(roll.id) },
                        )
                    }
                }
            }
        }
    }
}

// ---------- левая колонка ----------

/** Последний бросок крупно: его должно быть видно через стол. */
@Composable
private fun LastRollCard(roll: RollRecord?, onToggleDiscarded: (Long) -> Unit) {
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (roll == null) scheme.onSurfaceVariant else content.copy(alpha = 0.75f),
                )
                if (roll != null) {
                    Text(
                        text = formatTime(roll.receivedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.75f),
                    )
                }
            }

            Spacer(Modifier.height(PosohDimens.spaceM))

            if (roll == null) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.displayLarge,
                    color = scheme.outline,
                )
                Text(
                    text = "Бросков ещё не было",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = roll.total.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        textDecoration = if (roll.discarded) TextDecoration.LineThrough else null,
                    )
                    Spacer(Modifier.width(PosohDimens.spaceL))
                    Column(Modifier.padding(bottom = 14.dp)) {
                        FormulaChip(roll.formula, content)
                        if (roll.breakdown.isNotEmpty()) {
                            Spacer(Modifier.height(PosohDimens.spaceXs))
                            Text(
                                text = roll.breakdown,
                                fontSize = 19.sp,
                                style = MaterialTheme.typography.bodyMedium,
                                color = content.copy(alpha = 0.7f),
                            )
                        }
                    }
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
                        onClick = { onToggleDiscarded(roll.id) },
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

/** Взвод: выбор кубика и команда посоху. Пока нет кнопок на посохе, это единственный способ. */
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
                text = "Выбери кубик — удар об пол бросит его",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(PosohDimens.spaceM))

            // Кубики в два ряда по четыре: строка из восьми на планшете влезает,
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
                    text = "Кубиков",
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

/** Кнопка выбора кубика. */
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

/** Счётчик количества кубиков: от одного до десяти, больше посох не бросает. */
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

/** Строка состояния посоха: связь, взвод, заряд. */
@Composable
private fun StaffStateCard(connection: Connection, armed: ArmedState?, batteryPercent: Int?) {
    val scheme = MaterialTheme.colorScheme
    val connected = connection == Connection.Connected

    val title = when {
        !connected -> "Нет связи с посохом"
        armed != null -> "Посох взведён · ${armed.formula}"
        else -> "Посох в покое"
    }
    val subtitle = when {
        connection == Connection.Lost -> "Потерялся: включи посох, связь восстановится сама"
        !connected -> "Найди посох в разделе «Посох»"
        armed != null -> "Ждёт удара об пол"
        else -> "Взведи его, чтобы бросить кубик"
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
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
        )
    }
}

/** Карточка броска в ленте. Нажатие отмечает бросок как случайный и возвращает обратно. */
@Composable
private fun FeedCard(roll: RollRecord, newest: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val extra = PosohTheme.extraColors

    val background = when {
        roll.critSuccess -> scheme.tertiaryContainer
        roll.critFail -> scheme.errorContainer
        newest -> Color(0xFFF5F9FE)
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
        newest -> Color(0xFFA9CBF2)
        else -> scheme.outlineVariant
    }
    val label = when {
        roll.critSuccess -> "КРИТ. УСПЕХ"
        roll.critFail -> "КРИТ. ПРОВАЛ"
        else -> null
    }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = background, contentColor = content),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth().height(62.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PosohDimens.spaceL, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PosohDimens.spaceL),
        ) {
            Text(
                text = roll.total.toString(),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.End,
                textDecoration = if (roll.discarded) TextDecoration.LineThrough else null,
                modifier = Modifier.width(PosohDimens.feedSumColumnWidth),
            )
            FormulaChip(roll.formula, content)
            Column(Modifier.weight(1f)) {
                if (label != null) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
                if (roll.breakdown.isNotEmpty()) {
                    Text(
                        text = roll.breakdown,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (label == null && roll.discarded) {
                    Text(
                        text = "не считается",
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.7f),
                    )
                }
            }
            Text(
                text = formatTime(roll.receivedAt),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.7f),
            )
        }
    }
}

/** Бросков ещё нет. */
@Composable
private fun EmptyFeed(connection: Connection) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(560.dp).padding(bottom = 60.dp),
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
                    "Взведи посох кнопкой слева и ударь им об пол. " +
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
