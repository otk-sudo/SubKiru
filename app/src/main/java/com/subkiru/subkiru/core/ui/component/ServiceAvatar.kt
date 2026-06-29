package com.subkiru.subkiru.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
        logoResId != null -> Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Color.White)
                .border(LOGO_BORDER_WIDTH, MaterialTheme.colorScheme.outline, shape)
                .padding(LOGO_INNER_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(logoResId),
                contentDescription = "${name}のロゴ",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        // 2. サービス名由来の頭文字カラーアバター
        initial != null -> {
            val bgColor = categoryColorHex?.let { parseColorHex(it) }
                ?.copy(alpha = CATEGORY_COLOR_ALPHA)
                ?.compositeOver(Color.White)
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // 3. サービス名が空 → デフォルトアイコン
        else -> Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Color.White)
                .border(LOGO_BORDER_WIDTH, MaterialTheme.colorScheme.outline, shape)
                .padding(LOGO_INNER_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_service_default),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
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

internal val AVATAR_SIZE = 40.dp
internal val AVATAR_CORNER_RADIUS = 10.dp
private val LOGO_INNER_PADDING = 6.dp
private val LOGO_BORDER_WIDTH = 1.dp
private const val CATEGORY_COLOR_ALPHA = 0.28f

// 頭文字カラーアバターの固定パレット（要素数は AVATAR_COLOR_COUNT と一致させること）
private val AVATAR_COLORS = listOf(
    Color(0xFFD7F3EC),
    Color(0xFFE4EEF6),
    Color(0xFFECE7F5),
    Color(0xFFF4E9DF),
    Color(0xFFE7F0E4),
    Color(0xFFF3E5E7),
    Color(0xFFE2EFF0),
    Color(0xFFECE9E5),
)
