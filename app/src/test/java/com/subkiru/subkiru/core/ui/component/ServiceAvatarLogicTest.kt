package com.subkiru.subkiru.core.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceAvatarLogicTest {

    // --- serviceInitial（頭文字生成） ---

    @Test
    fun 英字サービス名の頭文字は大文字1文字になる() {
        // Arrange & Act
        val initial = serviceInitial("Netflix")

        // Assert
        assertEquals("N", initial)
    }

    @Test
    fun 前後の空白を除去して頭文字を大文字化する() {
        // Arrange & Act
        val initial = serviceInitial(" amazon ")

        // Assert
        assertEquals("A", initial)
    }

    @Test
    fun 日本語サービス名は先頭文字をそのまま使う() {
        // Arrange & Act
        val initial = serviceInitial("ニコニコ")

        // Assert
        assertEquals("ニ", initial)
    }

    @Test
    fun 空文字の場合は頭文字がnullになる() {
        // Arrange & Act
        val initial = serviceInitial("")

        // Assert: 頭文字なし → ic_service_default へフォールバックする
        assertNull(initial)
    }

    @Test
    fun 空白のみの場合も頭文字がnullになる() {
        // Arrange & Act
        val initial = serviceInitial("   ")

        // Assert
        assertNull(initial)
    }

    // --- avatarColorIndex（色決定） ---

    @Test
    fun 同じサービス名は常に同じ色インデックスになる() {
        // Arrange & Act
        val first = avatarColorIndex("Netflix")
        val second = avatarColorIndex("Netflix")

        // Assert
        assertEquals(first, second)
    }

    @Test
    fun 色インデックスはパレット範囲内に収まる() {
        // Arrange & Act
        val index = avatarColorIndex("Spotify")

        // Assert
        assertTrue(index in 0 until AVATAR_COLOR_COUNT)
    }

    @Test
    fun 負数hashCodeの入力でもパレット範囲内に収まる() {
        // Arrange: hashCode が負数になる入力を十分な数だけ用意する
        val names = (0..5000).map { "service-$it" }
        val negativeHashNames = names.filter { it.hashCode() < 0 }

        // 前提: 負数 hashCode の入力が含まれていることを担保
        assertTrue(negativeHashNames.isNotEmpty())

        // Act & Assert: すべての入力でインデックスが範囲内に収まる
        negativeHashNames.forEach { name ->
            assertTrue(avatarColorIndex(name) in 0 until AVATAR_COLOR_COUNT)
        }
    }

    // --- serviceLogoResId（ローカルロゴ解決：暫定実装） ---

    @Test
    fun ロゴ素材未配置のため常にnullを返す() {
        // Arrange & Act & Assert: 現時点ではどのサービス名でも null（頭文字フォールバック）
        assertNull(serviceLogoResId("Netflix"))
        assertNull(serviceLogoResId("存在しないサービス"))
    }
}
