package com.subkiru.subkiru.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.subkiru.subkiru.core.data.db.converter.Converters
import com.subkiru.subkiru.core.data.db.dao.SubscriptionDao
import com.subkiru.subkiru.core.data.db.entity.SubscriptionEntity

@Database(
    entities = [SubscriptionEntity::class],
    version = SubKiruDatabase.DATABASE_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SubKiruDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "subkiru.db"

        @Volatile
        private var instance: SubKiruDatabase? = null

        fun getInstance(context: Context): SubKiruDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): SubKiruDatabase {
            return Room.databaseBuilder(
                context,
                SubKiruDatabase::class.java,
                DATABASE_NAME,
            ).build()
        }
    }
}
