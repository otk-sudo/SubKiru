package com.subkiru.subkiru.notification

import com.subkiru.subkiru.core.domain.model.Subscription

data class BillingReminder(
    val subscription: Subscription,
    val daysUntilBilling: Long,
)
