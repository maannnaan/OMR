package com.omrscanner.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExamEntity::class, StudentEntity::class, AnswerKeyEntity::class, SheetEntity::class,
        DocumentEntity::class, DocPageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exams(): ExamDao
    abstract fun docs(): DocDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** 1 → 2: which Lecturer.bd paper an exam came from, and when each sheet was last uploaded there. Keeps every exam and scan. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exams ADD COLUMN remotePaperId TEXT")
                db.execSQL("ALTER TABLE sheets ADD COLUMN uploadedAt INTEGER")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "omr.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
