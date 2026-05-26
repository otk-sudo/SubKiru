# 設定画面 タスクドキュメント（Codex向け）

## 概要

ユーザーがアプリの動作設定を管理する画面を実装する。
MVP では「請求日リマインダー通知の ON/OFF」「通知タイミング（何日前）」「アプリ情報（バージョン表示）」の 3 項目を提供する。
設定値は DataStore (Preferences) で永続化する。

---

## 前提

### 既存リソース（変更不要）
- `Screen.Settings` — ルート定義済み（`"settings"`）
- `BottomNavBar` — 設定タブ（`Icons.Default.Settings`、ラベル "設定"）
- `SubKiruNavGraph` — Settings のプレースホルダーが配置済み
- `gradle/libs.versions.toml` — `datastore = "1.1.4"` 定義済み
- `build.gradle.kts` — `implementation(libs.datastore.preferences)` 依存済み

### MVPスコープ
- 通知の実送信（WorkManager + BillingReminderWorker）: 未実装（後続タスク）。本タスクでは設定値の保存・読み出しのみ
- テーマ切替（ダークモード）: 未実装
- データエクスポート / インポート: 未実装
- Firebase Auth / データ同期: 未実装
- 通貨設定: 未実装（JPY 固定）

---

## タスク1: UserSettings ドメインモデル定義

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/model/UserSettings.kt`

```kotlin
package com.subkiru.subkiru.core.domain.model

data class UserSettings(
    val isReminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = DEFAULT_REMINDER_DAYS_BEFORE,
) {
    companion object {
        const val DEFAULT_REMINDER_DAYS_BEFORE = 1
        const val MIN_REMINDER_DAYS_BEFORE = 1
        const val MAX_REMINDER_DAYS_BEFORE = 7
    }
}
```

### 設計意図
- ドメインモデルとして定義し、DataStore の実装詳細から分離する
- `isReminderEnabled` は通知の有効/無効を管理する。デフォルトは `false`（オプトイン）
- `reminderDaysBefore` は請求日の何日前に通知するかを管理する。範囲は 1〜7 日
- デフォルト値と制約値を `companion object` に定義し、UI・Repository で共有する

---

## タスク2: SettingsRepository 定義と実装

### 2-1: Repository インターフェース

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/repository/SettingsRepository.kt`

```kotlin
package com.subkiru.subkiru.core.domain.repository

import com.subkiru.subkiru.core.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun updateReminderEnabled(enabled: Boolean)
    suspend fun updateReminderDaysBefore(days: Int)
}
```

### 2-2: Repository 実装（DataStore）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/data/repository/SettingsRepositoryImpl.kt`

```kotlin
package com.subkiru.subkiru.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.subkiru.subkiru.core.domain.model.UserSettings
import com.subkiru.subkiru.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {

    override fun observeSettings(): Flow<UserSettings> {
        return context.dataStore.data.map { preferences ->
            UserSettings(
                isReminderEnabled = preferences[KEY_REMINDER_ENABLED] ?: false,
                reminderDaysBefore = preferences[KEY_REMINDER_DAYS_BEFORE]
                    ?: UserSettings.DEFAULT_REMINDER_DAYS_BEFORE,
            )
        }
    }

    override suspend fun updateReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_REMINDER_ENABLED] = enabled
        }
    }

    override suspend fun updateReminderDaysBefore(days: Int) {
        val clampedDays = days.coerceIn(
            UserSettings.MIN_REMINDER_DAYS_BEFORE,
            UserSettings.MAX_REMINDER_DAYS_BEFORE,
        )
        context.dataStore.edit { preferences ->
            preferences[KEY_REMINDER_DAYS_BEFORE] = clampedDays
        }
    }

    companion object {
        private val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val KEY_REMINDER_DAYS_BEFORE = intPreferencesKey("reminder_days_before")
    }
}
```

### 設計意図
- `observeSettings()` は `Flow<UserSettings>` を返す。DataStore の変更をリアクティブに UI に反映する
- `updateReminderEnabled` / `updateReminderDaysBefore` は個別更新。設定変更のたびに全フィールドを送る必要がない
- `updateReminderDaysBefore` は `coerceIn` で範囲制限を行う（バリデーションは Data 層に閉じる）
- `preferencesDataStore` は `Context` の拡張プロパティとしてファイルトップに定義する（DataStore の公式パターン）
- DataStore のファイル名は `"settings"` とする
- **注意**: `preferencesDataStore(name = "settings")` の定義はこのファイルのみで行うこと。同じ name で複数定義すると実行時例外（`IllegalStateException`）が発生する

---

## タスク3: SubKiruApplication に DI 追加

**ファイル**: `app/src/main/java/com/subkiru/subkiru/SubKiruApplication.kt`

以下のプロパティを追加する。

**追加する import**:
```kotlin
import com.subkiru.subkiru.core.data.repository.SettingsRepositoryImpl
import com.subkiru.subkiru.core.domain.repository.SettingsRepository
```

**追加するプロパティ**（既存の `serviceTemplateRepository` の後に追加）:
```kotlin
val settingsRepository: SettingsRepository by lazy {
    SettingsRepositoryImpl(this)
}
```

---

## タスク4: SettingsViewModel 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/settings/SettingsViewModel.kt`

```kotlin
package com.subkiru.subkiru.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.UserSettings
import com.subkiru.subkiru.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class SettingsUiState(
    val isReminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = UserSettings.DEFAULT_REMINDER_DAYS_BEFORE,
    val reminderDaysBeforeSlider: Int = UserSettings.DEFAULT_REMINDER_DAYS_BEFORE,
    val appVersion: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val appVersion: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // 保存エラー等の一時的なエラーを通知するイベント（Snackbar 用）
    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings()
                .catch {
                    _uiState.update {
                        it.copy(error = ERROR_MESSAGE_LOAD, isLoading = false)
                    }
                }
                .collect { settings ->
                    _uiState.update {
                        it.copy(
                            isReminderEnabled = settings.isReminderEnabled,
                            reminderDaysBefore = settings.reminderDaysBefore,
                            reminderDaysBeforeSlider = settings.reminderDaysBefore,
                            appVersion = appVersion,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    fun onReminderEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.updateReminderEnabled(enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _errorEvent.emit(ERROR_MESSAGE_SAVE)
            }
        }
    }

    fun onReminderDaysBeforeSliderChanged(days: Int) {
        _uiState.update { it.copy(reminderDaysBeforeSlider = days) }
    }

    fun onReminderDaysBeforeSliderFinished() {
        val days = _uiState.value.reminderDaysBeforeSlider
        viewModelScope.launch {
            try {
                settingsRepository.updateReminderDaysBefore(days)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _errorEvent.emit(ERROR_MESSAGE_SAVE)
            }
        }
    }

    companion object {
        const val ERROR_MESSAGE_LOAD = "設定の読み込みに失敗しました"
        const val ERROR_MESSAGE_SAVE = "設定の保存に失敗しました"

        fun factory(app: SubKiruApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appVersion = try {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                        ?: "unknown"
                } catch (_: Exception) {
                    "unknown"
                }
                SettingsViewModel(
                    settingsRepository = app.settingsRepository,
                    appVersion = appVersion,
                )
            }
        }
    }
}
```

### 設計意図
- `settingsRepository.observeSettings()` を `collect` して UiState に反映する。DataStore の値が変更されると自動で UI が更新される
- `catch` は collector 側に配置する（HomeViewModel / AnalyticsViewModel / CalendarViewModel と同パターン）
- `onReminderEnabledChanged` は即時 DataStore 書き込み。`CancellationException` を再 throw するパターンを踏襲（HomeViewModel の `onDeleteSubscription` と同パターン）
- `onReminderDaysBeforeSliderChanged` は Slider ドラッグ中の表示値更新のみ（DataStore に書き込まない）。`onReminderDaysBeforeSliderFinished` でドラッグ完了時に DataStore に書き込む。これにより Slider 操作中の大量書き込みを防止する
- `reminderDaysBeforeSlider` は Slider の一時的な表示値。DataStore の値が flow で流れてくると `reminderDaysBefore` と同期される
- `collect` 内で `error = null` を明示的にセットし、正常なデータ取得後にエラー状態を解除する
- 保存エラーは `errorEvent: MutableSharedFlow<String>` で一時的に Snackbar 通知する（HomeViewModel の `errorEvent` と同パターン）。`error` フィールドは読み込みエラー専用とし、保存エラーで画面が操作不能になることを防ぐ
- `appVersion` は `PackageManager` から取得し、ViewModel のコンストラクタに注入する（テスタブル）。`getPackageInfo` の例外を `try-catch` で処理し、失敗時は `"unknown"` にフォールバックする
- エラーメッセージは読み込み用（`ERROR_MESSAGE_LOAD`）と保存用（`ERROR_MESSAGE_SAVE`）を分離する

---

## タスク5: SettingsScreen 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/settings/SettingsScreen.kt`

```kotlin
package com.subkiru.subkiru.feature.settings

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subkiru.subkiru.core.domain.model.UserSettings
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
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
        SettingsContent(
            uiState = uiState,
            onReminderEnabledChanged = viewModel::onReminderEnabledChanged,
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
                    valueRange = UserSettings.MIN_REMINDER_DAYS_BEFORE.toFloat()..UserSettings.MAX_REMINDER_DAYS_BEFORE.toFloat(),
                    steps = UserSettings.MAX_REMINDER_DAYS_BEFORE - UserSettings.MIN_REMINDER_DAYS_BEFORE - 1,
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
```

### 設計意図
- `SettingsScreen` / `SettingsContent` を分離する（他の画面と同パターン）
- `Scaffold` + `SnackbarHost` で保存エラーを Snackbar 通知する（HomeScreen と同パターン）
- 3 状態管理: loading / error / normal。設定画面は常に表示するため empty 状態は不要
- `Column + verticalScroll` を使用（Calendar と同じ。固定レイアウトのため LazyColumn は不要）
- `NotificationSettingsCard` はリマインダーの ON/OFF と日数設定をまとめたカード
- Slider の `steps` は `MAX - MIN - 1 = 5`（1,2,3,4,5,6,7 の離散値。`steps` は中間ステップ数なので端点を除いた数）
- Slider は `onValueChange` で表示値のみ更新し、`onValueChangeFinished` で DataStore に書き込む（ドラッグ中の大量書き込み防止）
- リマインダーが OFF のときは日数 Slider を非表示にする（条件付きレンダリング）
- `AppInfoCard` はバージョン表示のみ。タップイベント不要
- `SCREEN_BOTTOM_PADDING = 24.dp`（FAB なし画面。Analytics / Calendar と同値）
- `@Preview` はリマインダー ON / OFF の 2 パターン

---

## タスク6: SubKiruNavGraph 更新

**ファイル**: `app/src/main/java/com/subkiru/subkiru/navigation/SubKiruNavGraph.kt`

Settings のプレースホルダーを実画面に差し替える。

**変更前**:
```kotlin
composable(Screen.Settings.route) {
    // 設定画面（後続タスクで実装）
    ScreenPlaceholder(label = "Settings")
}
```

**変更後**:
```kotlin
composable(Screen.Settings.route) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(app),
    )
    SettingsScreen(viewModel = viewModel)
}
```

**追加する import**:
```kotlin
import com.subkiru.subkiru.feature.settings.SettingsScreen
import com.subkiru.subkiru.feature.settings.SettingsViewModel
```

### ScreenPlaceholder の削除

この変更により `ScreenPlaceholder` を使用する箇所がなくなるため、以下のコードを削除すること。

**削除対象**:
```kotlin
// 共通プレースホルダー Composable（後続タスクで実際の Screen に差し替える）
@Composable
private fun ScreenPlaceholder(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label)
    }
}
```

削除後、未使用になる import があれば合わせて削除すること。

---

## タスク7: SettingsViewModelTest 実装

**ファイル**: `app/src/test/java/com/subkiru/subkiru/feature/settings/SettingsViewModelTest.kt`

```kotlin
package com.subkiru.subkiru.feature.settings

import app.cash.turbine.test
import com.subkiru.subkiru.core.domain.model.UserSettings
import com.subkiru.subkiru.core.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val settingsFlow = MutableSharedFlow<UserSettings>(replay = 1)

    private val settingsRepository: SettingsRepository = mockk {
        every { observeSettings() } returns settingsFlow
        coEvery { updateReminderEnabled(any()) } returns Unit
        coEvery { updateReminderDaysBefore(any()) } returns Unit
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            settingsRepository = settingsRepository,
            appVersion = TEST_APP_VERSION,
        )
    }

    @Test
    fun 初期状態はローディング中である() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertEquals("", state.appVersion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 設定値が正しく読み込まれる() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            settingsFlow.emit(
                UserSettings(isReminderEnabled = true, reminderDaysBefore = 3),
            )
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertTrue(state.isReminderEnabled)
            assertEquals(3, state.reminderDaysBefore)
            assertEquals(TEST_APP_VERSION, state.appVersion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun リマインダーをONに変更するとリポジトリが呼ばれる() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            settingsFlow.emit(UserSettings())
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onReminderEnabledChanged(true)
            advanceUntilIdle()

            coVerify { settingsRepository.updateReminderEnabled(true) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun リマインダー日数を変更するとリポジトリが呼ばれる() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            settingsFlow.emit(UserSettings(isReminderEnabled = true))
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onReminderDaysBeforeSliderChanged(5)
            viewModel.onReminderDaysBeforeSliderFinished()
            advanceUntilIdle()

            coVerify { settingsRepository.updateReminderDaysBefore(5) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun 設定読み込みでエラーが発生するとエラー状態になる() = runTest {
        val errorFlow = flow<UserSettings> {
            throw RuntimeException("DataStore error")
        }
        val errorSettingsRepository: SettingsRepository = mockk {
            every { observeSettings() } returns errorFlow
        }
        val viewModel = SettingsViewModel(
            settingsRepository = errorSettingsRepository,
            appVersion = TEST_APP_VERSION,
        )

        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(SettingsViewModel.ERROR_MESSAGE_LOAD, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun リマインダー変更でエラーが発生するとSnackbarイベントが送信される() = runTest {
        coEvery { settingsRepository.updateReminderEnabled(any()) } throws RuntimeException("write error")
        val viewModel = createViewModel()

        settingsFlow.emit(UserSettings())
        advanceUntilIdle()

        viewModel.errorEvent.test {
            viewModel.onReminderEnabledChanged(true)
            advanceUntilIdle()

            assertEquals(SettingsViewModel.ERROR_MESSAGE_SAVE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun リマインダー日数保存でエラーが発生するとSnackbarイベントが送信される() = runTest {
        coEvery { settingsRepository.updateReminderDaysBefore(any()) } throws RuntimeException("write error")
        val viewModel = createViewModel()

        settingsFlow.emit(UserSettings(isReminderEnabled = true))
        advanceUntilIdle()

        viewModel.errorEvent.test {
            viewModel.onReminderDaysBeforeSliderChanged(5)
            viewModel.onReminderDaysBeforeSliderFinished()
            advanceUntilIdle()

            assertEquals(SettingsViewModel.ERROR_MESSAGE_SAVE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun スライダー操作中は表示値のみが更新されDataStoreには書き込まない() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            settingsFlow.emit(UserSettings(isReminderEnabled = true, reminderDaysBefore = 1))
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onReminderDaysBeforeSliderChanged(5)

            val state = expectMostRecentItem()
            assertEquals(5, state.reminderDaysBeforeSlider)
            coVerify(exactly = 0) { settingsRepository.updateReminderDaysBefore(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun DataStoreの値が変更されるとUIが自動更新される() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            settingsFlow.emit(UserSettings(isReminderEnabled = false, reminderDaysBefore = 1))
            advanceUntilIdle()

            val firstState = expectMostRecentItem()
            assertFalse(firstState.isReminderEnabled)
            assertEquals(1, firstState.reminderDaysBefore)

            // DataStore から新しい値が流れてくる
            settingsFlow.emit(UserSettings(isReminderEnabled = true, reminderDaysBefore = 5))
            advanceUntilIdle()

            val secondState = expectMostRecentItem()
            assertTrue(secondState.isReminderEnabled)
            assertEquals(5, secondState.reminderDaysBefore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    companion object {
        private const val TEST_APP_VERSION = "1.0.0-test"
    }
}
```

### テスト一覧（9テスト）

| テスト名 | 検証内容 |
|----------|---------|
| 初期状態はローディング中である | デフォルト値の確認 |
| 設定値が正しく読み込まれる | `observeSettings` の値が UiState に反映される |
| リマインダーをONに変更するとリポジトリが呼ばれる | `onReminderEnabledChanged` → `updateReminderEnabled` の呼び出し検証 |
| リマインダー日数を変更するとリポジトリが呼ばれる | `onReminderDaysBeforeSliderChanged` + `onReminderDaysBeforeSliderFinished` → `updateReminderDaysBefore` の呼び出し検証 |
| 設定読み込みでエラーが発生するとエラー状態になる | エラー時の固定メッセージ（`ERROR_MESSAGE_LOAD`） |
| リマインダー変更でエラーが発生するとSnackbarイベントが送信される | `errorEvent` に `ERROR_MESSAGE_SAVE` が emit される |
| リマインダー日数保存でエラーが発生するとSnackbarイベントが送信される | `onReminderDaysBeforeSliderFinished` エラー時の `errorEvent` 検証 |
| スライダー操作中は表示値のみが更新されDataStoreには書き込まない | `reminderDaysBeforeSlider` が更新され、`updateReminderDaysBefore` が呼ばれない |
| DataStoreの値が変更されるとUIが自動更新される | Flow の再 emit で UI が更新されることの検証 |

---

## 変更しないもの
- `GetSubscriptionsUseCase.kt` — 変更なし
- `MainActivity.kt` — 変更なし
- `BottomNavBar.kt` — 変更なし
- `Screen.kt` — 変更なし

---

## 検証
- `./gradlew.bat assembleDebug` が成功すること
- `./gradlew.bat test` で SettingsViewModelTest の全9テストが成功すること
- BottomNavBar の設定タブをタップして設定画面に遷移すること
- リマインダーの Switch を ON/OFF できること
- リマインダー ON 時に Slider が表示され、日数を変更できること
- リマインダー OFF 時に Slider が非表示であること
- アプリバージョンが正しく表示されること
- 設定を変更してアプリを再起動しても値が保持されていること
