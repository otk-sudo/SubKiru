package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository

class AddSubscriptionUseCase(
    private val repository: SubscriptionRepository,
) {
    sealed interface Result {
        data class Success(val id: Long) : Result
        data class ValidationError(val errors: List<Error>) : Result
    }

    enum class Error {
        EMPTY_NAME,
        NEGATIVE_AMOUNT,
        INVALID_INTERVAL_COUNT,
        START_DATE_AFTER_NEXT_BILLING_DATE,
    }

    suspend operator fun invoke(subscription: Subscription): Result {
        val errors = validate(subscription)
        if (errors.isNotEmpty()) {
            return Result.ValidationError(errors)
        }
        val id = repository.addSubscription(subscription)
        return Result.Success(id)
    }

    private fun validate(subscription: Subscription): List<Error> {
        return buildList {
            if (subscription.name.isBlank()) {
                add(Error.EMPTY_NAME)
            }
            if (subscription.amountMinor < 0L) {
                add(Error.NEGATIVE_AMOUNT)
            }
            if (subscription.billingInterval.count < MIN_INTERVAL_COUNT) {
                add(Error.INVALID_INTERVAL_COUNT)
            }
            if (subscription.startDate > subscription.nextBillingDate) {
                add(Error.START_DATE_AFTER_NEXT_BILLING_DATE)
            }
        }
    }

    companion object {
        private const val MIN_INTERVAL_COUNT = 1
    }
}
