package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.ui.theme.PosohDimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Сохранение и загрузка листа персонажа: две кнопки в верхней панели и три окна к ним.
 *
 * Сохранение — копия персонажа со всеми частями, лежит в той же таблице, см.
 * `CharacterRecord`. Загрузка в этот заход только показывает список: что делает
 * выбор строки, решается отдельно, а мёртвый выбор честнее придуманного.
 */

/** Время в названии сохранения и в списке: «2026-09-22 00:15:33». */
private val SAVE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun moment(millis: Long): String =
    SAVE_TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/**
 * Кнопки «Сохранить» и «Загрузить» рядом с чипом персонажа.
 *
 * `saved` — совпадает ли открытый лист с каким-нибудь своим сохранением. По нему
 * решается, спрашивать ли перед загрузкой.
 */
@Composable
fun CharacterSaveButtons(
    character: CharacterSheet,
    saves: List<CharacterSheet>,
    saved: Boolean,
    onSave: (String) -> Unit,
) {
    var saveOpen by rememberSaveable { mutableStateOf(false) }
    var askUnsaved by rememberSaveable { mutableStateOf(false) }
    var listOpen by rememberSaveable { mutableStateOf(false) }

    Row {
        TextButton(onClick = { saveOpen = true }) { Text("Сохранить") }
        TextButton(
            onClick = {
                // Несохранённый лист загрузкой затрёт, поэтому сначала вопрос.
                if (saved) listOpen = true else askUnsaved = true
            },
        ) { Text("Загрузить") }
    }

    if (saveOpen) {
        SaveDialog(
            // Имя персонажа и время: два сохранения подряд не столкнутся именами,
            // а в списке сразу видно, что когда снято.
            suggested = "${character.name} ${moment(System.currentTimeMillis())}",
            taken = saves.mapNotNull { it.record.saveName }.toSet(),
            onDismiss = { saveOpen = false },
            onSave = { name ->
                saveOpen = false
                onSave(name)
            },
        )
    }

    if (askUnsaved) {
        AlertDialog(
            onDismissRequest = { askUnsaved = false },
            title = { Text("Состояние не сохранено") },
            text = {
                Text(
                    text = "Текущее состояние персонажа не сохранено! Продолжить?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        askUnsaved = false
                        listOpen = true
                    },
                ) { Text("Да") }
            },
            dismissButton = {
                TextButton(onClick = { askUnsaved = false }) { Text("Нет") }
            },
        )
    }

    if (listOpen) {
        SavesDialog(saves = saves, onDismiss = { listOpen = false })
    }
}

/**
 * Окно сохранения. Занятое имя не отвергается после нажатия, а гасит саму кнопку:
 * человек видит отказ сразу, а не после того, как решил, что дело сделано.
 */
@Composable
private fun SaveDialog(
    suggested: String,
    taken: Set<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(suggested) }
    val trimmed = name.trim()
    val busy = trimmed in taken
    val ready = trimmed.isNotEmpty() && !busy

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сохранить персонажа") },
        text = {
            Column {
                Text(
                    text = "Лист сохранится целиком: числа, состояние в бою, оружие, " +
                        "черты и снаряжение.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(PosohDimens.spaceL))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    isError = busy,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) {
                    Spacer(Modifier.height(PosohDimens.spaceS))
                    Text(
                        text = "Такое название уже существует, придумайте новое.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(trimmed) }, enabled = ready) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отменить") }
        },
    )
}

/**
 * Список сохранений, свежие сверху. Выбор строки пока ничего не делает: сама загрузка
 * — отдельная задача, и подсовывать под неё нерабочее нажатие ни к чему.
 */
@Composable
private fun SavesDialog(saves: List<CharacterSheet>, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сохранения персонажа") },
        text = {
            if (saves.isEmpty()) {
                Text(
                    text = "У этого персонажа сохранений пока нет.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    saves.forEach { save ->
                        Column(Modifier.fillMaxWidth().padding(vertical = PosohDimens.spaceS)) {
                            Text(
                                text = save.record.saveName.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = save.record.savedAt?.let(::moment).orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}
