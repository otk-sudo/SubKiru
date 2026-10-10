package com.subkiru.subkiru.core.domain.model

// サブスクリプションの金額を月額に換算する
fun Subscription.toMonthlyAmount(): Long {
    val interval = billingInterval
    if (interval.count <= 0) return 0L
    return when (interval.unit) {
        BillingIntervalUnit.DAILY -> amountMinor * 30L / interval.count
        BillingIntervalUnit.WEEKLY -> amountMinor * 4L / interval.count
        BillingIntervalUnit.MONTHLY -> amountMinor / interval.count
        BillingIntervalUnit.YEARLY -> amountMinor / (12L * interval.count)
    }
}

// 通貨ごとの月額換算合計を返す。異なる通貨は足し合わせない（換算レートを持たないため）
fun List<Subscription>.monthlyTotalsByCurrency(): List<MoneyAmount> {
    return groupBy { it.currencyCode }
        .map { (currencyCode, subscriptions) ->
            MoneyAmount(
                amountMinor = subscriptions.sumOf { it.toMonthlyAmount() },
                currencyCode = currencyCode,
            )
        }
        .sortedWith(compareBy(currencyCodeComparator) { it.currencyCode })
}
