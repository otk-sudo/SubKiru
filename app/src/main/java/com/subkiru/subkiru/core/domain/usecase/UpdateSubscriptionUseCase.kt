package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository

class UpdateSubscriptionUseCase(
    private val repository: SubscriptionRepository,
) {
    sealed interface Result {
        data object Success : Result
        data class ValidationError(val errors: List<SubscriptionValidationError>) : Result

        // 対象が存在しない、または削除済み
        data object NotFound : Result
    }

    suspend operator fun invoke(subscription: Subscription): Result {
        val errors = SubscriptionValidator.validate(subscription)
        if (errors.isNotEmpty()) {
            return Result.ValidationError(errors)
        }
        val existing = repository.getSubscriptionById(subscription.id)
        if (existing == null || !existing.isActive) {
            return Result.NotFound
        }
        // 作成日時・有効フラグ・並び順は更新で変えない
        repository.updateSubscription(
            subscription.copy(
                createdAt = existing.createdAt,
                isActive = existing.isActive,
                sortOrder = existing.sortOrder,
            ),
        )
        return Result.Success
    }
}
