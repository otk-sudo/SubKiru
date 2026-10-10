package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.model.MoneyAmount
import com.subkiru.subkiru.core.domain.model.monthlyTotalsByCurrency
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CalculateMonthlyTotalUseCase(
    private val repository: SubscriptionRepository,
) {
    // 月額換算の合計を通貨ごとに返す。異なる通貨は足し合わせない
    operator fun invoke(): Flow<List<MoneyAmount>> {
        return repository.observeActiveSubscriptions().map { subscriptions ->
            subscriptions.monthlyTotalsByCurrency()
        }
    }
}
