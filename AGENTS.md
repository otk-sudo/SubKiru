# SubKiru — AGENTS.md

## プロジェクト概要

**アプリ名**: SubKiru（サブキル）  
**サブタイトル**: SubKiru - サブスク管理・節約家計簿  
**プラットフォーム**: Android（Google Play）  
**ターゲット**: 節約意識が高い20〜30代  
**コンセプト**: 無駄なサブスクを「切る」。ダーク・スタイリッシュなUI

---

## 技術スタック

| 領域 | 技術 |
|------|------|
| 言語 | Kotlin 2.2.10 |
| UI | Jetpack Compose BOM 2026.02.01 + Material 3 |
| アーキテクチャ | MVVM + Clean Architecture (3層) |
| DI | **手動 DI**（SubKiruApplication 経由）※ Hilt は AGP 9.x との互換性問題により保留 |
| 非同期 | Kotlin Coroutines + Flow |
| ローカルDB | Room 2.7.1（KSP 2.2.10-2.0.2） |
| 設定永続化 | DataStore 1.1.4 (Preferences) |
| 画像 | ローカル drawable + 頭文字カラーアバター（外部画像API・Coil 非依存） |
| 画面遷移 | Compose Navigation 2.9.0 |
| バックエンド | Firebase Auth + Firestore（v1.1以降・Pro機能のみ。MVP では未導入） |
| 課金 | Google Play Billing Library（v1.2以降。開始時に[公式リリースノート](https://developer.android.com/google/play/billing/release-notes?hl=ja)を確認。現時点の最新安定版は 8.3.0） |
| 通知 | WorkManager 2.10.1 + Notification API |
| ウィジェット | Glance 1.1.1 |
| テスト | JUnit 5 + MockK + Turbine |
| CI | GitHub Actions |
| AGP | 9.2.1 |
| Gradle | 9.4.1 |
| 最小SDK | Android 8.0 (API 26) |
| ターゲットSDK | Android 16 (API 36) |
| compileSdk | API 36 |

---

## アーキテクチャ原則

### 3層構造を厳守する

```
UI層        Composable + ViewModel
               ↓ 依存
ドメイン層   UseCase + Domain Model + Repository（interface）
               ↑ 依存（実装を注入）
データ層     RepositoryImpl + Room DAO + Firebase
```

依存の方向は **UI → Domain ← Data**。

- Domain 層は誰にも依存しない。Repository の **interface** を domain に置き、**実装**は data 層に置く
- UI 層は Domain（UseCase / Repository interface）にのみ依存する
- Data 層は Domain の interface を実装する。UI 層を知らない
- Room / Firebase を直接触れるのは RepositoryImpl のみ
- ViewModel は UseCase のみを呼ぶ。DAO を直接触らない
- Composable は ViewModel の uiState を購読するだけ。ロジックを持たない

### 手動 DI（Hilt の代替）

Hilt は AGP 9.x との互換性問題（`Android BaseExtension not found`）により現時点で導入不可。
代わりに `SubKiruApplication` でシングルトンを生成・保持する手動 DI パターンを採用する。

```kotlin
class SubKiruApplication : Application() {
    val clock: Clock = Clock.systemDefaultZone()
    val database by lazy { SubKiruDatabase.getInstance(this) }

    // Repository
    val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepositoryImpl(database.subscriptionDao(), clock)
    }

    // UseCase
    val getSubscriptionsUseCase by lazy { GetSubscriptionsUseCase(subscriptionRepository) }
    val addSubscriptionUseCase by lazy { AddSubscriptionUseCase(subscriptionRepository) }
    val deleteSubscriptionUseCase by lazy { DeleteSubscriptionUseCase(subscriptionRepository) }

    companion object {
        fun from(context: Context): SubKiruApplication =
            context.applicationContext as SubKiruApplication
    }
}
```

ViewModel からは `ViewModelProvider.Factory` 経由で UseCase を受け取る：

```kotlin
class HomeViewModel(
    getSubscriptionsUseCase: GetSubscriptionsUseCase,
    private val deleteSubscriptionUseCase: DeleteSubscriptionUseCase,
) : ViewModel() {
    companion object {
        fun factory(app: SubKiruApplication) = viewModelFactory {
            initializer {
                HomeViewModel(
                    getSubscriptionsUseCase = app.getSubscriptionsUseCase,
                    deleteSubscriptionUseCase = app.deleteSubscriptionUseCase,
                )
            }
        }
    }
}
```

**Hilt 再導入の条件**: AGP 9.x に対応した Hilt 安定版がリリースされ次第、
[google/dagger releases](https://github.com/google/dagger/releases) を確認して導入する。
その際は `SubKiruApplication` の手動 DI を `@HiltAndroidApp` に置き換えること。

### オフライン優先（Offline-First）

- Room が **Single Source of Truth**
- Firebase はバックアップ・端末間同期専用（Pro機能）
- Firebase への書き込みが失敗してもアプリは正常動作する
- ネットワーク状態をUIで確認してから処理を始めてはいけない

### 単方向データフロー（UDF）

- 状態は ViewModel → Composable へ一方向に流れる
- イベントは Composable → ViewModel へ一方向に上がる
- `MutableStateFlow` は ViewModel 内で private にする
- 画面の全状態を1つの `UiState` data class に集約する

---

## 命名規則

### クラス名

| 種別 | 命名パターン | 例 |
|------|------------|-----|
| Composable | `{Screen名}Screen` / `{Component名}` | `HomeScreen`, `SubscriptionCard` |
| ViewModel | `{Screen名}ViewModel` | `HomeViewModel` |
| UseCase | `{動詞}{名詞}UseCase` | `GetSubscriptionsUseCase` |
| Repository（IF） | `{名詞}Repository` | `SubscriptionRepository` |
| Repository（実装） | `{名詞}RepositoryImpl` | `SubscriptionRepositoryImpl` |
| Room Entity | `{名詞}Entity` | `SubscriptionEntity` |
| Room DAO | `{名詞}Dao` | `SubscriptionDao` |
| Domain Model | `{名詞}` | `Subscription` |

### 関数名

- UseCase の呼び出しエントリポイントは常に `operator fun invoke()` にする
- ViewModel のイベントハンドラは `on{動詞}{名詞}` にする（例: `onDeleteSubscription`）
- Flow を返す関数は `observe{名詞}` または `get{名詞}` にする
- 一回限りの書き込みは `suspend fun` + `add/update/delete{名詞}` にする

---

## ファイル構成

```
SubKiru/
├── app/
│   ├── MainActivity.kt
│   ├── SubKiruApplication.kt
│   └── navigation/
│       ├── SubKiruNavGraph.kt
│       ├── Screen.kt
│       └── BottomNavBar.kt
├── feature/
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── analytics/
│   │   ├── AnalyticsScreen.kt
│   │   └── AnalyticsViewModel.kt
│   ├── calendar/
│   │   ├── CalendarScreen.kt
│   │   └── CalendarViewModel.kt
│   ├── add/
│   │   ├── AddSubscriptionScreen.kt
│   │   └── AddSubscriptionViewModel.kt
│   └── settings/
│       ├── SettingsScreen.kt
│       └── SettingsViewModel.kt
├── core/
│   ├── data/
│   │   ├── db/
│   │   │   ├── SubKiruDatabase.kt
│   │   │   ├── dao/
│   │   │   │   ├── SubscriptionDao.kt
│   │   │   │   ├── CategoryDao.kt
│   │   │   │   ├── ServiceTemplateDao.kt
│   │   │   │   └── PaymentHistoryDao.kt
│   │   │   └── entity/
│   │   │       ├── SubscriptionEntity.kt
│   │   │       ├── CategoryEntity.kt
│   │   │       ├── ServiceTemplateEntity.kt
│   │   │       └── PaymentHistoryEntity.kt
│   │   ├── repository/
│   │   │   ├── SubscriptionRepositoryImpl.kt
│   │   │   ├── CategoryRepositoryImpl.kt
│   │   │   └── ServiceTemplateRepositoryImpl.kt
│   │   └── firebase/
│   │       └── FirestoreSyncManager.kt
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Subscription.kt
│   │   │   ├── Category.kt
│   │   │   ├── BillingInterval.kt        ← unit + count の組み合わせ
│   │   │   └── ServiceTemplate.kt
│   │   ├── repository/
│   │   │   ├── SubscriptionRepository.kt   ← インターフェース（domain層に置く）
│   │   │   ├── CategoryRepository.kt
│   │   │   └── ServiceTemplateRepository.kt
│   │   └── usecase/
│   │       ├── GetSubscriptionsUseCase.kt
│   │       ├── CalculateMonthlyTotalUseCase.kt
│   │       ├── AddSubscriptionUseCase.kt
│   │       ├── DeleteSubscriptionUseCase.kt
│   │       └── GetUpcomingBillingsUseCase.kt
│   └── ui/
│       └── component/
│           └── SubscriptionCard.kt
├── ui/
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── notification/
│   └── BillingReminderWorker.kt
└── widget/
    └── SubKiruWidget.kt
```

---

## コーディングルール

### 一般

1. `TODO` コメントを残さない。実装するか、GitHub Issue にする
2. `!!` （non-null assertion）の使用を禁止する。`?: return` や `?: throw` で代替
3. `Log.d()` を本番コードに残さない。Timber または Crashlytics を使う
4. `magic number` を禁止する。`const val` に名前をつけること
5. 関数は1つの責務のみ持つ。20行を超えたら分割を検討する

### Compose

1. `@Composable` 関数の先頭文字は大文字にする
2. `@Preview` を全 Screen に必ずつける
3. Composable 内でビジネスロジックを書かない（ViewModelに移す）
4. `LazyColumn` の `items` には常に `key` を指定する
5. `remember { }` の乱用を避ける。ViewModelで管理できる状態はそちらに置く

### Repository

1. Repository は interface と Impl を必ず分離する
2. Room の DAO を Repository 以外から直接呼ばない
3. Firebase 操作の例外処理は `CancellationException` を再throwすること。`runCatching` はCoroutineキャンセルも捕捉するため直接使用を禁止。代わりに以下のパターンを使う:
   ```kotlin
   try {
       syncToFirestore(data)
   } catch (e: CancellationException) {
       throw e                    // キャンセルは必ず再throw
   } catch (e: Exception) {
       enqueueRetrySync(data.id)  // 通常例外はWorkManagerにリトライ登録
   }
   ```
4. `Flow` を返す関数は `suspend` をつけない

### UseCase

1. `operator fun invoke()` をエントリポイントにする
2. 戻り値が成功/失敗を持つ場合は `sealed interface Result` で返す
3. UseCase がネットワークや DB を直接知ってはいけない（Repositoryを経由）
4. バリデーションは UseCase に書く（ViewModelに書かない）

### テスト

1. **TDD を採用する**: 実装前にテストを書く
2. UseCase と Repository の単体テストを必ず書く
3. ViewModel のテストは `Turbine` ライブラリで Flow をテストする
4. テストクラス名は `{対象クラス名}Test` にする
5. テスト関数名は日本語で記述してよい（例: `fun 月額合計が正しく計算される()`）

---

## Roomスキーマ（主要テーブル）

### 金額の保持方針

金額は **`amountMinor: Long`** + **`currencyCode: String`** で保持する。

- JPY（最小単位 = 円）: `980` → `amountMinor = 980`
- USD（最小単位 = セント）: `9.99` → `amountMinor = 999`
- 理由: `Int` では USD など小数通貨で精度が失われ、将来の為替換算で破綻する

### 日付の保持方針

請求日は **時刻ではなく「日付」** なので `epochDay: Long`（`LocalDate.toEpochDay()`）で保持する。

- Unix ミリ秒（UnixMs）はタイムゾーン次第で前日/翌日にズレる恐れがある
- `TypeConverter` で `LocalDate ↔ Long (epochDay)` を変換する
- `created_at` / `updated_at` のような「時刻も必要なタイムスタンプ」は引き続き UnixMs（Long）を使う

```
subscriptions
  id                     Long  PK AutoGenerate
  name                   String
  amount_minor           Long                       -- 金額（最小通貨単位）
  currency_code          String   default "JPY"     -- ISO 4217 (例: "JPY", "USD")
  billing_interval_unit  String   DAILY / WEEKLY / MONTHLY / YEARLY
  billing_interval_count Int      default 1         -- 例: 3ヶ月 → unit=MONTHLY, count=3
  start_date_epoch       Long                       -- LocalDate.toEpochDay()
  next_billing_date_epoch Long                      -- LocalDate.toEpochDay()
  category_id            Long?    FK → categories.id
  template_id            Long?    FK → service_templates.id
  logo_uri               String?
  memo                   String?
  is_active              Boolean  default true      -- 論理削除
  created_at             Long                       -- UnixMs（時刻が必要なため）
  updated_at             Long                       -- UnixMs

categories
  id         Long   PK AutoGenerate
  name       String
  icon_name  String
  color_hex  String
  sort_order Int

service_templates                                   -- 人気サービスのプリセット
  id               Long   PK AutoGenerate
  name             String                           -- 表示名 (例: "Netflix")
  default_amount_minor    Long                      -- 推奨金額（最小通貨単位）
  default_currency_code   String  default "JPY"
  default_interval_unit   String  default "MONTHLY"
  default_interval_count  Int     default 1
  logo_resource_name      String                   -- drawableリソース名
  category_id             Long    FK → categories.id
  search_keywords         String                   -- 検索用（カンマ区切り）

payment_history
  id                  Long   PK AutoGenerate
  subscription_id     Long   FK → subscriptions.id
  billing_date_epoch  Long                         -- LocalDate.toEpochDay()
  amount_minor        Long                         -- 実際に請求された金額
  currency_code       String
  is_paid             Boolean
  recorded_at         Long                         -- UnixMs
```

### service_templates の初期データ投入方針

`RoomDatabase.Callback#onCreate` 内で `CoroutineScope` を使って初期データを挿入する。
ソースは `assets/service_templates.json` に JSON で管理し、アプリアップデートで追加できるようにする。

```kotlin
// SubKiruDatabase.kt
addCallback(object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        scope.launch { prePopulateTemplates(instance) }
    }
})
```

---

## UIカラーパレット（ライトモード）

| 用途 | カラーコード |
|------|------------|
| 背景ベース | `#F7FAF9` |
| カード背景 | `#FFFFFF` |
| サーフェス | `#F0FAF7` |
| プライマリ | `#0F6E56` |
| アクセント | `#9FE1CB` |
| テキスト主 | `#0D2620` |
| テキスト副 | `#6AADA0` |
| 警告 | `#E05C5C` |

---

## 行動規範

- **コード生成前に必ずアーキテクチャ原則を確認すること**
- 既存ファイルを編集する前に、依存関係を把握してから変更すること
- 破壊的な変更（DB スキーマ変更、public API 変更）は変更内容を日本語で説明してから実施すること
- Room のスキーマ変更は必ず `Migration` を追加すること。`fallbackToDestructiveMigration` は使用禁止
- テストなしでの機能追加を行わないこと

---

## 言語

- コード中のコメントは **日本語** で書く
- コミットメッセージは **日本語** で書く（例: `feat: サブスク追加画面を実装`）
- AGENTS.md および設計ドキュメントは日本語で記述する
- 変数名・関数名・クラス名は **英語** にする