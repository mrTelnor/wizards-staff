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

/**
 * Состояние персонажа в бою: временные ПЗ, ранения и «при смерти». Добавляются столбцами
 * к уже существующим листам, поэтому у каждого есть умолчание — иначе старой строке
 * нечем заполнить новое поле, и SQLite такую правку просто не примет.
 *
 * Умолчание продублировано в `@ColumnInfo(defaultValue = "0")` у поля записи: Room
 * сверяет со слепком и умолчания тоже, и столбец без него не сойдётся со столбцом с ним.
 */
val BATTLE_STATE_SQL: List<String> = listOf(
    "ALTER TABLE `characters` ADD COLUMN `tempHp` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `characters` ADD COLUMN `wounded` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `characters` ADD COLUMN `dying` INTEGER NOT NULL DEFAULT 0",
)

/** Версия 2 → 3: счётчики состояния в листе. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        BATTLE_STATE_SQL.forEach(db::execSQL)
    }
}

/**
 * ПЗ щита: сколько осталось и сколько бывает целиком. Как и состояние боя, добавляются
 * столбцами к уже заполненным листам, поэтому у каждого есть умолчание.
 *
 * Порога поломки среди них нет: он всегда половина максимума и считается движком.
 */
val SHIELD_HP_SQL: List<String> = listOf(
    "ALTER TABLE `characters` ADD COLUMN `shieldHp` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `characters` ADD COLUMN `shieldMaxHp` INTEGER NOT NULL DEFAULT 0",
)

/** Версия 3 → 4: ПЗ щита. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        SHIELD_HP_SQL.forEach(db::execSQL)
    }
}

/**
 * Поднят ли щит. Тоже столбцом к готовым листам, тоже с умолчанием: опущен.
 */
val SHIELD_RAISED_SQL: List<String> = listOf(
    "ALTER TABLE `characters` ADD COLUMN `shieldRaised` INTEGER NOT NULL DEFAULT 0",
)

/**
 * Версия 4 → 5: галочка «щит поднят», а заодно правка данных.
 *
 * Правка данных в переезде — случай особый и, вообще говоря, нежелательный. Здесь она
 * оправдана: ПЗ щита появились в версии 4 нулями, потому что в бланке их нет, а число
 * (20) автор назвал уже после. Засев их не поставит — он срабатывает только на пустой
 * базе, а лист Сильврина в ней уже лежит. Условие `WHERE shieldMaxHp = 0` бережёт тех,
 * кто успел проставить ПЗ руками.
 */
val SHIELD_HP_FILL_SQL =
    "UPDATE characters SET shieldMaxHp = 20, shieldHp = 20 " +
        "WHERE shieldMaxHp = 0 AND (shieldAc > 0 OR shieldHardness > 0)"

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        SHIELD_RAISED_SQL.forEach(db::execSQL)
        db.execSQL(SHIELD_HP_FILL_SQL)
    }
}

/** Прибавка к инициативе. Столбцом к готовым листам, с умолчанием: прибавки нет. */
val INITIATIVE_SQL: List<String> = listOf(
    "ALTER TABLE `characters` ADD COLUMN `initiativeBonus` INTEGER NOT NULL DEFAULT 0",
)

/**
 * Версия 5 → 6: прибавка к инициативе, и сразу проставленная тем, у кого она есть.
 *
 * Правка данных здесь по той же причине, что и у ПЗ щита: засев срабатывает только
 * на пустой базе, а листы в ней уже лежат. Но условие честнее: двойка достаётся
 * не всем подряд, а ровно тем персонажам, у кого в чертах записана «Невероятная
 * инициатива» — та самая черта, которая её и даёт.
 */
val INITIATIVE_FILL_SQL =
    "UPDATE characters SET initiativeBonus = 2 WHERE initiativeBonus = 0 AND id IN " +
        "(SELECT characterId FROM character_feats WHERE name = 'Невероятная инициатива')"

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        INITIATIVE_SQL.forEach(db::execSQL)
        db.execSQL(INITIATIVE_FILL_SQL)
    }
}

/** Тип урона у оружия. Столбцом к уже записанному, с умолчанием «дробящее». */
val DAMAGE_TYPE_SQL: List<String> = listOf(
    "ALTER TABLE `character_weapons` ADD COLUMN `damageType` TEXT NOT NULL DEFAULT 'BLUDGEONING'",
)

/**
 * Версия 6 → 7: чем бьёт оружие, и попутно правка засеянных данных.
 *
 * Правка нужна по той же причине, что и прежние: засев срабатывает только на пустой
 * базе, а оружие в ней уже лежит. Тип урона когтей и настоящее имя молота иначе
 * остались бы старыми навсегда.
 *
 * Такие правки — временная мера. Как только появится лист снаряжения, оружие начнёт
 * редактироваться руками, и чинить его миграциями больше не придётся.
 */
val WEAPON_FIX_SQL: List<String> = listOf(
    "UPDATE character_weapons SET damageType = 'SLASHING' WHERE name = 'Когти'",
    "UPDATE character_weapons SET name = 'Двуручный молот +1', " +
        "shortName = 'двуручным молотом' " +
        "WHERE name = 'Двуручный молот кошачьей ярости +1'",
)

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DAMAGE_TYPE_SQL.forEach(db::execSQL)
        WEAPON_FIX_SQL.forEach(db::execSQL)
    }
}

/** Короткие свойства оружия. Столбцом к уже записанному, с пустым умолчанием. */
val SHORT_TRAITS_SQL: List<String> = listOf(
    "ALTER TABLE `character_weapons` ADD COLUMN `shortTraits` TEXT NOT NULL DEFAULT ''",
)

/**
 * Версия 7 → 8: сокращения свойств оружия, и снова правка засеянного.
 *
 * Причина прежняя: засев на непустой базе не срабатывает. Условие узкое — только
 * то оружие, у которого свойства записаны ровно как в засеве.
 */
val SHORT_TRAITS_FILL_SQL: List<String> = listOf(
    "UPDATE character_weapons SET shortTraits = 'безоруж., быстр., фехт.' " +
        "WHERE traits = 'безоружно, быстрое, фехтовальное'",
    "UPDATE character_weapons SET shortTraits = 'толк.' WHERE traits = 'толкающее'",
)

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        SHORT_TRAITS_SQL.forEach(db::execSQL)
        SHORT_TRAITS_FILL_SQL.forEach(db::execSQL)
    }
}

/**
 * Сохранения персонажа. Столбцы пустые у всех, кто уже лежит в базе: живой лист
 * тем и отличается от копии, что `saveName` у него пуст.
 *
 * Умолчание тут не нужно — столбцы не `NOT NULL`, и старым строкам есть чем
 * заполниться: ничем.
 */
val CHARACTER_SAVES_SQL: List<String> = listOf(
    "ALTER TABLE `characters` ADD COLUMN `saveName` TEXT",
    "ALTER TABLE `characters` ADD COLUMN `savedAt` INTEGER",
    "ALTER TABLE `characters` ADD COLUMN `saveOf` INTEGER",
)

/** Версия 8 → 9: сохранения персонажа копиями в той же таблице. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        CHARACTER_SAVES_SQL.forEach(db::execSQL)
    }
}
