package com.parentalcontrol.mvp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MonitoringEvent::class],
    version = 1,
    exportSchema = false
)
abstract class MonitoringDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    
    companion object {
        @Volatile
        private var INSTANCE: MonitoringDatabase? = null
        
        fun getInstance(context: Context): MonitoringDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MonitoringDatabase::class.java,
                    "monitoring_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
