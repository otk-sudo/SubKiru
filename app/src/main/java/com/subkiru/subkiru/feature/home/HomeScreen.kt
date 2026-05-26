package com.subkiru.subkiru.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.ui.component.SubscriptionCard
import com.subkiru.subkiru.core.ui.component.formatAmountJpy
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        HomeContent(
            uiState = uiState,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
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

        uiState.subscriptions.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "サブスクがまだありません",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "右下の＋ボタンから追加しましょう",
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
                item(key = "monthly_total") {
                    MonthlyTotalHeader(monthlyTotal = uiState.monthlyTotal)
                }
                items(
                    items = uiState.subscriptions,
                    key = { it.id },
                ) { subscription ->
                    SubscriptionCard(subscription = subscription)
                }
            }
        }
    }
}

@Composable
private fun MonthlyTotalHeader(
    monthlyTotal: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = HEADER_VERTICAL_PADDING),
    ) {
        Text(
            text = "月額合計",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatAmountJpy(monthlyTotal),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SCREEN_TOP_PADDING = 8.dp
private val SCREEN_BOTTOM_PADDING = 88.dp
private val LIST_ITEM_SPACING = 12.dp
private val HEADER_VERTICAL_PADDING = 16.dp

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    SubKiruTheme {
        HomeContent(
            uiState = HomeUiState(
                subscriptions = listOf(
                    Subscription(
                        id = 1L,
                        name = "Netflix",
                        amountMinor = 1490L,
                        currencyCode = "JPY",
                        billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
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
                    Subscription(
                        id = 2L,
                        name = "Spotify",
                        amountMinor = 980L,
                        currencyCode = "JPY",
                        billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
                        startDate = LocalDate.of(2025, 3, 15),
                        nextBillingDate = LocalDate.of(2026, 6, 15),
                        categoryId = null,
                        templateId = null,
                        logoUri = null,
                        memo = null,
                        isActive = true,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                ),
                monthlyTotal = 2470L,
                isLoading = false,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentEmptyPreview() {
    SubKiruTheme {
        HomeContent(
            uiState = HomeUiState(
                subscriptions = emptyList(),
                isLoading = false,
            ),
        )
    }
}
