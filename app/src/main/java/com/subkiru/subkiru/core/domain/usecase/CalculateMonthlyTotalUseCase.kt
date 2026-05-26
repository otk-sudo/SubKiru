package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CalculateMonthlyTotalUseCase(
    private val repository: SubscriptionRepository,
) {
    operator fun invoke(): Flow<Long> {
        return repository.observeActiveSubscriptions().map { subscriptions ->
            subscriptions.sumOf { toMonthlyAmount(it) }
        }
    }

    private fun toMonthlyAmount(subscription: Subscription): Long {
        val interval = subscription.billingInterval
        return when (interval.unit) {
            BillingIntervalUnit.DAILY -> subscription.amountMinor * DAYS_PER_MONTH / interval.count
            BillingIntervalUnit.WEEKLY -> subscription.amountMinor * WEEKS_PER_MONTH / interval.count
            BillingIntervalUnit.MONTHLY -> subscription.amountMinor / interval.count
            BillingIntervalUnit.YEARLY -> subscription.amountMinor / (MONTHS_PER_YEAR * interval.count)
        }
    }

    companion object {
        private const val DAYS_PER_MONTH = 30L
        private const val WEEKS_PER_MONTH = 4L
        private const val MONTHS_PER_YEAR = 12L
    }
}
