package ru.telnor.wizardsstaff

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.telnor.wizardsstaff.ble.Connection
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.ui.ChangePinDialog
import ru.telnor.wizardsstaff.ui.CharacterAvatar
import ru.telnor.wizardsstaff.ui.CharacterScreen
import ru.telnor.wizardsstaff.ui.LogScreen
import ru.telnor.wizardsstaff.ui.PermissionDialog
import ru.telnor.wizardsstaff.ui.PinPromptDialog
import ru.telnor.wizardsstaff.ui.RollsScreen
import ru.telnor.wizardsstaff.ui.SheetActions
import ru.telnor.wizardsstaff.ui.StaffIcons
import ru.telnor.wizardsstaff.ui.StaffScreen
import ru.telnor.wizardsstaff.ui.theme.PosohDimens
import ru.telnor.wizardsstaff.ui.theme.PosohTheme
import ru.telnor.wizardsstaff.ui.theme.WizardsStaffTheme

/*
 * Каркас приложения: слева навигационная рейка с индикатором связи, сверху панель заголовка,
 * дальше содержимое раздела. Разметка по макетам в docs/design.
 *
 * Раздел «Статистика» пока заглушка: он появится в задаче D1 плана docs/ПЛАН-APP.md.
 */

/** Разделы приложения. */
private enum class Section(val title: String, val subtitle: String, val icon: ImageVector) {
    Rolls("Броски", "лента бросков посоха", StaffIcons.Dice),
    Characters("Персонажи", "листы и действия", StaffIcons.Person),
    Stats("Статистика", "сколько и как выпадало", StaffIcons.Chart),
    Staff("Посох", "подключение и настройки", StaffIcons.Staff),
}

/** Разрешения, без которых система не даст искать устройства рядом. */
private val blePermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WizardsStaffTheme {
                AppFrame()
            }
        }
    }
}

@Composable
private fun AppFrame(
    viewModel: StaffViewModel = viewModel(),
    characterViewModel: CharacterViewModel = viewModel(),
) {
    val scheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current

    // rememberSaveable, а не remember: при повороте планшета система пересоздаёт активность,
    // и обычный remember теряет значение. Приложение возвращалось в «Броски», даже если
    // человек читал логи. Саму связь с посохом поворот не рвёт: она живёт во ViewModel,
    // а та переживает пересоздание.
    var section by rememberSaveable { mutableStateOf(Section.Rolls) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var showPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var showBluetoothOffDialog by rememberSaveable { mutableStateOf(false) }
    var showChangePinDialog by rememberSaveable { mutableStateOf(false) }

    val connection by viewModel.connection.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val scanFinished by viewModel.scanFinished.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val authState by viewModel.auth.collectAsState()
    val pinPrompt by viewModel.pinPrompt.collectAsState()
    val pinChangeResult by viewModel.pinChangeResult.collectAsState()
    val rolls by viewModel.rolls.collectAsState()
    val battery by viewModel.batteryPercent.collectAsState()
    val firmware by viewModel.firmware.collectAsState()
    val armSeconds by viewModel.armSeconds.collectAsState()
    val dice by viewModel.dice.collectAsState()
    val armed by viewModel.armed.collectAsState()
    val clockSkew by viewModel.clockSkew.collectAsState()
    val logLines by viewModel.logLines.collectAsState()
    val characters by characterViewModel.characters.collectAsState()
    val character by characterViewModel.selected.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) viewModel.startScan()
    }

    /** Поиск посоха: сначала разрешения и включённый Bluetooth, потом сам поиск. */
    fun findStaff() {
        if (!viewModel.isBluetoothOn()) {
            showBluetoothOffDialog = true
            return
        }
        val missing = blePermissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        when {
            !missing -> viewModel.startScan()
            // На Android 10 система спросит про геолокацию: сначала объясняем, зачем она.
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> showPermissionDialog = true
            else -> permissionLauncher.launch(blePermissions)
        }
    }

    // imePadding здесь намеренно НЕ стоит. Он подгоняет высоту окна под клавиатуру, и тогда
    // разметка сжимается: журнал схлопывается, а строка ввода с быстрыми командами уезжает
    // за край экрана. Вместо этого клавиатура ложится поверх, а экран, которому она мешает,
    // сам отодвигает содержимое прокруткой - см. LogScreen. Размеры при этом не меняются
    // ни в альбомной ориентации, ни в портретной.
    Scaffold(containerColor = scheme.background) { innerPadding ->
        Row(Modifier.fillMaxSize().padding(innerPadding)) {

            NavigationRail(
                containerColor = scheme.surface,
                header = {
                    ConnectionBadge(
                        connected = connection == Connection.Connected,
                        percent = battery,
                        onClick = { section = Section.Staff },
                    )
                },
                modifier = Modifier.width(PosohDimens.navigationRailWidth),
            ) {
                Spacer(Modifier.height(PosohDimens.spaceL))
                Section.entries.forEach { item ->
                    NavigationRailItem(
                        selected = section == item,
                        onClick = {
                            section = item
                            showLogs = false
                        },
                        icon = {
                            Icon(item.icon, contentDescription = item.title, Modifier.size(22.dp))
                        },
                        label = { Text(item.title, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = scheme.onPrimaryContainer,
                            selectedTextColor = scheme.onSurface,
                            indicatorColor = scheme.primaryContainer,
                            unselectedIconColor = scheme.onSurfaceVariant,
                            unselectedTextColor = scheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            Column(Modifier.fillMaxSize()) {
                TopBar(
                    section = section,
                    logsOpen = showLogs,
                    rollCount = rolls.size,
                    connection = connection,
                    armedFormula = armed?.formula,
                    characters = characters,
                    character = character,
                    onSelectCharacter = characterViewModel::select,
                )
                HorizontalDivider(color = scheme.outlineVariant)

                when {
                    showLogs -> LogScreen(
                        lines = logLines,
                        connected = connection == Connection.Connected,
                        onBack = { showLogs = false },
                        onSend = viewModel::sendRaw,
                        onClear = viewModel::clearLog,
                    )

                    section == Section.Rolls -> RollsScreen(
                        rolls = rolls,
                        armed = armed,
                        dice = dice,
                        connection = connection,
                        batteryPercent = battery,
                        onArm = viewModel::arm,
                        onDisarm = viewModel::disarm,
                        onToggleDiscarded = viewModel::toggleDiscarded,
                        onSaveNote = viewModel::setNote,
                        onLoadMore = viewModel::loadMoreRolls,
                        onGoToStaff = { section = Section.Staff },
                    )

                    section == Section.Staff -> StaffScreen(
                        connection = connection,
                        devices = devices,
                        scanFinished = scanFinished,
                        connectedDevice = connectedDevice,
                        batteryPercent = battery,
                        firmware = firmware,
                        armSeconds = armSeconds,
                        armed = armed,
                        clockSkew = clockSkew,
                        onScan = { findStaff() },
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect,
                        onSyncTime = viewModel::syncTime,
                        onOpenLogs = { showLogs = true },
                        onChangePin = { showChangePinDialog = true },
                        onEnterPin = viewModel::showPinPrompt,
                        authOpen = authState is AuthState.Open || authState is AuthState.NotNeeded,
                        pinNeeded = authState is AuthState.NeedPin,
                        onExplainPermission = { showPermissionDialog = true },
                    )

                    section == Section.Characters -> CharacterScreen(
                        character = character,
                        actions = SheetActions(
                            heroPoints = characterViewModel::addHeroPoints,
                            hp = characterViewModel::addHp,
                            tempHp = characterViewModel::addTempHp,
                            wounded = characterViewModel::addWounded,
                            dying = characterViewModel::setDying,
                        ),
                    )

                    else -> Placeholder(
                        title = "Статистика",
                        text = "Сколько бросков за сессию, среднее по d20, распределение " +
                            "и доля критов. Появится, когда броски начнут сохраняться в базу.",
                    )
                }
            }
        }
    }

    if (showPermissionDialog) {
        PermissionDialog(
            onAllow = {
                showPermissionDialog = false
                permissionLauncher.launch(blePermissions)
            },
            onDismiss = { showPermissionDialog = false },
        )
    }

    // Окно ввода PIN живёт на уровне каркаса, а не экрана «Посох»: посох требует вход
    // независимо от того, какой раздел сейчас открыт, и спрашивать надо там же, где человек.
    if (pinPrompt) {
        val state = authState
        PinPromptDialog(
            wrong = state is AuthState.NeedPin && state.wrong,
            waitSeconds = (state as? AuthState.NeedPin)?.waitSeconds ?: 0,
            onSubmit = viewModel::submitPin,
            onDismiss = viewModel::dismissPinPrompt,
        )
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            savedPin = viewModel.savedPin(),
            result = pinChangeResult,
            onChange = viewModel::changePin,
            onDismiss = {
                showChangePinDialog = false
                viewModel.clearPinChangeResult()
            },
        )
    }

    if (showBluetoothOffDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothOffDialog = false },
            title = { Text("Bluetooth выключен") },
            text = { Text("Включи Bluetooth в шторке планшета и попробуй найти посох снова.") },
            confirmButton = {
                TextButton(onClick = { showBluetoothOffDialog = false }) { Text("Понятно") }
            },
        )
    }
}

/** Индикатор связи в шапке рейки: иконка и заряд. Нажатие ведёт в раздел «Посох». */
@Composable
private fun ConnectionBadge(connected: Boolean, percent: Int?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(top = PosohDimens.spaceM)
            .width(64.dp)
            .background(
                if (connected) scheme.primaryContainer else scheme.surfaceVariant,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = PosohDimens.spaceS),
    ) {
        Icon(
            imageVector = if (connected) StaffIcons.Bluetooth else StaffIcons.BluetoothOff,
            contentDescription = if (connected) "Посох на связи" else "Посоха нет рядом",
            tint = if (connected) scheme.primary else PosohTheme.extraColors.staffOffline,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = if (connected && percent != null) "$percent %" else if (connected) "есть" else "нет",
            style = MaterialTheme.typography.labelMedium,
            color = if (connected) scheme.onPrimaryContainer else PosohTheme.extraColors.staffOffline,
        )
    }
}

/** Верхняя панель: название раздела, счётчик бросков и состояние посоха справа. */
@Composable
private fun TopBar(
    section: Section,
    logsOpen: Boolean,
    rollCount: Int,
    connection: Connection,
    armedFormula: String?,
    characters: List<CharacterSheet>,
    character: CharacterSheet?,
    onSelectCharacter: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PosohDimens.topBarHeight)
            .background(scheme.surface)
            .padding(horizontal = PosohDimens.screenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (logsOpen) "Логи посоха" else section.title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.width(PosohDimens.spaceM))
        // У раздела «Персонажи» вместо подписи стоит переключатель: подпись «листы
        // и действия» ничего не добавляла бы рядом с именем открытого персонажа.
        if (!logsOpen && section == Section.Characters && character != null) {
            CharacterChip(
                character = character,
                characters = characters,
                onSelect = onSelectCharacter,
            )
        } else {
            Text(
                text = if (logsOpen) {
                    "обмен по Bluetooth"
                } else if (section == Section.Rolls && rollCount > 0) {
                    "сегодня · $rollCount " + plural(rollCount, "бросок", "броска", "бросков")
                } else {
                    section.subtitle
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        StaffChip(connection, armedFormula)
    }
}

/**
 * Чип выбора персонажа в верхней панели. Списка персонажей сбоку нет (SCREENS.md, 3):
 * на одного-двух персонажей колонка съедала бы треть экрана.
 *
 * Пока персонаж один, меню всё равно открывается: пустое нажатие объяснимее, чем
 * чип, который иногда нажимается, а иногда нет.
 */
@Composable
private fun CharacterChip(
    character: CharacterSheet,
    characters: List<CharacterSheet>,
    onSelect: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(scheme.surface)
                .border(BorderStroke(1.dp, scheme.outlineVariant), RoundedCornerShape(22.dp))
                .clickable { open = true }
                .padding(start = 6.dp, end = 12.dp),
        ) {
            CharacterAvatar(character.name, size = 32)
            Text(character.name.substringBefore(' '), style = MaterialTheme.typography.labelLarge)
            Icon(
                imageVector = StaffIcons.ChevronDown,
                contentDescription = "Выбрать персонажа",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            characters.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name) },
                    onClick = {
                        onSelect(item.id)
                        open = false
                    },
                )
            }
        }
    }
}

/** Чип состояния посоха в правом углу панели. */
@Composable
private fun StaffChip(connection: Connection, armedFormula: String?) {
    val scheme = MaterialTheme.colorScheme
    val connected = connection == Connection.Connected
    val text = when {
        connected && armedFormula != null -> "Взведён · $armedFormula"
        connected -> "На связи"
        connection == Connection.Connecting -> "Подключаемся…"
        connection == Connection.Scanning -> "Ищем посох…"
        connection == Connection.Lost -> "Связь потеряна"
        else -> "Посох не подключён"
    }
    Row(
        modifier = Modifier
            .background(
                if (connected) scheme.primaryContainer else scheme.surfaceVariant,
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = PosohDimens.spaceL, vertical = PosohDimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (connected) PosohTheme.extraColors.connected else PosohTheme.extraColors.staffOffline,
                    CircleShape,
                )
        )
        Spacer(Modifier.width(PosohDimens.spaceS))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (connected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}

/** Раздел, которого пока нет. */
@Composable
private fun Placeholder(title: String, text: String) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(PosohDimens.screenPadding), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(560.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(PosohDimens.spaceM))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Русское склонение: 1 бросок, 2 броска, 5 бросков. */
private fun plural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
