package com.subkiru.subkiru.core.ui.component

/**
 * ServiceAvatar の表示判定に使う純粋ロジック。
 * Compose / Android リソースに依存しないため、JVM ユニットテストで直接検証できる。
 */

// 頭文字カラーアバターの固定パレット数（ServiceAvatar.kt の AVATAR_COLORS と要素数を一致させること）
internal const val AVATAR_COLOR_COUNT = 8

/**
 * サービス名から頭文字を生成する。
 * - 前後の空白を除去する
 * - 先頭1文字を使用する（英字は大文字化、日本語などはそのまま）
 * - 空文字（空白のみ含む）の場合は null を返す（頭文字なし → ic_service_default へフォールバック）
 */
internal fun serviceInitial(name: String): String? {
    val first = name.trim().firstOrNull() ?: return null
    return first.uppercase()
}

/**
 * サービス名から決定論的にパレットのインデックスを求める。
 * 同じサービス名では常に同じインデックスを返す。
 *
 * hashCode が負数（Int.MIN_VALUE を含む）になる入力でも、toLong().and(0xFFFFFFFFL) で
 * 非負の範囲へ正規化してから剰余を取るため、必ず 0 until AVATAR_COLOR_COUNT に収まる。
 */
internal fun avatarColorIndex(name: String): Int {
    return (name.hashCode().toLong().and(0xFFFFFFFFL) % AVATAR_COLOR_COUNT).toInt()
}

/**
 * サービス名 → アプリ内同梱のローカル公式ロゴを解決する関数（暫定実装）。
 *
 * 現時点では公式ロゴ素材を未配置のため、常に null を返す（→ 頭文字カラーアバターにフォールバックする）。
 * 公式ロゴ素材を追加する際は、この関数でサービス名に対応する drawable のリソースIDを返すよう実装する。
 *   例: if (name.trim() == "Netflix") return R.drawable.logo_netflix
 *
 * 注意: サービス名をキーにしているため、表記揺れ・大文字小文字・名称変更の影響を受ける。
 * 将来的には安定した識別子（テンプレートID等）への移行を検討する（残課題参照）。
 */
fun serviceLogoResId(name: String): Int? {
    // 公式ロゴ素材を追加したら、ここでサービス名 → drawable のリソースIDを返す。
    return null
}
