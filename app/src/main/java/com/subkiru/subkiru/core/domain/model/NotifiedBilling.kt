package com.subkiru.subkiru.core.domain.model

import java.time.LocalDate

// 通知済みの請求を表す。「サブスクID + 請求日」の組で1回の請求を識別する
data class NotifiedBilling(
    val subscriptionId: Long,
    val billingDate: LocalDate,
)
