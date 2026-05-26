# Data層 RepositoryImpl 実装タスク（Codex向け）

## 前提
- パッケージ: `com.subkiru.subkiru`
- AGENTS.md の設計規約に従うこと
- TDD: テストを先に書いてから実装すること
- RepositoryImpl は Domain の Repository interface を実装する
- RepositoryImpl のみが DAO を直接呼べる
- Entity ↔ Domain Model の変換は RepositoryImpl 内で行う
- `Flow` を返す関数は `suspend` をつけない
- `runCatching` 禁止（CancellationException 問題。Firebase 操作時のみ関連するが習慣として避ける）
- テストは androidTest（Room のインメモリDB使用）ではなく、DAO を MockK でモックした純粋なユニットテスト
- テスト配置先: `app/src/test/java/com/subkiru/subkiru/core/data/repository/`

## 既存コードの参照

### Domain Model（変換先）
- `app/src/main/java/com/subkiru/subkiru/core/domain/model/Subscription.kt`
- `app/src/main/java/com/subkiru/subkiru/core/domain/model/Category.kt`
- `app/src/main/java/com/subkiru/subkiru/core/domain/model/ServiceTemplate.kt`
- `app/src/main/java/com/subkiru/subkiru/core/domain/model/BillingInterval.kt`
- `app/src/main/java/com/subkiru/subkiru/core/domain/model/BillingIntervalUnit.kt`

### Repository Interface（実装対象）
- `app/src/main/java/com/subkiru/subkiru/core/domain/repository/SubscriptionRepository.kt`
- `app/src/main/java/com/subkiru/subkiru/core/domain/repository/CategoryRepository.kt`
- `app/src/main/java/com/subkiru/subkiru/core/domain/repository/ServiceTemplateRepository.kt`

### Entity（変換元）
- `app/src/main/java/com/subkiru/subkiru/core/data/db/entity/SubscriptionEntity.kt`
- `app/src/main/java/com/subkiru/subkiru/core/data/db/entity/CategoryEntity.kt`
- `app/src/main/java/com/subkiru/subkiru/core/data/db/entity/ServiceTemplateEntity.kt`

### DAO（依存先）
- `app/src/main/java/com/subkiru/subkiru/core/data/db/dao/SubscriptionDao.kt`
- `app/src/main/java/com/subkiru/subkiru/core/data/db/dao/CategoryDao.kt`
- `app/src/main/java/com/subkiru/subkiru/core/data/db/dao/ServiceTemplateDao.kt`

---

## タスク1: SubscriptionRepositoryImpl

### 1-1. 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/data/repository/SubscriptionRepositoryImpl.kt`

```kotlin
package com.subkiru.subkiru.core.data.repository

import com.subkiru.subkiru.core.data.db.dao.SubscriptionDao
import com.subkiru.subkiru.core.data.db.entity.SubscriptionEntity
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class SubscriptionRepositoryImpl(
    private val dao: SubscriptionDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) : SubscriptionRepository {

    override fun observeActiveSubscriptions(): Flow<List<Subscription>> {
        return dao.observeActiveSubscriptions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSubscriptionById(id: Long): Subscription? {
        return dao.getSubscriptionById(id)?.toDomain()
    }

    override suspend fun addSubscription(subscription: Subscription): Long {
        return dao.addSubscription(subscription.toEntity())
    }

    override suspend fun updateSubscription(subscription: Subscription) {
        dao.updateSubscription(subscription.toEntity())
    }

    override suspend fun deactivateSubscription(id: Long) {
        dao.deactivateSubscription(id, updatedAt = Instant.now(clock).toEpochMilli())
    }

    override suspend fun deleteSubscription(subscription: Subscription) {
        dao.deleteSubscription(subscription.toEntity())
    }
}
```

### 1-2. Entity ↔ Domain 変換

変換は RepositoryImpl のファイル内に private 拡張関数として定義する。
別ファイル（Mapper クラス等）に分離しない。

```kotlin
// Entity → Domain
private fun SubscriptionEntity.toDomain(): Subscription {
    return Subscription(
        id = id,
        name = name,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        billingInterval = BillingInterval(
            unit = billingIntervalUnit,
            count = billingIntervalCount,
        ),
        startDate = LocalDate.ofEpochDay(startDateEpoch),
        nextBillingDate = LocalDate.ofEpochDay(nextBillingDateEpoch),
        categoryId = categoryId,
        templateId = templateId,
        logoUri = logoUri,
        memo = memo,
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )
}

// Domain → Entity
private fun Subscription.toEntity(): SubscriptionEntity {
    return SubscriptionEntity(
        id = id,
        name = name,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        billingIntervalUnit = billingInterval.unit,
        billingIntervalCount = billingInterval.count,
        startDateEpoch = startDate.toEpochDay(),
        nextBillingDateEpoch = nextBillingDate.toEpochDay(),
        categoryId = categoryId,
        templateId = templateId,
        logoUri = logoUri,
        memo = memo,
        isActive = isActive,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )
}
```

**注意点**:
- `deactivateSubscription` は DAO の `updatedAt` を `Instant.now(clock).toEpochMilli()` で生成する
- `Clock` をコンストラクタ注入する（テスト時に固定時刻を注入するため）
- `deleteSubscription` は Domain Model → Entity に変換してから DAO の `@Delete` に渡す

### 1-3. テスト

**ファイル**: `app/src/test/java/com/subkiru/subkiru/core/data/repository/SubscriptionRepositoryImplTest.kt`

DAO を MockK でモック。JUnit 5 + Turbine 使用。

**テストケース**:
1. `observeActiveSubscriptionsでEntityがDomainModelに変換される`
   - DAO が `List<SubscriptionEntity>` を返す → `List<Subscription>` に変換されていることを検証
   - 特に `startDateEpoch` → `LocalDate`、`createdAt` → `Instant` の変換を検証
   - `billingIntervalUnit` + `billingIntervalCount` → `BillingInterval` の変換を検証
2. `getSubscriptionByIdでnullの場合はnullを返す`
3. `getSubscriptionByIdでEntityがDomainModelに変換される`
4. `addSubscriptionでDomainModelがEntityに変換されてDAOに渡される`
   - `coVerify` で DAO に渡された Entity の各フィールドを検証
   - 特に `LocalDate` → `epochDay`、`Instant` → `epochMilli` の変換を検証
5. `updateSubscriptionでDomainModelがEntityに変換されてDAOに渡される`
6. `deactivateSubscriptionで現在時刻がupdatedAtとしてDAOに渡される`
   - `Clock.fixed()` で固定時刻を注入し、`updatedAt` の値を検証
7. `deleteSubscriptionでDomainModelがEntityに変換されてDAOに渡される`

---

## タスク2: CategoryRepositoryImpl

### 2-1. 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/data/repository/CategoryRepositoryImpl.kt`

```kotlin
class CategoryRepositoryImpl(
    private val dao: CategoryDao,
) : CategoryRepository {

    override fun observeAllCategories(): Flow<List<Category>> {
        return dao.observeAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCategoryById(id: Long): Category? {
        return dao.getCategoryById(id)?.toDomain()
    }

    override suspend fun addCategory(category: Category): Long {
        return dao.addCategory(category.toEntity())
    }

    override suspend fun updateCategory(category: Category) {
        dao.updateCategory(category.toEntity())
    }

    override suspend fun deleteCategory(category: Category) {
        dao.deleteCategory(category.toEntity())
    }
}
```

### 2-2. Entity ↔ Domain 変換

```kotlin
private fun CategoryEntity.toDomain(): Category {
    return Category(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        sortOrder = sortOrder,
    )
}

private fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        sortOrder = sortOrder,
    )
}
```

### 2-3. テスト

**ファイル**: `app/src/test/java/com/subkiru/subkiru/core/data/repository/CategoryRepositoryImplTest.kt`

**テストケース**:
1. `observeAllCategoriesでEntityがDomainModelに変換される`
2. `getCategoryByIdでnullの場合はnullを返す`
3. `getCategoryByIdでEntityがDomainModelに変換される`
4. `addCategoryでDomainModelがEntityに変換されてDAOに渡される`
5. `updateCategoryでDomainModelがEntityに変換されてDAOに渡される`
6. `deleteCategoryでDomainModelがEntityに変換されてDAOに渡される`

---

## タスク3: ServiceTemplateRepositoryImpl

### 3-1. 実装

**ファイル**: `app/src/main/java/com/subkiru/subkiru/core/data/repository/ServiceTemplateRepositoryImpl.kt`

```kotlin
class ServiceTemplateRepositoryImpl(
    private val dao: ServiceTemplateDao,
) : ServiceTemplateRepository {

    override fun observeAllTemplates(): Flow<List<ServiceTemplate>> {
        return dao.observeAllTemplates().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTemplateById(id: Long): ServiceTemplate? {
        return dao.getTemplateById(id)?.toDomain()
    }

    override fun searchTemplates(query: String): Flow<List<ServiceTemplate>> {
        return dao.searchTemplates(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
```

### 3-2. Entity ↔ Domain 変換

```kotlin
private fun ServiceTemplateEntity.toDomain(): ServiceTemplate {
    return ServiceTemplate(
        id = id,
        name = name,
        defaultAmountMinor = defaultAmountMinor,
        defaultCurrencyCode = defaultCurrencyCode,
        defaultInterval = BillingInterval(
            unit = defaultIntervalUnit,
            count = defaultIntervalCount,
        ),
        logoResourceName = logoResourceName,
        categoryId = categoryId,
        searchKeywords = searchKeywords,
    )
}
```

**注意**: ServiceTemplateRepository は読み取り専用（add/update/delete なし）のため `toEntity()` は不要。

### 3-3. テスト

**ファイル**: `app/src/test/java/com/subkiru/subkiru/core/data/repository/ServiceTemplateRepositoryImplTest.kt`

**テストケース**:
1. `observeAllTemplatesでEntityがDomainModelに変換される`
   - 特に `defaultIntervalUnit` + `defaultIntervalCount` → `BillingInterval` の変換を検証
2. `getTemplateByIdでnullの場合はnullを返す`
3. `getTemplateByIdでEntityがDomainModelに変換される`
4. `searchTemplatesでEntityがDomainModelに変換される`

---

## 検証
- `./gradlew.bat test` が成功すること（ユニットテスト）
- `./gradlew.bat assembleDebug` が成功すること
---

## レビュー指摘反映

### W-1: `addSubscription` の `id = 0L` 前提

`SubscriptionRepositoryImpl.addSubscription()` は `Subscription.toEntity()` で `id` をそのまま `SubscriptionEntity.id` にマッピングする。
新規追加時は `Subscription.id` を `0L` とし、`SubscriptionEntity.UNSAVED_ID` と一致させること。

- 新規追加時の `Subscription.id` は `0L` とする（`SubscriptionEntity.UNSAVED_ID` と一致）
- 既存 `Subscription`（`id != 0`）を `addSubscription` に渡さないこと（UseCase 側で制御）

### W-2: `deleteSubscription` の削除条件

現状の `SubscriptionRepository.deleteSubscription(subscription: Subscription)` は Domain Model 全体を引数に取る設計であり、RepositoryImpl では Domain Model を Entity に変換して DAO の `@Delete` に渡す。
DAO の `@Delete` は Entity の全フィールドに依存するため、変換後フィールドがずれると 0 行削除になる可能性がある。

MVP 段階では既存 interface を維持する。将来的には `id` だけで削除する `@Query` ベースの DAO/API へ変更することを検討する。
