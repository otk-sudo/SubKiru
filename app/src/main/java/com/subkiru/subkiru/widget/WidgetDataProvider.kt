package com.subkiru.subkiru.widget

import com.subkiru.subkiru.core.domain.usecase.CalculateMonthlyTotalUseCase
import com.subkiru.subkiru.core.domain.usecase.GetUpcomingBillingsUseCase
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ウィジェット表示用の請求予定アイテム
data class UpcomingBillingItem(
    val name: String,
    val amountMinor: Long,
    val currencyCode: String,
    val daysUntil: Long,
)

// ウィジェット表示用のデータモデル
data class WidgetData(
    val monthlyTotal: Long,
    val currencyCode: String = "JPY",
    val upcomingBillings: List<UpcomingBillingItem>,
)

// ウィジェット表示用データを UseCases から取得するプロバイダー
class WidgetDataProvider(
    private val calculateMonthlyTotalUseCase: CalculateMonthlyTotalUseCase,
    private val getUpcomingBillingsUseCase: GetUpcomingBillingsUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun getWidgetData(): WidgetData {
        val monthlyTotal: Long = calculateMonthlyTotalUseCase().first()
        val today: LocalDate = LocalDate.now(clock)
        val upcomingBillings: List<UpcomingBillingItem> =
            getUpcomingBillingsUseCase(UPCOMING_DAYS)
                .first()
                .take(MAX_UPCOMING_ITEMS)
                .map { subscription ->
                    UpcomingBillingItem(
                        name = subscription.name,
                        amountMinor = subscription.amountMinor,
                        currencyCode = subscription.currencyCode,
                        daysUntil = ChronoUnit.DAYS.between(today, subscription.nextBillingDate),
                    )
                }
        return WidgetData(
            monthlyTotal = monthlyTotal,
            upcomingBillings = upcomingBillings,
        )
    }

    companion object {
        // 直近何日以内の請求予定を取得するか
        const val UPCOMING_DAYS: Int = 7
        // ウィジェットに表示する最大件数
        const val MAX_UPCOMING_ITEMS: Int = 3
    }
}
