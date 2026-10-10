package com.subkiru.subkiru.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubKiruDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SubKiruDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun バージョン2から3へ移行するとsort_orderが現在の表示順に沿って振られる() {
        // Arrange: version 2 で、請求日が前後するサブスクを登録する
        helper.createDatabase(TEST_DB_NAME, 2).apply {
            insertSubscription(id = 1L, name = "C Service", nextBillingDateEpoch = LATER_EPOCH)
            insertSubscription(id = 2L, name = "B Service", nextBillingDateEpoch = EARLIER_EPOCH)
            insertSubscription(id = 3L, name = "A Service", nextBillingDateEpoch = EARLIER_EPOCH)
            close()
        }

        // Act
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            3,
            true,
            SubKiruDatabase.MIGRATION_2_3,
        )

        // Assert: 次回請求日 → 名前の昇順（A, B, C）
        val sortOrders = mutableMapOf<Long, Int>()
        migrated.query("SELECT id, sort_order FROM subscriptions").use { cursor ->
            while (cursor.moveToNext()) {
                sortOrders[cursor.getLong(0)] = cursor.getInt(1)
            }
        }
        assertEquals(mapOf(3L to 0, 2L to 1, 1L to 2), sortOrders)
    }

    @Test
    fun バージョン2から3へ移行しても既存の列の値は変わらない() {
        // Arrange
        helper.createDatabase(TEST_DB_NAME, 2).apply {
            insertSubscription(id = 1L, name = "Netflix", nextBillingDateEpoch = LATER_EPOCH)
            close()
        }

        // Act
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            3,
            true,
            SubKiruDatabase.MIGRATION_2_3,
        )

        // Assert
        migrated.query("SELECT name, amount_minor, next_billing_date_epoch FROM subscriptions").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Netflix", cursor.getString(0))
            assertEquals(AMOUNT_MINOR, cursor.getLong(1))
            assertEquals(LATER_EPOCH, cursor.getLong(2))
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertSubscription(
        id: Long,
        name: String,
        nextBillingDateEpoch: Long,
    ) {
        execSQL(
            """
            INSERT INTO subscriptions (
                id, name, amount_minor, currency_code, billing_interval_unit, billing_interval_count,
                start_date_epoch, next_billing_date_epoch, is_active, created_at, updated_at
            ) VALUES (
                $id, '$name', $AMOUNT_MINOR, 'JPY', 'MONTHLY', 1,
                $EARLIER_EPOCH, $nextBillingDateEpoch, 1, 0, 0
            )
            """.trimIndent(),
        )
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test"
        private const val AMOUNT_MINOR = 980L
        private const val EARLIER_EPOCH = 20_000L
        private const val LATER_EPOCH = 20_100L
    }
}
