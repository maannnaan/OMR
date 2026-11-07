package com.omrscanner.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ExamEntity::class, StudentEntity::class, AnswerKeyEntity::class, SheetEntity::class,
        DocumentEntity::class, DocPageEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exams(): ExamDao
    abstract fun docs(): DocDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "omr.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
