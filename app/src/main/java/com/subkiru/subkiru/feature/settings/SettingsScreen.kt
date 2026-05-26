package com.subkiru.subkiru.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.domain.model.UserSettings
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            viewModel.onReminderEnabledChanged(true)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("通知を送信するには通知の許可が必要です")
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        SettingsContent(
            uiState = uiState,
            onReminderEnabledChanged = { enabled ->
                if (enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            viewModel.onReminderEnabledChanged(true)
                        } else {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        viewModel.onReminderEnabledChanged(true)
                    }
                } else {
                    viewModel.onReminderEnabledChanged(false)
                }
            },
            onReminderDaysBeforeSliderChanged = viewModel::onReminderDaysBeforeSliderChanged,
            onReminderDaysBeforeSliderFinished = viewModel::onReminderDaysBeforeSliderFinished,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onReminderDaysBeforeSliderChanged: (Int) -> Unit,
    onReminderDaysBeforeSliderFinished: () -> Unit,
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

                SectionHeader(title = "通知")

                Spacer(modifier = Modifier.height(SECTION_CONTENT_SPACING))

                NotificationSettingsCard(
                    isReminderEnabled = uiState.isReminderEnabled,
                    reminderDaysBefore = uiState.reminderDaysBeforeSlider,
                    onReminderEnabledChanged = onReminderEnabledChanged,
                    onReminderDaysBeforeSliderChanged = onReminderDaysBeforeSliderChanged,
                    onReminderDaysBeforeSliderFinished = onReminderDaysBeforeSliderFinished,
                )

                Spacer(modifier = Modifier.height(SECTION_SPACING))

                SectionHeader(title = "アプリ情報")

                Spacer(modifier = Modifier.height(SECTION_CONTENT_SPACING))

                AppInfoCard(appVersion = uiState.appVersion)

                Spacer(modifier = Modifier.height(SCREEN_BOTTOM_PADDING))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
private fun NotificationSettingsCard(
    isReminderEnabled: Boolean,
    reminderDaysBefore: Int,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onReminderDaysBeforeSliderChanged: (Int) -> Unit,
    onReminderDaysBeforeSliderFinished: () -> Unit,
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
            // リマインダー ON/OFF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "請求日リマインダー",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "請求日の前に通知でお知らせします",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = isReminderEnabled,
                    onCheckedChange = onReminderEnabledChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            // リマインダー日数（ON の時のみ表示）
            if (isReminderEnabled) {
                Spacer(modifier = Modifier.height(CARD_ITEM_SPACING))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(modifier = Modifier.height(CARD_ITEM_SPACING))

                Text(
                    text = "通知タイミング",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${reminderDaysBefore}日前に通知",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )

                Slider(
                    value = reminderDaysBefore.toFloat(),
                    onValueChange = { onReminderDaysBeforeSliderChanged(it.roundToInt()) },
                    onValueChangeFinished = onReminderDaysBeforeSliderFinished,
                    valueRange = UserSettings.MIN_REMINDER_DAYS_BEFORE.toFloat()..
                        UserSettings.MAX_REMINDER_DAYS_BEFORE.toFloat(),
                    steps = UserSettings.MAX_REMINDER_DAYS_BEFORE -
                        UserSettings.MIN_REMINDER_DAYS_BEFORE - 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AppInfoCard(
    appVersion: String,
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
                    text = "バージョン",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = appVersion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
    }
}

private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val SECTION_TOP_PADDING = 8.dp
private val SCREEN_BOTTOM_PADDING = 24.dp
private val SECTION_SPACING = 24.dp
private val SECTION_CONTENT_SPACING = 8.dp
private val CARD_PADDING = 16.dp
private val CARD_ITEM_SPACING = 12.dp

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    SubKiruTheme {
        SettingsContent(
            uiState = SettingsUiState(
                isReminderEnabled = true,
                reminderDaysBefore = 2,
                reminderDaysBeforeSlider = 2,
                appVersion = "1.0.0",
                isLoading = false,
            ),
            onReminderEnabledChanged = {},
            onReminderDaysBeforeSliderChanged = {},
            onReminderDaysBeforeSliderFinished = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentReminderOffPreview() {
    SubKiruTheme {
        SettingsContent(
            uiState = SettingsUiState(
                isReminderEnabled = false,
                reminderDaysBefore = 1,
                appVersion = "1.0.0",
                isLoading = false,
            ),
            onReminderEnabledChanged = {},
            onReminderDaysBeforeSliderChanged = {},
            onReminderDaysBeforeSliderFinished = {},
        )
    }
}
