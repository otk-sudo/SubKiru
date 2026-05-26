# HomeViewModel catch 配置位置修正②（Codex向け）

## 対象ファイル
- `app/src/main/java/com/subkiru/subkiru/feature/home/HomeViewModel.kt`

---

## 背景

前回の修正で `catch` を collector 側に移動したが、以下3点が設計から逸脱している。

| # | 問題 | 内容 |
|---|------|------|
| 1 | `CoroutineExceptionHandler` + カスタム `CoroutineScope` | 設計にない追加。collector 側の `catch` とエラー処理が二重になっている |
| 2 | `@Suppress("DEPRECATION")` | 抑制すべき deprecation 警告が存在しない。不要 |
| 3 | 不要な中間変数 `subscriptionsWithErrorHandling` | 設計ではチェーンで直接記述する |

---

## 修正内容

`init` ブロックを以下に差し替える。それ以外のコードは変更しない。

**変更前**:
```kotlin
init {
    // 同一 DAO Flow を shareIn で共有し、DB クエリの重複実行を防止する。
    val loadErrorHandler = CoroutineExceptionHandler { _, _ ->
        _uiState.update {
            it.copy(error = ERROR_MESSAGE_LOAD, isLoading = false)
        }
    }
    val sharedSubscriptions = getSubscriptionsUseCase()
        .shareIn(
            scope = CoroutineScope(viewModelScope.coroutineContext + loadErrorHandler),
            started = SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS),
            replay = 1,
        )

    viewModelScope.launch {
        @Suppress("DEPRECATION")
        val subscriptionsWithErrorHandling = sharedSubscriptions
            .catch {
                _uiState.update {
                    it.copy(error = ERROR_MESSAGE_LOAD, isLoading = false)
                }
            }

        subscriptionsWithErrorHandling.collect { subscriptions ->
            _uiState.update {
                it.copy(subscriptions = subscriptions, isLoading = false)
            }
        }
    }

    viewModelScope.launch {
        sharedSubscriptions
            .map { subscriptions -> subscriptions.sumOf { toMonthlyAmount(it) } }
            .catch {
                // 月額合計の計算失敗時は 0L のまま維持する。
            }
            .collect { total ->
                _uiState.update { it.copy(monthlyTotal = total) }
            }
    }
}
```

**変更後**:
```kotlin
init {
    // 同一 DAO Flow を shareIn で共有し、DB クエリの重複実行を防止する
    val sharedSubscriptions = getSubscriptionsUseCase()
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS),
            replay = 1,
        )

    viewModelScope.launch {
        sharedSubscriptions
            .catch {
                _uiState.update {
                    it.copy(error = ERROR_MESSAGE_LOAD, isLoading = false)
                }
            }
            .collect { subscriptions ->
                _uiState.update {
                    it.copy(subscriptions = subscriptions, isLoading = false)
                }
            }
    }

    viewModelScope.launch {
        sharedSubscriptions
            .map { subscriptions -> subscriptions.sumOf { toMonthlyAmount(it) } }
            .catch {
                // 月額合計の計算失敗時は 0L のまま維持する。
            }
            .collect { total ->
                _uiState.update { it.copy(monthlyTotal = total) }
            }
    }
}
```

### 変更点まとめ

| # | 変更 |
|---|------|
| 1 | `CoroutineExceptionHandler` と `CoroutineScope(...)` を削除し、`viewModelScope` を直接使用 |
| 2 | `@Suppress("DEPRECATION")` を削除 |
| 3 | 中間変数 `subscriptionsWithErrorHandling` を削除し、チェーンで直接記述 |

### 不要になる import の削除

以下2つの import を削除する:
```kotlin
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
```

---

## 変更しないもの
- `init` ブロック以外のコード（`onDeleteSubscription`, `toMonthlyAmount`, `companion object` 等）
- 他ファイル

---

## 検証
- `./gradlew.bat test` で HomeViewModelTest の全7テストが成功すること
