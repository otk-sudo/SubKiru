package com.subkiru.subkiru.core.ui.component

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
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SubscriptionCard(
    subscription: Subscription,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CARD_PADDING),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
                    text = "次回: ${subscription.nextBillingDate.format(DATE_FORMATTER)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    return "¥${AMOUNT_FORMATTER.format(amountMinor)}"
}

private val CARD_PADDING = 16.dp
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
private val AMOUNT_FORMATTER: NumberFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

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
