package com.subkiru.subkiru.core.data.db.dao

import android.content.Context
import app.cash.turbine.test
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subkiru.subkiru.core.data.db.SubKiruDatabase
import com.subkiru.subkiru.core.data.db.entity.SubscriptionEntity
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SubscriptionDaoTest {
    private lateinit var database: SubKiruDatabase
    private lateinit var dao: SubscriptionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SubKiruDatabase::class.java).build()
        dao = database.subscriptionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun サブスクを追加してepochDayと最小通貨単位で取得できる() = runBlocking {
        val startDateEpoch = LocalDate.of(2026, 5, 1).toEpochDay()
        val nextBillingDateEpoch = LocalDate.of(2026, 6, 1).toEpochDay()
        val id = dao.addSubscription(
            subscription = subscription(
                amountMinor = 980L,
                startDateEpoch = startDateEpoch,
                nextBillingDateEpoch = nextBillingDateEpoch,
            )
        )

        val actual = requireNotNull(dao.getSubscriptionById(id))

        assertEquals(980L, actual.amountMinor)
        assertEquals(DEFAULT_CURRENCY_CODE, actual.currencyCode)
        assertEquals(BillingIntervalUnit.MONTHLY, actual.billingIntervalUnit)
        assertEquals(startDateEpoch, actual.startDateEpoch)
        assertEquals(nextBillingDateEpoch, actual.nextBillingDateEpoch)
    }

    @Test
    fun 非アクティブ化したサブスクは一覧から除外される() = runBlocking {
        val id = dao.addSubscription(subscription = subscription())

        dao.deactivateSubscription(id = id, updatedAt = UPDATED_AT)

        assertFalse(dao.observeActiveSubscriptions().first().any { it.id == id })
    }

    @Test
    fun updateSubscriptionで更新後に正しい値が取得できる() = runBlocking {
        val id = dao.addSubscription(subscription = subscription())
        val updated = requireNotNull(dao.getSubscriptionById(id)).copy(
            name = UPDATED_SUBSCRIPTION_NAME,
            amountMinor = UPDATED_AMOUNT_MINOR,
            currencyCode = UPDATED_CURRENCY_CODE,
            billingIntervalUnit = BillingIntervalUnit.YEARLY,
            billingIntervalCount = UPDATED_BILLING_INTERVAL_COUNT,
            nextBillingDateEpoch = UPDATED_NEXT_BILLING_DATE_EPOCH,
            memo = UPDATED_MEMO,
            updatedAt = UPDATED_AT,
        )

        dao.updateSubscription(updated)

        val actual = requireNotNull(dao.getSubscriptionById(id))
        assertEquals(UPDATED_SUBSCRIPTION_NAME, actual.name)
        assertEquals(UPDATED_AMOUNT_MINOR, actual.amountMinor)
        assertEquals(UPDATED_CURRENCY_CODE, actual.currencyCode)
        assertEquals(BillingIntervalUnit.YEARLY, actual.billingIntervalUnit)
        assertEquals(UPDATED_BILLING_INTERVAL_COUNT, actual.billingIntervalCount)
        assertEquals(UPDATED_NEXT_BILLING_DATE_EPOCH, actual.nextBillingDateEpoch)
        assertEquals(UPDATED_MEMO, actual.memo)
        assertEquals(UPDATED_AT, actual.updatedAt)
    }

    @Test
    fun deleteSubscriptionで物理削除後にnullが返る() = runBlocking {
        val id = dao.addSubscription(subscription = subscription())
        val saved = requireNotNull(dao.getSubscriptionById(id))

        dao.deleteSubscription(saved)

        assertNull(dao.getSubscriptionById(id))
    }

    @Test
    fun observeActiveSubscriptionsのソート順が正しい() = runBlocking {
        val laterId = dao.addSubscription(
            subscription = subscription(
                name = SORT_LATER_NAME,
                nextBillingDateEpoch = LocalDate.of(2026, 7, 1).toEpochDay(),
            )
        )
        val sameDateSecondId = dao.addSubscription(
            subscription = subscription(
                name = SORT_SAME_DATE_SECOND_NAME,
                nextBillingDateEpoch = LocalDate.of(2026, 6, 1).toEpochDay(),
            )
        )
        val sameDateFirstId = dao.addSubscription(
            subscription = subscription(
                name = SORT_SAME_DATE_FIRST_NAME,
                nextBillingDateEpoch = LocalDate.of(2026, 6, 1).toEpochDay(),
            )
        )

        val actualIds = dao.observeActiveSubscriptions().first().map { it.id }

        assertEquals(listOf(sameDateFirstId, sameDateSecondId, laterId), actualIds)
    }

    @Test
    fun getSubscriptionByIdに存在しないIDを渡すとnullが返る() = runBlocking {
        assertNull(dao.getSubscriptionById(NOT_FOUND_ID))
    }

    @Test
    fun addSubscriptionでデフォルト値が正しく適用される() = runBlocking {
        val id = dao.addSubscription(subscription = subscription())

        val actual = requireNotNull(dao.getSubscriptionById(id))
        assertEquals(DEFAULT_AMOUNT_MINOR, actual.amountMinor)
        assertEquals(SubscriptionEntity.DEFAULT_CURRENCY_CODE, actual.currencyCode)
        assertEquals(SubscriptionEntity.DEFAULT_BILLING_INTERVAL_COUNT, actual.billingIntervalCount)
        assertTrue(actual.isActive)
    }

    @Test
    fun deactivateSubscription後にisActiveがfalseかつupdatedAtが更新されている() = runBlocking {
        val id = dao.addSubscription(subscription = subscription())

        dao.deactivateSubscription(id = id, updatedAt = UPDATED_AT)

        val actual = requireNotNull(dao.getSubscriptionById(id))
        assertFalse(actual.isActive)
        assertEquals(UPDATED_AT, actual.updatedAt)
    }

    @Test
    fun サブスク追加後にFlowが更新を通知する() = runBlocking {
        dao.observeActiveSubscriptions().test {
            assertEquals(emptyList<SubscriptionEntity>(), awaitItem())

            val id = dao.addSubscription(subscription = subscription())

            assertEquals(listOf(id), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun subscription(
        id: Long = SubscriptionEntity.UNSAVED_ID,
        name: String = SUBSCRIPTION_NAME,
        amountMinor: Long = DEFAULT_AMOUNT_MINOR,
        currencyCode: String = DEFAULT_CURRENCY_CODE,
        billingIntervalUnit: BillingIntervalUnit = BillingIntervalUnit.MONTHLY,
        billingIntervalCount: Int = DEFAULT_BILLING_INTERVAL_COUNT,
        startDateEpoch: Long = LocalDate.of(2026, 5, 1).toEpochDay(),
        nextBillingDateEpoch: Long = LocalDate.of(2026, 6, 1).toEpochDay(),
        categoryId: Long? = null,
        templateId: Long? = null,
        logoUri: String? = null,
        memo: String? = null,
        isActive: Boolean = SubscriptionEntity.DEFAULT_IS_ACTIVE,
        createdAt: Long = CREATED_AT,
        updatedAt: Long = CREATED_AT,
    ): SubscriptionEntity {
        return SubscriptionEntity(
            id = id,
            name = name,
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            billingIntervalUnit = billingIntervalUnit,
            billingIntervalCount = billingIntervalCount,
            startDateEpoch = startDateEpoch,
            nextBillingDateEpoch = nextBillingDateEpoch,
            categoryId = categoryId,
            templateId = templateId,
            logoUri = logoUri,
            memo = memo,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    companion object {
        private const val SUBSCRIPTION_NAME = "Netflix"
        private const val UPDATED_SUBSCRIPTION_NAME = "YouTube Premium"
        private const val SORT_SAME_DATE_FIRST_NAME = "Alpha"
        private const val SORT_SAME_DATE_SECOND_NAME = "Beta"
        private const val SORT_LATER_NAME = "Zulu"
        private const val DEFAULT_CURRENCY_CODE = "JPY"
        private const val UPDATED_CURRENCY_CODE = "USD"
        private const val DEFAULT_BILLING_INTERVAL_COUNT = 1
        private const val UPDATED_BILLING_INTERVAL_COUNT = 12
        private const val DEFAULT_AMOUNT_MINOR = 1_200L
        private const val UPDATED_AMOUNT_MINOR = 13_990L
        private const val UPDATED_MEMO = "年額プランへ変更"
        private const val NOT_FOUND_ID = -1L
        private const val CREATED_AT = 1_778_760_000_000L
        private const val UPDATED_AT = 1_778_846_400_000L
        private val UPDATED_NEXT_BILLING_DATE_EPOCH = LocalDate.of(2027, 6, 1).toEpochDay()
    }
}
