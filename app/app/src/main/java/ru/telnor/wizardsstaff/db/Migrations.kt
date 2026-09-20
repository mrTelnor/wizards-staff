package ru.telnor.wizardsstaff.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Переезды базы с версии на версию.
 *
 * SQL здесь не написан от руки: он скопирован из выгруженного слепка схемы
 * `app/schemas/.../2.json`, поле `createSql`. Room при открытии базы сверяет
 * получившиеся таблицы со слепком побуквенно, и своя формулировка того же смысла
 * («TEXT» вместо «TEXT NOT NULL», другой порядок столбцов) роняет приложение
 * на старте с рассказом, чем ожидаемое отличается от найденного.
 *
 * Поэтому запросы лежат списком, а не внутри `migrate()`: так их сверяет со слепком
 * обычный тест (`MigrationSqlTest`), и расхождение находится при сборке, а не на планшете.
 */

/** Таблицы листов персонажей — всё, что добавляет версия 2. */
val CHARACTER_TABLES_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `characters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`name` TEXT NOT NULL, `ancestry` TEXT NOT NULL, `background` TEXT NOT NULL, " +
        "`className` TEXT NOT NULL, `deity` TEXT NOT NULL, `alignment` TEXT NOT NULL, " +
        "`size` TEXT NOT NULL, `level` INTEGER NOT NULL, `xp` INTEGER NOT NULL, " +
        "`heroPoints` INTEGER NOT NULL, `scores` TEXT NOT NULL, `keyAbility` TEXT NOT NULL, " +
        "`armorName` TEXT, `armorRanks` TEXT NOT NULL, `perception` TEXT NOT NULL, " +
        "`perceptionItem` INTEGER NOT NULL, `saves` TEXT NOT NULL, `saveItems` TEXT NOT NULL, " +
        "`skills` TEXT NOT NULL, `skillItems` TEXT NOT NULL, `classDc` TEXT NOT NULL, " +
        "`spellAttack` TEXT NOT NULL, `spellDc` TEXT NOT NULL, `baseSpeed` INTEGER NOT NULL, " +
        "`weaponTraining` TEXT NOT NULL, `lore1` TEXT, `lore2` TEXT, `maxHp` INTEGER NOT NULL, " +
        "`currentHp` INTEGER NOT NULL, `resistances` TEXT NOT NULL, `languages` TEXT NOT NULL, " +
        "`shieldAc` INTEGER NOT NULL, `shieldHardness` INTEGER NOT NULL, " +
        "`platinum` INTEGER NOT NULL, `gold` INTEGER NOT NULL, `silver` INTEGER NOT NULL, " +
        "`copper` INTEGER NOT NULL, `bulk` INTEGER NOT NULL, `encumberedAt` INTEGER NOT NULL, " +
        "`maxBulk` INTEGER NOT NULL, `tradition` TEXT NOT NULL, " +
        "`preparedCaster` INTEGER NOT NULL, `cantripRank` INTEGER NOT NULL)",

    "CREATE TABLE IF NOT EXISTS `character_weapons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`characterId` INTEGER NOT NULL, `name` TEXT NOT NULL, `shortName` TEXT NOT NULL, " +
        "`rank` TEXT NOT NULL, `damageDice` TEXT NOT NULL, `potency` INTEGER NOT NULL, " +
        "`finesse` INTEGER NOT NULL, `traits` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
        "FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_character_weapons_characterId` " +
        "ON `character_weapons` (`characterId`)",

    "CREATE TABLE IF NOT EXISTS `character_feats` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`characterId` INTEGER NOT NULL, `featGroup` TEXT NOT NULL, `name` TEXT NOT NULL, " +
        "`tag` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
        "FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_character_feats_characterId` " +
        "ON `character_feats` (`characterId`)",

    "CREATE TABLE IF NOT EXISTS `character_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`characterId` INTEGER NOT NULL, `name` TEXT NOT NULL, `details` TEXT NOT NULL, " +
        "`bulk` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
        "FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_character_items_characterId` " +
        "ON `character_items` (`characterId`)",

    "CREATE TABLE IF NOT EXISTS `character_spells` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`characterId` INTEGER NOT NULL, `name` TEXT NOT NULL, `rank` INTEGER NOT NULL, " +
        "`actions` TEXT NOT NULL, `frequency` TEXT NOT NULL, `hasRoll` INTEGER NOT NULL, " +
        "`position` INTEGER NOT NULL, " +
        "FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_character_spells_characterId` " +
        "ON `character_spells` (`characterId`)",
)

/**
 * Версия 1 → 2: появились листы персонажей.
 *
 * Таблица `rolls` не трогается вовсе: броски за прошлые игры должны пережить обновление,
 * ради этого схему и выгружаем в git. Новые таблицы создаются пустыми, Сильврина в них
 * кладёт засев при первом запуске — миграции про содержимое знать незачем.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        CHARACTER_TABLES_SQL.forEach(db::execSQL)
    }
}
