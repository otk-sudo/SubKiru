package com.subkiru.subkiru.feature.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionReorderTest {

    @Test
    fun 下方向の項目の位置へ移動すると移動元がその位置に入る() {
        val result = moveIdTo(listOf(1L, 2L, 3L, 4L), movingId = 1L, targetId = 3L)

        assertEquals(listOf(2L, 3L, 1L, 4L), result)
    }

    @Test
    fun 上方向の項目の位置へ移動すると移動元がその位置に入る() {
        val result = moveIdTo(listOf(1L, 2L, 3L, 4L), movingId = 4L, targetId = 2L)

        assertEquals(listOf(1L, 4L, 2L, 3L), result)
    }

    @Test
    fun 隣の項目へ移動すると2つが入れ替わる() {
        val result = moveIdTo(listOf(1L, 2L, 3L), movingId = 2L, targetId = 3L)

        assertEquals(listOf(1L, 3L, 2L), result)
    }

    @Test
    fun 移動元と移動先が同じ場合は並びが変わらない() {
        val result = moveIdTo(listOf(1L, 2L, 3L), movingId = 2L, targetId = 2L)

        assertEquals(listOf(1L, 2L, 3L), result)
    }

    @Test
    fun 一覧にないIDを指定した場合は並びが変わらない() {
        val result = moveIdTo(listOf(1L, 2L, 3L), movingId = 9L, targetId = 2L)

        assertEquals(listOf(1L, 2L, 3L), result)
    }
}
