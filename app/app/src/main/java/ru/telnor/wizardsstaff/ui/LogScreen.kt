package ru.telnor.wizardsstaff.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import ru.telnor.wizardsstaff.ble.LogDirection
import ru.telnor.wizardsstaff.ble.LogLine
import ru.telnor.wizardsstaff.ui.theme.PosohDimens
import ru.telnor.wizardsstaff.ui.theme.PosohTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Журнал обмена с посохом — то же, что монитор порта в Arduino IDE, только по Bluetooth.
 * Видно каждую строку в обе стороны, можно отправить команду руками, найти строку по тексту
 * и сохранить всё в файл.
 *
 * Строки живут в памяти приложения (последние 2000) и пропадают при его закрытии:
 * если журнал нужен для разбора, его надо сохранить.
 */

private val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogScreen(
    lines: List<LogLine>,
    connected: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    /** Отправляет команду и убирает клавиатуру: держать её открытой после отправки незачем. */
    fun sendAndHide(text: String) {
        onSend(text)
        focus.clearFocus()
        keyboard?.hide()
    }

    var query by rememberSaveable { mutableStateOf("") }
    var command by rememberSaveable { mutableStateOf("") }
    var autoScroll by rememberSaveable { mutableStateOf(true) }
    // По умолчанию «Инфо»: читать журнал приходят за строками посоха, а не за машинным
    // JSON. Он никуда не девается, кнопка «Всё» рядом.
    var filter by rememberSaveable { mutableStateOf(LogFilter.Info) }

    // Фильтр и поиск складываются: сначала отбираем по виду строки, потом по тексту.
    val shown = remember(lines, query, filter) {
        lines
            .filter { filter.accepts(it) }
            .filter { query.isBlank() || it.text.contains(query, ignoreCase = true) }
    }
    val listState = rememberLazyListState()

    // Автопрокрутка: пока включена, экран следует за свежими строками. Выключил — журнал
    // стоит на месте, и можно спокойно читать, хотя строки продолжают приходить.
    // Высота клавиатуры в ключах: когда она выезжает, список становится короче, и без этого
    // автопрокрутка осталась бы стоять там, где была, показывая уже не последние строки.
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(shown.size, autoScroll, imeBottom) {
        if (autoScroll && shown.isNotEmpty()) listState.animateScrollToItem(shown.lastIndex)
    }

    // Файл сохраняем через системный выбор места: так не нужно ни одного разрешения,
    // и файл попадает туда, куда решит хозяин планшета.
    val linesToSave by rememberUpdatedState(lines)
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = buildLogFile(linesToSave)
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }.isSuccess
        Toast.makeText(
            context,
            if (ok) "Журнал сохранён" else "Не удалось сохранить журнал",
            Toast.LENGTH_SHORT,
        ).show()
    }

    Column(modifier.fillMaxSize().padding(PosohDimens.screenPadding)) {

        // ---------- заголовок ----------
        // В строке заголовка только выбор, что показывать: кнопки узкие и помещаются.
        // «Сохранить» и «Очистить» вынесены строкой ниже: в портретной ориентации планшета
        // на одну строку их не хватало, и заголовок начинал переноситься по слогам.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.size(PosohDimens.railTouchTarget)) {
                Icon(StaffIcons.Back, contentDescription = "Назад, в раздел «Посох»")
            }
            Spacer(Modifier.width(PosohDimens.spaceS))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Логи посоха",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                )
                Text(
                    text = if (query.isBlank() && filter == LogFilter.All) {
                        "строк: ${lines.size}"
                    } else {
                        "показано ${shown.size} из ${lines.size}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            // Что показывать: выбрано всегда ровно одно из трёх.
            LogFilter.entries.forEach { item -> LogFilterChip(item, filter == item) { filter = item } }
        }

        Spacer(Modifier.height(PosohDimens.spaceS))

        // ---------- управление ----------
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // Тот же стиль, что у надписей внутри TextButton справа. Раньше здесь был
            // bodyMedium (14sp, обычное начертание) против labelLarge у кнопок (15sp,
            // полужирное): надписи в одной строке выглядели разными и слегка разъезжались.
            Text(
                text = "Автопрокрутка",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(PosohDimens.spaceS))
            Switch(checked = autoScroll, onCheckedChange = { autoScroll = it })

            Spacer(Modifier.weight(1f))

            TextButton(onClick = { saveLauncher.launch(defaultFileName()) }) {
                Icon(StaffIcons.Save, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(PosohDimens.spaceS))
                Text("Сохранить")
            }
            TextButton(onClick = onClear) {
                Icon(StaffIcons.Trash, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(PosohDimens.spaceS))
                Text("Очистить")
            }
        }

        Spacer(Modifier.height(PosohDimens.spaceM))

        // ---------- поиск ----------
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            label = { Text("Поиск по строкам журнала") },
            leadingIcon = {
                Icon(StaffIcons.Search, contentDescription = null, Modifier.size(20.dp))
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(StaffIcons.Close, contentDescription = "Очистить поиск", Modifier.size(18.dp))
                    }
                }
            },
        )

        Spacer(Modifier.height(PosohDimens.spaceM))

        // ---------- сам журнал ----------
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLowest),
            border = BorderStroke(1.dp, scheme.outlineVariant),
            // Касание журнала убирает клавиатуру: иначе закрыть её можно только системным
            // жестом «назад», а это не очевидно. Прокрутке списка не мешает: сюда приходят
            // только касания без движения, протяжки забирает LazyColumn.
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures { focus.clearFocus(); keyboard?.hide() }
                },
        ) {
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            lines.isEmpty() && !connected ->
                                "Журнал пуст. Подключись к посоху в разделе «Посох»."
                            lines.isEmpty() -> "Журнал пуст. Строки появятся сами: заряд приходит раз в 10 секунд."
                            query.isNotBlank() -> "По запросу ничего не нашлось."
                            filter == LogFilter.Errors -> "Ошибок и предупреждений нет."
                            else -> "Нечего показать с этим фильтром. Нажми «Всё»."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(PosohDimens.spaceM),
                ) {
                    items(shown, key = { it.at.toString() + it.text.hashCode() }) { line ->
                        LogRow(line)
                    }
                }
            }
        }

        Spacer(Modifier.height(PosohDimens.spaceM))

        // ---------- отправка команды ----------
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = connected,
                shape = MaterialTheme.shapes.small,
                label = { Text("Команда посоху") },
                placeholder = { Text("""{"cmd":"info"}""") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    sendAndHide(command)
                    command = ""
                }),
            )
            Spacer(Modifier.width(PosohDimens.spaceM))
            FilledIconButton(
                onClick = {
                    sendAndHide(command)
                    command = ""
                },
                enabled = connected && command.isNotBlank(),
                modifier = Modifier.size(52.dp),
            ) {
                Icon(StaffIcons.Send, contentDescription = "Отправить команду", Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.height(PosohDimens.spaceS))

        // Быстрые команды. Подписи — глаголы по-русски, теми же словами, что в справочнике
        // «Язык посоха» на экране «Посох»: одно действие не должно называться двумя способами.
        // Переназначения костей (map) тут нет намеренно: команда редкая и длинная,
        // её проще взять из справочника. FlowRow вместо Row, чтобы кнопки переносились
        // сами, когда их станет больше или экран окажется узким.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PosohDimens.spaceXs),
            verticalArrangement = Arrangement.spacedBy(PosohDimens.spaceXs),
        ) {
            listOf(
                "сведения" to """{"cmd":"info"}""",
                "взвести 1d20" to """{"cmd":"arm","n":1,"d":20}""",
                "снять взвод" to """{"cmd":"disarm"}""",
                "бросок 3d6" to """{"cmd":"roll","n":3,"d":6}""",
                "история" to """{"cmd":"hist","after":0}""",
            ).forEach { (label, text) ->
                TextButton(
                    onClick = { command = text },
                    enabled = connected,
                    // Своё поле вместо стандартного: у TextButton по краям 12 dp,
                    // и пять кнопок подряд расползались вширь.
                    contentPadding = PaddingValues(
                        horizontal = PosohDimens.spaceS,
                        vertical = PosohDimens.spaceXs,
                    ),
                ) { Text(label) }
            }
        }
    }
}

/** Кнопка выбора фильтра. Узкая, потому что живёт в строке заголовка рядом с названием. */
@Composable
private fun LogFilterChip(item: LogFilter, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(start = PosohDimens.spaceS)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(
                width = 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = PosohDimens.spaceM, vertical = PosohDimens.spaceS),
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}

/**
 * Что показывать в журнале. Посох присылает всё, а отбирает приложение: так ничего
 * не теряется и переключиться можно задним числом, не трогая прошивку.
 */
private enum class LogFilter(val label: String) {
    /** Всё подряд: отправленные команды, машинные события JSON, строки журнала посоха. */
    All("Всё"),

    /** Только строки журнала посоха, машинный JSON скрыт. Плюс заметки приложения о связи. */
    Info("Инфо"),

    /** Только строки журнала посоха с уровнем warn или ERROR!. */
    Errors("Ошибки");

    fun accepts(line: LogLine): Boolean {
        if (this == All) return true
        val message = staffLogMessage(line.text)
        if (message == null) {
            // Заметки самого приложения («связь разорвана») в «Инфо» оставляем: без них
            // непонятно, почему строки перестали приходить. В «Ошибках» они не нужны.
            return this == Info && line.direction == LogDirection.System
        }
        if (this == Info) return true
        val level = staffLogLevel(message)
        return level == "warn" || level == "ERROR!"
    }
}

/**
 * Уровень из строки журнала посоха. Строка выглядит так:
 * «2026-09-18 23:36:07.190 STRIKE     info: 20 переключений за 100 мс, УДАР».
 * Уровень — последнее слово перед первым двоеточием с пробелом. Во времени двоеточия
 * есть, но без пробела после, поэтому спутать нельзя.
 */
private fun staffLogLevel(message: String): String? {
    val end = message.indexOf(": ")
    if (end <= 0) return null
    return message.substring(0, end).trimEnd().substringAfterLast(' ')
}

/**
 * Строки, которые посох печатает в свой монитор порта, приходят как {"ev":"log","msg":"…"}.
 * Показываем их человеческим текстом: это срабатывания датчика и удары мимо взвода,
 * ради них в журнал и заглядывают.
 */
private fun staffLogMessage(text: String): String? {
    if (!text.contains("\"log\"")) return null
    val message = runCatching { JSONObject(text) }.getOrNull()?.optString("msg").orEmpty()
    return message.ifEmpty { null }
}

/** Одна строка журнала: время, направление, текст. */
@Composable
private fun LogRow(line: LogLine) {
    val scheme = MaterialTheme.colorScheme
    val staffMessage = if (line.direction == LogDirection.In) staffLogMessage(line.text) else null
    val color = when {
        staffMessage != null -> scheme.secondary
        line.direction == LogDirection.Out -> scheme.primary
        line.direction == LogDirection.System -> PosohTheme.extraColors.staffOffline
        else -> scheme.onSurface
    }
    val arrow = when (line.direction) {
        LogDirection.In -> "←"
        LogDirection.Out -> "→"
        LogDirection.System -> "·"
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = logTimeFormat.format(Date(line.at)),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(PosohDimens.spaceM))
        Text(
            text = arrow,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = color,
        )
        Spacer(Modifier.width(PosohDimens.spaceS))
        Text(
            text = staffMessage ?: line.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Имя файла по умолчанию: wizards-staff-logs-2026-09-18-01-23-45.txt */
private fun defaultFileName(): String =
    "wizards-staff-logs-${fileNameFormat.format(Date())}.txt"

/** Собирает текст файла: шапка и строки журнала в том же виде, что на экране. */
private fun buildLogFile(lines: List<LogLine>): String = buildString {
    appendLine("Посох мага — журнал обмена по Bluetooth")
    appendLine("Сохранено: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
    appendLine("Строк: ${lines.size}")
    appendLine("Обозначения: <- пришло с посоха, -> отправлено посоху, .. событие приложения")
    appendLine()
    lines.forEach { line ->
        val arrow = when (line.direction) {
            LogDirection.In -> "<-"
            LogDirection.Out -> "->"
            LogDirection.System -> ".."
        }
        val text = if (line.direction == LogDirection.In) {
            staffLogMessage(line.text) ?: line.text
        } else {
            line.text
        }
        appendLine("${logTimeFormat.format(Date(line.at))}  $arrow  $text")
    }
}
