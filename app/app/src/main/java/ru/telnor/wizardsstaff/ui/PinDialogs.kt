package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import ru.telnor.wizardsstaff.ui.theme.PosohDimens

/** Сколько цифр в PIN. Столько же ждёт прошивка, см. PIN_LENGTH в config.h. */
private const val PIN_LENGTH = 4

/** Оставляет только цифры и обрезает по длине PIN: в поле нельзя набрать ничего лишнего. */
private fun digitsOnly(text: String) = text.filter { it.isDigit() }.take(PIN_LENGTH)

/**
 * Окно ввода PIN. Появляется само, когда посох требует вход, а планшет его PIN не знает
 * или посох сохранённый не принял.
 *
 * Про «Отмена»: посох при этом никуда не денется, он продолжит переподключаться и
 * разрывать связь каждые десять секунд. Окно просто не будет лезть само, открыть его
 * снова можно кнопкой на экране «Посох».
 */
@Composable
fun PinPromptDialog(
    wrong: Boolean,
    waitSeconds: Int,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val ready = pin.length == PIN_LENGTH && waitSeconds == 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Посох просит PIN") },
        text = {
            Column {
                Text(
                    text = when {
                        waitSeconds > 0 ->
                            "Посох счёл это подбором и не принимает попытки ещё " +
                                "${waitSeconds} с. Столько же он не пустит и с верным PIN."
                        wrong -> "Посох не принял этот PIN. Попробуй ещё раз."
                        else -> "Без PIN посох не отвечает ни на что и сам разрывает связь " +
                            "через десять секунд. Планшет запомнит PIN и дальше будет " +
                            "называть его сам."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(PosohDimens.spaceL))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = digitsOnly(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = wrong,
                    label = { Text("PIN, $PIN_LENGTH цифры") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(pin) }, enabled = ready) { Text("Войти") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * Окно смены PIN. Новый набирается дважды не для красоты: посох примет опечатку молча,
 * и тогда PIN будет знать только этот планшет. Сотрёшь данные приложения — и в посох
 * не войти вообще, пока не перезальёшь прошивку с очисткой памяти.
 */
@Composable
fun ChangePinDialog(
    savedPin: String?,
    result: String?,
    onChange: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var oldPin by remember { mutableStateOf(savedPin.orEmpty()) }
    var newPin by remember { mutableStateOf("") }
    var repeatPin by remember { mutableStateOf("") }

    val mismatch = repeatPin.length == PIN_LENGTH && repeatPin != newPin
    val ready = oldPin.length == PIN_LENGTH &&
        newPin.length == PIN_LENGTH &&
        repeatPin == newPin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сменить PIN посоха") },
        text = {
            Column {
                Text(
                    text = "Новый PIN сразу ляжет в память посоха. Планшет запомнит его сам, " +
                        "а остальным устройствам придётся вводить заново.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(PosohDimens.spaceL))
                PinField(oldPin, "Старый PIN") { oldPin = it }
                Spacer(Modifier.height(PosohDimens.spaceM))
                PinField(newPin, "Новый PIN") { newPin = it }
                Spacer(Modifier.height(PosohDimens.spaceM))
                PinField(repeatPin, "Новый PIN ещё раз", isError = mismatch) { repeatPin = it }
                if (mismatch) {
                    Spacer(Modifier.height(PosohDimens.spaceS))
                    Text(
                        text = "Два новых PIN не совпадают.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (result != null) {
                    Spacer(Modifier.height(PosohDimens.spaceM))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChange(oldPin, newPin) }, enabled = ready) { Text("Сменить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

/** Поле под один PIN: цифры, скрытый ввод, ровно нужная длина. */
@Composable
private fun PinField(
    value: String,
    label: String,
    isError: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(digitsOnly(it)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Next,
        ),
    )
}
