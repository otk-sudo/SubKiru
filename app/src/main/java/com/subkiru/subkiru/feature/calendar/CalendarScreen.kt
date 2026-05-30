package com.subkiru.subkiru.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.ui.component.formatAmountJpy
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarContent(
        uiState = uiState,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onGoToCurrentMonth = viewModel::onGoToCurrentMonth,
        onDateSelected = viewModel::onDateSelected,
        modifier = modifier,
    )
}

@Composable
private fun CalendarContent(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToCurrentMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
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

        else -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SCREEN_HORIZONTAL_PADDING),
            ) {
                Spacer(modifier = Modifier.height(SECTION_TOP_PADDING))

                MonthHeader(
                    yearMonth = uiState.displayedYearMonth,
                    isCurrentMonth = uiState.displayedYearMonth == YearMonth.from(uiState.today),
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onGoToCurrentMonth = onGoToCurrentMonth,
                )

                Spacer(modifier = Modifier.height(CALENDAR_VERTICAL_SPACING))

                DayOfWeekHeader()

                CalendarGrid(
                    yearMonth = uiState.displayedYearMonth,
                    billingDays = uiState.billingDays,
                    selectedDate = uiState.selectedDate,
                    today = uiState.today,
                    onDateSelected = onDateSelected,
                )

                if (uiState.selectedDate != null) {
                    Spacer(modifier = Modifier.height(SELECTED_SECTION_TOP_SPACING))

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Spacer(modifier = Modifier.height(SELECTED_SECTION_TOP_SPACING))

                    SelectedDateSection(
                        selectedDate = uiState.selectedDate,
                        billings = uiState.selectedDateBillings,
                    )
                }

                Spacer(modifier = Modifier.height(SCREEN_BOTTOM_PADDING))
            }
        }
    }
}

@Composable
private fun MonthHeader(
    yearMonth: YearMonth,
    isCurrentMonth: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToCurrentMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(MONTH_HEADER_CORNER_RADIUS),
            )
            .padding(vertical = MONTH_HEADER_VERTICAL_PADDING),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "前の月",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = "${yearMonth.year}年${yearMonth.monthValue}月",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            IconButton(onClick = onNextMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "次の月",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        if (!isCurrentMonth) {
            TextButton(
                onClick = onGoToCurrentMonth,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = "今月に戻る",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun DayOfWeekHeader(
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        DAY_OF_WEEK_LABELS.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    billingDays: Set<Int>,
    selectedDate: LocalDate?,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // DayOfWeek.value は ISO-8601（月=1..日=7）。% 7 で日曜始まり（日=0..土=6）に変換する
    val firstDayOffset = yearMonth.atDay(1).dayOfWeek.value % DAYS_IN_WEEK
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = firstDayOffset + daysInMonth
    val rows = (totalCells + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK

    Column(modifier = modifier.fillMaxWidth()) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until DAYS_IN_WEEK) {
                    val cellIndex = row * DAYS_IN_WEEK + col
                    val day = cellIndex - firstDayOffset + 1

                    if (day in 1..daysInMonth) {
                        val date = yearMonth.atDay(day)
                        val isSelected = date == selectedDate
                        val isToday = date == today
                        val hasBilling = day in billingDays

                        DayCell(
                            day = day,
                            isSelected = isSelected,
                            isToday = isToday,
                            hasBilling = hasBilling,
                            onClick = { onDateSelected(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    hasBilling: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = DAY_CELL_VERTICAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(DAY_CELL_SIZE)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }
        if (hasBilling) {
            Box(
                modifier = Modifier
                    .padding(top = BILLING_DOT_TOP_PADDING)
                    .size(BILLING_DOT_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        } else {
            Spacer(
                modifier = Modifier
                    .padding(top = BILLING_DOT_TOP_PADDING)
                    .size(BILLING_DOT_SIZE),
            )
        }
    }
}

@Composable
private fun SelectedDateSection(
    selectedDate: LocalDate,
    billings: List<BillingEvent>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BILLING_LIST_ITEM_SPACING),
    ) {
        Text(
            text = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日の請求",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (billings.isEmpty()) {
            Text(
                text = "この日の請求はありません",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = EMPTY_MESSAGE_VERTICAL_PADDING),
            )
        } else {
            billings.forEach { billing ->
                BillingEventItem(billing = billing)
            }
        }
    }
}

@Composable
private fun BillingEventItem(
    billing: BillingEvent,
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
            Text(
                text = billing.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatAmountJpy(billing.amountMinor),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val MONTH_HEADER_CORNER_RADIUS = 16.dp
private val MONTH_HEADER_VERTICAL_PADDING = 4.dp
private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SECTION_TOP_PADDING = 8.dp
private val SCREEN_BOTTOM_PADDING = 24.dp
private val CALENDAR_VERTICAL_SPACING = 8.dp
private val SELECTED_SECTION_TOP_SPACING = 16.dp
private val DAY_CELL_SIZE = 36.dp
private val DAY_CELL_VERTICAL_PADDING = 4.dp
private val BILLING_DOT_SIZE = 6.dp
private val BILLING_DOT_TOP_PADDING = 2.dp
private val BILLING_LIST_ITEM_SPACING = 8.dp
private val EMPTY_MESSAGE_VERTICAL_PADDING = 8.dp
private val CARD_PADDING = 16.dp
private const val DAYS_IN_WEEK = 7
private val DAY_OF_WEEK_LABELS = listOf("日", "月", "火", "水", "木", "金", "土")

@Preview(showBackground = true)
@Composable
private fun CalendarContentPreview() {
    SubKiruTheme {
        CalendarContent(
            uiState = CalendarUiState(
                displayedYearMonth = YearMonth.of(2026, 5),
                today = LocalDate.of(2026, 5, 24),
                billingDays = setOf(1, 15, 24),
                selectedDate = LocalDate.of(2026, 5, 15),
                selectedDateBillings = listOf(
                    BillingEvent(1L, "Netflix", 1_490L),
                    BillingEvent(2L, "Spotify", 980L),
                ),
                isLoading = false,
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onGoToCurrentMonth = {},
            onDateSelected = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarContentEmptySelectionPreview() {
    SubKiruTheme {
        CalendarContent(
            uiState = CalendarUiState(
                displayedYearMonth = YearMonth.of(2026, 5),
                today = LocalDate.of(2026, 5, 24),
                billingDays = setOf(15),
                selectedDate = LocalDate.of(2026, 5, 10),
                selectedDateBillings = emptyList(),
                isLoading = false,
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onGoToCurrentMonth = {},
            onDateSelected = {},
        )
    }
}
