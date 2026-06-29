# SubKiru デザインブラッシュアップ指示書（Codex 向け）

> 本書は SubKiru の画面デザインを競合アプリ **「サブスクBox」** のテイストに寄せて
> ブラッシュアップするための実装指示書です。実装は本書と `AGENTS.md` の両方に従ってください。
> **目的**: 「Material 3 の初期値＋緑のベタ塗り」による“量産アプリ感（AIっぽさ）”を脱却する。

---

## 0. 前提・スコープ

- **対象**: UI 層のみ（`feature/*` の `*Screen.kt`、`core/ui/component/*`、`ui/theme/*`）
- **触らない**: Room スキーマ / DAO / Entity / UseCase / Repository のロジック。
  - 例外: カレンダーの「日付→サービスロゴ」表示に必要な **UiState の拡張**は可（DB は変更しない。既存データから導出する）。
- **画像方針の継続**: 外部画像API・Coil は使わない（Clearbit 依存削除済み）。
  ロゴは **ローカル drawable** ＋（無い場合）頭文字カラーアバターのフォールバックを維持する。
- **遵守**: `AGENTS.md` のコーディングルール（マジックナンバー禁止 / `@Preview` 必須 /
  Composable にロジックを書かない / `!!` 禁止 / マジックナンバーは `private val ... .dp` に命名）。
- **TDD**: ViewModel / UiState のロジックを変更する箇所（カレンダーのロゴ導出など）は
  **テストを先に書く**こと（`AGENTS.md` TDD ルール準拠）。

---

## 1. デザインコンセプト（サブスクBox から抽出した3原則）

1. **配色は3色に絞る**: 白背景 ＋ 黒（合計カード・テキスト・グラフ）＋ **ティール1色**（差し色）。
   原色を散らさない。色には必ず役割を持たせる（高額=赤 / 節約=ティール）。
2. **金額が主役**: ホーム合計はその画面で最も大きい要素（ヒーロー数字）にする。
3. **カードを乱用しない・実ロゴを使う**: サービス一覧は「カード」ではなく **リスト行**。
   カードは「合計」「節約実績」など意味のあるブロックだけに限定。ロゴは公式ロゴを優先表示。

> `AGENTS.md` のコンセプト文は現在「ダーク・スタイリッシュ」だが、実装はライト基調。
> 本ブラッシュアップは **ライト基調＋黒の合計カード**で進める。整合のため
> `AGENTS.md` のコンセプト文を「白基調 × 黒の引き締め × ティールの差し色」に更新することを推奨（任意）。

---

## 2. デザイントークン（`ui/theme/Color.kt` 改訂）

現状は緑一色。以下へ再編する。**HEX は初期値。最終調整可**。

| 用途 | 変数名（提案） | 新 HEX | 現状 |
|------|---------------|--------|------|
| 画面背景 | `BackgroundBase` | `#FFFFFF` | `#F7FAF9` |
| セクション背景(任意) | `ScreenBackgroundSubtle` | `#F7F8F8` | － |
| カード背景 | `CardBackground` | `#FFFFFF` | `#FFFFFF` |
| **合計カード背景** | `HeroCardBackground`（新） | `#1A1A1A` | （緑 `Primary`） |
| 合計カード文字 | `OnHeroCard`（新） | `#FFFFFF` | － |
| 合計カード副文字 | `OnHeroCardMuted`（新） | `#FFFFFF` α0.6 | － |
| **アクセント(ティール)** | `Primary` | `#0E9E88` | `#0F6E56` |
| ティール淡色(節約等) | `PrimaryContainer` | `#D7F3EC` | `#B8F0DD` |
| 高額/警告 | `Warning` / `error` | `#E5484D` | `#E05C5C` |
| 高額ハイライト淡色 | `ErrorContainer` | `#FDECEC` | `#FCDADA` |
| テキスト主 | `TextPrimary` | `#1A1A1A` | `#0D2620` |
| テキスト副 | `TextSecondary` | `#8A8F98`（中立グレー） | `#6AADA0`（緑がかり） |
| 区切り線 | `Outline` / divider | `#ECECEE` | `#B0C9C2` |

ポイント:
- `TextSecondary` を **緑がかったグレー → 中立グレー**にするだけで一気に量産感が抜ける。
- `Primary` を深緑からやや明るい **ティール**へ寄せる（差し色・選択チップ・節約・カレンダー印に使用）。
- `Theme.kt` の `lightColorScheme` マッピングも追従して更新する。

---

## 3. タイポグラフィ（`ui/theme/Type.kt`）

- **ヒーロー金額用**に最大級スタイルを強化：`displayLarge` を `fontSize = 40.sp` /
  `fontWeight = FontWeight.ExtraBold` / `letterSpacing = (-0.5).sp`（大きい数字は詰める）。
- 見出し（`headlineMedium`/`titleLarge`）は `SemiBold→Bold` 寄りでメリハリを強める。
- フォントは当面 `FontFamily.Default` 維持で可。
  （任意）将来的に Noto Sans JP / M PLUS 等の可変フォント導入を別タスクで検討。

---

## 4. 共通コンポーネント

### 4-1. `HeroTotalCard`（新規 / `core/ui/component/`）
- 黒背景（`HeroCardBackground`）角丸 20dp、内側パディング 20dp。
- 構成: 小ラベル（`OnHeroCardMuted`）→ **ヒーロー金額**（`displayLarge`, `OnHeroCard`）→
  下段に「年間換算 ¥… / 契約数 …件」を `SpaceBetween` で配置。
- ホームと（必要なら）分析のサマリで再利用。

### 4-2. `SubscriptionListRow`（新規 / `core/ui/component/`）
- **Card をやめて行レイアウト**にする。高さは内容＋縦 14dp 程度。
- 構成: `ServiceAvatar`(40dp) ＋ Column(名前＝`titleMedium`/サブ＝`bodySmall` 中立グレー)
  ＋ 右に `¥金額` ＋ `/月`（副文字）＋ シェブロン `>`。
- 行間の区切りは薄い `HorizontalDivider(color = Outline)` か余白で。
- 既存 `SubscriptionCard.kt` はリスト用途では本コンポーネントに置換（カードが必要な箇所のみ残す）。

### 4-3. `ServiceAvatar`（既存改修 / `core/ui/component/ServiceAvatar.kt`）
- 優先順位は現状維持（**ローカル公式ロゴ → 頭文字アバター → デフォルト**）。
- 改修点:
  - 公式ロゴは **白背景パディング付きの角丸スクエア**で表示（box 同様の“整って見える”見せ方）。
  - 頭文字アバターの `AVATAR_COLORS`（原色8色）を、**彩度を抑えたトーン**に差し替えるか、
    `Primary` 基調のモノトーン＋アクセントに寄せる（原色バラバラが量産感の一因）。
- **ロゴ拡充は要相談**: 主要サービスの drawable を増やすほど box に近づくが、
  **ロゴ画像の調達・著作権・配布可否はユーザー判断**。コードはリソースが増えても動く設計を維持。

### 4-4. フィルターピル（ホーム上部）
- `[請求日][高い順][安い順][新しい順][カスタム]` のピル型 Chip 群（横スクロール可）。
- 選択中＝黒背景・白文字、非選択＝白背景・中立グレー・枠なし。
- 並べ替えロジックは ViewModel 側（`AGENTS.md`: Composable にロジックを書かない）。
  **新規ロジックは TDD で**。

### 4-5. ボトムナビ（`navigation/BottomNavBar.kt`）
- 選択中＝黒（または `Primary`）/ 非選択＝中立グレーに統一。
- box は「ホーム / カレンダー / 分析 / 診断 / 設定」の5タブ。
  ※「診断」は現状 SubKiru に画面が無い。**今回スコープ外**（追加するなら別タスク化）。

---

## 5. 画面別の改修指示（Before → After）

### 5-1. ホーム `feature/home/HomeScreen.kt`
- **Before**: `MonthlyTotalHeader` が `Primary`(緑) ベタ塗り Box / 合計 `headlineLarge`。
  一覧は `SubscriptionCard`(白カード) を `LazyColumn` で連打。
- **After**:
  - 合計を **`HeroTotalCard`（黒・ヒーロー金額）** に置換。月額/年額の切替は現状の
    `FilterChip` を踏襲しつつ、黒カード上に白系チップで配置（または下のピル群に統合）。
  - 一覧を **`SubscriptionListRow`** に置換（カード廃止）。区切りは薄い divider。
  - 上部に **フィルターピル**（4-4）を追加。
  - FAB（＋）は現状の追加動線を踏襲（右下、`Primary` か黒）。

### 5-2. カレンダー `feature/calendar/CalendarScreen.kt`
- **Before**: `MonthHeader` が `Primary`(緑) ベタ塗り。`DayCell` は請求日に **小さいドット**のみ。
- **After**:
  - 月ヘッダーの緑ベタ塗りを**廃止**し、白地＋黒文字の `< 2026年4月 >` 中央タイトルに。
  - **`DayCell` の請求日表示をドット→サービスロゴ**へ（box の最大の魅力）。
    - そのため UiState を拡張: `billingDays: Set<Int>` →
      `billingsByDay: Map<Int, List<BillingMark>>`（`BillingMark` = サービス名＋logoResId 等）。
    - 1日に複数ある場合は **最大2個＋「+N」**の省略表示。
    - **この導出ロジックは ViewModel 側で TDD（テスト先行）**。DB は変更しない。
  - 下部の「選択日の請求」一覧は **`SubscriptionListRow`** に寄せ、先頭に
    「{月}の合計 ¥…」のヘッダー行を追加。
  - 曜日ヘッダーの 日=赤 / 土=青 を踏襲。

### 5-3. 分析 `feature/analytics/AnalyticsScreen.kt`
- **Before**: 全て白カード。内訳は `LinearProgressIndicator`（緑）。
- **After**:
  - 「コストが高いサブスク」セクション: リスト化し、**最高額の行を淡い赤(`ErrorContainer`)で
    ハイライト＋「特に高額」赤バッジ**。金額は赤系。元通貨と「/月換算」を併記。
  - 「節約実績」カード: **淡いティール(`PrimaryContainer`)背景**＋ティールの大きな金額。
  - 「支出の推移」: **縦棒グラフ**（黒バー＋最新月のみティール＋金額ラベル）。
    - 実装は **Canvas ベースの軽量自作バーチャート**を推奨。
      外部チャートライブラリの追加は **依存が増えるため要相談**（`gradle` 変更は事前合意）。
  - サマリ（月額/年額/件数）は `HeroTotalCard` 流用も可。

### 5-4. 設定 `feature/settings/SettingsScreen.kt`
- トークン追従のみ（緑→ティール、テキスト中立グレー）。レイアウト大改修は不要。

---

## 6. 修正順序（リスクの低い順）

1. `ui/theme/Color.kt` / `Theme.kt` / `Type.kt`（トークン）← 全体に波及。Preview で確認
2. `core/ui/component/ServiceAvatar.kt`（ロゴ見せ方・アバター配色）
3. `core/ui/component/` に `HeroTotalCard` / `SubscriptionListRow` を**新規追加**
4. `feature/home/HomeScreen.kt`（一覧リスト化・ヒーローカード・ピル）
5. `feature/calendar/CalendarScreen.kt`（UiState 拡張＋ロゴ表示）※ ViewModel は **TDD**
6. `feature/analytics/AnalyticsScreen.kt`（赤ハイライト・節約カード・バーチャート）
7. `navigation/BottomNavBar.kt` / `feature/settings/*`（追従）
8. （任意）`AGENTS.md` コンセプト文の更新

各ステップ完了ごとに `@Preview` でビジュアル確認し、ビルドを通すこと。

---

## 7. 受け入れ基準（チェックリスト）

- [ ] 画面に **緑のベタ塗りカードが残っていない**（合計は黒、差し色はティール）
- [ ] ホーム/分析の **合計金額が画面内で最大の文字**になっている
- [ ] サービス一覧が **カードではなくリスト行**で表示される
- [ ] **公式ロゴがあるサービスはロゴ表示**、無いものは頭文字アバター（原色バラバラでない）
- [ ] カレンダーの請求日に **サービスロゴ**が出る（ドットのみではない）
- [ ] 高額=赤 / 節約=ティール の **色の役割**が一貫している
- [ ] `TextSecondary` が中立グレー（緑がかっていない）
- [ ] マジックナンバーが `private val ….dp/.sp` に命名されている
- [ ] 全 `*Screen` に `@Preview` がある
- [ ] 変更した ViewModel ロジックに **テストがある（TDD）**
- [ ] DB スキーマ / Room は変更していない
- [ ] 外部画像API・Coil を導入していない

---

## 8. 参考（サブスクBox スクショ観察メモ）

- ホーム: 黒の合計カード（¥44,216 が極太ヒーロー / 年間換算・契約数を下段）→ フィルターピル →
  実ロゴのリスト行（iCloud+ / Vercel / Amazon Prime / Netflix / X / ChatGPT）。右下に黒FAB。
- カレンダー: 月グリッドの各日セルに**サービスロゴを直接配置**。下に「{月}の合計」＋日別リスト。
- 診断: 白カード＋KEEPバッジ＋ロゴ大、下に 使ってない/まあまあ/よく使った の3択（赤/黄/青）。※今回スコープ外
- 分析: 高額サブスクの赤ハイライト、淡ティールの節約実績カード（豚の貯金箱）、黒＋ティールの棒グラフ。

> 配色の最終 HEX、フォント、ロゴ調達の範囲は実装中にユーザーと調整すること。
