package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository

class DeleteSubscriptionUseCase(
    private val repository: SubscriptionRepository,
) {
    suspend operator fun invoke(id: Long) {
        repository.deactivateSubscription(id)
    }
}
