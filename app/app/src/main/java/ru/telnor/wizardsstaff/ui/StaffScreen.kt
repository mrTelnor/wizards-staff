package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.telnor.wizardsstaff.ArmedState
import ru.telnor.wizardsstaff.ble.Connection
import ru.telnor.wizardsstaff.ble.FoundDevice
import ru.telnor.wizardsstaff.ui.theme.PosohDialogShape
import ru.telnor.wizardsstaff.ui.theme.PosohDimens

/*
 * Экран «Посох»: поиск, подключение, состояние. Разметка по макету
 * docs/design/png/Staff-Disconnected.png.
 */

/** Ширина содержимого экрана по макету. */
private val ContentWidth = 616.dp

@Composable
fun StaffScreen(
    connection: Connection,
    devices: List<FoundDevice>,
    scanFinished: Boolean,
    connectedDevice: FoundDevice?,
    batteryPercent: Int?,
    firmware: String?,
    armed: ArmedState?,
    clockSkew: Long?,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSyncTime: () -> Unit,
    onOpenLogs: () -> Unit,
    onExplainPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PosohDimens.screenPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.width(ContentWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (connection == Connection.Connected) {
                ConnectedCard(
                    device = connectedDevice,
                    batteryPercent = batteryPercent,
                    firmware = firmware,
                    armed = armed,
                    clockSkew = clockSkew,
                    onDisconnect = onDisconnect,
                    onSyncTime = onSyncTime,
                    onOpenLogs = onOpenLogs,
                )
                Spacer(Modifier.height(PosohDimens.spaceL))
                CommandsCard()
            } else {
                Disconnected(connection, devices, scanFinished, onScan, onConnect, onExplainPermission)
            }
        }
    }
}

@Composable
private fun Disconnected(
    connection: Connection,
    devices: List<FoundDevice>,
    scanFinished: Boolean,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onExplainPermission: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scanning = connection == Connection.Scanning

    Spacer(Modifier.height(PosohDimens.spaceXxxl))
    Icon(
        imageVector = StaffIcons.Staff,
        contentDescription = null,
        tint = scheme.outline,
        modifier = Modifier.size(104.dp),
    )
    Spacer(Modifier.height(PosohDimens.spaceXl))
    Text(
        text = when (connection) {
            Connection.Lost -> "Связь с посохом потеряна"
            Connection.Connecting -> "Подключаемся к посоху"
            else -> "Посох не подключён"
        },
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(PosohDimens.spaceM))
    Text(
        text = when (connection) {
            Connection.Lost ->
                "Посох выключен или унесён далеко. Как только он окажется рядом и включится, " +
                    "связь восстановится сама."
            else ->
                "Приложение работает и без него: история бросков и листы персонажей на месте. " +
                    "Новые броски начнут записываться, как только посох окажется рядом."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(PosohDimens.spaceXxl))
    Button(
        onClick = onScan,
        enabled = !scanning,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.height(52.dp),
    ) {
        if (scanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = scheme.onPrimary,
            )
        } else {
            Icon(StaffIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(PosohDimens.spaceS))
        Text(if (scanning) "Ищем посох…" else "Найти посох")
    }

    if (devices.isNotEmpty() || scanning || scanFinished) {
        Spacer(Modifier.height(PosohDimens.spaceXl))
        FoundCard(devices, scanning, scanFinished, onConnect)
    }

    Spacer(Modifier.height(PosohDimens.spaceL))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onExplainPermission).padding(PosohDimens.spaceS),
    ) {
        Icon(
            imageVector = StaffIcons.Info,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(PosohDimens.spaceS))
        Text(
            text = "Зачем Android просит геолокацию для поиска",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
        )
    }
}

@Composable
private fun FoundCard(
    devices: List<FoundDevice>,
    scanning: Boolean,
    scanFinished: Boolean,
    onConnect: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PosohDimens.spaceXl, vertical = PosohDimens.spaceL),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "НАЙДЕНО ПОБЛИЗОСТИ",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = when {
                    scanning -> "идёт поиск…"
                    scanFinished -> "поиск завершён · ${devices.size} шт."
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        if (devices.isEmpty()) {
            Text(
                text = if (scanning) {
                    "Пока пусто. Посох должен быть включён: на плате мигает синий светодиод."
                } else {
                    "Ничего не нашлось. Проверь, включён ли посох, и поищи ещё раз."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = PosohDimens.spaceXl,
                    end = PosohDimens.spaceXl,
                    bottom = PosohDimens.spaceXl,
                ),
            )
        }

        devices.forEach { device ->
            HorizontalDivider(color = scheme.outlineVariant)
            DeviceRow(device, onConnect)
        }
    }
}

@Composable
private fun DeviceRow(device: FoundDevice, onConnect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val alpha = if (device.isStaff) 1f else 0.55f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PosohDimens.spaceXl, vertical = PosohDimens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (device.isStaff) scheme.primaryContainer else scheme.surfaceContainer,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (device.isStaff) StaffIcons.Staff else StaffIcons.Bluetooth,
                contentDescription = null,
                tint = if (device.isStaff) scheme.primary else scheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(PosohDimens.spaceL))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (device.isStaff) "Посох мага" else device.name ?: "Неизвестное устройство",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = "${device.address} · сигнал ${device.rssi} дБм",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        if (device.isStaff) {
            OutlinedButton(
                onClick = { onConnect(device.address) },
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.height(44.dp),
            ) {
                Text("Подключить")
            }
        }
    }
}

@Composable
private fun ConnectedCard(
    device: FoundDevice?,
    batteryPercent: Int?,
    firmware: String?,
    armed: ArmedState?,
    clockSkew: Long?,
    onDisconnect: () -> Unit,
    onSyncTime: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Spacer(Modifier.height(PosohDimens.spaceXxxl))
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(PosohDimens.spaceXl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(56.dp).background(scheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = StaffIcons.Staff,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(PosohDimens.spaceL))
                Column(Modifier.weight(1f)) {
                    Text("Посох мага", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = device?.address ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text("Отключить")
                }
            }

            Spacer(Modifier.height(PosohDimens.spaceXl))
            HorizontalDivider(color = scheme.outlineVariant)
            Spacer(Modifier.height(PosohDimens.spaceXl))

            Row(
                horizontalArrangement = Arrangement.spacedBy(PosohDimens.spaceXxl),
                verticalAlignment = Alignment.Bottom,
            ) {
                StaffFact("ЗАРЯД", batteryPercent?.let { "$it %" } ?: "—")
                StaffFact("СОСТОЯНИЕ", if (armed != null) "взведён ${armed.formula}" else "покой")
                StaffFact("ПРОШИВКА", firmware ?: "—")
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onOpenLogs,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Icon(StaffIcons.Logs, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(PosohDimens.spaceS))
                    Text("Логи")
                }
            }

            Spacer(Modifier.height(PosohDimens.spaceXl))
            HorizontalDivider(color = scheme.outlineVariant)
            Spacer(Modifier.height(PosohDimens.spaceL))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "ЧАСЫ ПОСОХА",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(PosohDimens.spaceXs))
                    Text(
                        text = describeSkew(clockSkew),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onSyncTime,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text("Синхронизировать")
                }
            }
        }
    }

    Spacer(Modifier.height(PosohDimens.spaceL))
    Text(
        text = "Часы посоха подводятся по планшету при каждом подключении. " +
            "Планшет берёт точное время из сети, посох хранит его в DS3231 от батарейки.",
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
    )
}

/**
 * Короткий справочник по обмену с посохом: что можно ему сказать и что он отвечает.
 * Эти же строки можно отправлять руками на экране логов.
 */
@Composable
private fun CommandsCard() {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(PosohDimens.spaceXl)) {
            Text(
                text = "ЯЗЫК ПОСОХА",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(PosohDimens.spaceXs))
            Text(
                text = "Посох и планшет обмениваются строками JSON, по одной на сообщение. " +
                    "Приложение шлёт их само, но то же самое можно отправить руками на экране логов.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(PosohDimens.spaceL))
            Text("Что можно сказать посоху", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(PosohDimens.spaceS))

            CommandRow(
                command = """{"cmd":"info"}""",
                answer = """{"ev":"info","fw":"0.3.0","hist":5,"dice":[2,4,…],"ts":1789000000}""",
                explain = "Версия прошивки, размер истории, кубики кнопок и время часов посоха.",
            )
            CommandRow(
                command = """{"cmd":"arm","n":1,"d":20}""",
                answer = """{"ev":"state","st":"armed","n":1,"d":20}""",
                explain = "Взвести: следующий удар об пол бросит 1d20. Взвод спадает через 30 секунд.",
            )
            CommandRow(
                command = """{"cmd":"disarm"}""",
                answer = """{"ev":"state","st":"idle"}""",
                explain = "Снять взвод. Невзведённый посох удары не считает.",
            )
            CommandRow(
                command = """{"cmd":"roll","n":3,"d":6}""",
                answer = """{"ev":"roll","id":7,"n":3,"d":6,"v":[2,1,4],"t":7,"ts":1789000000}""",
                explain = "Бросок без удара, для отладки: посох кидает и сразу отвечает результатом.",
            )
            CommandRow(
                command = """{"cmd":"time","epoch":1789000000}""",
                answer = """{"ev":"ok","cmd":"time"}""",
                explain = "Выставить часы. Секунды с 1 января 1970 года по UTC; приложение делает это при подключении.",
            )

            Spacer(Modifier.height(PosohDimens.spaceL))
            Text("Что посох говорит сам", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(PosohDimens.spaceS))

            CommandRow(
                command = """{"ev":"bat","mv":3958,"pct":69}""",
                answer = null,
                explain = "Заряд: милливольты и проценты, раз в 10 секунд.",
            )
            CommandRow(
                command = """{"ev":"roll","id":8,"n":1,"d":20,"v":[20],"t":20,"ts":…}""",
                answer = null,
                explain = "Бросок после удара об пол. Поле ts — время посоха, ноль значит «часы не выставлены».",
            )
            CommandRow(
                command = """{"ev":"err","msg":"unknown cmd"}""",
                answer = null,
                explain = "Команда не понята: опечатка в JSON или неизвестное имя.",
            )
        }
    }
}

/** Строка справочника: команда, ответ и пояснение по-русски. */
@Composable
private fun CommandRow(command: String, answer: String?, explain: String) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = PosohDimens.spaceM)) {
        Text(
            text = command,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = scheme.primary,
        )
        if (answer != null) {
            Text(
                text = "→ $answer",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = scheme.onSurface,
            )
        }
        Text(
            text = explain,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** Человеческое описание расхождения часов посоха и планшета. */
private fun describeSkew(skew: Long?): String = when {
    skew == null -> "Часы посоха не выставлены"
    skew == 0L -> "Совпадают с планшетом"
    kotlin.math.abs(skew) < 60 -> "Расходятся с планшетом на ${kotlin.math.abs(skew)} с"
    else -> "Расходятся с планшетом на ${kotlin.math.abs(skew) / 60} мин"
}

@Composable
private fun StaffFact(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(PosohDimens.spaceXs))
        Text(text = value, style = MaterialTheme.typography.displaySmall)
    }
}

/**
 * Объяснение, зачем Android 10 требует геолокацию для поиска Bluetooth-устройств.
 * Показывается перед первым поиском, чтобы запрос системы не выглядел слежкой.
 */
@Composable
fun PermissionDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PosohDialogShape,
        icon = {
            Box(
                modifier = Modifier.size(48.dp).background(scheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = StaffIcons.Info,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        title = {
            Text("Нужен доступ к геолокации", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column {
                Text(
                    text = "Так устроен Android 10: искать устройства Bluetooth рядом разрешено " +
                        "только приложениям с доступом к геолокации. Это требование системы, " +
                        "а не наша выдумка.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(PosohDimens.spaceM))
                Text(
                    text = "Координаты приложение не запрашивает, не показывает и никуда " +
                        "не отправляет. Оно вообще ничего не отправляет наружу.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = {
            Button(onClick = onAllow, modifier = Modifier.height(44.dp)) { Text("Разрешить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(44.dp)) { Text("Не сейчас") }
        },
    )
}
