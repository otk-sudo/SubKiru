package com.subkiru.subkiru.widget

import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.usecase.CalculateMonthlyTotalUseCase
import com.subkiru.subkiru.core.domain.usecase.GetUpcomingBillingsUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WidgetDataProviderTest {

    private val mockCalculateMonthlyTotalUseCase: CalculateMonthlyTotalUseCase = mockk()
    private val mockGetUpcomingBillingsUseCase: GetUpcomingBillingsUseCase = mockk()

    // テスト用固定日時: 2026-05-25
    private val fixedDate: LocalDate = LocalDate.of(2026, 5, 25)
    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2026-05-25T00:00:00Z"),
        ZoneId.of("UTC"),
    )

    private lateinit var provider: WidgetDataProvider

    @BeforeEach
    fun setUp() {
        provider = WidgetDataProvider(
            calculateMonthlyTotalUseCase = mockCalculateMonthlyTotalUseCase,
            getUpcomingBillingsUseCase = mockGetUpcomingBillingsUseCase,
            clock = fixedClock,
        )
    }

    // テスト用サブスクリプションを生成するヘルパー
    private fun createSubscription(
        id: Long = 1L,
        name: String = "Netflix",
        amountMinor: Long = 1490L,
        currencyCode: String = "JPY",
        nextBillingDate: LocalDate = fixedDate.plusDays(3),
    ): Subscription = Subscription(
        id = id,
        name = name,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        billingInterval = BillingInterval(count = 1, unit = BillingIntervalUnit.MONTHLY),
        startDate = LocalDate.of(2024, 1, 1),
        nextBillingDate = nextBillingDate,
        categoryId = null,
        templateId = null,
        logoUri = null,
        memo = null,
        isActive = true,
        createdAt = Instant.parse("2024-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
    )

    @Nested
    inner class `月額合計取得` {

        @Test
        fun `should return correct monthlyTotal when CalculateMonthlyTotalUseCase emits value`() = runTest {
            // Arrange（準備）
            val expectedTotal = 12345L
            every { mockCalculateMonthlyTotalUseCase() } returns flowOf(expectedTotal)
            every { mockGetUpcomingBillingsUseCase(any()) } returns flowOf(emptyList())

            // Act（実行）
            val result: WidgetData = provider.getWidgetData()

            // Assert（検証）
            assertEquals(expectedTotal, result.monthlyTotal)
        }
    }

    @Nested
    inner class `直近請求予定の件数制限` {

        @Test
        fun `should limit upcomingBillings to 3 items when UseCase returns 5 subscriptions`() = runTest {
            // Arrange（準備）
            val subscriptions: List<Subscription> = (1..5).map { i ->
                createSubscription(id = i.toLong(), name = "Service$i", nextBillingDate = fixedDate.plusDays(i.toLong()))
            }
            every { mockCalculateMonthlyTotalUseCase() } returns flowOf(0L)
            every { mockGetUpcomingBillingsUseCase(any()) } returns flowOf(subscriptions)

            // Act（実行）
            val result: WidgetData = provider.getWidgetData()

            // Assert（検証）
            assertEquals(3, result.upcomingBillings.size)
        }

        @Test
        fun `should return empty list when GetUpcomingBillingsUseCase returns empty list`() = runTest {
            // Arrange（準備）
            every { mockCalculateMonthlyTotalUseCase() } returns flowOf(0L)
            every { mockGetUpcomingBillingsUseCase(any()) } returns flowOf(emptyList())

            // Act（実行）
            val result: WidgetData = provider.getWidgetData()

            // Assert（検証）
            assertEquals(emptyList<UpcomingBillingItem>(), result.upcomingBillings)
        }
    }

    @Nested
    inner class `UpcomingBillingItemへのマッピング` {

        @Test
        fun `should correctly map Subscription fields to UpcomingBillingItem`() = runTest {
            // Arrange（準備）
            val subscription: Subscription = createSubscription(
                name = "Spotify",
                amountMinor = 980L,
                currencyCode = "JPY",
                nextBillingDate = fixedDate.plusDays(3),
            )
            every { mockCalculateMonthlyTotalUseCase() } returns flowOf(0L)
            every { mockGetUpcomingBillingsUseCase(any()) } returns flowOf(listOf(subscription))

            // Act（実行）
            val result: WidgetData = provider.getWidgetData()

            // Assert（検証）
            val item: UpcomingBillingItem = result.upcomingBillings.first()
            assertEquals("Spotify", item.name)
            assertEquals(980L, item.amountMinor)
            assertEquals("JPY", item.currencyCode)
            assertEquals(3L, item.daysUntil)
        }
    }

    @Nested
    inner class `直近7日間の取得` {

        @Test
        fun `should call GetUpcomingBillingsUseCase with UPCOMING_DAYS when getWidgetData is called`() = runTest {
            // Arrange（準備）
            every { mockCalculateMonthlyTotalUseCase() } returns flowOf(0L)
            every { mockGetUpcomingBillingsUseCase(WidgetDataProvider.UPCOMING_DAYS) } returns flowOf(emptyList())

            // Act（実行）
            provider.getWidgetData()

            // Assert（検証）
            verify(exactly = 1) { mockGetUpcomingBillingsUseCase(WidgetDataProvider.UPCOMING_DAYS) }
        }
    }
}
