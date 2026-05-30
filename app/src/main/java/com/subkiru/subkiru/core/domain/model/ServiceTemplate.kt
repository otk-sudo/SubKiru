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
    // サービスのドメイン（例: netflix.com）。現在ロゴ表示には未使用だが、DB互換のため保持（残課題: DBカラム整理）
    val domain: String,
)
