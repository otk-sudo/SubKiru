package com.subkiru.subkiru.feature.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun AddSubscriptionScreen(
    viewModel: AddSubscriptionViewModel,
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
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNameChange = viewModel::onNameChange,
        onAmountChange = viewModel::onAmountChange,
        onBillingIntervalUnitChange = viewModel::onBillingIntervalUnitChange,
        onBillingIntervalCountChange = viewModel::onBillingIntervalCountChange,
        onStartDateChange = viewModel::onStartDateChange,
        onMemoChange = viewModel::onMemoChange,
        onSave = viewModel::onSave,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSubscriptionContent(
    uiState: AddSubscriptionUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onBillingIntervalUnitChange: (BillingIntervalUnit) -> Unit,
    onBillingIntervalCountChange: (String) -> Unit,
    onStartDateChange: (String) -> Unit,
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
            snackbarHostState = SnackbarHostState(),
            onNavigateBack = {},
            onNameChange = {},
            onAmountChange = {},
            onBillingIntervalUnitChange = {},
            onBillingIntervalCountChange = {},
            onStartDateChange = {},
            onMemoChange = {},
            onSave = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AddSubscriptionContentErrorPreview() {
    SubKiruTheme {
        AddSubscriptionContent(
            uiState = AddSubscriptionUiState(
                nameError = "サービス名を入力してください",
                amountError = "有効な金額を入力してください",
                startDateText = "2026/05/24",
                nextBillingDateText = "2026/06/24",
            ),
            snackbarHostState = SnackbarHostState(),
            onNavigateBack = {},
            onNameChange = {},
            onAmountChange = {},
            onBillingIntervalUnitChange = {},
            onBillingIntervalCountChange = {},
            onStartDateChange = {},
            onMemoChange = {},
            onSave = {},
        )
    }
}
