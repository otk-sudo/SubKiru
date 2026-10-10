package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.model.Subscription

enum class SubscriptionValidationError {
    EMPTY_NAME,
    NEGATIVE_AMOUNT,
    INVALID_INTERVAL_COUNT,
    START_DATE_AFTER_NEXT_BILLING_DATE,
}

// 追加・更新で共通のバリデーション規則
internal object SubscriptionValidator {
    private const val MIN_INTERVAL_COUNT = 1

    fun validate(subscription: Subscription): List<SubscriptionValidationError> {
        return buildList {
            if (subscription.name.isBlank()) {
                add(SubscriptionValidationError.EMPTY_NAME)
            }
            if (subscription.amountMinor < 0L) {
                add(SubscriptionValidationError.NEGATIVE_AMOUNT)
            }
            if (subscription.billingInterval.count < MIN_INTERVAL_COUNT) {
                add(SubscriptionValidationError.INVALID_INTERVAL_COUNT)
            }
            if (subscription.startDate > subscription.nextBillingDate) {
                add(SubscriptionValidationError.START_DATE_AFTER_NEXT_BILLING_DATE)
            }
        }
    }
}
