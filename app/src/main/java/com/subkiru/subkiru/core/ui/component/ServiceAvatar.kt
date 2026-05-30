package com.subkiru.subkiru.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.subkiru.subkiru.R

/**
 * サービスのロゴ表示用の共通 Composable。
 *
 * 表示優先順位:
 *  1. アプリ内に同梱されたローカル公式ロゴ（logoResId != null）
 *  2. サービス名由来の頭文字カラーアバター（name が空白でない）
 *  3. サービス名が空の場合の ic_service_default
 *
 * 外部画像URL（Clearbit 等）は一切扱わない（完全オフライン表示）。
 */
@Composable
fun ServiceAvatar(
    name: String,
    logoResId: Int? = null,
    categoryColorHex: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = AVATAR_SIZE,
    cornerRadius: Dp = AVATAR_CORNER_RADIUS,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val initial = serviceInitial(name)
    when {
        // 1. アプリ内同梱のローカル公式ロゴ
        logoResId != null -> Image(
            painter = painterResource(logoResId),
            contentDescription = "${name}のロゴ",
            modifier = modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Crop,
        )
        // 2. サービス名由来の頭文字カラーアバター
        initial != null -> {
            val bgColor = categoryColorHex?.let { parseColorHex(it) }
                ?: AVATAR_COLORS[avatarColorIndex(name)]
            Box(
                modifier = modifier
                    .size(size)
                    .clip(shape)
                    .background(bgColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
            }
        }
        // 3. サービス名が空 → デフォルトアイコン
        else -> Image(
            painter = painterResource(R.drawable.ic_service_default),
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Crop,
        )
    }
}

// カテゴリカラー16進数文字列を Compose の Color 型にパース（失敗時は null でフォールバック）
private fun parseColorHex(hex: String): Color? {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal val AVATAR_SIZE = 36.dp
internal val AVATAR_CORNER_RADIUS = 10.dp

// 頭文字カラーアバターの固定パレット（要素数は AVATAR_COLOR_COUNT と一致させること）
private val AVATAR_COLORS = listOf(
    Color(0xFFE50914), // 赤
    Color(0xFF1DB954), // 緑
    Color(0xFF0078D4), // 青
    Color(0xFFFF9500), // オレンジ
    Color(0xFF6C63FF), // 紫
    Color(0xFFE91E63), // ピンク
    Color(0xFF00BCD4), // シアン
    Color(0xFF795548), // 茶
)
