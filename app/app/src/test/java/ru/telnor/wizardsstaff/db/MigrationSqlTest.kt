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
        for (version in 2..9) {
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
        assertEquals(
            "ALTER-ы разошлись со слепком: перенеси описание столбца из 3.json как есть",
            addedColumns(2, 3).values.map { "ALTER TABLE `characters` ADD COLUMN $it" }.sorted(),
            BATTLE_STATE_SQL.map { it.normalized() }.sorted(),
        )
    }

    @Test
    fun `миграция 3 в 4 добавляет ровно те столбцы, что появились в слепке`() {
        assertEquals(
            "ALTER-ы разошлись со слепком: перенеси описание столбца из 4.json как есть",
            addedColumns(3, 4).values.map { "ALTER TABLE `characters` ADD COLUMN $it" }.sorted(),
            SHIELD_HP_SQL.map { it.normalized() }.sorted(),
        )
    }

    @Test
    fun `миграция 4 в 5 добавляет ровно те столбцы, что появились в слепке`() {
        assertEquals(
            "ALTER-ы разошлись со слепком: перенеси описание столбца из 5.json как есть",
            addedColumns(4, 5).values.map { "ALTER TABLE `characters` ADD COLUMN $it" }.sorted(),
            SHIELD_RAISED_SQL.map { it.normalized() }.sorted(),
        )
    }

    @Test
    fun `миграция 5 в 6 добавляет ровно те столбцы, что появились в слепке`() {
        assertEquals(
            "ALTER-ы разошлись со слепком: перенеси описание столбца из 6.json как есть",
            addedColumns(5, 6).values.map { "ALTER TABLE `characters` ADD COLUMN $it" }.sorted(),
            INITIATIVE_SQL.map { it.normalized() }.sorted(),
        )
    }

    @Test
    fun `миграция 6 в 7 добавляет ровно те столбцы, что появились в слепке`() {
        assertEquals(
            "ALTER-ы разошлись со слепком: перенеси описание столбца из 7.json как есть",
            addedColumns(6, 7, "character_weapons").values
                .map { "ALTER TABLE `character_weapons` ADD COLUMN $it" }
                .sorted(),
            DAMAGE_TYPE_SQL.map { it.normalized() }.sorted(),
        )
    }

    @Test
    fun `миграция 7 в 8 добавляет ровно те столбцы, что появились в слепке`() {
        assertEquals(
            "ALTER-ы разошлись со слепком: перенеси описание столбца из 8.json как есть",
            addedColumns(7, 8, "character_weapons").values
                .map { "ALTER TABLE `character_weapons` ADD COLUMN $it" }
                .sorted(),
            SHORT_TRAITS_SQL.map { it.normalized() }.sorted(),
        )
    }

    @Test
    fun `миграция 8 в 9 добавляет ровно те столбцы, что появились в слепке`() {
        assertEquals(
            "ALTER-ы разошлись со слепком: перенеси описание столбца из 9.json как есть",
            addedColumns(8, 9).values.map { "ALTER TABLE `characters` ADD COLUMN $it" }.sorted(),
            CHARACTER_SAVES_SQL.map { it.normalized() }.sorted(),
        )
    }

    @Test
    fun `новые столбцы не остаются без умолчания`() {
        // Правило общее для всех переездов, поэтому проверяется на каждом: забыть
        // DEFAULT легче всего в следующей миграции, а не в уже написанной.
        for (version in 3..9) {
            // В версиях 7 и 8 столбцы прибавились у оружия, в остальных — у персонажа.
            val table = if (version in 7..8) "character_weapons" else "characters"
            for ((name, definition) in addedColumns(version - 1, version, table)) {
                // SQLite не добавит к заполненной таблице столбец NOT NULL без DEFAULT:
                // существующим строкам нечем заполнить новое поле.
                assertTrue(
                    "столбцу $name нужен DEFAULT: он NOT NULL, а листы в базе уже есть",
                    !definition.contains("NOT NULL") || definition.contains("DEFAULT"),
                )
            }
        }
    }

    /** Столбцы таблицы, появившиеся между двумя версиями слепка. */
    private fun addedColumns(from: Int, to: Int, table: String = "characters"): Map<String, String> {
        val before = tableColumns(schema(from).readText(), table)
        val after = tableColumns(schema(to).readText(), table)
        val added = after.filterKeys { it !in before }
        assertTrue("между версиями $from и $to в `$table` не прибавилось ни одного столбца", added.isNotEmpty())
        return added
    }

    /**
     * Столбцы таблицы из слепка: имя → описание вида «`tempHp` INTEGER NOT NULL
     * DEFAULT 0». Разбирается прямо из `createSql`, поэтому у таблицы с внешними
     * ключами хвост после последнего столбца просто отбрасывается.
     */
    private fun tableColumns(json: String, table: String): Map<String, String> {
        val sql = createStatements(json).first { it.startsWith("CREATE TABLE IF NOT EXISTS `$table`") }
        val inside = sql.substringAfter("(").substringBeforeLast(")").substringBefore(", FOREIGN KEY")
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
