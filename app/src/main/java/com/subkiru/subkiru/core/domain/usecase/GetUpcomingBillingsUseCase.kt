package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

class GetUpcomingBillingsUseCase(
    private val repository: SubscriptionRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    operator fun invoke(withinDays: Int): Flow<List<Subscription>> {
        return repository.observeActiveSubscriptions().map { subscriptions ->
            val today = LocalDate.now(clock)
            val deadline = today.plusDays(withinDays.toLong())
            subscriptions.filter { it.nextBillingDate in today..deadline }
        }
    }
}
