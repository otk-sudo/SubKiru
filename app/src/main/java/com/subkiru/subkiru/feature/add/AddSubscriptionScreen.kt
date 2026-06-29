package com.subkiru.subkiru.feature.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Category
import com.subkiru.subkiru.core.domain.model.ServiceTemplate
import com.subkiru.subkiru.core.ui.component.ServiceAvatar
import com.subkiru.subkiru.core.ui.component.serviceLogoResId
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun AddSubscriptionScreen(
    viewModel: AddSubscriptionViewModel,
    templates: List<ServiceTemplate>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collect {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    AddSubscriptionContent(
        uiState = uiState,
        templates = templates,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onTemplateSelected = { template ->
            viewModel.applyTemplate(
                name = template.name,
                amountMinor = template.defaultAmountMinor,
                unit = template.defaultInterval.unit,
                count = template.defaultInterval.count,
                categoryId = template.categoryId,
            )
        },
        onNameChange = viewModel::onNameChange,
        onAmountChange = viewModel::onAmountChange,
        onBillingIntervalUnitChange = viewModel::onBillingIntervalUnitChange,
        onBillingIntervalCountChange = viewModel::onBillingIntervalCountChange,
        onStartDateChange = viewModel::onStartDateChange,
        onCategoryChange = viewModel::onCategoryChange,
        onMemoChange = viewModel::onMemoChange,
        onSave = viewModel::onSave,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSubscriptionContent(
    uiState: AddSubscriptionUiState,
    templates: List<ServiceTemplate>,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onTemplateSelected: (ServiceTemplate) -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onBillingIntervalUnitChange: (BillingIntervalUnit) -> Unit,
    onBillingIntervalCountChange: (String) -> Unit,
    onStartDateChange: (String) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onMemoChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("サブスク追加") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = SCREEN_HORIZONTAL_PADDING)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FIELD_SPACING),
        ) {
            Spacer(modifier = Modifier.height(SECTION_TOP_PADDING))

            if (templates.isNotEmpty()) {
                Text(
                    text = "人気サービス",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TemplateGrid(
                    templates = templates,
                    onTemplateSelected = onTemplateSelected,
                )
                Spacer(modifier = Modifier.height(SECTION_TOP_PADDING))
            }

            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text("サービス名") },
                placeholder = { Text("例: Netflix") },
                isError = uiState.nameError != null,
                supportingText = uiState.nameError?.let { error -> { Text(error) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.amountText,
                onValueChange = onAmountChange,
                label = { Text("金額（円）") },
                placeholder = { Text("例: 1490") },
                isError = uiState.amountError != null,
                supportingText = uiState.amountError?.let { error -> { Text(error) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FIELD_SPACING),
            ) {
                OutlinedTextField(
                    value = uiState.billingIntervalCountText,
                    onValueChange = onBillingIntervalCountChange,
                    label = { Text("間隔") },
                    isError = uiState.intervalError != null,
                    supportingText = uiState.intervalError?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )

                BillingIntervalUnitDropdown(
                    selected = uiState.billingIntervalUnit,
                    onSelect = onBillingIntervalUnitChange,
                    modifier = Modifier.weight(1f),
                )
            }

            StartDateField(
                startDateText = uiState.startDateText,
                isError = uiState.startDateError != null,
                errorText = uiState.startDateError,
                onStartDateChange = onStartDateChange,
            )

            OutlinedTextField(
                value = uiState.nextBillingDateText,
                onValueChange = {},
                enabled = false,
                label = { Text("次回請求日（自動計算）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            CategoryDropdown(
                selectedCategoryId = uiState.categoryId,
                categories = uiState.categories,
                onCategoryChange = onCategoryChange,
            )

            OutlinedTextField(
                value = uiState.memo,
                onValueChange = onMemoChange,
                label = { Text("メモ（任意）") },
                placeholder = { Text("例: 家族プラン") },
                minLines = MIN_MEMO_LINES,
                maxLines = MAX_MEMO_LINES,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(SECTION_TOP_PADDING))

            Button(
                onClick = onSave,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = PROGRESS_STROKE_WIDTH,
                        modifier = Modifier.size(PROGRESS_SIZE),
                    )
                } else {
                    Text("保存")
                }
            }

            Spacer(modifier = Modifier.height(SCREEN_BOTTOM_PADDING))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillingIntervalUnitDropdown(
    selected: BillingIntervalUnit,
    onSelect: (BillingIntervalUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.toDisplayLabel(),
            onValueChange = {},
            readOnly = true,
            label = { Text("単位") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            BillingIntervalUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.toDisplayLabel()) },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDateField(
    startDateText: String,
    isError: Boolean,
    errorText: String?,
    onStartDateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    // 現在の開始日テキストを初期選択日としてエポックミリ秒に変換
    val initialSelectedMillis = remember(startDateText) {
        try {
            val date = LocalDate.parse(startDateText, AddSubscriptionViewModel.DATE_FORMATTER)
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: java.time.format.DateTimeParseException) {
            null
        }
    }

    OutlinedTextField(
        value = startDateText,
        onValueChange = {},
        readOnly = true,
        label = { Text("開始日") },
        placeholder = { Text("yyyy/MM/dd") },
        isError = isError,
        supportingText = errorText?.let { error -> { Text(error) } },
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = "カレンダーから選択",
                )
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialSelectedMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            // DatePicker は UTC ミリ秒を返すため UTC で変換
                            val selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onStartDateChange(selectedDate.format(AddSubscriptionViewModel.DATE_FORMATTER))
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("キャンセル")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun TemplateGrid(
    templates: List<ServiceTemplate>,
    onTemplateSelected: (ServiceTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // LazyVerticalGridはスクロール可能な親内に置けないため、高さ固定
    val rows = (templates.size + TEMPLATE_GRID_COLUMNS - 1) / TEMPLATE_GRID_COLUMNS
    val gridHeight = (rows * TEMPLATE_CELL_HEIGHT_VALUE).dp

    LazyVerticalGrid(
        columns = GridCells.Fixed(TEMPLATE_GRID_COLUMNS),
        modifier = modifier
            .fillMaxWidth()
            .height(gridHeight),
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(TEMPLATE_GRID_SPACING),
        verticalArrangement = Arrangement.spacedBy(TEMPLATE_GRID_SPACING),
        userScrollEnabled = false,
    ) {
        items(
            items = templates,
            key = { it.id },
        ) { template ->
            TemplateItem(
                template = template,
                onClick = { onTemplateSelected(template) },
            )
        }
    }
}

@Composable
private fun TemplateItem(
    template: ServiceTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(TEMPLATE_CORNER_RADIUS))
            .clickable(onClick = onClick)
            .padding(TEMPLATE_ITEM_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ロゴ表示は共通 ServiceAvatar に委譲（ローカルロゴ→頭文字カラーアバター→デフォルトの順、外部通信なし）
        ServiceAvatar(
            name = template.name,
            logoResId = serviceLogoResId(template.name),
            size = TEMPLATE_ICON_SIZE,
            cornerRadius = TEMPLATE_ICON_CORNER_RADIUS,
        )
        Spacer(modifier = Modifier.height(TEMPLATE_TEXT_TOP_PADDING))
        Text(
            text = template.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selectedCategoryId: Long?,
    categories: List<Category>,
    onCategoryChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.find { it.id == selectedCategoryId }?.name ?: "未選択"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("カテゴリ") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("未選択") },
                onClick = {
                    onCategoryChange(null)
                    expanded = false
                },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CATEGORY_DOT_SPACING),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(CATEGORY_DOT_SIZE)
                                    .clip(RoundedCornerShape(CATEGORY_DOT_SIZE / 2))
                                    .background(parseColorHex(category.colorHex)),
                            )
                            Text(category.name)
                        }
                    },
                    onClick = {
                        onCategoryChange(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun parseColorHex(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: IllegalArgumentException) {
        Color.Gray
    }
}

private fun BillingIntervalUnit.toDisplayLabel(): String = when (this) {
    BillingIntervalUnit.DAILY -> "日"
    BillingIntervalUnit.WEEKLY -> "週"
    BillingIntervalUnit.MONTHLY -> "月"
    BillingIntervalUnit.YEARLY -> "年"
}

private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SECTION_TOP_PADDING = 8.dp
private val SCREEN_BOTTOM_PADDING = 24.dp
private val FIELD_SPACING = 12.dp
private val PROGRESS_SIZE = 24.dp
private val PROGRESS_STROKE_WIDTH = 2.dp
private const val MIN_MEMO_LINES = 2
private const val MAX_MEMO_LINES = 4
private const val TEMPLATE_GRID_COLUMNS = 4
private const val TEMPLATE_CELL_HEIGHT_VALUE = 80
private val TEMPLATE_GRID_SPACING = 8.dp
private val TEMPLATE_CORNER_RADIUS = 8.dp
private val TEMPLATE_ITEM_PADDING = 6.dp
private val TEMPLATE_ICON_SIZE = 36.dp
private val TEMPLATE_ICON_CORNER_RADIUS = 8.dp
private val TEMPLATE_TEXT_TOP_PADDING = 4.dp
private val CATEGORY_DOT_SIZE = 10.dp
private val CATEGORY_DOT_SPACING = 8.dp

@Preview(showBackground = true)
@Composable
private fun AddSubscriptionContentPreview() {
    SubKiruTheme {
        AddSubscriptionContent(
            uiState = AddSubscriptionUiState(
                name = "Netflix",
                amountText = "1490",
                startDateText = "2026/05/24",
                nextBillingDateText = "2026/06/24",
            ),
            templates = emptyList(),
            snackbarHostState = SnackbarHostState(),
            onNavigateBack = {},
            onTemplateSelected = {},
            onNameChange = {},
            onAmountChange = {},
            onBillingIntervalUnitChange = {},
            onBillingIntervalCountChange = {},
            onStartDateChange = {},
            onCategoryChange = {},
            onMemoChange = {},
            onSave = {},
        )
    }
}
