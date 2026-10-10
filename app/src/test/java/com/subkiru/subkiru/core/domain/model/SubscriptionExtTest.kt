package com.subkiru.subkiru.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class SubscriptionExtTest {

    @Test
    fun 月額サブスクはそのまま月額になる() {
        val subscription = subscription(amountMinor = 1_000L)

        assertEquals(1_000L, subscription.toMonthlyAmount())
    }

    @Test
    fun 年額サブスクは12で割って切り捨てる() {
        val subscription = subscription(
            amountMinor = 5_900L,
            interval = BillingInterval(BillingIntervalUnit.YEARLY, 1),
        )

        assertEquals(491L, subscription.toMonthlyAmount())
    }

    @Test
    fun 請求間隔の回数が0以下の場合は0になる() {
        val subscription = subscription(
            amountMinor = 1_000L,
            interval = BillingInterval(BillingIntervalUnit.MONTHLY, 0),
        )

        assertEquals(0L, subscription.toMonthlyAmount())
    }

    @Test
    fun 同じ通貨のサブスクは月額換算を合算する() {
        val subscriptions = listOf(
            subscription(id = 1L, amountMinor = 1_000L),
            subscription(id = 2L, amountMinor = 12_000L, interval = BillingInterval(BillingIntervalUnit.YEARLY, 1)),
        )

        val result = subscriptions.monthlyTotalsByCurrency()

        assertEquals(listOf(MoneyAmount(2_000L, "JPY")), result)
    }

    @Test
    fun 異なる通貨は足し合わせず通貨ごとに集計する() {
        val subscriptions = listOf(
            subscription(id = 1L, amountMinor = 1_000L, currencyCode = "JPY"),
            subscription(id = 2L, amountMinor = 999L, currencyCode = "USD"),
            subscription(id = 3L, amountMinor = 500L, currencyCode = "JPY"),
        )

        val result = subscriptions.monthlyTotalsByCurrency()

        assertEquals(listOf(MoneyAmount(1_500L, "JPY"), MoneyAmount(999L, "USD")), result)
    }

    @Test
    fun 集計結果はJPYを先頭にして他は通貨コード順に並ぶ() {
        val subscriptions = listOf(
            subscription(id = 1L, amountMinor = 100L, currencyCode = "USD"),
            subscription(id = 2L, amountMinor = 100L, currencyCode = "EUR"),
            subscription(id = 3L, amountMinor = 100L, currencyCode = "JPY"),
        )

        val result = subscriptions.monthlyTotalsByCurrency()

        assertEquals(listOf("JPY", "EUR", "USD"), result.map { it.currencyCode })
    }

    @Test
    fun サブスクが空の場合は空リストを返す() {
        val result = emptyList<Subscription>().monthlyTotalsByCurrency()

        assertTrue(result.isEmpty())
    }

    @Test
    fun 金額リストを通貨を保ったまま倍にできる() {
        val amounts = listOf(MoneyAmount(1_500L, "JPY"), MoneyAmount(999L, "USD"))

        val result = amounts.multipliedBy(12L)

        assertEquals(listOf(MoneyAmount(18_000L, "JPY"), MoneyAmount(11_988L, "USD")), result)
    }

    private fun subscription(
        id: Long = 1L,
        amountMinor: Long,
        currencyCode: String = "JPY",
        interval: BillingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
    ): Subscription {
        return Subscription(
            id = id,
            name = "Netflix",
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            billingInterval = interval,
            startDate = LocalDate.of(2026, 5, 1),
            nextBillingDate = LocalDate.of(2026, 6, 1),
            categoryId = null,
            templateId = null,
            logoUri = null,
            memo = null,
            isActive = true,
            createdAt = Instant.ofEpochMilli(1_778_760_000_000L),
            updatedAt = Instant.ofEpochMilli(1_778_760_000_000L),
        )
    }
}
