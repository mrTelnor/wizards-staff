package ru.telnor.wizardsstaff.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
 * Сверка запросов миграции с выгруженным слепком схемы.
 *
 * Room сравнивает базу, получившуюся после миграции, со слепком побуквенно и при
 * расхождении роняет приложение на старте. Ловить это на планшете дорого: нужна старая
 * база с бросками, а ошибка вылезает только при обновлении поверх неё. Здесь то же
 * сравнение делается при сборке.
 *
 * Слепок читается текстом, без разбора JSON: в юнит-тестах Android библиотека `org.json`
 * подменена заглушкой и настоящего разбора не делает, а тащить свою ради одного поля
 * незачем.
 */
class MigrationSqlTest {

    private val schema =
        File("schemas/ru.telnor.wizardsstaff.db.StaffDatabase/2.json")

    @Test
    fun `слепок схемы версии 2 лежит в проекте`() {
        // Если файла нет, остальные проверки прошли бы молча и впустую.
        assertTrue(
            "не найден ${schema.absolutePath}: выгрузка схемы отключена или каталог переехал",
            schema.isFile,
        )
    }

    @Test
    fun `миграция создаёт ровно то, что описано в слепке`() {
        val expected = createStatements(schema.readText())
            .filterNot { it.contains("`rolls`") }

        assertEquals(
            "запросы миграции разошлись со слепком схемы: перенеси createSql из 2.json как есть",
            expected.sorted(),
            CHARACTER_TABLES_SQL.map { it.normalized() }.sorted(),
        )
    }

    /**
     * Достаёт из слепка все `createSql` и подставляет вместо `${TABLE_NAME}` имя таблицы.
     * Имя берётся у ближайшего предыдущего поля `tableName`: в слепке таблица идёт раньше
     * своих индексов, а индексы относятся к ней же.
     */
    private fun createStatements(json: String): List<String> {
        val field = Regex("\"(tableName|createSql)\"\\s*:\\s*\"([^\"]*)\"")
        var table = ""
        val statements = mutableListOf<String>()
        for (match in field.findAll(json)) {
            val (name, value) = match.destructured
            if (name == "tableName") {
                table = value
            } else {
                statements += value.replace("\${TABLE_NAME}", "`$table`")
                    .replace("``", "`")
                    .normalized()
            }
        }
        return statements
    }

    /** Пробелы не в счёт: перенос строки в коде не меняет смысла запроса. */
    private fun String.normalized(): String = trim().replace(Regex("\\s+"), " ")
}
