# SubKiruApplication 手動DI セットアップタスク（Codex向け）

## 前提
- パッケージ: `com.subkiru.subkiru`
- AGENTS.md の手動DI方針に従うこと
- Hilt は AGP 9.x 互換性問題により使用しない
- `SubKiruApplication` でシングルトンを生成・保持する
- ViewModel は `ViewModelProvider.Factory` 経由で依存を受け取る
- テストは不要（Application クラスのDI配線のみ。ロジックなし）

## 既存コードの参照

### Database / DAO
- `app/src/main/java/com/subkiru/subkiru/core/data/db/SubKiruDatabase.kt` — `getInstance(context)` でシングルトン取得

### RepositoryImpl
- `app/src/main/java/com/subkiru/subkiru/core/data/repository/SubscriptionRepositoryImpl.kt` — `SubscriptionRepositoryImpl(dao, clock)` — Clock はデフォルト引数あり
- `app/src/main/java/com/subkiru/subkiru/core/data/repository/CategoryRepositoryImpl.kt` — `CategoryRepositoryImpl(dao)`
- `app/src/main/java/com/subkiru/subkiru/core/data/repository/ServiceTemplateRepositoryImpl.kt` — `ServiceTemplateRepositoryImpl(dao)`

### UseCase
- `app/src/main/java/com/subkiru/subkiru/core/domain/usecase/GetSubscriptionsUseCase.kt`
- `app/src/main/java/com/subkiru/subkiru/core/domain/usecase/AddSubscriptionUseCase.kt`
- `app/src/main/java/com/subkiru/subkiru/core/domain/usecase/DeleteSubscriptionUseCase.kt`
- `app/src/main/java/com/subkiru/subkiru/core/domain/usecase/CalculateMonthlyTotalUseCase.kt`
- `app/src/main/java/com/subkiru/subkiru/core/domain/usecase/GetUpcomingBillingsUseCase.kt`

### 既存ファイル（変更対象）
- `app/src/main/AndroidManifest.xml` — `android:name` 属性の追加が必要
- `app/src/main/java/com/subkiru/subkiru/MainActivity.kt` — 現在はテンプレートのまま（変更不要）

---

## タスク1: SubKiruApplication の作成

**ファイル**: `app/src/main/java/com/subkiru/subkiru/SubKiruApplication.kt`

```kotlin
package com.subkiru.subkiru

import android.app.Application
import com.subkiru.subkiru.core.data.db.SubKiruDatabase
import com.subkiru.subkiru.core.data.repository.CategoryRepositoryImpl
import com.subkiru.subkiru.core.data.repository.ServiceTemplateRepositoryImpl
import com.subkiru.subkiru.core.data.repository.SubscriptionRepositoryImpl
import com.subkiru.subkiru.core.domain.repository.CategoryRepository
import com.subkiru.subkiru.core.domain.repository.ServiceTemplateRepository
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import com.subkiru.subkiru.core.domain.usecase.AddSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.CalculateMonthlyTotalUseCase
import com.subkiru.subkiru.core.domain.usecase.DeleteSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import com.subkiru.subkiru.core.domain.usecase.GetUpcomingBillingsUseCase
import java.time.Clock

class SubKiruApplication : Application() {

    // Clock（テスト時にフェイクへ差し替え可能にするため一元管理）
    val clock: Clock = Clock.systemDefaultZone()

    // Database
    val database by lazy { SubKiruDatabase.getInstance(this) }

    // Repository
    val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepositoryImpl(database.subscriptionDao(), clock)
    }
    val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(database.categoryDao())
    }
    val serviceTemplateRepository: ServiceTemplateRepository by lazy {
        ServiceTemplateRepositoryImpl(database.serviceTemplateDao())
    }

    // UseCase
    val getSubscriptionsUseCase by lazy { GetSubscriptionsUseCase(subscriptionRepository) }
    val addSubscriptionUseCase by lazy { AddSubscriptionUseCase(subscriptionRepository) }
    val deleteSubscriptionUseCase by lazy { DeleteSubscriptionUseCase(subscriptionRepository) }
    val calculateMonthlyTotalUseCase by lazy { CalculateMonthlyTotalUseCase(subscriptionRepository) }
    val getUpcomingBillingsUseCase by lazy { GetUpcomingBillingsUseCase(subscriptionRepository, clock) }
}
```

**設計方針**:
- 全て `by lazy` で遅延初期化する（アプリ起動時間への影響を最小化）
- Repository は Domain の interface 型で公開する（`SubscriptionRepository`、`SubscriptionRepositoryImpl` ではない）
- UseCase は ViewModel から直接参照するため `val` で公開する
- Clock は SubKiruApplication で生成し、必要なクラスに注入する（テスト時にフェイクへ差し替え可能にするため）

---

## タスク2: AndroidManifest.xml の変更

**ファイル**: `app/src/main/AndroidManifest.xml`

`<application>` タグに `android:name` 属性を追加する:

```xml
<application
    android:name=".SubKiruApplication"
    android:allowBackup="true"
    ...
```

**変更箇所は1行のみ**: `android:name=".SubKiruApplication"` を追加するだけ。他の属性は一切変更しない。

---

## スコープ外
- PaymentHistory 関連（Repository / UseCase）は未実装のため本タスクの対象外。配線しないこと。

---

## 検証
- `./gradlew.bat assembleDebug` が成功すること
