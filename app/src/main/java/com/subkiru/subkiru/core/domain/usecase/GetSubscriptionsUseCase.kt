package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow

class GetSubscriptionsUseCase(
    private val repository: SubscriptionRepository,
) {
    operator fun invoke(): Flow<List<Subscription>> {
        return repository.observeActiveSubscriptions()
    }
}
