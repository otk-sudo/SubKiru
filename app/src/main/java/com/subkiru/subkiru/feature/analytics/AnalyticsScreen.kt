package com.subkiru.subkiru.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.ui.component.formatAmountJpy
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AnalyticsContent(
        uiState = uiState,
        modifier = modifier,
    )
}

@Composable
private fun AnalyticsContent(
    uiState: AnalyticsUiState,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        uiState.error != null -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        uiState.subscriptionCount == 0 -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "分析するサブスクがありません",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "サブスクを追加すると分析が表示されます",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        }

        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = SCREEN_HORIZONTAL_PADDING,
                    end = SCREEN_HORIZONTAL_PADDING,
                    top = SCREEN_TOP_PADDING,
                    bottom = SCREEN_BOTTOM_PADDING,
                ),
                verticalArrangement = Arrangement.spacedBy(LIST_ITEM_SPACING),
            ) {
                item(key = "summary") {
                    SummaryCard(
                        monthlyTotal = uiState.monthlyTotal,
                        annualTotal = uiState.annualTotal,
                        subscriptionCount = uiState.subscriptionCount,
                    )
                }

                item(key = "breakdown_header") {
                    Text(
                        text = "サブスク別内訳",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = SECTION_TOP_PADDING),
                    )
                }

                items(
                    items = uiState.breakdowns,
                    key = { it.id },
                ) { breakdown ->
                    BreakdownItem(breakdown = breakdown)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    monthlyTotal: Long,
    annualTotal: Long,
    subscriptionCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_ITEM_SPACING),
        ) {
            SummaryRow(label = "月額合計", value = formatAmountJpy(monthlyTotal))
            SummaryRow(label = "年額換算", value = formatAmountJpy(annualTotal))
            SummaryRow(label = "サブスク数", value = "${subscriptionCount}件")
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BreakdownItem(
    breakdown: SubscriptionBreakdown,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = breakdown.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatAmountJpy(breakdown.monthlyAmount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(PROGRESS_TOP_SPACING))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { breakdown.percentage },
                    modifier = Modifier
                        .weight(1f)
                        .height(PROGRESS_HEIGHT),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                Text(
                    text = "${(breakdown.percentage * PERCENTAGE_MULTIPLIER).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = PERCENTAGE_START_PADDING),
                )
            }
        }
    }
}

private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SCREEN_TOP_PADDING = 8.dp
private val SCREEN_BOTTOM_PADDING = 24.dp
private val LIST_ITEM_SPACING = 12.dp
private val SECTION_TOP_PADDING = 8.dp
private val CARD_PADDING = 16.dp
private val CARD_ITEM_SPACING = 12.dp
private val PROGRESS_TOP_SPACING = 8.dp
private val PROGRESS_HEIGHT = 8.dp
private val PERCENTAGE_START_PADDING = 8.dp
private const val PERCENTAGE_MULTIPLIER = 100

@Preview(showBackground = true)
@Composable
private fun AnalyticsContentPreview() {
    SubKiruTheme {
        AnalyticsContent(
            uiState = AnalyticsUiState(
                monthlyTotal = 3_750L,
                annualTotal = 45_000L,
                subscriptionCount = 3,
                breakdowns = listOf(
                    SubscriptionBreakdown(1L, "Netflix", 1_490L, 0.40f),
                    SubscriptionBreakdown(2L, "YouTube Premium", 1_280L, 0.34f),
                    SubscriptionBreakdown(3L, "Spotify", 980L, 0.26f),
                ),
                isLoading = false,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalyticsContentEmptyPreview() {
    SubKiruTheme {
        AnalyticsContent(
            uiState = AnalyticsUiState(
                subscriptionCount = 0,
                isLoading = false,
            ),
        )
    }
}
