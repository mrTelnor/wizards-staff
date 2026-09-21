package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.telnor.wizardsstaff.db.CharacterRecord
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.rules.ArmorCategory
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.rules.Sheet
import ru.telnor.wizardsstaff.rules.armorClassParts
import ru.telnor.wizardsstaff.rules.shieldBroken
import ru.telnor.wizardsstaff.rules.shieldBrokenThreshold
import ru.telnor.wizardsstaff.ui.theme.PosohDimens

/*
 * Блок «Класс брони и щит» вкладки «Обзор». Одна карточка на всю строку, четыре части
 * слева направо: сам КБ, надетый доспех, владение бронёй и щит.
 *
 * КБ стоит слева и нарисован силуэтом доспеха: это главное число блока, за столом
 * его спрашивают чаще всего.
 *
 * Всё здесь нарочно мелкое. Блок обязан влезать в одну строку не выше блока жизни:
 * четыре части, вставшие столбиком, отъедали треть экрана, и за ними переставало быть
 * видно то, ради чего лист открывают.
 *
 * Правится только щит: его ПЗ убывают от каждого блока. Доспех и владение
 * показываются — они меняются раз в несколько уровней, и место им в редакторе листа.
 *
 * Ни одного выведенного числа тут не хранится: КБ и его слагаемые считает `Pf2.kt`,
 * предел ловкости и бонус доспеха приходят из справочника `ArmorCatalog` по имени брони,
 * а порог поломки щита — половина его максимальных ПЗ.
 */

/**
 * Силуэт доспеха. Тело почти квадратное, за квадрат выходят только рукава, поэтому
 * ширина больше высоты. Слагаемые и название брони встают под ним по той же ширине.
 */
private val TunicWidth = 70.dp
private val TunicHeight = 58.dp

/**
 * Свободное место строки раскладывается поровну: `SpaceEvenly` даёт одинаковый зазор
 * перед первой частью, между каждой парой соседей и после последней. Значит у всех
 * четырёх частей поля слева и справа — до края карточки или до разделителя — равны
 * по построению, сколько бы места ни осталось.
 *
 * Поэтому у карточки нет боковых полей, а у разделителя — своих отступов: иначе они
 * прибавились бы к крайним частям и равенство сломалось бы.
 */

/** Имя категории в столбце владения. Общая ширина держит бейджи в колонку. */
private val ProficiencyNameWidth = 60.dp

/**
 * Правая половина щита: числа, кнопки и шкала под ними. Ширина задана числом, а не
 * содержимым: по ней же растянута шкала, и от содержимого она бы прыгала на каждом
 * изменении ПЗ.
 */
private val ShieldColumnWidth = 104.dp

/** Силуэт щита. Меньше доспеха: он и в жизни меньше, и число в нём короче. */
private val ShieldWidth = 54.dp
private val ShieldHeight = 62.dp

/** Кнопки щита мельче обычных: их тут четыре штуки в тесном столбце. */
private val ShieldStepper = 26.dp

/**
 * Ниже этой ширины четыре части в строку уже не помещаются и встают в два ряда.
 * Планшет, ради которого всё делается, шире и в портретной.
 */
private val OneRowFrom = 500.dp

@Composable
fun ArmorCard(
    character: CharacterSheet,
    actions: SheetActions,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val sheet = character.sheet
    val record = character.record

    // Сломанный щит не даёт КБ, даже поднятый: по правилам он просто груз в руке.
    val broken = shieldBroken(record.shieldHp, record.shieldMaxHp)
    val shieldBonus = if (record.shieldRaised && !broken) record.shieldAc else 0

    // Ширину меряет сам блок, а не весь лист: остальным хватает и меньшей, а этому
    // нужно знать, влезут ли в строку именно его четыре части.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val oneRow = maxWidth >= OneRowFrom
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            border = BorderStroke(1.dp, scheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = PosohDimens.spaceS)) {
                if (oneRow) {
                    // IntrinsicSize.Min: так разделители дотягиваются ровно до высоты
                    // самой высокой части, а не до придуманного заранее числа.
                    // Каждая часть занимает ровно столько, сколько нужно её
                    // содержимому; остаток строки раскладывает `SpaceEvenly`.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ArmorClassPart(sheet, shieldBonus, partWidth())
                        PartDivider(0.dp)
                        ArmorPart(sheet, partWidth())
                        PartDivider(0.dp)
                        ProficiencyPart(sheet, partWidth())
                        PartDivider(0.dp)
                        ShieldPart(record, actions, partWidth())
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ArmorClassPart(sheet, shieldBonus, partWidth())
                        PartDivider(0.dp)
                        ArmorPart(sheet, partWidth())
                    }
                    RowDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ProficiencyPart(sheet, partWidth())
                        PartDivider(0.dp)
                        ShieldPart(record, actions, partWidth())
                    }
                }
            }
        }
    }
}

/**
 * Ширина части по её содержимому: `IntrinsicSize.Max` спрашивает, сколько нужно
 * в одну строку. Поля вокруг частей не задаются здесь — их поровну раскладывает
 * `SpaceEvenly` в строке.
 */
private fun partWidth(): Modifier = Modifier.width(IntrinsicSize.Max)

/** Тот же разделитель, когда части сложились в два ряда. */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(vertical = PosohDimens.spaceS, horizontal = PosohDimens.spaceM),
    )
}

/**
 * КБ: одно число в силуэте доспеха, и больше ничего.
 *
 * Ни слагаемых, ни названия брони тут нет — по решению автора от 2026-09-21. Всё это
 * и так лежит рядом: тип и бонус брони в соседней части, владение в следующей.
 */
@Composable
private fun ArmorClassPart(sheet: Sheet, shieldBonus: Int, modifier: Modifier = Modifier) {
    LabeledPart("КБ", modifier) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TunicBadge(sheet.armorClassParts(shieldBonus).total.toString())
        }
    }
}

/** Число в силуэте доспеха: плечи, рукава и подол. */
@Composable
private fun TunicBadge(value: String) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(TunicWidth, TunicHeight), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val path = tunicPath(size.width, size.height)
            drawPath(path, scheme.primaryContainer)
            drawPath(path, scheme.primary.copy(alpha = 0.5f), style = Stroke(width = 1.5.dp.toPx()))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = BigNumber),
            color = scheme.onPrimaryContainer,
        )
    }
}

/**
 * Силуэт доспеха. Тело — квадрат от 0,13 до 0,87 ширины, за него выходят только рукава.
 * Доли ширины и высоты, чтобы рисунок жил при любом размере.
 */
private fun tunicPath(w: Float, h: Float): Path = Path().apply {
    moveTo(0.38f * w, 0.02f * h)
    quadraticTo(0.50f * w, 0.16f * h, 0.62f * w, 0.02f * h)  // вырез горловины
    lineTo(0.72f * w, 0.00f * h)
    lineTo(1.00f * w, 0.10f * h)                             // правый рукав
    lineTo(1.00f * w, 0.38f * h)
    lineTo(0.87f * w, 0.42f * h)
    lineTo(0.87f * w, 1.00f * h)                             // правый бок и подол
    lineTo(0.13f * w, 1.00f * h)
    lineTo(0.13f * w, 0.42f * h)
    lineTo(0.00f * w, 0.38f * h)                             // левый рукав
    lineTo(0.00f * w, 0.10f * h)
    lineTo(0.28f * w, 0.00f * h)
    close()
}

/**
 * Доспех: тип галочками, а рядом столбиком предел ловкости и бонус к КБ. Всё это
 * только показывается.
 *
 * Оба столбца одной высоты: `IntrinsicSize.Min` берёт высоту у более длинного
 * (у списка типов), а `SpaceBetween` растягивает по ней два поля. Подгонять числами
 * бесполезно — от размера шрифта в настройках планшета они разъедутся.
 */
@Composable
private fun ArmorPart(sheet: Sheet, modifier: Modifier = Modifier) {
    val armor = sheet.armor
    val category = armor?.category ?: ArmorCategory.UNARMORED
    LabeledPart("Доспех", modifier) {
        // Содержимое по центру: иначе слева до квадратиков один зазор,
        // а справа до полей другой.
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FieldLabel("Тип")
                Spacer(Modifier.height(2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    ArmorCategory.entries.forEach { CategoryCheck(it, checked = it == category) }
                }
            }
            Spacer(Modifier.width(PosohDimens.spaceS))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight(),
            ) {
                // Предел ловкости со знаком: он ограничивает именно модификатор, а не значение.
                FieldBox("Макс. лвк.", armor?.let { signed(it.dexCap) } ?: "—")
                FieldBox("Бонус КБ", armor?.let { signed(it.acBonus) } ?: "—")
            }
        }
    }
}

/** Строка с галочкой: одна из четырёх категорий брони. Отмечена та, что надета. */
@Composable
private fun CategoryCheck(category: ArmorCategory, checked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        CheckSquare(checked)
        Spacer(Modifier.width(PosohDimens.spaceXs))
        Text(
            text = category.short,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            color = scheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * Квадратик с галочкой. Свой, а не `Checkbox`: тот занимает 48 точек под палец и всем
 * видом обещает, что по нему можно нажать, — а здесь это просто отметка, как в бланке.
 */
@Composable
private fun CheckSquare(checked: Boolean, side: Dp = 12.dp) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .size(side)
            .background(if (checked) scheme.primary else Color.Transparent, shape)
            .border(1.dp, if (checked) scheme.primary else scheme.outline, shape),
    ) {
        if (checked) {
            Canvas(Modifier.matchParentSize()) {
                val tick = Path().apply {
                    moveTo(0.24f * size.width, 0.52f * size.height)
                    lineTo(0.43f * size.width, 0.72f * size.height)
                    lineTo(0.78f * size.width, 0.28f * size.height)
                }
                drawPath(
                    path = tick,
                    color = scheme.onPrimary,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** Подпись поля внутри части: мельче и без верхнего регистра, чтобы не спорить с «ДОСПЕХ». */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

/** Поле со значением: рамка, как у поля ввода в бланке, но число в ней только показано. */
@Composable
private fun FieldBox(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FieldLabel(label)
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(24.dp)
                .background(scheme.surfaceContainerLow, shape)
                .border(1.dp, scheme.outlineVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 15.sp),
                maxLines = 1,
            )
        }
    }
}

/** Владение бронёй: четыре категории со степенями. Подсвечена та, что идёт в КБ. */
@Composable
private fun ProficiencyPart(sheet: Sheet, modifier: Modifier = Modifier) {
    val worn = sheet.armor?.category ?: ArmorCategory.UNARMORED
    LabeledPart("Навык", modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            ArmorCategory.entries.forEach { category ->
                ProficiencyRow(
                    category = category,
                    rank = sheet.armorRanks[category] ?: Rank.UNTRAINED,
                    worn = category == worn,
                )
            }
        }
    }
}

/** Одна строка владения. Подсветка говорит, чьё владение сейчас работает. */
@Composable
private fun ProficiencyRow(category: ArmorCategory, rank: Rank, worn: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val content = if (worn) scheme.onPrimaryContainer else scheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (worn) scheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            // Ширина имени задана числом, а не содержимым: иначе бейджи у строк разной
            // длины разъехались бы по горизонтали и перестали читаться столбцом.
            text = category.title.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            fontWeight = if (worn) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
            maxLines = 1,
            modifier = Modifier.width(ProficiencyNameWidth),
        )
        Spacer(Modifier.width(PosohDimens.spaceXs))
        // Степень словом не пишется: буква на бейдже говорит то же самое, а место
        // в этом столбце дороже. Расшифровка букв будет в карточке-легенде вкладки
        // «Навыки», шаг 3б.
        RankBadge(rank, size = 18.dp)
    }
}

/**
 * Щит: сколько он даёт, насколько твёрдый, сколько в нём осталось и поднят ли он.
 *
 * ПЗ сделаны как здоровье персонажа — число, шкала и две кнопки: щит теряет их такими
 * же десятками и так же на глазах. Максимум отсюда не правится, он приходит из листа.
 */
@Composable
private fun ShieldPart(record: CharacterRecord, actions: SheetActions, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val broken = shieldBroken(record.shieldHp, record.shieldMaxHp)

    LabeledPart("Щит", modifier) {
        if (record.shieldAc == 0 && record.shieldHardness == 0 && record.shieldMaxHp == 0) {
            Text(
                text = "Щита нет",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = scheme.onSurface,
            )
            return@LabeledPart
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top,
        ) {
            // Твёрдость стоит под самим щитом: это его собственное свойство, оно
            // не меняется и кнопок ему не нужно.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ShieldBadge(record.shieldAc, broken)
                Text(
                    text = "Твёрд. ${record.shieldHardness}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = scheme.onSurface,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(PosohDimens.spaceS))
            Column(Modifier.width(ShieldColumnWidth), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (record.shieldMaxHp == 0) {
                    Text(
                        text = "ПЗ щита не заполнены",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = scheme.onSurface,
                    )
                } else {
                    ShieldHp(record, actions, broken)
                }
                RaisedCheck(
                    raised = record.shieldRaised,
                    // Сломанным не закроешься, и КБ он не даёт: ставить галочку некуда.
                    enabled = !broken,
                    onChange = actions.shieldRaised,
                )
            }
        }
    }
}

/** ПЗ щита: число с кнопками, шкала под ними, предел прочности и состояние. */
@Composable
private fun ShieldHp(record: CharacterRecord, actions: SheetActions, broken: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // Сломанный щит виден с первого взгляда: и число, и шкала краснеют.
    val accent = if (broken) scheme.error else scheme.primary
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
    val number = if (broken) scheme.error else scheme.onSurface

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = record.shieldHp.toString(),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 15.sp),
            color = number,
            maxLines = 1,
        )
        Spacer(Modifier.width(2.dp))
        Text(
            // Максимум мельче и не жирный: он не меняется, а глаз ловит первое число.
            text = "/ ${record.shieldMaxHp}",
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = number,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        HoldStepperButton("−", enabled = record.shieldHp > 0, size = ShieldStepper) {
            actions.shieldHp(-1)
        }
        Spacer(Modifier.width(2.dp))
        HoldStepperButton(
            label = "+",
            enabled = record.shieldHp < record.shieldMaxHp,
            size = ShieldStepper,
        ) { actions.shieldHp(1) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(scheme.surfaceContainerHigh, RoundedCornerShape(3.dp)),
    ) {
        val fill = (record.shieldHp.toFloat() / record.shieldMaxHp).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth(fill)
                .height(5.dp)
                .background(accent, RoundedCornerShape(3.dp)),
        )
    }
    Text(
        text = "Предел проч. ${shieldBrokenThreshold(record.shieldMaxHp)}",
        style = small,
        color = scheme.onSurface,
        maxLines = 1,
    )
    Text(
        text = if (broken) "сломан" else "целый",
        style = small,
        fontWeight = if (broken) FontWeight.SemiBold else FontWeight.Normal,
        color = if (broken) scheme.error else scheme.onSurface,
        maxLines = 1,
    )
}

/**
 * Галочка «поднят». Пока стоит, КБ щита идёт в общий класс брони.
 *
 * Нажимается вся строка, а не квадратик: пальцем в четырнадцать точек не попасть.
 * `toggleable` с ролью — чтобы это и читалось вслух как галочка, а не как кнопка.
 */
@Composable
private fun RaisedCheck(raised: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .toggleable(value = raised, enabled = enabled, role = Role.Checkbox) { onChange(it) }
            .padding(vertical = 4.dp),
    ) {
        CheckSquare(raised && enabled, side = 14.dp)
        Spacer(Modifier.width(PosohDimens.spaceXs))
        Text(
            text = "Поднят",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            fontWeight = if (raised && enabled) FontWeight.SemiBold else FontWeight.Normal,
            color = scheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Значок щита с прибавкой к КБ. Сломанный краснеет: такой не даёт её вовсе. */
@Composable
private fun ShieldBadge(acBonus: Int, broken: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val fill = if (broken) scheme.errorContainer else scheme.tertiaryContainer
    val line = if (broken) scheme.error else scheme.tertiary
    val content = if (broken) scheme.onErrorContainer else scheme.onTertiaryContainer

    Box(Modifier.size(ShieldWidth, ShieldHeight), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val path = shieldPath(size.width, size.height)
            drawPath(path, fill)
            drawPath(path, line.copy(alpha = 0.6f), style = Stroke(width = 1.5.dp.toPx()))
        }
        Text(
            text = signed(acBonus),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = BigNumber),
            color = content,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

/** Силуэт щита: ровный верх, бока и остриё внизу. */
private fun shieldPath(w: Float, h: Float): Path = Path().apply {
    moveTo(0.00f * w, 0.05f * h)
    lineTo(0.50f * w, 0.00f * h)
    lineTo(1.00f * w, 0.05f * h)
    lineTo(1.00f * w, 0.45f * h)
    cubicTo(1.00f * w, 0.74f * h, 0.76f * w, 0.92f * h, 0.50f * w, 1.00f * h)
    cubicTo(0.24f * w, 0.92f * h, 0.00f * w, 0.74f * h, 0.00f * w, 0.45f * h)
    close()
}
