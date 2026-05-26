package com.subkiru.subkiru.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.subkiru.subkiru.core.data.db.converter.Converters
import com.subkiru.subkiru.core.data.db.dao.CategoryDao
import com.subkiru.subkiru.core.data.db.dao.PaymentHistoryDao
import com.subkiru.subkiru.core.data.db.dao.ServiceTemplateDao
import com.subkiru.subkiru.core.data.db.dao.SubscriptionDao
import com.subkiru.subkiru.core.data.db.entity.CategoryEntity
import com.subkiru.subkiru.core.data.db.entity.PaymentHistoryEntity
import com.subkiru.subkiru.core.data.db.entity.ServiceTemplateEntity
import com.subkiru.subkiru.core.data.db.entity.SubscriptionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Database(
    entities = [
        SubscriptionEntity::class,
        CategoryEntity::class,
        ServiceTemplateEntity::class,
        PaymentHistoryEntity::class,
    ],
    version = SubKiruDatabase.DATABASE_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SubKiruDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun serviceTemplateDao(): ServiceTemplateDao
    abstract fun paymentHistoryDao(): PaymentHistoryDao

    companion object {
        const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "subkiru.db"

        @Volatile
        private var instance: SubKiruDatabase? = null

        fun getInstance(
            context: Context,
            scope: CoroutineScope? = null,
        ): SubKiruDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext, scope).also { instance = it }
            }
        }

        private fun buildDatabase(
            context: Context,
            scope: CoroutineScope?,
        ): SubKiruDatabase {
            lateinit var database: SubKiruDatabase
            database = Room.databaseBuilder(
                context,
                SubKiruDatabase::class.java,
                DATABASE_NAME,
            ).addCallback(object : RoomDatabase.Callback() {
                /** DB が初回作成されたタイミングで初期データを投入する */
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // scope が渡されなかった場合は初期データ投入をスキップする
                    val coroutineScope = scope ?: return
                    coroutineScope.launch {
                        try {
                            DatabasePrepopulator.populate(
                                context = context,
                                categoryDao = database.categoryDao(),
                                serviceTemplateDao = database.serviceTemplateDao(),
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "初期データ投入に失敗しました", e)
                        }
                    }
                }
            }).build()
            return database
        }

        private const val TAG = "SubKiruDatabase"
    }
}
