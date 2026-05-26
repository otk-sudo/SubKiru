package com.subkiru.subkiru.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.subkiru.subkiru.MainActivity
import com.subkiru.subkiru.SubKiruApplication
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

// ウィジェット用カラー定数（ダークモード対応時の変更箇所を限定するため集約）
private object WidgetColors {
    val background = Color(0xFFF7FAF9)
    val accent = Color(0xFF6AADA0)
    val textPrimary = Color(0xFF0D2620)
    val primary = Color(0xFF0F6E56)
    val warning = Color(0xFFE05C5C)
}

// ホーム画面ウィジェット本体
class SubKiruWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // アプリから UseCase を取得（手動DI）
        val app: SubKiruApplication = SubKiruApplication.from(context)
        val provider = WidgetDataProvider(
            calculateMonthlyTotalUseCase = app.calculateMonthlyTotalUseCase,
            getUpcomingBillingsUseCase = app.getUpcomingBillingsUseCase,
        )
        val data: WidgetData = try {
            provider.getWidgetData()
        } catch (e: CancellationException) {
            // コルーチンキャンセルは必ず再スロー
            throw e
        } catch (e: Exception) {
            // DB読み込みエラー時はデフォルト値を表示
            WidgetData(monthlyTotal = 0L, upcomingBillings = emptyList())
        }

        provideContent {
            GlanceTheme {
                SubKiruWidgetContent(context = context, data = data)
            }
        }
    }
}

// ウィジェットのルートコンポーザブル
@Composable
private fun SubKiruWidgetContent(context: Context, data: WidgetData) {
    // ウィジェットタップでアプリを起動
    val launchAction = actionStartActivity<MainActivity>()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(color = WidgetColors.background))
            .clickable(launchAction)
            .padding(16.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // ヘッダー: 月額合計ラベル
            Text(
                text = "月額合計",
                style = TextStyle(
                    color = ColorProvider(color = WidgetColors.accent),
                    fontSize = 12.sp,
                ),
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            // 月額合計金額（大きめフォント）
            Text(
                text = formatAmount(data.monthlyTotal, data.currencyCode),
                style = TextStyle(
                    color = ColorProvider(color = WidgetColors.textPrimary),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // 区切り線
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ColorProvider(color = WidgetColors.accent)),
            ) {}

            Spacer(modifier = GlanceModifier.height(6.dp))

            // 直近の請求予定リスト（最大3件）
            if (data.upcomingBillings.isEmpty()) {
                Text(
                    text = "直近の請求予定はありません",
                    style = TextStyle(
                        color = ColorProvider(color = WidgetColors.accent),
                        fontSize = 11.sp,
                    ),
                )
            } else {
                data.upcomingBillings.forEach { item ->
                    BillingItemRow(item = item)
                }
            }
        }
    }
}

// 請求予定の1行表示
@Composable
private fun BillingItemRow(item: UpcomingBillingItem) {
    // 期限が迫っている場合（当日・翌日）は警告色で表示
    val daysColor: Color = if (item.daysUntil <= 1L) {
        WidgetColors.warning
    } else {
        WidgetColors.accent
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        // サービス名
        Text(
            text = item.name,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = ColorProvider(color = WidgetColors.textPrimary),
                fontSize = 12.sp,
            ),
        )
        // 金額
        Text(
            text = formatAmount(item.amountMinor, item.currencyCode),
            style = TextStyle(
                color = ColorProvider(color = WidgetColors.primary),
                fontSize = 12.sp,
            ),
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        // 日数表示
        Text(
            text = formatDaysUntil(item.daysUntil),
            style = TextStyle(
                color = ColorProvider(color = daysColor),
                fontSize = 11.sp,
            ),
        )
    }
}

// テストから参照できるよう internal で公開
// amountMinor を currency の defaultFractionDigits に基づいて表示金額に変換する
// 例: JPY は fractionDigits=0 のためそのまま、USD は fractionDigits=2 のため 100 で割る
internal fun formatAmount(amountMinor: Long, currencyCode: String): String {
    val locale: Locale = if (currencyCode == "JPY") Locale.JAPAN else Locale.getDefault()
    val formatter: NumberFormat = NumberFormat.getCurrencyInstance(locale)
    val currency: Currency = Currency.getInstance(currencyCode)
    formatter.currency = currency
    val fractionDigits: Int = currency.defaultFractionDigits
    val displayAmount: Double = if (fractionDigits > 0) {
        amountMinor.toDouble() / Math.pow(10.0, fractionDigits.toDouble())
    } else {
        amountMinor.toDouble()
    }
    return formatter.format(displayAmount)
}

// テストから参照できるよう internal で公開
// 負の値 → 期限超過、0日 → 今日、1日 → 明日、それ以外 → N日後
internal fun formatDaysUntil(daysUntil: Long): String = when {
    daysUntil < 0L -> "期限超過"
    daysUntil == 0L -> "今日"
    daysUntil == 1L -> "明日"
    else -> "${daysUntil}日後"
}
