package com.subkiru.subkiru.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
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
        const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "subkiru.db"

        // バージョン1→2: service_templates テーブルに domain カラムを追加し、既存レコードにドメインを設定
        private val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL("ALTER TABLE service_templates ADD COLUMN domain TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE service_templates SET domain = 'netflix.com' WHERE name = 'Netflix'")
            db.execSQL("UPDATE service_templates SET domain = 'amazon.co.jp' WHERE name = 'Amazon Prime'")
            db.execSQL("UPDATE service_templates SET domain = 'disneyplus.com' WHERE name = 'Disney+'")
            db.execSQL("UPDATE service_templates SET domain = 'unext.jp' WHERE name = 'U-NEXT'")
            db.execSQL("UPDATE service_templates SET domain = 'hulu.jp' WHERE name = 'Hulu'")
            db.execSQL("UPDATE service_templates SET domain = 'youtube.com' WHERE name = 'YouTube Premium'")
            db.execSQL("UPDATE service_templates SET domain = 'spotify.com' WHERE name = 'Spotify'")
            db.execSQL("UPDATE service_templates SET domain = 'apple.com' WHERE name = 'Apple Music'")
            db.execSQL("UPDATE service_templates SET domain = 'amazon.co.jp' WHERE name = 'Amazon Music Unlimited'")
            db.execSQL("UPDATE service_templates SET domain = 'line.me' WHERE name = 'LINE MUSIC'")
            db.execSQL("UPDATE service_templates SET domain = 'google.com' WHERE name = 'Google One'")
            db.execSQL("UPDATE service_templates SET domain = 'apple.com' WHERE name = 'iCloud+'")
            db.execSQL("UPDATE service_templates SET domain = 'dropbox.com' WHERE name = 'Dropbox Plus'")
            db.execSQL("UPDATE service_templates SET domain = 'playstation.com' WHERE name = 'PlayStation Plus'")
            db.execSQL("UPDATE service_templates SET domain = 'nintendo.co.jp' WHERE name = 'Nintendo Switch Online'")
            db.execSQL("UPDATE service_templates SET domain = 'xbox.com' WHERE name = 'Xbox Game Pass'")
            db.execSQL("UPDATE service_templates SET domain = 'nikkei.com' WHERE name = '日経電子版'")
            db.execSQL("UPDATE service_templates SET domain = 'amazon.co.jp' WHERE name = 'Kindle Unlimited'")
            db.execSQL("UPDATE service_templates SET domain = 'rakuten.co.jp' WHERE name = '楽天マガジン'")
            db.execSQL("UPDATE service_templates SET domain = 'microsoft.com' WHERE name = 'Microsoft 365'")
            db.execSQL("UPDATE service_templates SET domain = 'adobe.com' WHERE name = 'Adobe Creative Cloud'")
            db.execSQL("UPDATE service_templates SET domain = '1password.com' WHERE name = '1Password'")
        }

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
            ).addMigrations(MIGRATION_1_2)
                .addCallback(object : RoomDatabase.Callback() {
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
