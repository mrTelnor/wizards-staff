package ru.telnor.wizardsstaff.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * База приложения: броски и листы персонажей.
 *
 * Версия и выгрузка схемы: слепок таблиц кладётся в `app/schemas` и едет в git.
 * Когда таблицы поменяются, версию надо поднять и написать миграцию — без слепка
 * Room не с чем будет сверять, а терять базу с бросками за год игр не хочется.
 */
@Database(
    entities = [
        RollRecord::class,
        CharacterRecord::class,
        CharacterWeaponRecord::class,
        CharacterFeatRecord::class,
        CharacterItemRecord::class,
        CharacterSpellRecord::class,
    ],
    version = 9,
    exportSchema = true,
)
@TypeConverters(RollConverters::class, CharacterConverters::class)
abstract class StaffDatabase : RoomDatabase() {

    abstract fun rolls(): RollDao

    abstract fun characters(): CharacterDao

    companion object {
        @Volatile
        private var instance: StaffDatabase? = null

        /**
         * База одна на всё приложение: Room держит в ней пул соединений и кэш запросов,
         * и открывать её дважды значит получить два независимых кэша и гонки при записи.
         */
        fun open(context: Context): StaffDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StaffDatabase::class.java,
                    "wizards-staff.db",
                )
                    // Переезды перечисляются явно. Сноса базы при незнакомой версии
                    // (fallbackToDestructiveMigration) здесь быть не должно: он молча
                    // стёр бы броски за все прошлые игры.
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                    )
                    .build().also { instance = it }
            }
    }
}
