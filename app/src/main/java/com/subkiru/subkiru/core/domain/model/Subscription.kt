package com.subkiru.subkiru.core.domain.model

import java.time.Instant
import java.time.LocalDate

data class Subscription(
    val id: Long,
    val name: String,
    val amountMinor: Long,
    val currencyCode: String,
    val billingInterval: BillingInterval,
    val startDate: LocalDate,
    val nextBillingDate: LocalDate,
    val categoryId: Long?,
    val templateId: Long?,
    val logoUri: String?,
    val memo: String?,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
