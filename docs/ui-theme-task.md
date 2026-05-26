# UI Theme 実装タスク（Codex向け）

## 前提
- パッケージ: `com.subkiru.subkiru`
- AGENTS.md の UIカラーパレットとファイル構成に従うこと
- Material 3 の `ColorScheme` を使用
- ダークモードは現時点では対応しない（ライトモードのみ）
- Dynamic Color（Android 12+）は無効にする（SubKiru 独自のブランドカラーを使用するため）
- テストは不要（UI テーマ定義のみ。ロジックなし）
- **デザイントーン**: AGENTS.md の「ダーク・スタイリッシュ」はデザインの雰囲気（落ち着いた洗練されたトーン）を指しており、ダークモード実装を意味しない。深緑ベースのカラーパレットでライトモードのみ実装する。

## パッケージ移動について

現在のテーマファイルは `com.subkiru.subkiru.ui.theme` に配置されているが、
AGENTS.md のファイル構成では `core/ui/theme/` 配下と定義されている。

**しかし、現在の `ui/theme/` は Android Studio が自動生成した場所であり、
`core/` 配下にはデータ層・ドメイン層のコードがある。
UI Theme は `core/` に含めるべきではないため、現在の `ui/theme/` のまま維持する。**

（AGENTS.md の `core/ui/theme/` は設計図の理想配置だが、
実際のプロジェクトでは `com.subkiru.subkiru.ui.theme` で問題ない）

> **TODO**: AGENTS.md のファイル構成を `core/ui/theme/` → `ui/theme/` に更新する必要あり（本タスクのスコープ外）。

## 既存コードの参照

### 変更対象（テンプレートから上書き）
- `app/src/main/java/com/subkiru/subkiru/ui/theme/Color.kt` — 現在は Purple/Pink のテンプレートカラー
- `app/src/main/java/com/subkiru/subkiru/ui/theme/Theme.kt` — 現在は Dynamic Color + ダーク/ライト切替のテンプレート
- `app/src/main/java/com/subkiru/subkiru/ui/theme/Type.kt` — 現在はデフォルト Typography

### 影響を受けるファイル
- `app/src/main/java/com/subkiru/subkiru/MainActivity.kt` — `SubKiruTheme` を使用中（import パス変更なし）

## AGENTS.md カラーパレット（ライトモード）

| 用途 | カラーコード | Material 3 での対応 |
|------|------------|-------------------|
| 背景ベース | `#F7FAF9` | `background` |
| カード背景 | `#FFFFFF` | `surfaceContainerLowest` |
| サーフェス | `#F0FAF7` | `surface` |
| プライマリ | `#0F6E56` | `primary` |
| アクセント | `#9FE1CB` | `tertiary` |
| テキスト主 | `#0D2620` | `onBackground`, `onSurface` |
| テキスト副（カスタム） | `#6AADA0` | `TextSecondary`（カスタムカラーとして直接参照） |
| onSurfaceVariant | `#5F6B67` | `onSurfaceVariant`（グレー系中間色） |
| 警告 | `#E05C5C` | `error` |

---

## タスク1: Color.kt の書き換え

**ファイル**: `app/src/main/java/com/subkiru/subkiru/ui/theme/Color.kt`

テンプレートの Purple/Pink カラーを全て削除し、AGENTS.md のカラーパレットに置き換える。

```kotlin
package com.subkiru.subkiru.ui.theme

import androidx.compose.ui.graphics.Color

// AGENTS.md UIカラーパレット（ライトモード）
val BackgroundBase = Color(0xFFF7FAF9)
val CardBackground = Color(0xFFFFFFFF)
val Surface = Color(0xFFF0FAF7)
val Primary = Color(0xFF0F6E56)
val Accent = Color(0xFF9FE1CB)
val TextPrimary = Color(0xFF0D2620)
val TextSecondary = Color(0xFF6AADA0) // AGENTS.md 定義色。カスタムUI要素で直接参照する用途
val Warning = Color(0xFFE05C5C)

// Material 3 で自動導出できない補助カラー
val OnPrimary = Color.White
val PrimaryContainer = Color(0xFFB8F0DD)
val OnPrimaryContainer = Color(0xFF002117)

val Secondary = Color(0xFF4A6358)
val OnSecondary = Color.White
val SecondaryContainer = Color(0xFFCCE8DC)
val OnSecondaryContainer = Color(0xFF072018)

val OnTertiary = Color(0xFF003828)
val TertiaryContainer = Color(0xFFC2F0DF)
val OnTertiaryContainer = Color(0xFF002117)

val OnSurfaceVariant = Color(0xFF5F6B67) // グレー系中間色（Material 3 の規約に準拠）

val ErrorContainer = Color(0xFFFCDADA)
val OnError = Color.White
val Outline = Color(0xFFB0C9C2)
```

---

## タスク2: Theme.kt の書き換え

**ファイル**: `app/src/main/java/com/subkiru/subkiru/ui/theme/Theme.kt`

テンプレートを全て削除し、SubKiru 専用テーマに置き換える。

```kotlin
package com.subkiru.subkiru.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SubKiruLightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Accent,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = BackgroundBase,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = CardBackground,
    error = Warning,
    onError = OnError,
    errorContainer = ErrorContainer,
    outline = Outline,
)

@Composable
fun SubKiruTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SubKiruLightColorScheme,
        typography = SubKiruTypography,
        content = content,
    )
}
```

**注意点**:
- `darkTheme` / `dynamicColor` パラメータを削除する（ライトモードのみ。「ダーク・スタイリッシュ」はデザイントーンであり、ダークモード実装ではない）
- `SubKiruTheme` の関数名は変更しない（`MainActivity.kt` が使用中）
- `Typography` の参照名を `SubKiruTypography` に変更する（タスク3で定義）
- `TextSecondary` は `onSurfaceVariant` には使用しない。Material 3 コンポーネントが期待するグレー系中間色 `OnSurfaceVariant` を使用する。`TextSecondary` はカスタムUI要素で直接参照する。

---

## タスク3: Type.kt の書き換え

**ファイル**: `app/src/main/java/com/subkiru/subkiru/ui/theme/Type.kt`

デフォルト Typography を SubKiru 向けにカスタマイズする。
日本語フォント対応のため `fontFamily` は `FontFamily.Default`（Noto Sans CJK が適用される）のまま。

```kotlin
package com.subkiru.subkiru.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SubKiruTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
    ),
)
```

---

## タスク4: MainActivity.kt の確認

**ファイル**: `app/src/main/java/com/subkiru/subkiru/MainActivity.kt`

`SubKiruTheme` の import パスは変更なし（`com.subkiru.subkiru.ui.theme.SubKiruTheme`）。
ただし、`Theme.kt` から `darkTheme` / `dynamicColor` パラメータを削除するため、
`MainActivity.kt` で `SubKiruTheme` に引数を渡している場合は削除すること。

現在の `MainActivity.kt` は引数なしで `SubKiruTheme { ... }` を呼んでいるため、**変更不要**。

`GreetingPreview` コンポーザブルも `SubKiruTheme` を引数なしで使用しているため、同様に**変更不要**。

---

## 検証
- `./gradlew.bat assembleDebug` が成功すること
- テンプレートの Purple/Pink カラーが完全に除去されていること
