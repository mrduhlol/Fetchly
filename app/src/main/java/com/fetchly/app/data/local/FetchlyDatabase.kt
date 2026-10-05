package com.fetchly.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [HistoryEntity::class], version = 2, exportSchema = false)
abstract class FetchlyDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
