package com.subkiru.subkiru.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            onDeleteSubscription = viewModel::onDeleteSubscription,
            onDisplayModeChange = viewModel::onDisplayModeChange,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onDeleteSubscription: (Long) -> Unit = {},
    onDisplayModeChange: (DisplayMode) -> Unit = {},
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
                    MonthlyTotalHeader(
                        monthlyTotal = uiState.monthlyTotal,
                        yearlyTotal = uiState.yearlyTotal,
                        displayMode = uiState.displayMode,
                        onDisplayModeChange = onDisplayModeChange,
                    )
                }
                items(
                    items = uiState.subscriptions,
                    key = { it.id },
                ) { subscription ->
                    SwipeToDismissSubscriptionCard(
                        subscription = subscription,
                        categoryColorHex = subscription.categoryId
                            ?.let { uiState.categoryColorMap[it] },
                        onDelete = onDeleteSubscription,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthlyTotalHeader(
    monthlyTotal: Long,
    yearlyTotal: Long,
    displayMode: DisplayMode,
    onDisplayModeChange: (DisplayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(HEADER_CORNER_RADIUS),
            )
            .padding(HEADER_INNER_PADDING),
    ) {
        Column {
            val label = when (displayMode) {
                DisplayMode.MONTHLY -> "今月の合計"
                DisplayMode.YEARLY -> "年間の合計"
            }
            val amount = when (displayMode) {
                DisplayMode.MONTHLY -> monthlyTotal
                DisplayMode.YEARLY -> yearlyTotal
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
            )
            Text(
                text = formatAmountJpy(amount),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING),
                modifier = Modifier.padding(top = CHIP_TOP_PADDING),
            ) {
                FilterChip(
                    selected = displayMode == DisplayMode.MONTHLY,
                    onClick = { onDisplayModeChange(DisplayMode.MONTHLY) },
                    label = { Text("月額") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                        labelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    ),
                    border = null,
                )
                FilterChip(
                    selected = displayMode == DisplayMode.YEARLY,
                    onClick = { onDisplayModeChange(DisplayMode.YEARLY) },
                    label = { Text("年額") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                        labelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    ),
                    border = null,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissSubscriptionCard(
    subscription: Subscription,
    categoryColorHex: String? = null,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState()

    // スワイプ完了を検知してダイアログを表示し、スワイプ状態をリセット
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            showDeleteDialog = true
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                label = "swipe_bg_color",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = SWIPE_ICON_PADDING),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = modifier,
    ) {
        SubscriptionCard(
            subscription = subscription,
            categoryColorHex = categoryColorHex,
            onLongClick = { showDeleteDialog = true },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("削除の確認") },
            text = { Text("「${subscription.name}」を削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(subscription.id)
                }) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("キャンセル")
                }
            },
        )
    }
}

private val SWIPE_ICON_PADDING = 24.dp
private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SCREEN_TOP_PADDING = 8.dp
private val SCREEN_BOTTOM_PADDING = 88.dp
private val LIST_ITEM_SPACING = 12.dp
private val HEADER_CORNER_RADIUS = 16.dp
private val HEADER_INNER_PADDING = 20.dp
private val CHIP_SPACING = 8.dp
private val CHIP_TOP_PADDING = 12.dp

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
