package ru.telnor.wizardsstaff.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * База приложения. Пока в ней одна таблица — броски; персонажи и их действия придут
 * вместе с листами персонажей.
 *
 * Версия и выгрузка схемы: слепок таблиц кладётся в `app/schemas` и едет в git.
 * Когда таблицы поменяются, версию надо поднять и написать миграцию — без слепка
 * Room не с чем будет сверять, а терять базу с бросками за год игр не хочется.
 */
@Database(entities = [RollRecord::class], version = 1, exportSchema = true)
@TypeConverters(RollConverters::class)
abstract class StaffDatabase : RoomDatabase() {

    abstract fun rolls(): RollDao

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
                ).build().also { instance = it }
            }
    }
}
