# HomeViewModel catch 配置位置修正（Codex向け）

## 対象ファイル
- `app/src/main/java/com/subkiru/subkiru/feature/home/HomeViewModel.kt`

---

## 背景

`catch` が `shareIn` の前（上流側）に配置されているが、設計では `shareIn` の後（各collector側）に配置する方針。

上流側に `catch` を置くと、エラー後にFlowが正常終了扱いとなり、`WhileSubscribed` による再購読時のDB接続復帰自動リトライが効かない。collector側に置くことで、再購読時に上流Flowが再実行され、復帰が可能になる。

---

## 修正内容

`init` ブロック内の `sharedSubscriptions` 定義と2つの `collect` を以下に差し替える。

**変更前**:
```kotlin
init {
    val sharedSubscriptions = getSubscriptionsUseCase()
        .catch {
            _uiState.update {
                it.copy(error = ERROR_MESSAGE_LOAD, isLoading = false)
            }
        }
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS),
            replay = 1,
        )

    viewModelScope.launch {
        sharedSubscriptions.collect { subscriptions ->
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

| 変更 | 内容 |
|------|------|
| `catch` を `shareIn` の前から削除 | 上流側でのエラー吸収を除去 |
| `catch` を1つ目の collector に追加 | サブスク一覧取得エラー時に `ERROR_MESSAGE_LOAD` を設定 |
| 2つ目の collector は変更なし | 既に collector 側に `catch` がある |

---

## 変更しないもの
- `init` ブロック以外のコード（`onDeleteSubscription`, `toMonthlyAmount`, `companion object` 等）
- 他ファイル（HomeScreen.kt, SubscriptionCard.kt, HomeViewModelTest.kt 等）

---

## 検証
- `./gradlew.bat test` で HomeViewModelTest の全7テストが成功すること
