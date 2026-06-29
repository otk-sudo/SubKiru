# 通知権限 Lintエラー修正指示書（Codex向け）

> Play Store リリース前のシステムテスト（フェーズA: Lint）で検出された
> **エラー2件**を解消するための修正指示書。実装は本書と `AGENTS.md` の両方に従うこと。

---

## 1. 背景・現象

`gradlew :app:lintDebug` が **エラー2件**で失敗し、リリースビルドをブロックしている。

```
NotificationHelper.kt:59  Error: MissingPermission  notificationManager.notify(...)
NotificationHelper.kt:86  Error: MissingPermission  notificationManager.notify(SUMMARY_NOTIFICATION_ID, ...)
```

## 2. 原因

- `AndroidManifest.xml:5` に `POST_NOTIFICATIONS` 権限は宣言済み。
- しかし `NotificationHelper.showBillingReminders()` が `notify()` を呼ぶ前に
  **ランタイム権限を確認していない**。
- Android 13（API 33）以降、`POST_NOTIFICATIONS` はランタイム権限。
  ユーザーが拒否していると通知が出ず、Lint は静的にエラー判定する。
- 本アプリは `minSdk=26` のため、**API 33未満では権限不要（常に許可扱い）**という分岐も必要。

## 3. 対象ファイル

- `app/src/main/java/com/subkiru/subkiru/notification/NotificationHelper.kt`
- （テスト）`app/src/test/java/com/subkiru/subkiru/notification/NotificationHelperTest.kt`

## 4. 修正方針

`showBillingReminders()` の冒頭に**権限ガード**を追加し、未許可なら何もせず `return` する。
判定は純粋に切り出してテスト可能にする。

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

// 通知を投稿できるか（API33+は POST_NOTIFICATIONS 必須 / それ未満は常に true）
internal fun canPostNotifications(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

fun showBillingReminders(context: Context, reminders: List<BillingReminder>) {
    if (reminders.isEmpty()) return
    if (!canPostNotifications(context)) return   // ← 追加。これでLintのMissingPermissionも解消
    // 以降は既存ロジックのまま
}
```

- マジックナンバー禁止・`!!` 禁止（`AGENTS.md`）を遵守。
- `checkSelfPermission` ガードを**同一メソッド内**に置くことで、
  Lint が `notify()` の権限チェック済みと認識しエラーが消える。

## 5. テスト方針（TDD・AGENTS.md準拠）

- `canPostNotifications()` の境界（SDK_INT分岐・許可/拒否）を検証したいが、
  `Context` / `PackageManager` 依存のため純粋ユニットテストは困難。
- **推奨**: 既存の純粋ロジック（`formatReminderText` 等）のテストは維持。
  権限判定はモック（MockK）で `ContextCompat.checkSelfPermission` を覆うか、
  `Context` をモックして `PERMISSION_GRANTED` / `DENIED` の2系統を検証。
  - `Build.VERSION.SDK_INT` の分岐検証が必要なら Robolectric が要るが、
    **依存追加は事前合意が必要**（勝手に `gradle` へ追加しない）。
    Robolectric を入れないなら、SDK33+前提で
    「許可時=通知処理が走る / 拒否時=早期return」の2ケースに絞る。
- テストを先に書き（Red）→ ガード実装（Green）→ 整理（Refactor）。

## 6. 受け入れ基準

- [ ] `gradlew :app:lintDebug` が **エラー0** で成功する
- [ ] `gradlew :app:testDebugUnitTest` が全件成功
- [ ] `gradlew :app:assembleDebug` が成功
- [ ] DB・他画面・ロジックに影響を与えていない
- [ ] 追加した権限判定にテストがある

## 7.（任意）警告40件の扱い

リリース可否には影響しないが、ついでに対応するなら:

- 未使用リソース削除（`colors.xml` の `purple_*` / `teal_*` / `black` / `white`）
- `modifier` を最初のオプション引数に並べ替え（ModifierParameter:
  `HomeScreen.kt:94` / `ServiceAvatar.kt:40` / `SubscriptionCard.kt:38`）
- `UseTomlInstead`（`org.json` を `libs.versions.toml` へ移動）
- 依存ライブラリ更新（Room/Compose BOM/Lifecycle 等）は別タスク推奨

---

## 付録: システムテスト フェーズA 結果（2026-06-29 時点）

| テスト | コマンド | 結果 |
|--------|---------|------|
| A1 コンパイル | `gradlew :app:assembleDebug` | ✅ 成功 |
| A2 全ユニットテスト | `gradlew :app:testDebugUnitTest` | ✅ 成功（失敗ゼロ） |
| A3 Lint | `gradlew :app:lintDebug` | ❌ エラー2件・警告40件 |

本書のエラー解消後、フェーズB（実機/エミュレータでの結合・UIテスト）、
フェーズC（署名設定＋リリースAAB生成＋手動シナリオテスト）へ進む。
