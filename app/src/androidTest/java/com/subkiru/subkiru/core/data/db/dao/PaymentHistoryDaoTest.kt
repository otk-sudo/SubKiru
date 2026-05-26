package com.subkiru.subkiru.core.data.db.dao

import android.content.Context
import app.cash.turbine.test
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subkiru.subkiru.core.data.db.SubKiruDatabase
import com.subkiru.subkiru.core.data.db.entity.CategoryEntity
import com.subkiru.subkiru.core.data.db.entity.PaymentHistoryEntity
import com.subkiru.subkiru.core.data.db.entity.SubscriptionEntity
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PaymentHistoryDaoTest {
    private lateinit var database: SubKiruDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var subscriptionDao: SubscriptionDao
    private lateinit var dao: PaymentHistoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SubKiruDatabase::class.java).build()
        categoryDao = database.categoryDao()
        subscriptionDao = database.subscriptionDao()
        dao = database.paymentHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun 支払い履歴を追加して取得できる() = runBlocking {
        val subscriptionId = addSubscription()
        val billingDateEpoch = LocalDate.of(2026, 5, 20).toEpochDay()
        val id = dao.addPayment(
            payment = payment(
                subscriptionId = subscriptionId,
                billingDateEpoch = billingDateEpoch,
                amountMinor = AMOUNT_MINOR,
            )
        )

        val actual = requireNotNull(dao.getPaymentById(id))

        assertEquals(subscriptionId, actual.subscriptionId)
        assertEquals(billingDateEpoch, actual.billingDateEpoch)
        assertEquals(AMOUNT_MINOR, actual.amountMinor)
        assertEquals(CURRENCY_CODE, actual.currencyCode)
    }

    @Test
    fun observeBySubscriptionIdで対象サブスクの履歴のみ取得できる() = runBlocking {
        val targetSubscriptionId = addSubscription(name = TARGET_SUBSCRIPTION_NAME)
        val otherSubscriptionId = addSubscription(name = OTHER_SUBSCRIPTION_NAME)
        val targetPaymentId = dao.addPayment(payment = payment(subscriptionId = targetSubscriptionId))
        dao.addPayment(payment = payment(subscriptionId = otherSubscriptionId))

        val actualIds = dao.observeBySubscriptionId(targetSubscriptionId).first().map { it.id }

        assertEquals(listOf(targetPaymentId), actualIds)
    }

    @Test
    fun observeBySubscriptionIdのソート順が正しい() = runBlocking {
        val subscriptionId = addSubscription()
        val olderId = dao.addPayment(
            payment = payment(
                subscriptionId = subscriptionId,
                billingDateEpoch = LocalDate.of(2026, 4, 20).toEpochDay(),
            )
        )
        val newerId = dao.addPayment(
            payment = payment(
                subscriptionId = subscriptionId,
                billingDateEpoch = LocalDate.of(2026, 6, 20).toEpochDay(),
            )
        )

        val actualIds = dao.observeBySubscriptionId(subscriptionId).first().map { it.id }

        assertEquals(listOf(newerId, olderId), actualIds)
    }

    @Test
    fun observeByDateRangeで期間内の履歴のみ取得できる() = runBlocking {
        val subscriptionId = addSubscription()
        dao.addPayment(
            payment = payment(
                subscriptionId = subscriptionId,
                billingDateEpoch = LocalDate.of(2026, 4, 20).toEpochDay(),
            )
        )
        val inRangeId = dao.addPayment(
            payment = payment(
                subscriptionId = subscriptionId,
                billingDateEpoch = LocalDate.of(2026, 5, 20).toEpochDay(),
            )
        )

        val actualIds = dao.observeByDateRange(
            startEpoch = LocalDate.of(2026, 5, 1).toEpochDay(),
            endEpoch = LocalDate.of(2026, 5, 31).toEpochDay(),
        ).first().map { it.id }

        assertEquals(listOf(inRangeId), actualIds)
    }

    @Test
    fun updatePaymentで更新後に正しい値が取得できる() = runBlocking {
        val subscriptionId = addSubscription()
        val id = dao.addPayment(payment = payment(subscriptionId = subscriptionId))
        val updated = requireNotNull(dao.getPaymentById(id)).copy(
            billingDateEpoch = UPDATED_BILLING_DATE_EPOCH,
            amountMinor = UPDATED_AMOUNT_MINOR,
            currencyCode = UPDATED_CURRENCY_CODE,
            isPaid = false,
            recordedAt = UPDATED_RECORDED_AT,
        )

        dao.updatePayment(updated)

        val actual = requireNotNull(dao.getPaymentById(id))
        assertEquals(UPDATED_BILLING_DATE_EPOCH, actual.billingDateEpoch)
        assertEquals(UPDATED_AMOUNT_MINOR, actual.amountMinor)
        assertEquals(UPDATED_CURRENCY_CODE, actual.currencyCode)
        assertEquals(false, actual.isPaid)
        assertEquals(UPDATED_RECORDED_AT, actual.recordedAt)
    }

    @Test
    fun deletePaymentで物理削除後にnullが返る() = runBlocking {
        val subscriptionId = addSubscription()
        val id = dao.addPayment(payment = payment(subscriptionId = subscriptionId))
        val saved = requireNotNull(dao.getPaymentById(id))

        dao.deletePayment(saved)

        assertNull(dao.getPaymentById(id))
    }

    @Test
    fun getPaymentByIdに存在しないIDを渡すとnullが返る() = runBlocking {
        assertNull(dao.getPaymentById(NOT_FOUND_ID))
    }

    @Test
    fun 支払い履歴追加後にFlowが更新を通知する() = runBlocking {
        val subscriptionId = addSubscription()

        dao.observeBySubscriptionId(subscriptionId).test {
            assertEquals(emptyList<PaymentHistoryEntity>(), awaitItem())

            val id = dao.addPayment(payment = payment(subscriptionId = subscriptionId))

            assertEquals(listOf(id), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun addSubscription(name: String = SUBSCRIPTION_NAME): Long {
        val categoryId = categoryDao.addCategory(
            category = CategoryEntity(
                name = CATEGORY_NAME,
                iconName = ICON_NAME,
                colorHex = COLOR_HEX,
                sortOrder = SORT_ORDER,
            )
        )
        return subscriptionDao.addSubscription(
            subscription = SubscriptionEntity(
                name = name,
                amountMinor = SUBSCRIPTION_AMOUNT_MINOR,
                currencyCode = CURRENCY_CODE,
                billingIntervalUnit = BillingIntervalUnit.MONTHLY,
                billingIntervalCount = BILLING_INTERVAL_COUNT,
                startDateEpoch = START_DATE_EPOCH,
                nextBillingDateEpoch = NEXT_BILLING_DATE_EPOCH,
                categoryId = categoryId,
                createdAt = CREATED_AT,
                updatedAt = CREATED_AT,
            )
        )
    }

    private fun payment(
        id: Long = PaymentHistoryEntity.UNSAVED_ID,
        subscriptionId: Long,
        billingDateEpoch: Long = BILLING_DATE_EPOCH,
        amountMinor: Long = AMOUNT_MINOR,
        currencyCode: String = CURRENCY_CODE,
        isPaid: Boolean = true,
        recordedAt: Long = RECORDED_AT,
    ): PaymentHistoryEntity {
        return PaymentHistoryEntity(
            id = id,
            subscriptionId = subscriptionId,
            billingDateEpoch = billingDateEpoch,
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            isPaid = isPaid,
            recordedAt = recordedAt,
        )
    }

    companion object {
        private const val CATEGORY_NAME = "動画"
        private const val ICON_NAME = "movie"
        private const val COLOR_HEX = "#0F6E56"
        private const val SORT_ORDER = 1
        private const val SUBSCRIPTION_NAME = "Netflix"
        private const val TARGET_SUBSCRIPTION_NAME = "Target"
        private const val OTHER_SUBSCRIPTION_NAME = "Other"
        private const val SUBSCRIPTION_AMOUNT_MINOR = 1_490L
        private const val AMOUNT_MINOR = 1_490L
        private const val UPDATED_AMOUNT_MINOR = 1_980L
        private const val CURRENCY_CODE = "JPY"
        private const val UPDATED_CURRENCY_CODE = "USD"
        private const val BILLING_INTERVAL_COUNT = 1
        private const val CREATED_AT = 1_778_760_000_000L
        private const val RECORDED_AT = 1_778_760_000_000L
        private const val UPDATED_RECORDED_AT = 1_778_846_400_000L
        private const val NOT_FOUND_ID = -1L
        private val START_DATE_EPOCH = LocalDate.of(2026, 5, 1).toEpochDay()
        private val NEXT_BILLING_DATE_EPOCH = LocalDate.of(2026, 6, 1).toEpochDay()
        private val BILLING_DATE_EPOCH = LocalDate.of(2026, 5, 20).toEpochDay()
        private val UPDATED_BILLING_DATE_EPOCH = LocalDate.of(2026, 6, 20).toEpochDay()
    }
}
