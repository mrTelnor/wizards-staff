package ru.telnor.wizardsstaff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
 * Ловушка для инструкции в приложении: у каждой кнопки посоха и у каждого пункта его меню
 * есть своё описание — заголовок карточки или подпись строки в HelpContent.
 *
 * Списки читаются прямо из прошивки, а не из HelpContent, иначе тест проверял бы сам себя:
 * кнопки — из firmware/wizards_staff/config.h (строки «const int BTN_* = N; // Имя»),
 * пункты меню — из menu.h (MENU_NAMES). Поменялись кнопки или меню в прошивке — тест упадёт:
 * поправь HelpContent и help.h на посохе. Поменялся вид этих строк — поправь разбор ниже.
 */
class HelpContentTest {

    /** Папка прошивки: ищем вверх от рабочей папки теста, где лежит firmware/wizards_staff. */
    private val firmwareDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "firmware/wizards_staff")
            if (candidate.isDirectory) return@lazy candidate
            dir = dir.parentFile
        }
        throw AssertionError(
            "Не нашёл firmware/wizards_staff выше ${System.getProperty("user.dir")}: " +
                "тест читает списки кнопок и меню из прошивки",
        )
    }

    private fun firmwareFile(name: String): String {
        val file = File(firmwareDir, name)
        if (!file.isFile) throw AssertionError("Нет файла прошивки ${file.path}")
        return file.readText(Charsets.UTF_8)
    }

    /**
     * Служебные кнопки и кнопки меню из config.h: подпись из комментария строки BTN_*.
     * У ◄ и ► в комментарии «◄ Назад», «► Вперёд» — на кнопке и в инструкции только стрелка.
     */
    private val staffButtons: List<String> by lazy {
        Regex("""^const int BTN_(\w+)\s*=\s*\d+;\s*//\s*(.+)$""", RegexOption.MULTILINE)
            .findAll(firmwareFile("config.h"))
            .map { m ->
                val (name, comment) = m.destructured
                if (name == "PREV" || name == "NEXT") comment.trim().split(" ").first() else comment.trim()
            }
            .toList()
    }

    /** Пункты меню посоха из menu.h, массив MENU_NAMES. */
    private val menuItems: List<String> by lazy {
        val body = Regex("""MENU_NAMES\[[^\]]*\]\s*=\s*\{([^}]*)\}""")
            .find(firmwareFile("menu.h"))
            ?.groupValues?.get(1)
            ?: throw AssertionError("В menu.h не нашёл MENU_NAMES")
        Regex(""""([^"]*)"""").findAll(body).map { it.groupValues[1] }.toList()
    }

    /**
     * Всё, что в инструкции стоит заголовком карточки или подписью строки. Кнопка или пункт
     * меню засчитываются в обоих случаях: служебные кнопки описаны своими карточками
     * («Тихо», «Свет»), а кнопки меню и пункты меню — подписями строк («ОК», «Сон»).
     */
    private val described: Set<String> = HelpContent.sections
        .flatMap { section -> listOf(section.title) + section.items.mapNotNull { it.label } }
        .toSet()

    @Test
    fun разборПрошивкиНашёлВсеКнопкиИПункты() {
        // Чтобы разбор, нашедший ноль, не прошёл молча. Число поменялось в прошивке —
        // поправь здесь вместе с инструкцией.
        assertEquals("Кнопок в config.h: $staffButtons", 8, staffButtons.size)
        assertEquals("Пунктов в menu.h: $menuItems", 7, menuItems.size)
    }

    @Test
    fun уКаждойКнопкиПосохаЕстьОписание() {
        val missing = staffButtons.filterNot { it in described }
        assertTrue("В инструкции не описаны кнопки: $missing", missing.isEmpty())
    }

    @Test
    fun уКаждогоПунктаМенюЕстьОписание() {
        val missing = menuItems.filterNot { it in described }
        assertTrue("В инструкции не описаны пункты меню: $missing", missing.isEmpty())
    }

    @Test
    fun вКарточкахНетПустыхСтрок() {
        HelpContent.sections.forEach { section ->
            assertTrue("Пустой заголовок карточки", section.title.isNotBlank())
            assertTrue("Карточка «${section.title}» без строк", section.items.isNotEmpty())
            section.items.forEach {
                assertTrue("Пустое пояснение в карточке «${section.title}»", it.text.isNotBlank())
            }
        }
    }
}
