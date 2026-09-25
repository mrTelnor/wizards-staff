package ru.telnor.wizardsstaff.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * Поиск описания черты по названию из листа.
 *
 * Названия в лист вписывал автор, а в справочник они попали с pf2.ru, и совпадают
 * не буква в букву: «Лечащие руки» против «Лечащие Руки». Чтение самого файла здесь
 * не проверяется — в юнит-тестах `org.json` подменён заглушкой.
 */
class FeatCatalogTest {

    private val catalog = listOf(
        feat("Healing Hands", "Лечащие Руки"),
        feat("Shield Block", "Блок Щитом"),
        feat("Incredible Initiative", "Невероятная Инициатива"),
    )

    private fun feat(english: String, russian: String) = CatalogFeat(
        english = english,
        russian = russian,
        level = 1,
        traits = listOf("Общая"),
        header = "Источник: Основная книга правил",
        text = "Текст правила.",
        url = "https://pf2.ru/feats/example",
    )

    @Test
    fun `заглавные буквы поиску не мешают`() {
        // Ровно тот случай, ради которого сравнение идёт без регистра.
        assertEquals("Healing Hands", catalog.findByRussian("Лечащие руки")?.english)
        assertEquals("Shield Block", catalog.findByRussian("блок щитом")?.english)
    }

    @Test
    fun `лишние пробелы по краям не мешают`() {
        assertEquals("Shield Block", catalog.findByRussian("  Блок щитом ")?.english)
    }

    @Test
    fun `незнакомая черта не находится и ничего не подставляет`() {
        // Справочник неполон и будет неполон: подставить похожее хуже, чем не найти.
        assertNull(catalog.findByRussian("Медицина в бою"))
        assertNull(catalog.findByRussian("Лечащие"))
    }
}
