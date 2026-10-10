package com.subkiru.subkiru.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.MoneyAmount
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.ui.component.HeroTotalCard
import com.subkiru.subkiru.core.ui.component.NoWindowInsets
import com.subkiru.subkiru.core.ui.component.SubscriptionListRow
import com.subkiru.subkiru.core.ui.component.formatCurrencyAmounts
import com.subkiru.subkiru.core.ui.component.formatCurrencyAmount
import com.subkiru.subkiru.core.ui.component.serviceLogoResId
import com.subkiru.subkiru.ui.theme.HeroCardBackground
import com.subkiru.subkiru.ui.theme.OnHeroCard
import com.subkiru.subkiru.ui.theme.OnHeroCardMuted
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSubscriptionClick: (Long) -> Unit,
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
        contentWindowInsets = NoWindowInsets,
        modifier = modifier,
    ) { innerPadding ->
        HomeContent(
            uiState = uiState,
            onDeleteSubscription = viewModel::onDeleteSubscription,
            onSubscriptionClick = onSubscriptionClick,
            onReorderSubscriptions = viewModel::onReorderSubscriptions,
            onDisplayModeChange = viewModel::onDisplayModeChange,
            onSortOrderChange = viewModel::onSortOrderChange,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onDeleteSubscription: (Long) -> Unit = {},
    onSubscriptionClick: (Long) -> Unit = {},
    onReorderSubscriptions: (List<Long>) -> Unit = {},
    onDisplayModeChange: (DisplayMode) -> Unit = {},
    onSortOrderChange: (HomeSortOrder) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> LoadingContent(modifier)
        uiState.error != null -> ErrorContent(uiState.error, modifier)
        uiState.subscriptions.isEmpty() -> EmptyContent(modifier)
        else -> {
            val listState = rememberLazyListState()
            val reorderState = rememberSubscriptionReorderState(
                subscriptions = uiState.subscriptions,
                listState = listState,
                onReordered = onReorderSubscriptions,
            )
            // 並べ替え（ドラッグ）ができるのは「カスタム」を選んでいるときだけ
            val isReorderEnabled = uiState.sortOrder == HomeSortOrder.CUSTOM
            val displayedSubscriptions = if (isReorderEnabled) {
                reorderState.displayedSubscriptions
            } else {
                uiState.subscriptions
            }

            LazyColumn(
                state = listState,
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    start = SCREEN_HORIZONTAL_PADDING,
                    end = SCREEN_HORIZONTAL_PADDING,
                    top = SCREEN_TOP_PADDING,
                    bottom = SCREEN_BOTTOM_PADDING,
                ),
            ) {
                item(key = "hero_total") {
                    HomeHeroCard(
                        uiState = uiState,
                        onDisplayModeChange = onDisplayModeChange,
                        modifier = Modifier.padding(bottom = HERO_BOTTOM_SPACING),
                    )
                }

                item(key = "sort_filters") {
                    SortFilterRow(
                        selectedSortOrder = uiState.sortOrder,
                        onSortOrderChange = onSortOrderChange,
                        modifier = Modifier.padding(bottom = FILTER_BOTTOM_SPACING),
                    )
                }

                item(key = "subscription_header") {
                    Text(
                        text = "サブスクリプション",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = SECTION_HEADER_VERTICAL_PADDING),
                    )
                }

                items(
                    items = displayedSubscriptions,
                    key = { it.id },
                ) { subscription ->
                    val isDragging = reorderState.draggingId == subscription.id
                    SwipeToDismissSubscriptionRow(
                        subscription = subscription,
                        categoryColorHex = subscription.categoryId
                            ?.let { uiState.categoryColorMap[it] },
                        onDelete = onDeleteSubscription,
                        onClick = onSubscriptionClick,
                        reorderState = reorderState.takeIf { isReorderEnabled },
                        // ドラッグ中の行は指に追従させ、それ以外の行は入れ替わりをアニメーションさせる
                        modifier = if (isDragging) {
                            Modifier
                                .zIndex(DRAGGING_Z_INDEX)
                                .graphicsLayer { translationY = reorderState.dragOffsetY }
                        } else {
                            Modifier.animateItem()
                        },
                    )
                }
            }
        }
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

@Composable
private fun HomeHeroCard(
    uiState: HomeUiState,
    onDisplayModeChange: (DisplayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMonthly = uiState.displayMode == DisplayMode.MONTHLY
    HeroTotalCard(
        label = if (isMonthly) "今月の合計" else "年間の合計",
        amount = formatCurrencyAmounts(if (isMonthly) uiState.monthlyTotals else uiState.yearlyTotals),
        annualAmount = formatCurrencyAmounts(uiState.yearlyTotals),
        subscriptionCount = uiState.subscriptions.size,
        modifier = modifier,
    ) {
        HeroModeChip(
            label = "月額",
            selected = isMonthly,
            onClick = { onDisplayModeChange(DisplayMode.MONTHLY) },
        )
        HeroModeChip(
            label = "年額",
            selected = !isMonthly,
            onClick = { onDisplayModeChange(DisplayMode.YEARLY) },
        )
    }
}

@Composable
private fun HeroModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = OnHeroCardMuted,
            selectedContainerColor = OnHeroCard,
            selectedLabelColor = HeroCardBackground,
        ),
        border = null,
    )
}

@Composable
private fun SortFilterRow(
    selectedSortOrder: HomeSortOrder,
    onSortOrderChange: (HomeSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FILTER_CHIP_SPACING),
    ) {
        items(
            items = HomeSortOrder.entries,
            key = { it.name },
        ) { sortOrder ->
            FilterChip(
                selected = selectedSortOrder == sortOrder,
                onClick = { onSortOrderChange(sortOrder) },
                label = { Text(sortOrder.label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = TextSecondary,
                    selectedContainerColor = HeroCardBackground,
                    selectedLabelColor = OnHeroCard,
                ),
                border = null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissSubscriptionRow(
    subscription: Subscription,
    categoryColorHex: String?,
    onDelete: (Long) -> Unit,
    onClick: (Long) -> Unit,
    reorderState: SubscriptionReorderState?,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState()

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
        SubscriptionListRow(
            serviceName = subscription.name,
            supportingText = "次回 ${formatBillingDate(subscription.nextBillingDate)}",
            amountText = formatCurrencyAmount(
                subscription.amountMinor,
                subscription.currencyCode,
            ),
            amountSuffix = formatBillingSuffix(subscription.billingInterval),
            logoResId = serviceLogoResId(subscription.name),
            categoryColorHex = categoryColorHex,
            onClick = { onClick(subscription.id) },
            trailingContent = reorderState?.let { state ->
                { DragHandle(reorderState = state, subscriptionId = subscription.id) }
            },
            onLongClick = { showDeleteDialog = true },
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
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

// 「カスタム」並べ替え用のドラッグハンドル。このアイコンを縦にドラッグした場合だけ並べ替えになる。
// 行のタップ（編集）・長押し（削除）・左スワイプ（削除）とは別の領域で操作を受け付ける
@Composable
private fun DragHandle(
    reorderState: SubscriptionReorderState,
    subscriptionId: Long,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.Filled.DragHandle,
        contentDescription = "ドラッグして並べ替え",
        tint = TextSecondary,
        modifier = modifier
            .size(DRAG_HANDLE_SIZE)
            .padding(DRAG_HANDLE_PADDING)
            // ハンドルのタップが行のタップ（編集画面への遷移）にならないようにする
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .pointerInput(reorderState, subscriptionId) {
                detectDragGestures(
                    onDragStart = { reorderState.onDragStart(subscriptionId) },
                    onDragEnd = reorderState::onDragEnd,
                    onDragCancel = reorderState::onDragCancel,
                ) { change, dragAmount ->
                    change.consume()
                    reorderState.onDrag(dragAmount.y)
                }
            },
    )
}

private val HomeSortOrder.label: String
    get() = when (this) {
        HomeSortOrder.BILLING_DATE -> "請求日"
        HomeSortOrder.AMOUNT_HIGH -> "高い順"
        HomeSortOrder.AMOUNT_LOW -> "安い順"
        HomeSortOrder.NEWEST -> "新しい順"
        HomeSortOrder.CUSTOM -> "カスタム"
    }

private fun formatBillingDate(date: LocalDate): String =
    "${date.monthValue}月${date.dayOfMonth}日"

private fun formatBillingSuffix(interval: BillingInterval): String {
    val unit = when (interval.unit) {
        BillingIntervalUnit.DAILY -> "日"
        BillingIntervalUnit.WEEKLY -> "週"
        BillingIntervalUnit.MONTHLY -> "月"
        BillingIntervalUnit.YEARLY -> "年"
    }
    return if (interval.count == 1) "/$unit" else "/${interval.count}$unit"
}

private val SWIPE_ICON_PADDING = 24.dp
private val DRAG_HANDLE_SIZE = 40.dp
private val DRAG_HANDLE_PADDING = 8.dp
private const val DRAGGING_Z_INDEX = 1f
private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SCREEN_TOP_PADDING = 12.dp
private val SCREEN_BOTTOM_PADDING = 88.dp
private val HERO_BOTTOM_SPACING = 16.dp
private val FILTER_BOTTOM_SPACING = 8.dp
private val FILTER_CHIP_SPACING = 8.dp
private val SECTION_HEADER_VERTICAL_PADDING = 12.dp

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    SubKiruTheme {
        HomeContent(
            uiState = HomeUiState(
                subscriptions = previewSubscriptions,
                monthlyTotals = listOf(MoneyAmount(2_470L, "JPY")),
                yearlyTotals = listOf(MoneyAmount(29_640L, "JPY")),
                isLoading = false,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentEmptyPreview() {
    SubKiruTheme {
        HomeContent(uiState = HomeUiState(isLoading = false))
    }
}

private val previewSubscriptions = listOf(
    previewSubscription(1L, "Netflix", 1_490L, 1),
    previewSubscription(2L, "Spotify", 980L, 15),
)

private fun previewSubscription(
    id: Long,
    name: String,
    amountMinor: Long,
    billingDay: Int,
): Subscription = Subscription(
    id = id,
    name = name,
    amountMinor = amountMinor,
    currencyCode = "JPY",
    billingInterval = BillingInterval(BillingIntervalUnit.MONTHLY, 1),
    startDate = LocalDate.of(2025, 1, 1),
    nextBillingDate = LocalDate.of(2026, 6, billingDay),
    categoryId = null,
    templateId = null,
    logoUri = null,
    memo = null,
    isActive = true,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
