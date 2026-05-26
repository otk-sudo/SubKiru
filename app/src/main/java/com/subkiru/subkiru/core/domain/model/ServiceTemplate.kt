package com.subkiru.subkiru.core.domain.model

data class ServiceTemplate(
    val id: Long,
    val name: String,
    val defaultAmountMinor: Long,
    val defaultCurrencyCode: String,
    val defaultInterval: BillingInterval,
    val logoResourceName: String,
    val categoryId: Long,
    val searchKeywords: String,
)
