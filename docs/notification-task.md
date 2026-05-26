# 通知リマインダー タスクドキュメント（Codex向け）

## 概要

ユーザーが設定画面で有効にした「請求日リマインダー」を実際に通知として送信する機能を実装する。
WorkManager の `CoroutineWorker` で 1 日 1 回バックグラウンド実行し、設定の `reminderDaysBefore` 日以内に請求日があるサブスクリプションをチェックして通知を表示する。

---

## 前提

### 既存リソース（変更不要）
- `UserSettings` — `isReminderEnabled` / `reminderDaysBefore` 定義済み
- `SettingsRepository` — `observeSettings(): Flow<UserSettings>` 定義済み
- `SubscriptionRepository` — `observeActiveSubscriptions(): Flow<List<Subscription>>` 定義済み
- `GetUpcomingBillingsUseCase` — `invoke(withinDays): Flow<List<Subscription>>` 定義済み
- `Subscription` — `nextBillingDate: LocalDate` / `amountMinor: Long` / `currencyCode: String` 定義済み
- `gradle/libs.versions.toml` — `work-manager = "2.10.1"` 定義済み
- `build.gradle.kts` — `implementation(libs.work.runtime.ktx)` 依存済み

### MVPスコープ
- 通知アイコンは `R.drawable.ic_launcher_foreground` を暫定使用（専用アイコンは後続タスク）
- 通知タップでアプリ起動（MainActivity）。特定画面への遷移は後続タスク
- 金額表示は JPY のみ対応（`¥{amountMinor}`）。多通貨対応は後続タスク
- Android 13（API 33）以上の `POST_NOTIFICATIONS` ランタイムパーミッション要求を含む

---

## タスク1: AndroidManifest.xml 更新

**ファイル**: `app/src/main/AndroidManifest.xml`

`<manifest>` タグ直下（`<application>` の前）に以下を追加する。

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

**変更後の全体**:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".SubKiruApplication"
        ...>
        ...
    </application>

</manifest>
```

### 設計意図
- Android 13（API 33）以上で通知を表示するには `POST_NOTIFICATIONS` パーミッションが必須
- WorkManager 自体は `AndroidManifest.xml` への追加設定不要（ライブラリ側で自動マージされる）

---

## タスク2: NotificationHelper 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/notification/NotificationHelper.kt`

```kotlin
package com.subkiru.subkiru.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.subkiru.subkiru.MainActivity
import com.subkiru.subkiru.R
import com.subkiru.subkiru.core.domain.model.Subscription

object NotificationHelper {

    const val CHANNEL_ID = "billing_reminder"
    private const val GROUP_KEY = "com.subkiru.subkiru.BILLING_REMINDER"
    private const val SUMMARY_NOTIFICATION_ID = 0

    /**
     * 通知チャンネルを作成する。
     * アプリ起動時に毎回呼んでよい（既存チャンネルは上書きされない）。
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "請求日リマインダー",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "サブスクリプションの請求日をお知らせします"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * 請求予定のサブスクリプションを通知する。
     * 1件の場合は単独通知、複数件の場合はグループ通知（InboxStyle）を使用する。
     */
    fun showBillingReminders(
        context: Context,
        reminders: List<BillingReminder>,
    ) {
        if (reminders.isEmpty()) return

        val notificationManager = NotificationManagerCompat.from(context)

        // 個別通知
        reminders.forEach { reminder ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("請求日リマインダー")
                .setContentText(formatReminderText(reminder))
                .setContentIntent(createAppPendingIntent(context))
                .setGroup(GROUP_KEY)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(
                reminder.subscription.id.toInt(),
                notification,
            )
        }

        // 複数件の場合はサマリー通知を追加
        if (reminders.size > 1) {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("請求日リマインダー")
                .setSummaryText("${reminders.size}件の請求予定")

            reminders.forEach { reminder ->
                inboxStyle.addLine(formatReminderText(reminder))
            }

            val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("請求日リマインダー")
                .setContentText("${reminders.size}件の請求予定があります")
                .setContentIntent(createAppPendingIntent(context))
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setStyle(inboxStyle)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
        }
    }

    /**
     * リマインダーのテキストを生成する。
     * 例: "Netflix の請求日が明日です（¥980）"
     */
    internal fun formatReminderText(reminder: BillingReminder): String {
        val timing = when (reminder.daysUntilBilling) {
            0L -> "今日"
            1L -> "明日"
            else -> "${reminder.daysUntilBilling}日後"
        }
        val amount = formatAmount(reminder.subscription.amountMinor, reminder.subscription.currencyCode)
        return "${reminder.subscription.name} の請求日が${timing}です（${amount}）"
    }

    private fun formatAmount(amountMinor: Long, currencyCode: String): String {
        return when (currencyCode) {
            "JPY" -> "¥$amountMinor"
            else -> "$currencyCode $amountMinor"
        }
    }

    private fun createAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
```

### 設計意図
- `object` として定義し、Context を引数で受け取るユーティリティパターン
- `createChannel` はアプリ起動時に毎回呼ぶ。既存チャンネルの再作成はノーオプ（Android 公式推奨）
- 1件の場合は単独通知、複数件の場合はグループ通知（`InboxStyle`）で一括表示
- 通知 ID はサブスクリプションの `id.toInt()` を使用し、同じサブスクの通知は上書きされる
- サマリー通知の ID は固定値 `0`（サブスクの `id` は AutoGenerate で 1 以上のため衝突しない）
- `formatReminderText` は `internal` にしてテスト可能にする
- `formatAmount` は MVP では JPY のみ対応。その他の通貨はコード + 金額のフォールバック
- 通知タップで `MainActivity` を起動する（`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`）
- `PendingIntent.FLAG_IMMUTABLE` は Android 12 以上で必須

---

## タスク3: BillingReminder モデル + BillingReminderWorker 実装

### 3-1: BillingReminder データクラス

**ファイル**: `app/src/main/java/com/subkiru/subkiru/notification/BillingReminder.kt`

```kotlin
package com.subkiru.subkiru.notification

import com.subkiru.subkiru.core.domain.model.Subscription

data class BillingReminder(
    val subscription: Subscription,
    val daysUntilBilling: Long,
)
```

### 3-2: BillingReminderWorker

**ファイル**: `app/src/main/java/com/subkiru/subkiru/notification/BillingReminderWorker.kt`

```kotlin
package com.subkiru.subkiru.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.subkiru.subkiru.SubKiruApplication
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.model.UserSettings
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BillingReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SubKiruApplication

        val settings = app.settingsRepository.observeSettings().first()
        if (!settings.isReminderEnabled) return Result.success()

        val subscriptions = app.subscriptionRepository
            .observeActiveSubscriptions().first()

        val reminders = getSubscriptionsToNotify(
            settings = settings,
            subscriptions = subscriptions,
            today = LocalDate.now(app.clock),
        )

        if (reminders.isNotEmpty()) {
            NotificationHelper.showBillingReminders(applicationContext, reminders)
        }

        return Result.success()
    }

    companion object {
        /**
         * 通知対象のサブスクリプションを抽出する。
         * 純粋関数としてテスト可能にする。
         */
        internal fun getSubscriptionsToNotify(
            settings: UserSettings,
            subscriptions: List<Subscription>,
            today: LocalDate,
        ): List<BillingReminder> {
            if (!settings.isReminderEnabled) return emptyList()

            val deadline = today.plusDays(settings.reminderDaysBefore.toLong())
            return subscriptions
                .filter { it.nextBillingDate in today..deadline }
                .map { subscription ->
                    BillingReminder(
                        subscription = subscription,
                        daysUntilBilling = ChronoUnit.DAYS.between(today, subscription.nextBillingDate),
                    )
                }
        }
    }
}
```

### 設計意図
- `CoroutineWorker` を継承し、`doWork()` で suspend 関数を使用する
- `applicationContext as SubKiruApplication` で手動 DI のリポジトリにアクセスする（既存パターンに準拠）
- `observeSettings().first()` / `observeActiveSubscriptions().first()` で一回限りの値を取得する（Worker は Flow を collect し続けない）
- リマインダーが無効の場合は即座に `Result.success()` を返す（エラーではない）
- `getSubscriptionsToNotify` は `companion object` の `internal` 関数として切り出し、純粋関数としてテスト可能にする
- `today..deadline` の範囲チェックで、当日（`daysUntilBilling = 0`）も通知対象に含める
- `ChronoUnit.DAYS.between` で正確な日数差を計算する
- `BillingReminder` は通知モジュール固有のデータクラス。Domain 層には置かない（通知表示にのみ使用するため）
- Worker は常に `Result.success()` を返す。通知対象がない場合もエラーではない。`Result.retry()` / `Result.failure()` は使わない（リトライは WorkManager の定期実行に任せる）

---

## タスク4: ReminderScheduler 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/notification/ReminderScheduler.kt`

```kotlin
package com.subkiru.subkiru.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ReminderScheduler(
    private val context: Context,
) {

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<BillingReminderWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
        ).build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }

    fun cancel() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_NAME)
    }

    companion object {
        internal const val WORK_NAME = "billing_reminder"
    }
}
```

### 設計意図
- `class` として定義し、`Context` をコンストラクタで受け取る（テスト時にモック可能）
- `schedule()` は `PeriodicWorkRequest` を 1 日間隔で登録する
- `ExistingPeriodicWorkPolicy.KEEP` により、既にスケジュール済みの場合は重複登録しない
- `cancel()` はユニークワーク名で指定してキャンセルする
- `WORK_NAME` は `internal` にしてテストから参照可能にする
- WorkManager の最小反復間隔は 15 分だが、1 日間隔なので問題ない

---

## タスク5: SubKiruApplication 更新

**ファイル**: `app/src/main/java/com/subkiru/subkiru/SubKiruApplication.kt`

### 追加する import

```kotlin
import com.subkiru.subkiru.notification.NotificationHelper
import com.subkiru.subkiru.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
```

### 追加するプロパティ

既存の `settingsRepository` の後に追加する:

```kotlin
val reminderScheduler by lazy { ReminderScheduler(this) }
```

### onCreate をオーバーライド

```kotlin
private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

override fun onCreate() {
    super.onCreate()
    NotificationHelper.createChannel(this)
    initializeReminder()
}

private fun initializeReminder() {
    applicationScope.launch {
        val settings = settingsRepository.observeSettings().first()
        if (settings.isReminderEnabled) {
            reminderScheduler.schedule()
        }
    }
}
```

### 変更後の全体

```kotlin
package com.subkiru.subkiru

import android.app.Application
import android.content.Context
import com.subkiru.subkiru.core.data.db.SubKiruDatabase
import com.subkiru.subkiru.core.data.repository.CategoryRepositoryImpl
import com.subkiru.subkiru.core.data.repository.SettingsRepositoryImpl
import com.subkiru.subkiru.core.data.repository.ServiceTemplateRepositoryImpl
import com.subkiru.subkiru.core.data.repository.SubscriptionRepositoryImpl
import com.subkiru.subkiru.core.domain.repository.CategoryRepository
import com.subkiru.subkiru.core.domain.repository.SettingsRepository
import com.subkiru.subkiru.core.domain.repository.ServiceTemplateRepository
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import com.subkiru.subkiru.core.domain.usecase.AddSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.CalculateMonthlyTotalUseCase
import com.subkiru.subkiru.core.domain.usecase.DeleteSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import com.subkiru.subkiru.core.domain.usecase.GetUpcomingBillingsUseCase
import com.subkiru.subkiru.notification.NotificationHelper
import com.subkiru.subkiru.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock

class SubKiruApplication : Application() {
    val clock: Clock = Clock.systemDefaultZone()

    val database by lazy { SubKiruDatabase.getInstance(this) }

    val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepositoryImpl(database.subscriptionDao(), clock)
    }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(database.categoryDao())
    }

    val serviceTemplateRepository: ServiceTemplateRepository by lazy {
        ServiceTemplateRepositoryImpl(database.serviceTemplateDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(this)
    }

    val reminderScheduler by lazy { ReminderScheduler(this) }

    val getSubscriptionsUseCase by lazy { GetSubscriptionsUseCase(subscriptionRepository) }
    val addSubscriptionUseCase by lazy { AddSubscriptionUseCase(subscriptionRepository) }
    val deleteSubscriptionUseCase by lazy { DeleteSubscriptionUseCase(subscriptionRepository) }
    val calculateMonthlyTotalUseCase by lazy { CalculateMonthlyTotalUseCase(subscriptionRepository) }
    val getUpcomingBillingsUseCase by lazy {
        GetUpcomingBillingsUseCase(subscriptionRepository, clock)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        initializeReminder()
    }

    private fun initializeReminder() {
        applicationScope.launch {
            val settings = settingsRepository.observeSettings().first()
            if (settings.isReminderEnabled) {
                reminderScheduler.schedule()
            }
        }
    }

    companion object {
        fun from(context: Context): SubKiruApplication {
            return context.applicationContext as SubKiruApplication
        }
    }
}
```

### 設計意図
- `NotificationHelper.createChannel` はアプリ起動時に毎回呼ぶ（Android 公式推奨パターン。既存チャンネルは上書きされない）
- `initializeReminder` は `applicationScope` で非同期実行する。`onCreate` をブロックしない
- `SupervisorJob()` により子コルーチンの失敗が他の子に影響しない
- `Dispatchers.IO` により DataStore の I/O をメインスレッドで行わない
- `observeSettings().first()` で設定を一回取得し、ON の場合のみ Worker をスケジュールする
- アプリ起動時にスケジュールすることで、デバイス再起動後も Worker が復帰する（WorkManager は永続化されるが、念のため再登録。`KEEP` ポリシーにより重複しない）

---

## タスク6: SettingsViewModel・SettingsScreen 連携

### 6-1: SettingsViewModel 更新

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/settings/SettingsViewModel.kt`

#### コンストラクタ変更

**変更前**:
```kotlin
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val appVersion: String,
) : ViewModel() {
```

**変更後**:
```kotlin
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val appVersion: String,
) : ViewModel() {
```

#### 追加する import

```kotlin
import com.subkiru.subkiru.notification.ReminderScheduler
```

#### onReminderEnabledChanged 変更

**変更前**:
```kotlin
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
```

**変更後**:
```kotlin
fun onReminderEnabledChanged(enabled: Boolean) {
    viewModelScope.launch {
        try {
            settingsRepository.updateReminderEnabled(enabled)
            if (enabled) {
                reminderScheduler.schedule()
            } else {
                reminderScheduler.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _errorEvent.emit(ERROR_MESSAGE_SAVE)
        }
    }
}
```

#### factory 変更

**変更前**:
```kotlin
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
```

**変更後**:
```kotlin
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
            reminderScheduler = app.reminderScheduler,
            appVersion = appVersion,
        )
    }
}
```

### 6-2: SettingsScreen 更新（パーミッション要求追加）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/feature/settings/SettingsScreen.kt`

#### 追加する import

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
```

#### SettingsScreen 関数の変更

**変更前**:
```kotlin
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
```

**変更後**:
```kotlin
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
```

### 設計意図
- SettingsViewModel にスケジューラ連携を追加: ON → `schedule()` / OFF → `cancel()`
- スケジューラの呼び出しは `try-catch` ブロック内に配置し、設定保存とスケジュールを一連の処理として実行する。スケジュール失敗時も `ERROR_MESSAGE_SAVE` で Snackbar 通知する
- パーミッション要求は SettingsScreen（UI 層）の責務とする。ViewModel はパーミッションを知らない
- Android 13（API 33 / `TIRAMISU`）以上の場合のみランタイムパーミッションを要求する。API 32 以下ではパーミッション不要
- パーミッションが既に付与済みの場合は直接 `onReminderEnabledChanged(true)` を呼ぶ
- パーミッション拒否時は Snackbar でメッセージを表示し、リマインダーは OFF のまま（Switch は切り替わらない）
- `rememberLauncherForActivityResult` は Composable のトップレベルで呼ぶ（条件分岐内では使えない）

---

## タスク7: テスト

### 7-1: BillingReminderWorkerTest（新規作成）

**ファイル**: `app/src/test/java/com/subkiru/subkiru/notification/BillingReminderWorkerTest.kt`

```kotlin
package com.subkiru.subkiru.notification

import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.model.UserSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class BillingReminderWorkerTest {

    @Test
    fun リマインダーが無効の場合は空リストを返す() {
        val settings = UserSettings(isReminderEnabled = false, reminderDaysBefore = 3)
        val subscriptions = listOf(createSubscription(nextBillingDate = TODAY.plusDays(1)))

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = subscriptions,
            today = TODAY,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun 請求予定がない場合は空リストを返す() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val subscriptions = emptyList<Subscription>()

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = subscriptions,
            today = TODAY,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun 請求日が範囲内の場合はリストに含まれる() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val subscription = createSubscription(
            name = "Netflix",
            nextBillingDate = TODAY.plusDays(2),
        )

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
        )

        assertEquals(1, result.size)
        assertEquals("Netflix", result[0].subscription.name)
    }

    @Test
    fun 請求日が範囲外の場合はリストに含まれない() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val subscription = createSubscription(nextBillingDate = TODAY.plusDays(5))

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun 複数の請求予定がある場合は全て返す() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val subscriptions = listOf(
            createSubscription(name = "Netflix", nextBillingDate = TODAY.plusDays(1)),
            createSubscription(name = "Spotify", nextBillingDate = TODAY.plusDays(2)),
            createSubscription(name = "YouTube", nextBillingDate = TODAY.plusDays(5)),
        )

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = subscriptions,
            today = TODAY,
        )

        assertEquals(2, result.size)
        assertEquals("Netflix", result[0].subscription.name)
        assertEquals("Spotify", result[1].subscription.name)
    }

    @Test
    fun 請求日までの日数が正しく計算される() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 7)
        val subscription = createSubscription(nextBillingDate = TODAY.plusDays(5))

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
        )

        assertEquals(1, result.size)
        assertEquals(5L, result[0].daysUntilBilling)
    }

    @Test
    fun 今日が請求日の場合もリストに含まれる() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 1)
        val subscription = createSubscription(nextBillingDate = TODAY)

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
        )

        assertEquals(1, result.size)
        assertEquals(0L, result[0].daysUntilBilling)
    }

    @Test
    fun 過去の請求日はリストに含まれない() {
        val settings = UserSettings(isReminderEnabled = true, reminderDaysBefore = 3)
        val subscription = createSubscription(nextBillingDate = TODAY.minusDays(1))

        val result = BillingReminderWorker.getSubscriptionsToNotify(
            settings = settings,
            subscriptions = listOf(subscription),
            today = TODAY,
        )

        assertTrue(result.isEmpty())
    }

    companion object {
        private val TODAY = LocalDate.of(2026, 1, 15)
        private val NOW = Instant.parse("2026-01-15T00:00:00Z")

        private fun createSubscription(
            id: Long = 1L,
            name: String = "TestService",
            amountMinor: Long = 980L,
            currencyCode: String = "JPY",
            nextBillingDate: LocalDate = TODAY.plusDays(1),
        ): Subscription {
            return Subscription(
                id = id,
                name = name,
                amountMinor = amountMinor,
                currencyCode = currencyCode,
                billingInterval = BillingInterval(
                    unit = BillingIntervalUnit.MONTHLY,
                    count = 1,
                ),
                startDate = TODAY.minusMonths(1),
                nextBillingDate = nextBillingDate,
                categoryId = null,
                templateId = null,
                logoUri = null,
                memo = null,
                isActive = true,
                createdAt = NOW,
                updatedAt = NOW,
            )
        }
    }
}
```

### 7-2: NotificationHelperTest（新規作成）

**ファイル**: `app/src/test/java/com/subkiru/subkiru/notification/NotificationHelperTest.kt`

```kotlin
package com.subkiru.subkiru.notification

import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.Subscription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class NotificationHelperTest {

    @Test
    fun 請求日が今日の場合のテキスト() {
        val reminder = createReminder(daysUntilBilling = 0)

        val text = NotificationHelper.formatReminderText(reminder)

        assertEquals("TestService の請求日が今日です（¥980）", text)
    }

    @Test
    fun 請求日が明日の場合のテキスト() {
        val reminder = createReminder(daysUntilBilling = 1)

        val text = NotificationHelper.formatReminderText(reminder)

        assertEquals("TestService の請求日が明日です（¥980）", text)
    }

    @Test
    fun 請求日が2日以上後の場合のテキスト() {
        val reminder = createReminder(daysUntilBilling = 3)

        val text = NotificationHelper.formatReminderText(reminder)

        assertEquals("TestService の請求日が3日後です（¥980）", text)
    }

    companion object {
        private val BASE_DATE = LocalDate.of(2026, 1, 15)
        private val NOW = Instant.parse("2026-01-15T00:00:00Z")

        private fun createReminder(
            name: String = "TestService",
            amountMinor: Long = 980L,
            daysUntilBilling: Long = 1L,
        ): BillingReminder {
            return BillingReminder(
                subscription = Subscription(
                    id = 1L,
                    name = name,
                    amountMinor = amountMinor,
                    currencyCode = "JPY",
                    billingInterval = BillingInterval(
                        unit = BillingIntervalUnit.MONTHLY,
                        count = 1,
                    ),
                    startDate = BASE_DATE.minusMonths(1),
                    nextBillingDate = BASE_DATE.plusDays(daysUntilBilling),
                    categoryId = null,
                    templateId = null,
                    logoUri = null,
                    memo = null,
                    isActive = true,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
                daysUntilBilling = daysUntilBilling,
            )
        }
    }
}
```

### 7-3: SettingsViewModelTest 更新

**ファイル**: `app/src/test/java/com/subkiru/subkiru/feature/settings/SettingsViewModelTest.kt`

#### 追加する import

```kotlin
import com.subkiru.subkiru.notification.ReminderScheduler
import io.mockk.verify
```

#### settingsRepository の後に追加

```kotlin
private val reminderScheduler: ReminderScheduler = mockk(relaxed = true)
```

#### createViewModel 変更

**変更前**:
```kotlin
private fun createViewModel(): SettingsViewModel {
    return SettingsViewModel(
        settingsRepository = settingsRepository,
        appVersion = TEST_APP_VERSION,
    )
}
```

**変更後**:
```kotlin
private fun createViewModel(): SettingsViewModel {
    return SettingsViewModel(
        settingsRepository = settingsRepository,
        reminderScheduler = reminderScheduler,
        appVersion = TEST_APP_VERSION,
    )
}
```

#### 既存テスト「設定読み込みでエラーが発生するとエラー状態になる」の変更

**変更前**:
```kotlin
val viewModel = SettingsViewModel(
    settingsRepository = errorSettingsRepository,
    appVersion = TEST_APP_VERSION,
)
```

**変更後**:
```kotlin
val viewModel = SettingsViewModel(
    settingsRepository = errorSettingsRepository,
    reminderScheduler = reminderScheduler,
    appVersion = TEST_APP_VERSION,
)
```

#### 追加するテスト（2件）

既存テスト「DataStoreの値が変更されるとUIが自動更新される」の後に追加する:

```kotlin
@Test
fun リマインダーをONにするとスケジューラが呼ばれる() = runTest {
    val viewModel = createViewModel()

    viewModel.uiState.test {
        awaitItem()

        settingsFlow.emit(UserSettings())
        advanceUntilIdle()
        expectMostRecentItem()

        viewModel.onReminderEnabledChanged(true)
        advanceUntilIdle()

        verify { reminderScheduler.schedule() }
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun リマインダーをOFFにするとスケジューラがキャンセルされる() = runTest {
    val viewModel = createViewModel()

    viewModel.uiState.test {
        awaitItem()

        settingsFlow.emit(UserSettings(isReminderEnabled = true))
        advanceUntilIdle()
        expectMostRecentItem()

        viewModel.onReminderEnabledChanged(false)
        advanceUntilIdle()

        verify { reminderScheduler.cancel() }
        cancelAndIgnoreRemainingEvents()
    }
}
```

### テスト一覧（合計 21 テスト）

#### BillingReminderWorkerTest（8テスト）

| テスト名 | 検証内容 |
|----------|---------|
| リマインダーが無効の場合は空リストを返す | `isReminderEnabled = false` で空リスト |
| 請求予定がない場合は空リストを返す | 空のサブスクリストで空リスト |
| 請求日が範囲内の場合はリストに含まれる | `reminderDaysBefore` 以内の請求日が含まれる |
| 請求日が範囲外の場合はリストに含まれない | `reminderDaysBefore` を超える請求日は除外される |
| 複数の請求予定がある場合は全て返す | 範囲内の複数件が全て返る |
| 請求日までの日数が正しく計算される | `daysUntilBilling` の値が正確 |
| 今日が請求日の場合もリストに含まれる | `daysUntilBilling = 0` のケース |
| 過去の請求日はリストに含まれない | 昨日以前の請求日は除外される |

#### NotificationHelperTest（3テスト）

| テスト名 | 検証内容 |
|----------|---------|
| 請求日が今日の場合のテキスト | `daysUntilBilling = 0` → "今日" |
| 請求日が明日の場合のテキスト | `daysUntilBilling = 1` → "明日" |
| 請求日が2日以上後の場合のテキスト | `daysUntilBilling = 3` → "3日後" |

#### SettingsViewModelTest（既存9テスト + 新規2テスト = 11テスト）

| テスト名 | 検証内容 | 状態 |
|----------|---------|------|
| 初期状態はローディング中である | デフォルト値の確認 | 既存 |
| 設定値が正しく読み込まれる | `observeSettings` の値が UiState に反映される | 既存 |
| リマインダーをONに変更するとリポジトリが呼ばれる | `updateReminderEnabled` の呼び出し検証 | 既存 |
| リマインダー日数を変更するとリポジトリが呼ばれる | `updateReminderDaysBefore` の呼び出し検証 | 既存 |
| 設定読み込みでエラーが発生するとエラー状態になる | エラー時の固定メッセージ | 既存（変更あり） |
| リマインダー変更でエラーが発生するとSnackbarイベントが送信される | `errorEvent` に `ERROR_MESSAGE_SAVE` | 既存 |
| リマインダー日数保存でエラーが発生するとSnackbarイベントが送信される | `errorEvent` 検証 | 既存 |
| スライダー操作中は表示値のみが更新されDataStoreには書き込まない | `updateReminderDaysBefore` が呼ばれない | 既存 |
| DataStoreの値が変更されるとUIが自動更新される | Flow の再 emit で UI が更新される | 既存 |
| リマインダーをONにするとスケジューラが呼ばれる | `reminderScheduler.schedule()` の呼び出し検証 | **新規** |
| リマインダーをOFFにするとスケジューラがキャンセルされる | `reminderScheduler.cancel()` の呼び出し検証 | **新規** |

---

## 変更しないもの
- `UserSettings.kt` — 変更なし
- `SettingsRepository.kt` — 変更なし
- `SettingsRepositoryImpl.kt` — 変更なし
- `GetUpcomingBillingsUseCase.kt` — 変更なし（Worker は直接 Repository を使う）
- `SubscriptionRepository.kt` — 変更なし
- `SubKiruNavGraph.kt` — 変更なし
- `BottomNavBar.kt` — 変更なし
- `Screen.kt` — 変更なし
- `MainActivity.kt` — 変更なし
- `gradle/libs.versions.toml` — 変更なし（work-manager は定義済み）
- `build.gradle.kts` — 変更なし（work-runtime-ktx は依存済み）

---

## 検証
- `./gradlew.bat assembleDebug` が成功すること
- `./gradlew.bat test` で BillingReminderWorkerTest の全8テストが成功すること
- `./gradlew.bat test` で NotificationHelperTest の全3テストが成功すること
- `./gradlew.bat test` で SettingsViewModelTest の全11テストが成功すること
- 設定画面でリマインダーを ON にすると通知パーミッション要求ダイアログが表示されること（Android 13以上）
- パーミッション許可後、リマインダーが ON になること
- パーミッション拒否時、リマインダーが OFF のまま Snackbar が表示されること
- リマインダー ON の状態でアプリを再起動しても Worker がスケジュールされていること
- リマインダー OFF にした後、Worker がキャンセルされること
