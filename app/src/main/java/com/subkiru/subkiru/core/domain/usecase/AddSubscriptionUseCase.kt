package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository

class AddSubscriptionUseCase(
    private val repository: SubscriptionRepository,
) {
    sealed interface Result {
        data class Success(val id: Long) : Result
        data class ValidationError(val errors: List<SubscriptionValidationError>) : Result
    }

    suspend operator fun invoke(subscription: Subscription): Result {
        val errors = SubscriptionValidator.validate(subscription)
        if (errors.isNotEmpty()) {
            return Result.ValidationError(errors)
        }
        val id = repository.addSubscription(subscription)
        return Result.Success(id)
    }
}
