package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository

class ReorderSubscriptionsUseCase(
    private val repository: SubscriptionRepository,
) {
    // 渡された ID の並びを新しい表示順として保存する。重複した ID は最初の出現位置だけを残す
    suspend operator fun invoke(orderedIds: List<Long>) {
        val distinctIds = orderedIds.distinct()
        if (distinctIds.isEmpty()) return
        repository.reorderSubscriptions(distinctIds)
    }
}
