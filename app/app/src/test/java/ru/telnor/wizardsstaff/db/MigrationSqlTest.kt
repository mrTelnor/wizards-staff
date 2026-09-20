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

    private fun schema(version: Int) =
        File("schemas/ru.telnor.wizardsstaff.db.StaffDatabase/$version.json")

    @Test
    fun `слепки схем лежат в проекте`() {
        // Если файлов нет, остальные проверки прошли бы молча и впустую.
        for (version in 2..3) {
            assertTrue(
                "не найден ${schema(version).absolutePath}: выгрузка схемы отключена " +
                    "или каталог переехал",
                schema(version).isFile,
            )
        }
    }

    @Test
    fun `миграция 1 в 2 создаёт ровно то, что описано в слепке`() {
        val expected = createStatements(schema(2).readText())
            .filterNot { it.contains("`rolls`") }

        assertEquals(
            "запросы миграции разошлись со слепком схемы: перенеси createSql из 2.json как есть",
            expected.sorted(),
            CHARACTER_TABLES_SQL.map { it.normalized() }.sorted(),
        )
    }

    @Test
    fun `миграция 2 в 3 добавляет ровно те столбцы, что появились в слепке`() {
        val before = charactersColumns(schema(2).readText())
        val after = charactersColumns(schema(3).readText())
        val added = after.filterKeys { it !in before }

        assertTrue("между версиями 2 и 3 у персонажа не прибавилось ни одного столбца", added.isNotEmpty())
        assertEquals(
            "ALTER-ы разошлись со слепком: перенеси описание столбца из 3.json как есть",
            added.values.map { "ALTER TABLE `characters` ADD COLUMN $it" }.sorted(),
            BATTLE_STATE_SQL.map { it.normalized() }.sorted(),
        )
    }

    @Test
    fun `новые столбцы не остаются без умолчания`() {
        val before = charactersColumns(schema(2).readText())
        val after = charactersColumns(schema(3).readText())
        for ((name, definition) in after.filterKeys { it !in before }) {
            // SQLite не добавит к заполненной таблице столбец NOT NULL без DEFAULT:
            // существующим строкам нечем заполнить новое поле.
            assertTrue(
                "столбцу $name нужен DEFAULT: он NOT NULL, а листы в базе уже есть",
                !definition.contains("NOT NULL") || definition.contains("DEFAULT"),
            )
        }
    }

    /**
     * Столбцы таблицы персонажа из слепка: имя → описание вида «`tempHp` INTEGER NOT NULL
     * DEFAULT 0». Разбирается прямо из `createSql`: у этой таблицы нет внешних ключей,
     * поэтому скобки внутри описания не встречаются и хватает разделения по запятой.
     */
    private fun charactersColumns(json: String): Map<String, String> {
        val sql = createStatements(json).first { it.startsWith("CREATE TABLE IF NOT EXISTS `characters`") }
        val inside = sql.substringAfter("(").substringBeforeLast(")")
        return inside.split(", `")
            .map { if (it.startsWith("`")) it else "`$it" }
            .associateBy { it.substringAfter("`").substringBefore("`") }
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
