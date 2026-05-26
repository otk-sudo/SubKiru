# Domain層実装タスク（Codex向け）

## 前提
- パッケージ: `com.subkiru.subkiru`
- AGENTS.md の設計規約に従うこと
- TDD: テストを先に書いてから実装すること
- **Domain層は誰にも依存しない**（Room, Android SDK への依存禁止）
- UseCase のエントリポイントは `operator fun invoke()`
- Flow を返す関数は `suspend` をつけない
- 一回限りの書き込みは `suspend fun`
- バリデーションは UseCase に書く
- テストは JUnit 5 + MockK（純粋なユニットテスト。androidTest ではない）
- テスト配置先: `app/src/test/java/com/subkiru/subkiru/core/domain/...`

## 既存コードの参照
- `app/src/main/java/com/subkiru/subkiru/core/domain/model/BillingIntervalUnit.kt` — 既に存在する enum
- `app/src/main/java/com/subkiru/subkiru/core/data/db/entity/SubscriptionEntity.kt` — Entity のフィールド一覧（Domain Model との対応確認用）
- `app/src/main/java/com/subkiru/subkiru/core/data/db/entity/CategoryEntity.kt`
- `app/src/main/java/com/subkiru/subkiru/core/data/db/entity/ServiceTemplateEntity.kt`
- `app/src/main/java/com/subkiru/subkiru/core/data/db/entity/PaymentHistoryEntity.kt`

---

## タスク1: Domain Model

### 1-1. `BillingInterval.kt`（新規）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/model/BillingInterval.kt`

AGENTS.md: `BillingInterval.kt ← unit + count の組み合わせ`

```kotlin
package com.subkiru.subkiru.core.domain.model

data class BillingInterval(
    val unit: BillingIntervalUnit,
    val count: Int,
)
```

### 1-2. `Subscription.kt`（新規）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/model/Subscription.kt`

Entity の全フィールドに対応するが、以下を変換:
- `billingIntervalUnit` + `billingIntervalCount` → `BillingInterval` に統合
- `startDateEpoch` / `nextBillingDateEpoch` → `LocalDate` 型
- `createdAt` / `updatedAt` → `Instant` 型
- `amountMinor` + `currencyCode` はそのまま保持（将来 Money クラスに昇格可能だが現時点では不要）

```kotlin
package com.subkiru.subkiru.core.domain.model

import java.time.Instant
import java.time.LocalDate

data class Subscription(
    val id: Long,
    val name: String,
    val amountMinor: Long,
    val currencyCode: String,
    val billingInterval: BillingInterval,
    val startDate: LocalDate,
    val nextBillingDate: LocalDate,
    val categoryId: Long?,
    val templateId: Long?,
    val logoUri: String?,
    val memo: String?,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

**注意**: `java.time` は Android SDK ではなく Java 標準ライブラリ。`minSdk = 26` なので利用可能。Domain層のAndroid依存にはならない。

### 1-3. `Category.kt`（新規）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/model/Category.kt`

```kotlin
package com.subkiru.subkiru.core.domain.model

data class Category(
    val id: Long,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val sortOrder: Int,
)
```

### 1-4. `ServiceTemplate.kt`（新規）

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/model/ServiceTemplate.kt`

```kotlin
package com.subkiru.subkiru.core.domain.model

data class ServiceTemplate(
    val id: Long,
    val name: String,
    val defaultAmountMinor: Long,
    val defaultCurrencyCode: String,
    val defaultInterval: BillingInterval,
    val logoResourceName: String,
    val categoryId: Long,
    val searchKeywords: String,
)
```

---

## タスク2: Repository Interface

### 2-1. `SubscriptionRepository.kt`

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/repository/SubscriptionRepository.kt`

```kotlin
package com.subkiru.subkiru.core.domain.repository

import com.subkiru.subkiru.core.domain.model.Subscription
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    fun observeActiveSubscriptions(): Flow<List<Subscription>>
    suspend fun getSubscriptionById(id: Long): Subscription?
    suspend fun addSubscription(subscription: Subscription): Long
    suspend fun updateSubscription(subscription: Subscription)
    suspend fun deactivateSubscription(id: Long)
    suspend fun deleteSubscription(subscription: Subscription)
}
```

**注意**:
- `deactivateSubscription` は `updatedAt` を引数に取らない。Repository実装側で現在時刻を設定する。Domain層は時刻生成の責務を持たない
- 全て Domain Model（`Subscription`）で入出力する。Entity は知らない

### 2-2. `CategoryRepository.kt`

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/repository/CategoryRepository.kt`

```kotlin
package com.subkiru.subkiru.core.domain.repository

import com.subkiru.subkiru.core.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAllCategories(): Flow<List<Category>>
    suspend fun getCategoryById(id: Long): Category?
    suspend fun addCategory(category: Category): Long
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(category: Category)
}
```

### 2-3. `ServiceTemplateRepository.kt`

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/repository/ServiceTemplateRepository.kt`

```kotlin
package com.subkiru.subkiru.core.domain.repository

import com.subkiru.subkiru.core.domain.model.ServiceTemplate
import kotlinx.coroutines.flow.Flow

interface ServiceTemplateRepository {
    fun observeAllTemplates(): Flow<List<ServiceTemplate>>
    suspend fun getTemplateById(id: Long): ServiceTemplate?
    fun searchTemplates(query: String): Flow<List<ServiceTemplate>>
}
```

---

## タスク3: UseCase

全 UseCase はコンストラクタで Repository を受け取り、`operator fun invoke()` をエントリポイントとする。

### 3-1. `GetSubscriptionsUseCase.kt`

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/usecase/GetSubscriptionsUseCase.kt`

```kotlin
class GetSubscriptionsUseCase(
    private val repository: SubscriptionRepository,
) {
    operator fun invoke(): Flow<List<Subscription>> {
        return repository.observeActiveSubscriptions()
    }
}
```

### 3-2. `AddSubscriptionUseCase.kt`

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/usecase/AddSubscriptionUseCase.kt`

バリデーション:
- `name` が空文字でないこと
- `amountMinor` が 0 以上であること
- `billingInterval.count` が 1 以上であること
- `startDate` が `nextBillingDate` 以前であること

戻り値は `sealed interface` で成功/失敗を返す:

```kotlin
class AddSubscriptionUseCase(
    private val repository: SubscriptionRepository,
) {
    sealed interface Result {
        data class Success(val id: Long) : Result
        data class ValidationError(val errors: List<Error>) : Result
    }

    enum class Error {
        EMPTY_NAME,
        NEGATIVE_AMOUNT,
        INVALID_INTERVAL_COUNT,
        START_DATE_AFTER_NEXT_BILLING_DATE,
    }

    suspend operator fun invoke(subscription: Subscription): Result {
        val errors = validate(subscription)
        if (errors.isNotEmpty()) {
            return Result.ValidationError(errors)
        }
        val id = repository.addSubscription(subscription)
        return Result.Success(id)
    }

    private fun validate(subscription: Subscription): List<Error> {
        // バリデーションロジック
    }
}
```

### 3-3. `DeleteSubscriptionUseCase.kt`

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/usecase/DeleteSubscriptionUseCase.kt`

論理削除（`deactivateSubscription`）を呼ぶ:

```kotlin
class DeleteSubscriptionUseCase(
    private val repository: SubscriptionRepository,
) {
    suspend operator fun invoke(id: Long) {
        repository.deactivateSubscription(id)
    }
}
```

### 3-4. `CalculateMonthlyTotalUseCase.kt`

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/usecase/CalculateMonthlyTotalUseCase.kt`

全アクティブサブスクの月額換算合計を計算する:

```kotlin
class CalculateMonthlyTotalUseCase(
    private val repository: SubscriptionRepository,
) {
    operator fun invoke(): Flow<Long> {
        return repository.observeActiveSubscriptions().map { subscriptions ->
            subscriptions.sumOf { toMonthlyAmount(it) }
        }
    }

    private fun toMonthlyAmount(subscription: Subscription): Long {
        val interval = subscription.billingInterval
        return when (interval.unit) {
            BillingIntervalUnit.DAILY -> subscription.amountMinor * DAYS_PER_MONTH / interval.count
            BillingIntervalUnit.WEEKLY -> subscription.amountMinor * WEEKS_PER_MONTH / interval.count
            BillingIntervalUnit.MONTHLY -> subscription.amountMinor / interval.count
            BillingIntervalUnit.YEARLY -> subscription.amountMinor / (MONTHS_PER_YEAR * interval.count)
        }
    }

    companion object {
        private const val DAYS_PER_MONTH = 30L
        private const val WEEKS_PER_MONTH = 4L
        private const val MONTHS_PER_YEAR = 12L
    }
}
```

**注意**: 通貨が混在する場合は考慮しない（MVP。将来的に通貨別合計に拡張可能）

### 3-5. `GetUpcomingBillingsUseCase.kt`

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/domain/usecase/GetUpcomingBillingsUseCase.kt`

指定日数以内に次回請求日が来るサブスクを返す:

```kotlin
class GetUpcomingBillingsUseCase(
    private val repository: SubscriptionRepository,
) {
    operator fun invoke(withinDays: Int): Flow<List<Subscription>> {
        return repository.observeActiveSubscriptions().map { subscriptions ->
            val today = LocalDate.now()
            val deadline = today.plusDays(withinDays.toLong())
            subscriptions.filter { it.nextBillingDate in today..deadline }
        }
    }
}
```

---

## タスク4: UseCase テスト

テスト配置先: `app/src/test/java/com/subkiru/subkiru/core/domain/usecase/`

MockK で Repository をモック。`Flow` のテストは Turbine を使用。

### 4-1. `GetSubscriptionsUseCaseTest.kt`

1. `アクティブなサブスク一覧を取得できる`
2. `サブスクが空の場合は空リストを返す`

### 4-2. `AddSubscriptionUseCaseTest.kt`

1. `正常なサブスクを追加するとSuccessを返す`
2. `名前が空文字の場合はEMPTY_NAMEエラーを返す`
3. `金額が負の場合はNEGATIVE_AMOUNTエラーを返す`
4. `請求間隔countが0以下の場合はINVALID_INTERVAL_COUNTエラーを返す`
5. `開始日が次回請求日より後の場合はSTART_DATE_AFTER_NEXT_BILLING_DATEエラーを返す`
6. `複数のバリデーションエラーがある場合は全てのエラーを返す`
7. `バリデーションエラーの場合はrepositoryのaddを呼ばない`

### 4-3. `DeleteSubscriptionUseCaseTest.kt`

1. `deactivateSubscriptionが呼ばれる`

### 4-4. `CalculateMonthlyTotalUseCaseTest.kt`

1. `月額サブスクの合計が正しく計算される`
2. `年額サブスクが月額に換算される`
3. `週額サブスクが月額に換算される`
4. `日額サブスクが月額に換算される`
5. `複数間隔のサブスクが混在する場合の合計が正しい`
6. `サブスクが空の場合は0を返す`
7. `3ヶ月ごとのサブスクが正しく月額換算される`

### 4-5. `GetUpcomingBillingsUseCaseTest.kt`

1. `指定日数以内のサブスクのみ返す`
2. `期限外のサブスクは除外される`
3. `当日が次回請求日のサブスクは含まれる`
4. `サブスクが空の場合は空リストを返す`

---

## 検証
- `./gradlew.bat test` が成功すること（ユニットテスト）
- `./gradlew.bat assembleDebug` が成功すること
