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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.ui.component.ServiceAvatar
import com.subkiru.subkiru.core.ui.component.SubscriptionListRow
import com.subkiru.subkiru.core.ui.component.formatAmountJpy
import com.subkiru.subkiru.core.ui.component.formatCurrencyAmount
import com.subkiru.subkiru.core.ui.component.serviceLogoResId
import com.subkiru.subkiru.ui.theme.HeroCardBackground
import com.subkiru.subkiru.ui.theme.OnHeroCard
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
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
        uiState.isLoading -> LoadingContent(modifier)
        uiState.error != null -> ErrorContent(uiState.error, modifier)
        else -> CalendarLoadedContent(
            uiState = uiState,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onGoToCurrentMonth = onGoToCurrentMonth,
            onDateSelected = onDateSelected,
            modifier = modifier,
        )
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
private fun CalendarLoadedContent(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToCurrentMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
            billingsByDay = uiState.billingsByDay,
            selectedDate = uiState.selectedDate,
            today = uiState.today,
            onDateSelected = onDateSelected,
        )

        Spacer(modifier = Modifier.height(MONTH_TOTAL_TOP_SPACING))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MonthTotalHeader(
            yearMonth = uiState.displayedYearMonth,
            monthlyTotal = uiState.monthlyTotal,
        )

        SelectedDateSection(
            selectedDate = uiState.selectedDate,
            billings = uiState.selectedDateBillings,
        )

        Spacer(modifier = Modifier.height(SCREEN_BOTTOM_PADDING))
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
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "前の月",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "${yearMonth.year}年${yearMonth.monthValue}月",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onNextMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "次の月",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (!isCurrentMonth) {
            TextButton(
                onClick = onGoToCurrentMonth,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(text = "今月に戻る", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DayOfWeekHeader(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        DAY_OF_WEEK_LABELS.forEachIndexed { index, label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = weekdayColor(index),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun weekdayColor(index: Int): Color = when (index) {
    SUNDAY_INDEX -> MaterialTheme.colorScheme.error
    SATURDAY_INDEX -> SATURDAY_COLOR
    else -> TextSecondary
}

@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    billingsByDay: Map<Int, List<BillingMark>>,
    selectedDate: LocalDate?,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstDayOffset = yearMonth.atDay(1).dayOfWeek.value % DAYS_IN_WEEK
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = firstDayOffset + daysInMonth
    val rows = (totalCells + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK

    Column(modifier = modifier.fillMaxWidth()) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until DAYS_IN_WEEK) {
                    val cellIndex = row * DAYS_IN_WEEK + column
                    val day = cellIndex - firstDayOffset + 1
                    if (day in 1..daysInMonth) {
                        val date = yearMonth.atDay(day)
                        DayCell(
                            day = day,
                            isSelected = date == selectedDate,
                            isToday = date == today,
                            billingMarks = billingsByDay[day].orEmpty(),
                            onClick = { onDateSelected(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .height(DAY_CELL_HEIGHT),
                        )
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
    billingMarks: List<BillingMark>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(DAY_CELL_HEIGHT)
            .clickable(onClick = onClick)
            .padding(vertical = DAY_CELL_VERTICAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(DAY_NUMBER_SIZE)
                .clip(CircleShape)
                .background(
                    when {
                        isSelected -> HeroCardBackground
                        isToday -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> OnHeroCard
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }

        Spacer(modifier = Modifier.height(BILLING_MARK_TOP_SPACING))
        BillingMarks(marks = billingMarks)
    }
}

@Composable
private fun BillingMarks(
    marks: List<BillingMark>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(BILLING_MARK_SIZE),
        horizontalArrangement = Arrangement.spacedBy(BILLING_MARK_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        marks.take(MAX_VISIBLE_BILLING_MARKS).forEach { mark ->
            ServiceAvatar(
                name = mark.name,
                logoResId = serviceLogoResId(mark.name),
                size = BILLING_MARK_SIZE,
                cornerRadius = BILLING_MARK_CORNER_RADIUS,
            )
        }
        val hiddenCount = marks.size - MAX_VISIBLE_BILLING_MARKS
        if (hiddenCount > 0) {
            Text(
                text = "+$hiddenCount",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun MonthTotalHeader(
    yearMonth: YearMonth,
    monthlyTotal: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MONTH_TOTAL_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${yearMonth.monthValue}月の合計",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formatAmountJpy(monthlyTotal),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SelectedDateSection(
    selectedDate: LocalDate?,
    billings: List<BillingMark>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when {
            selectedDate == null -> Text(
                text = "日付を選択すると請求内容を確認できます",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = EMPTY_MESSAGE_VERTICAL_PADDING),
            )
            billings.isEmpty() -> Text(
                text = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日の請求はありません",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = EMPTY_MESSAGE_VERTICAL_PADDING),
            )
            else -> {
                Text(
                    text = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日の請求",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = SELECTED_TITLE_BOTTOM_PADDING),
                )
                billings.forEach { billing ->
                    SubscriptionListRow(
                        serviceName = billing.name,
                        supportingText = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日",
                        amountText = formatCurrencyAmount(
                            billing.amountMinor,
                            billing.currencyCode,
                        ),
                        amountSuffix = "/請求",
                        logoResId = serviceLogoResId(billing.name),
                        showChevron = false,
                    )
                }
            }
        }
    }
}

private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SECTION_TOP_PADDING = 8.dp
private val SCREEN_BOTTOM_PADDING = 24.dp
private val CALENDAR_VERTICAL_SPACING = 8.dp
private val DAY_CELL_HEIGHT = 64.dp
private val DAY_CELL_VERTICAL_PADDING = 4.dp
private val DAY_NUMBER_SIZE = 26.dp
private val BILLING_MARK_SIZE = 18.dp
private val BILLING_MARK_CORNER_RADIUS = 5.dp
private val BILLING_MARK_TOP_SPACING = 4.dp
private val BILLING_MARK_SPACING = 2.dp
private val MONTH_TOTAL_TOP_SPACING = 12.dp
private val MONTH_TOTAL_VERTICAL_PADDING = 16.dp
private val EMPTY_MESSAGE_VERTICAL_PADDING = 8.dp
private val SELECTED_TITLE_BOTTOM_PADDING = 4.dp
private val SATURDAY_COLOR = Color(0xFF3F6FAF)
private const val DAYS_IN_WEEK = 7
private const val SUNDAY_INDEX = 0
private const val SATURDAY_INDEX = 6
private const val MAX_VISIBLE_BILLING_MARKS = 2
private val DAY_OF_WEEK_LABELS = listOf("日", "月", "火", "水", "木", "金", "土")

@Preview(showBackground = true)
@Composable
private fun CalendarContentPreview() {
    SubKiruTheme {
        CalendarContent(
            uiState = CalendarUiState(
                displayedYearMonth = YearMonth.of(2026, 5),
                today = LocalDate.of(2026, 5, 24),
                billingsByDay = mapOf(
                    1 to listOf(BillingMark(3L, "Amazon Prime", 600L, "JPY")),
                    15 to listOf(
                        BillingMark(1L, "Netflix", 1_490L, "JPY"),
                        BillingMark(2L, "Spotify", 980L, "JPY"),
                        BillingMark(4L, "ChatGPT", 3_000L, "JPY"),
                    ),
                    24 to listOf(BillingMark(5L, "iCloud+", 150L, "JPY")),
                ),
                monthlyTotal = 6_220L,
                selectedDate = LocalDate.of(2026, 5, 15),
                selectedDateBillings = listOf(
                    BillingMark(1L, "Netflix", 1_490L, "JPY"),
                    BillingMark(2L, "Spotify", 980L, "JPY"),
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
                isLoading = false,
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onGoToCurrentMonth = {},
            onDateSelected = {},
        )
    }
}
