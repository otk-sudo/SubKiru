package com.subkiru.subkiru.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.ui.component.HeroTotalCard
import com.subkiru.subkiru.core.ui.component.SubscriptionListRow
import com.subkiru.subkiru.core.ui.component.formatAmountJpy
import com.subkiru.subkiru.core.ui.component.formatCurrencyAmount
import com.subkiru.subkiru.core.ui.component.serviceLogoResId
import com.subkiru.subkiru.ui.theme.HeroCardBackground
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    AnalyticsContent(uiState = uiState, modifier = modifier)
}

@Composable
private fun AnalyticsContent(
    uiState: AnalyticsUiState,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> LoadingContent(modifier)
        uiState.error != null -> ErrorContent(uiState.error, modifier)
        uiState.subscriptionCount == 0 -> EmptyContent(modifier)
        else -> AnalyticsLoadedContent(uiState = uiState, modifier = modifier)
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun EmptyContent(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

@Composable
private fun AnalyticsLoadedContent(
    uiState: AnalyticsUiState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = SCREEN_HORIZONTAL_PADDING,
            end = SCREEN_HORIZONTAL_PADDING,
            top = SCREEN_TOP_PADDING,
            bottom = SCREEN_BOTTOM_PADDING,
        ),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        item(key = "summary") {
            HeroTotalCard(
                label = "今月の固定費",
                amount = formatAmountJpy(uiState.monthlyTotal),
                annualAmount = formatAmountJpy(uiState.annualTotal),
                subscriptionCount = uiState.subscriptionCount,
            )
        }

        item(key = "expensive_header") {
            SectionHeader(
                title = "コストが高いサブスク",
                supportingText = "月額換算で比較しています",
            )
        }

        itemsIndexed(
            items = uiState.breakdowns,
            key = { _, breakdown -> breakdown.id },
        ) { index, breakdown ->
            ExpensiveSubscriptionRow(
                breakdown = breakdown,
                isHighest = index == HIGHEST_COST_INDEX,
            )
        }

        item(key = "saving_simulation") {
            SavingSimulationCard(
                annualSavingPotential = uiState.breakdowns.first().monthlyAmount * MONTHS_PER_YEAR,
            )
        }

        item(key = "spend_trend") {
            SpendTrendSection(points = uiState.spendTrend)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun ExpensiveSubscriptionRow(
    breakdown: SubscriptionBreakdown,
    isHighest: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isHighest) {
                    Modifier
                        .clip(RoundedCornerShape(HIGHLIGHT_CORNER_RADIUS))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = HIGHLIGHT_HORIZONTAL_PADDING)
                } else {
                    Modifier
                },
            ),
    ) {
        if (isHighest) {
            Text(
                text = "特に高額",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .padding(top = BADGE_TOP_PADDING)
                    .clip(RoundedCornerShape(BADGE_CORNER_RADIUS))
                    .background(MaterialTheme.colorScheme.error)
                    .padding(
                        horizontal = BADGE_HORIZONTAL_PADDING,
                        vertical = BADGE_VERTICAL_PADDING,
                    ),
            )
        }
        SubscriptionListRow(
            serviceName = breakdown.name,
            supportingText = "元の請求 ${formatCurrencyAmount(
                breakdown.originalAmountMinor,
                breakdown.currencyCode,
            )} ・ ${(breakdown.percentage * PERCENTAGE_MULTIPLIER).toInt()}%",
            amountText = formatAmountJpy(breakdown.monthlyAmount),
            amountSuffix = "/月換算",
            amountColor = if (isHighest) MaterialTheme.colorScheme.error else null,
            logoResId = serviceLogoResId(breakdown.name),
            showChevron = false,
        )
    }
}

@Composable
private fun SavingSimulationCard(
    annualSavingPotential: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SAVING_CARD_CORNER_RADIUS))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(SAVING_CARD_PADDING),
        verticalArrangement = Arrangement.spacedBy(SAVING_CONTENT_SPACING),
    ) {
        Text(
            text = "節約シミュレーション",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = formatAmountJpy(annualSavingPotential),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "最も高い契約を見直した場合の年間削減余地",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SpendTrendSection(
    points: List<MonthlySpendPoint>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CHART_CORNER_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(CHART_PADDING),
    ) {
        SectionHeader(
            title = "支出の推移",
            supportingText = "契約開始日から算出した月額換算",
        )
        Spacer(modifier = Modifier.height(CHART_TOP_SPACING))
        SpendBarChart(points = points)
    }
}

@Composable
private fun SpendBarChart(
    points: List<MonthlySpendPoint>,
    modifier: Modifier = Modifier,
) {
    val maxAmount = points.maxOfOrNull { it.amount }?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(BAR_GROUP_SPACING),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEachIndexed { index, point ->
            val fraction = point.amount.toFloat() / maxAmount
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatCompactAmount(point.amount),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (point.amount > 0L) {
                        Box(
                            modifier = Modifier
                                .width(BAR_WIDTH)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(BAR_CORNER_RADIUS))
                                .background(
                                    if (index == points.lastIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        HeroCardBackground
                                    },
                                ),
                        )
                    }
                }
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = BAR_LABEL_TOP_PADDING),
                )
            }
        }
    }
}

private fun formatCompactAmount(amount: Long): String = when {
    amount >= THOUSAND_AMOUNT -> "¥${amount / THOUSAND_AMOUNT}k"
    else -> "¥$amount"
}

private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SCREEN_TOP_PADDING = 12.dp
private val SCREEN_BOTTOM_PADDING = 24.dp
private val SECTION_SPACING = 16.dp
private val HIGHLIGHT_CORNER_RADIUS = 16.dp
private val HIGHLIGHT_HORIZONTAL_PADDING = 12.dp
private val BADGE_TOP_PADDING = 10.dp
private val BADGE_CORNER_RADIUS = 8.dp
private val BADGE_HORIZONTAL_PADDING = 8.dp
private val BADGE_VERTICAL_PADDING = 3.dp
private val SAVING_CARD_CORNER_RADIUS = 20.dp
private val SAVING_CARD_PADDING = 20.dp
private val SAVING_CONTENT_SPACING = 4.dp
private val CHART_CORNER_RADIUS = 20.dp
private val CHART_PADDING = 20.dp
private val CHART_TOP_SPACING = 16.dp
private val CHART_HEIGHT = 180.dp
private val BAR_GROUP_SPACING = 6.dp
private val BAR_WIDTH = 24.dp
private val BAR_CORNER_RADIUS = 6.dp
private val BAR_LABEL_TOP_PADDING = 6.dp
private const val HIGHEST_COST_INDEX = 0
private const val PERCENTAGE_MULTIPLIER = 100
private const val MONTHS_PER_YEAR = 12L
private const val THOUSAND_AMOUNT = 1_000L

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
                    SubscriptionBreakdown(1L, "Netflix", 1_490L, "JPY", 1_490L, 0.40f),
                    SubscriptionBreakdown(2L, "YouTube Premium", 1_280L, "JPY", 1_280L, 0.34f),
                    SubscriptionBreakdown(3L, "Spotify", 980L, "JPY", 980L, 0.26f),
                ),
                spendTrend = listOf(
                    MonthlySpendPoint("1月", 2_100L),
                    MonthlySpendPoint("2月", 2_100L),
                    MonthlySpendPoint("3月", 2_770L),
                    MonthlySpendPoint("4月", 2_770L),
                    MonthlySpendPoint("5月", 3_750L),
                    MonthlySpendPoint("6月", 3_750L),
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
        AnalyticsContent(uiState = AnalyticsUiState(isLoading = false))
    }
}
