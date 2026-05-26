package com.subkiru.subkiru.core.data.repository

import app.cash.turbine.test
import com.subkiru.subkiru.core.data.db.dao.SubscriptionDao
import com.subkiru.subkiru.core.data.db.entity.SubscriptionEntity
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SubscriptionRepositoryImplTest {
    private val dao = mockk<SubscriptionDao>()
    private val clock = Clock.fixed(
        UPDATED_AT_INSTANT,
        ZoneId.systemDefault(),
    )
    private val repository = SubscriptionRepositoryImpl(dao, clock)

    @Test
    fun observeActiveSubscriptionsでEntityがDomainModelに変換される() = runTest {
        every { dao.observeActiveSubscriptions() } returns flowOf(listOf(subscriptionEntity()))

        repository.observeActiveSubscriptions().test {
            val actual = awaitItem().single()
            assertEquals(subscription(), actual)
            assertEquals(START_DATE, actual.startDate)
            assertEquals(CREATED_AT_INSTANT, actual.createdAt)
            assertEquals(BillingInterval(BillingIntervalUnit.MONTHLY, BILLING_INTERVAL_COUNT), actual.billingInterval)
            awaitComplete()
        }
    }

    @Test
    fun getSubscriptionByIdでnullの場合はnullを返す() = runTest {
        coEvery { dao.getSubscriptionById(SUBSCRIPTION_ID) } returns null

        assertNull(repository.getSubscriptionById(SUBSCRIPTION_ID))
    }

    @Test
    fun getSubscriptionByIdでEntityがDomainModelに変換される() = runTest {
        coEvery { dao.getSubscriptionById(SUBSCRIPTION_ID) } returns subscriptionEntity()

        assertEquals(subscription(), repository.getSubscriptionById(SUBSCRIPTION_ID))
    }

    @Test
    fun addSubscriptionでDomainModelがEntityに変換されてDAOに渡される() = runTest {
        val entitySlot = slot<SubscriptionEntity>()
        coEvery { dao.addSubscription(capture(entitySlot)) } returns ADDED_ID

        val id = repository.addSubscription(subscription())

        assertEquals(ADDED_ID, id)
        assertEquals(subscriptionEntity(), entitySlot.captured)
        assertEquals(START_DATE.toEpochDay(), entitySlot.captured.startDateEpoch)
        assertEquals(CREATED_AT_INSTANT.toEpochMilli(), entitySlot.captured.createdAt)
    }

    @Test
    fun updateSubscriptionでDomainModelがEntityに変換されてDAOに渡される() = runTest {
        val entitySlot = slot<SubscriptionEntity>()
        coEvery { dao.updateSubscription(capture(entitySlot)) } returns Unit

        repository.updateSubscription(subscription())

        assertEquals(subscriptionEntity(), entitySlot.captured)
    }

    @Test
    fun deactivateSubscriptionで現在時刻がupdatedAtとしてDAOに渡される() = runTest {
        coEvery { dao.deactivateSubscription(SUBSCRIPTION_ID, UPDATED_AT_INSTANT.toEpochMilli()) } returns Unit

        repository.deactivateSubscription(SUBSCRIPTION_ID)

        coVerify(exactly = 1) {
            dao.deactivateSubscription(SUBSCRIPTION_ID, UPDATED_AT_INSTANT.toEpochMilli())
        }
    }

    @Test
    fun deleteSubscriptionでDomainModelがEntityに変換されてDAOに渡される() = runTest {
        val entitySlot = slot<SubscriptionEntity>()
        coEvery { dao.deleteSubscription(capture(entitySlot)) } returns Unit

        repository.deleteSubscription(subscription())

        assertEquals(subscriptionEntity(), entitySlot.captured)
    }

    private fun subscription(): Subscription {
        return Subscription(
            id = SUBSCRIPTION_ID,
            name = SUBSCRIPTION_NAME,
            amountMinor = AMOUNT_MINOR,
            currencyCode = CURRENCY_CODE,
            billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, BILLING_INTERVAL_COUNT),
            startDate = START_DATE,
            nextBillingDate = NEXT_BILLING_DATE,
            categoryId = CATEGORY_ID,
            templateId = TEMPLATE_ID,
            logoUri = LOGO_URI,
            memo = MEMO,
            isActive = true,
            createdAt = CREATED_AT_INSTANT,
            updatedAt = UPDATED_AT_INSTANT,
        )
    }

    private fun subscriptionEntity(): SubscriptionEntity {
        return SubscriptionEntity(
            id = SUBSCRIPTION_ID,
            name = SUBSCRIPTION_NAME,
            amountMinor = AMOUNT_MINOR,
            currencyCode = CURRENCY_CODE,
            billingIntervalUnit = BillingIntervalUnit.MONTHLY,
            billingIntervalCount = BILLING_INTERVAL_COUNT,
            startDateEpoch = START_DATE.toEpochDay(),
            nextBillingDateEpoch = NEXT_BILLING_DATE.toEpochDay(),
            categoryId = CATEGORY_ID,
            templateId = TEMPLATE_ID,
            logoUri = LOGO_URI,
            memo = MEMO,
            isActive = true,
            createdAt = CREATED_AT_INSTANT.toEpochMilli(),
            updatedAt = UPDATED_AT_INSTANT.toEpochMilli(),
        )
    }

    companion object {
        private const val SUBSCRIPTION_ID = 1L
        private const val ADDED_ID = 10L
        private const val SUBSCRIPTION_NAME = "Netflix"
        private const val AMOUNT_MINOR = 1_490L
        private const val CURRENCY_CODE = "JPY"
        private const val BILLING_INTERVAL_COUNT = 1
        private const val CATEGORY_ID = 2L
        private const val TEMPLATE_ID = 3L
        private const val LOGO_URI = "content://logo"
        private const val MEMO = "動画"
        private val START_DATE = LocalDate.of(2026, 5, 1)
        private val NEXT_BILLING_DATE = LocalDate.of(2026, 6, 1)
        private val CREATED_AT_INSTANT = Instant.parse("2026-05-01T00:00:00Z")
        private val UPDATED_AT_INSTANT = Instant.parse("2026-05-21T00:00:00Z")
    }
}
