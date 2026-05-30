package com.subkiru.subkiru.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubscriptionCard(
    subscription: Subscription,
    categoryColorHex: String? = null,
    today: LocalDate = LocalDate.now(),
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { onLongClick?.invoke() },
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CARD_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CARD_CONTENT_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ロゴ表示は共通 ServiceAvatar に委譲（外部画像URLは扱わず、ローカルロゴ→頭文字→デフォルトの順）
            // 既存DBの subscription.logoUri（旧Clearbit URL）は参照しないため、描画経路に流れない
            // 背景色はサービス名から決定（テンプレートと同名同色にするため categoryColorHex は渡さない）
            ServiceAvatar(
                name = subscription.name,
                logoResId = serviceLogoResId(subscription.name),
            )
            Column(modifier = Modifier.weight(1f)) {
                val daysUntil = ChronoUnit.DAYS.between(today, subscription.nextBillingDate)
                Text(
                    text = subscription.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatBillingInterval(subscription.billingInterval),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    text = formatDaysUntilBilling(daysUntil),
                    style = MaterialTheme.typography.bodySmall,
                    color = daysUntilColor(daysUntil),
                )
            }
            Text(
                text = formatAmountJpy(subscription.amountMinor),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// 残り日数テキスト
internal fun formatDaysUntilBilling(days: Long): String {
    return when {
        days < 0 -> "請求日超過"
        days == 0L -> "今日"
        else -> "あと${days}日"
    }
}

// 残り日数に応じた色（3日以内は警告色）
@Composable
private fun daysUntilColor(days: Long): Color {
    return when {
        days <= URGENT_DAYS_THRESHOLD -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun formatBillingInterval(interval: BillingInterval): String {
    if (interval.count == 1) {
        return when (interval.unit) {
            BillingIntervalUnit.DAILY -> "日額"
            BillingIntervalUnit.WEEKLY -> "週額"
            BillingIntervalUnit.MONTHLY -> "月額"
            BillingIntervalUnit.YEARLY -> "年額"
        }
    }

    val unitLabel = when (interval.unit) {
        BillingIntervalUnit.DAILY -> "日"
        BillingIntervalUnit.WEEKLY -> "週間"
        BillingIntervalUnit.MONTHLY -> "ヶ月"
        BillingIntervalUnit.YEARLY -> "年"
    }
    return "${interval.count}${unitLabel}ごと"
}

internal fun formatAmountJpy(amountMinor: Long): String {
    return "¥${createAmountFormatter().format(amountMinor)}"
}

private val CARD_PADDING = 16.dp
private val CARD_CONTENT_SPACING = 12.dp
private const val URGENT_DAYS_THRESHOLD = 3L
private fun createAmountFormatter(): NumberFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

@Preview(showBackground = true)
@Composable
private fun SubscriptionCardPreview() {
    SubKiruTheme {
        SubscriptionCard(
            subscription = Subscription(
                id = 1L,
                name = "Netflix",
                amountMinor = 1490L,
                currencyCode = "JPY",
                billingInterval = BillingInterval(
                    unit = BillingIntervalUnit.MONTHLY,
                    count = 1,
                ),
                startDate = LocalDate.of(2025, 1, 1),
                nextBillingDate = LocalDate.of(2026, 6, 1),
                categoryId = null,
                templateId = null,
                logoUri = null,
                memo = null,
                isActive = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
