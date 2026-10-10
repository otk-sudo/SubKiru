package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository

class GetSubscriptionByIdUseCase(
    private val repository: SubscriptionRepository,
) {
    // 存在しない場合と削除済みの場合は null を返す
    suspend operator fun invoke(id: Long): Subscription? {
        return repository.getSubscriptionById(id)?.takeIf { it.isActive }
    }
}
