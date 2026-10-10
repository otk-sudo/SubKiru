package com.subkiru.subkiru.feature.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.subkiru.subkiru.core.domain.model.Subscription

// movingId の項目を targetId の項目がある位置へ移動した並びを返す。どちらかが一覧にない場合は変えない
internal fun moveIdTo(ids: List<Long>, movingId: Long, targetId: Long): List<Long> {
    val fromIndex = ids.indexOf(movingId)
    val toIndex = ids.indexOf(targetId)
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return ids
    return ids.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

// 「カスタム」並べ替え中の一時的な表示順とドラッグ量を保持する。
// ドラッグ中はこの表示順で一覧を描画し、ドラッグが終わった時点で保存する
internal class SubscriptionReorderState(
    private val subscriptions: List<Subscription>,
    private val listState: LazyListState,
    private val onReordered: (List<Long>) -> Unit,
) {
    private var orderedIds: List<Long> by mutableStateOf(subscriptions.map { it.id })

    var draggingId: Long? by mutableStateOf(null)
        private set

    // ドラッグ中の項目を、本来の位置から指の移動量ぶんずらして描画するためのオフセット
    var dragOffsetY: Float by mutableFloatStateOf(0f)
        private set

    val displayedSubscriptions: List<Subscription>
        get() {
            val byId = subscriptions.associateBy { it.id }
            return orderedIds.mapNotNull { byId[it] }
        }

    fun onDragStart(id: Long) {
        draggingId = id
        dragOffsetY = 0f
    }

    fun onDrag(deltaY: Float) {
        val movingId = draggingId ?: return
        dragOffsetY += deltaY

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val movingItem = visibleItems.firstOrNull { it.key == movingId } ?: return
        val movingCenter = movingItem.offset + dragOffsetY + movingItem.size / 2f
        val targetItem = visibleItems.firstOrNull { item ->
            item.key != movingId &&
                item.key in orderedIds &&
                movingCenter >= item.offset &&
                movingCenter <= item.offset + item.size
        } ?: return

        orderedIds = moveIdTo(orderedIds, movingId, targetItem.key as Long)
        // 項目の本来の位置が移動先に変わるぶん、描画のずれを打ち消して指の位置に追従させる
        dragOffsetY += (movingItem.offset - targetItem.offset).toFloat()
    }

    fun onDragEnd() {
        if (draggingId == null) return
        draggingId = null
        dragOffsetY = 0f
        onReordered(orderedIds)
    }

    fun onDragCancel() {
        draggingId = null
        dragOffsetY = 0f
        orderedIds = subscriptions.map { it.id }
    }
}

@Composable
internal fun rememberSubscriptionReorderState(
    subscriptions: List<Subscription>,
    listState: LazyListState,
    onReordered: (List<Long>) -> Unit,
): SubscriptionReorderState {
    val latestOnReordered by rememberUpdatedState(onReordered)
    // 保存後に DB から新しい並びが流れてきたら、一時的な表示順を作り直す
    return remember(subscriptions, listState) {
        SubscriptionReorderState(
            subscriptions = subscriptions,
            listState = listState,
            onReordered = { latestOnReordered(it) },
        )
    }
}
