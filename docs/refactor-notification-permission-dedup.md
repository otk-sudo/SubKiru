# 通知権限ロジックのDRY化 修正指示書（Codex向け）

> システムテスト中のレビューで指摘した「権限判定ロジックの二重定義」を解消するための
> 修正指示書。リリースをブロックする問題ではなく **品質改善（保守性・テストの有効性）** が目的。
> 実装は本書と `AGENTS.md` の両方に従うこと。

---

## 1. 背景

`docs/fix-notification-permission.md` の修正で Lint エラーは解消したが、
その過程で権限判定ロジックが二重定義になった。本書でそれを解消する。

## 2. 現状の問題（`NotificationHelper.kt`）

- 本番で実際に動くのは `showBillingReminders()`（49-57行）の **インライン** 権限ガード。
- 一方 `canPostNotifications(context)`（117-127行）と
  `canPostNotifications(sdkInt, granted)`（129-134行）が **別に存在し、
  `showBillingReminders()` から呼ばれていない**。
- 結果、**テストが検証しているのは `canPostNotifications`、本番で動くのはインラインコード** で乖離。
  さらに `canPostNotifications(context)` 内で SDK 判定が二重。

## 3. 修正方針

`showBillingReminders()` が **テスト済みの純粋関数 `canPostNotifications(sdkInt, granted)` を使う**
形に統一する。`checkSelfPermission` の呼び出しは **Lint対策のため `showBillingReminders()` 内に残す**。

```kotlin
fun showBillingReminders(context: Context, reminders: List<BillingReminder>) {
    if (reminders.isEmpty()) return
    val isPermissionGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    if (!canPostNotifications(Build.VERSION.SDK_INT, isPermissionGranted)) return
    // 以降は既存ロジックのまま
}

internal fun canPostNotifications(sdkInt: Int, isPermissionGranted: Boolean): Boolean {
    return sdkInt < Build.VERSION_CODES.TIRAMISU || isPermissionGranted
}
```

- **重複していた `canPostNotifications(context: Context)`（117-127行）は削除**（不要になる）。
- これでテスト対象の純粋関数が本番パスでも使われ、二重定義・二重SDK判定がともに解消。

## 4. ⚠️ 重要な検証ポイント（Lint復活リスク）

- Lint の `MissingPermission` は、`notify()` と **同一メソッド内** に `checkSelfPermission` が
  直接あることを期待する。上記のように結果を変数 → ヘルパー関数経由にすると、
  **Lint がフローを追えず `MissingPermission` が再発する可能性**がある。
- **`gradlew :app:lintDebug` を必ず実行して確認**すること。もしエラーが復活したら、
  フォールバックとして以下のいずれか:
  - (a) `notify()` を呼ぶ2箇所に `@SuppressLint("MissingPermission")`（理由コメント必須）、または
  - (b) DRY化を見送り、現状のインライン直接チェックを維持（その場合は本リファクタは「不可」と報告）。

## 5. テスト方針（TDD）

- 既存の `canPostNotifications(sdkInt, granted)` の3テスト（API32/33 × 許可/拒否）は
  **そのまま有効**（本番が同関数を使うようになり、テストの価値が上がる）。
- 削除する `canPostNotifications(context)` を参照するテストがあれば整理。

## 6. 受け入れ基準

- [ ] `gradlew :app:lintDebug` が **エラー0**（最重要・必ず実行）
- [ ] `gradlew :app:testDebugUnitTest` 全件成功
- [ ] `showBillingReminders()` が純粋関数 `canPostNotifications` を使用している
- [ ] `canPostNotifications(context)` の重複が解消されている
- [ ] プロダクションの挙動（権限なしで通知しない）は不変
