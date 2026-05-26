# Domain層レビュー指摘事項（Codex向け）

## 対象ファイル
- `app/src/main/java/com/subkiru/subkiru/core/domain/usecase/GetUpcomingBillingsUseCase.kt`
- `app/src/test/java/com/subkiru/subkiru/core/domain/usecase/GetUpcomingBillingsUseCaseTest.kt`

---

## W-1: テストヘルパーの `subscription()` が各テストクラスで重複定義

**判断: MVP段階では対応不要。テストが増えた段階で検討。**

### 現状
5つのテストファイル全てにほぼ同一の `subscription()` ヘルパーが存在する。

### 将来の修正案
`app/src/test/java/com/subkiru/subkiru/core/domain/TestFixtures.kt` にファクトリ関数を集約する。

---

## W-2: `GetUpcomingBillingsUseCase` が `LocalDate.now()` に直接依存

**判断: MVP段階では許容。通知やカレンダー機能と連携する際に対応する。**

### 現状

`GetUpcomingBillingsUseCase.kt:14` で `LocalDate.now()` を直接呼んでいる。
テスト側も `LocalDate.now()` で期待値を組んでいるため現時点では壊れないが、
時刻境界のエッジケースでflakyテストになるリスクがある。

### 修正方法

`Clock` をコンストラクタ注入し、`LocalDate.now(clock)` で取得する。

**実装側**:
```kotlin
// GetUpcomingBillingsUseCase.kt
import java.time.Clock

class GetUpcomingBillingsUseCase(
    private val repository: SubscriptionRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    operator fun invoke(withinDays: Int): Flow<List<Subscription>> {
        return repository.observeActiveSubscriptions().map { subscriptions ->
            val today = LocalDate.now(clock)
            val deadline = today.plusDays(withinDays.toLong())
            subscriptions.filter { it.nextBillingDate in today..deadline }
        }
    }
}
```

**テスト側**:
```kotlin
// GetUpcomingBillingsUseCaseTest.kt
import java.time.Clock
import java.time.ZoneId

class GetUpcomingBillingsUseCaseTest {
    private val repository = mockk<SubscriptionRepository>()
    private val fixedDate = LocalDate.of(2026, 5, 21)
    private val clock = Clock.fixed(
        fixedDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault(),
    )
    private val useCase = GetUpcomingBillingsUseCase(repository, clock)

    @Test
    fun 指定日数以内のサブスクのみ返す() = runTest {
        val included = subscription(id = 1L, nextBillingDate = fixedDate.plusDays(3))
        val excluded = subscription(id = 2L, nextBillingDate = fixedDate.plusDays(8))
        every { repository.observeActiveSubscriptions() } returns flowOf(listOf(included, excluded))

        useCase(withinDays = 7).test {
            assertEquals(listOf(included), awaitItem())
            awaitComplete()
        }
    }

    // 他のテストも LocalDate.now() → fixedDate に置き換える
}
```
