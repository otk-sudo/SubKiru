# SubKiru 設計図（DESIGN.md）

最終更新: 2026-10-10 / 対象: DB スキーマ version 3

データモデル・画面遷移・層構成を図で示します。記載内容は現在の実装に基づきます。
機能の仕様は [SPEC.md](SPEC.md)、開発ルールは [AGENTS.md](AGENTS.md) を参照してください。

---

## 1. データモデル

### 1.1 Room（`subkiru.db`）

```mermaid
erDiagram
    categories |o--o{ subscriptions : "category_id (SET NULL)"
    service_templates |o--o{ subscriptions : "template_id (SET NULL)"
    categories ||--o{ service_templates : "category_id (CASCADE)"
    subscriptions ||--o{ payment_history : "subscription_id (CASCADE)"

    subscriptions {
        INTEGER id PK "自動採番"
        TEXT name
        INTEGER amount_minor "最小通貨単位"
        TEXT currency_code "既定 JPY"
        TEXT billing_interval_unit "DAILY/WEEKLY/MONTHLY/YEARLY"
        INTEGER billing_interval_count "既定 1"
        INTEGER start_date_epoch "epochDay"
        INTEGER next_billing_date_epoch "epochDay"
        INTEGER category_id FK "NULL可"
        INTEGER template_id FK "NULL可"
        TEXT logo_uri "NULL可・未使用"
        TEXT memo "NULL可"
        INTEGER is_active "既定 1。0 で論理削除"
        INTEGER created_at "UnixMs"
        INTEGER updated_at "UnixMs"
        INTEGER sort_order "既定 0。カスタム並べ替え用"
    }

    categories {
        INTEGER id PK "自動採番"
        TEXT name
        TEXT icon_name
        TEXT color_hex
        INTEGER sort_order
    }

    service_templates {
        INTEGER id PK "自動採番"
        TEXT name
        INTEGER default_amount_minor
        TEXT default_currency_code "既定 JPY"
        TEXT default_interval_unit "既定 MONTHLY"
        INTEGER default_interval_count "既定 1"
        TEXT logo_resource_name
        INTEGER category_id FK
        TEXT search_keywords "カンマ区切り"
        TEXT domain "既定 空文字・未使用"
    }

    payment_history {
        INTEGER id PK "自動採番"
        INTEGER subscription_id FK
        INTEGER billing_date_epoch "epochDay"
        INTEGER amount_minor
        TEXT currency_code
        INTEGER is_paid
        INTEGER recorded_at "UnixMs"
    }
```

- 関係線のラベルは「外部キー（親を削除したときの子の挙動）」です。
- `payment_history` は定義のみで、読み書きする処理はありません。
- インデックス: `subscriptions`（`next_billing_date_epoch` / `is_active` / `category_id` / `template_id`）、`service_templates`（`category_id`）、`payment_history`（`subscription_id` / `billing_date_epoch`）。

**マイグレーション履歴**

| version | 変更 |
|---------|------|
| 1 → 2 | `service_templates` に `domain` を追加 |
| 2 → 3 | `subscriptions` に `sort_order` を追加し、既存行に順序を割り当て |

### 1.2 DataStore（Preferences）

| ファイル | キー | 型 | 内容 |
|----------|------|----|------|
| `settings` | `reminder_enabled` | Boolean | 請求日リマインダーの ON/OFF（既定 false） |
| `settings` | `reminder_days_before` | Int | 通知タイミング。1〜7日前（既定 1） |
| `reminder_history` | `notified_billings` | Set&lt;String&gt; | 通知済みの請求。`{サブスクID}:{請求日のepochDay}` 形式 |

### 1.3 ドメインモデルとの対応

```mermaid
classDiagram
    class Subscription {
        Long id
        String name
        Long amountMinor
        String currencyCode
        BillingInterval billingInterval
        LocalDate startDate
        LocalDate nextBillingDate
        Long? categoryId
        Long? templateId
        String? logoUri
        String? memo
        Boolean isActive
        Instant createdAt
        Instant updatedAt
        Int sortOrder
    }
    class BillingInterval {
        BillingIntervalUnit unit
        Int count
    }
    class BillingIntervalUnit {
        <<enumeration>>
        DAILY
        WEEKLY
        MONTHLY
        YEARLY
    }
    class Category {
        Long id
        String name
        String iconName
        String colorHex
        Int sortOrder
    }
    class ServiceTemplate {
        Long id
        String name
        Long defaultAmountMinor
        String defaultCurrencyCode
        BillingInterval defaultInterval
        String logoResourceName
        Long categoryId
        String searchKeywords
        String domain
    }
    class UserSettings {
        Boolean isReminderEnabled
        Int reminderDaysBefore
    }
    class NotifiedBilling {
        Long subscriptionId
        LocalDate billingDate
    }
    class MoneyAmount {
        Long amountMinor
        String currencyCode
    }

    Subscription *-- BillingInterval
    ServiceTemplate *-- BillingInterval
    BillingInterval --> BillingIntervalUnit
    Subscription ..> Category : categoryId
    Subscription ..> ServiceTemplate : templateId
    ServiceTemplate ..> Category : categoryId
    NotifiedBilling ..> Subscription : subscriptionId
```

Entity とドメインモデルの変換は各 `RepositoryImpl` 内で行います。主な変換は次のとおりです。

| Entity（Room） | ドメインモデル |
|----------------|----------------|
| `billing_interval_unit` + `billing_interval_count` | `BillingInterval` |
| `*_date_epoch`（Long, epochDay） | `LocalDate` |
| `created_at` / `updated_at`（Long, UnixMs） | `Instant` |

---

## 2. 画面遷移

```mermaid
flowchart TD
    Launcher(["ランチャーから起動"]) --> Home
    Notification(["リマインダー通知をタップ"]) --> Home
    Widget(["ウィジェットをタップ"]) --> Home

    subgraph Tabs["ボトムナビのタブ（相互に切り替え可能）"]
        Home["ホーム<br/>home"]
        Calendar["カレンダー<br/>calendar"]
        Analytics["分析<br/>analytics"]
        Settings["設定<br/>settings"]
    end

    Home <--> Calendar
    Calendar <--> Analytics
    Analytics <--> Settings

    Home -- "右下の＋ボタン" --> Add["サブスク追加<br/>add_subscription"]
    Home -- "行をタップ" --> Edit["サブスク編集<br/>edit_subscription/{subscriptionId}"]
    Add -- "保存成功 / 戻る" --> Home
    Edit -- "保存成功 / 戻る" --> Home

    Settings -. "リマインダーを ON（Android 13 以上・未許可時）" .-> Permission{{"通知権限ダイアログ"}}
    Permission -. "許可 / 拒否" .-> Settings
```

| 画面 | ルート | ボトムナビ | ＋ボタン |
|------|--------|-----------|----------|
| ホーム | `home` | 表示 | 表示 |
| カレンダー | `calendar` | 表示 | 非表示 |
| 分析 | `analytics` | 表示 | 非表示 |
| 設定 | `settings` | 表示 | 非表示 |
| サブスク追加 | `add_subscription` | 非表示 | 非表示 |
| サブスク編集 | `edit_subscription/{subscriptionId}`（Long） | 非表示 | 非表示 |

- 起動時の画面はホームです。通知・ウィジェットからの起動もホームを表示します（特定の画面への直接遷移はありません）。
- 図ではタブ間の矢印を隣同士だけ描いていますが、4つのタブはどれからどれへも直接切り替えられます。タブ切り替えではバックスタックを積まず、各タブの状態を保持します。
- 追加と編集は同じ画面実装（`AddSubscriptionScreen` / `AddSubscriptionViewModel`）を共用します。編集ではテンプレート一覧を表示しません。

---

## 3. 層構成

依存の方向は **UI → Domain ← Data** です。`SubKiruApplication` が Repository と UseCase を生成・保持し、各 ViewModel の `factory` に渡します（手動 DI）。

```mermaid
flowchart TB
    subgraph UI["UI層"]
        direction TB
        Screens["Screen（Composable）<br/>Home / AddSubscription / Calendar / Analytics / Settings"]
        VMs["ViewModel<br/>HomeViewModel / AddSubscriptionViewModel<br/>CalendarViewModel / AnalyticsViewModel / SettingsViewModel"]
        NavGraph["SubKiruNavGraph / MainActivity"]
        Screens -- "イベント" --> VMs
        VMs -- "uiState（StateFlow）" --> Screens
    end

    subgraph Entry["UI層以外の入口"]
        direction TB
        Worker["BillingReminderWorker<br/>（WorkManager・1日1回）"]
        WidgetBox["SubKiruWidget / WidgetDataProvider<br/>（Glance）"]
    end

    subgraph Domain["Domain層（他の層に依存しない）"]
        direction TB
        UseCases["UseCase<br/>GetSubscriptions / GetSubscriptionById<br/>AddSubscription / UpdateSubscription / DeleteSubscription<br/>ReorderSubscriptions / CalculateMonthlyTotal / GetUpcomingBillings"]
        Models["Model<br/>Subscription / BillingInterval / Category<br/>ServiceTemplate / UserSettings / NotifiedBilling / MoneyAmount"]
        RepoIF["Repository（interface）<br/>Subscription / Category / ServiceTemplate<br/>Settings / ReminderHistory"]
        UseCases --> RepoIF
        UseCases --> Models
    end

    subgraph Data["Data層"]
        direction TB
        RepoImpl["RepositoryImpl<br/>Subscription / Category / ServiceTemplate<br/>Settings / ReminderHistory"]
        Dao["Room DAO<br/>SubscriptionDao / CategoryDao<br/>ServiceTemplateDao / PaymentHistoryDao"]
        Room[("subkiru.db")]
        DataStore[("DataStore<br/>settings / reminder_history")]
        Assets[/"assets<br/>categories.json / service_templates.json"/]
        RepoImpl --> Dao --> Room
        RepoImpl --> DataStore
        Assets -- "DB 初回作成時に投入" --> Room
    end

    VMs --> UseCases
    WidgetBox --> UseCases
    RepoImpl -. "実装" .-> RepoIF

    VMs -. "直接参照" .-> RepoIF
    Worker -. "直接参照" .-> RepoIF

    App["SubKiruApplication（手動 DI）"]
    App -. "生成・保持" .-> RepoImpl
    App -. "生成・保持" .-> UseCases
    App -. "factory 経由で注入" .-> VMs
```

### 3.1 各入口が使う UseCase / Repository

| 入口 | UseCase | Repository を直接参照 |
|------|---------|----------------------|
| `HomeViewModel` | GetSubscriptions / DeleteSubscription / ReorderSubscriptions | `CategoryRepository` |
| `AddSubscriptionViewModel`（追加・編集） | AddSubscription / UpdateSubscription / GetSubscriptionById | `CategoryRepository` / `ServiceTemplateRepository`（追加時のテンプレート一覧） |
| `CalendarViewModel` | GetSubscriptions | — |
| `AnalyticsViewModel` | GetSubscriptions | — |
| `SettingsViewModel` | — | `SettingsRepository` |
| `BillingReminderWorker` | — | `SettingsRepository` / `SubscriptionRepository` / `ReminderHistoryRepository` |
| `SubKiruWidget` | CalculateMonthlyTotal / GetUpcomingBillings | — |

### 3.2 原則からの逸脱

AGENTS.md は「ViewModel は UseCase のみを呼ぶ」と定めていますが、図の点線「直接参照」のとおり、次の箇所は Repository の interface を直接使っています。いずれも Domain 層の interface への依存であり、DAO や Room には触れていません。

- カテゴリ一覧の取得（`HomeViewModel` / `AddSubscriptionViewModel`）
- テンプレート一覧の取得（`AddSubscriptionViewModel`。追加時のみ）
- 設定の読み書き（`SettingsViewModel`）
- リマインダーの日次チェック（`BillingReminderWorker`）

### 3.3 データの流れ（サブスク追加の例）

```mermaid
sequenceDiagram
    actor User as ユーザー
    participant Screen as AddSubscriptionScreen
    participant VM as AddSubscriptionViewModel
    participant UC as AddSubscriptionUseCase
    participant Repo as SubscriptionRepositoryImpl
    participant Dao as SubscriptionDao
    participant Home as HomeViewModel

    Note over Screen,VM: 画面を開くとテンプレート一覧を読み込み、完了まで読み込み中表示
    User->>Screen: 入力して「保存」
    Screen->>VM: onSave()
    VM->>VM: 文字列を数値・日付に変換
    VM->>UC: invoke(subscription)
    UC->>UC: バリデーション
    alt 検証エラー
        UC-->>VM: ValidationError
        VM-->>Screen: uiState（項目ごとのエラー）
    else 成功
        UC->>Repo: addSubscription()
        Repo->>Dao: Entity に変換して INSERT
        Dao-->>Repo: id
        Repo-->>UC: id
        UC-->>VM: Success
        VM-->>Screen: savedEvent
        Screen->>Screen: ホームへ戻る
        Dao--)Home: Flow が新しい一覧を通知
    end
```

Room を唯一の情報源とし、一覧系の画面は DAO の `Flow` を購読しています。書き込みが完了すると、ホーム・カレンダー・分析は自動的に再描画されます。
