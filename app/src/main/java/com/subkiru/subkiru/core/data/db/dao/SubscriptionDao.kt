package com.subkiru.subkiru.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.subkiru.subkiru.core.data.db.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

// 並び順を1つのトランザクションで更新するため、interface ではなく抽象クラスにしている
@Dao
abstract class SubscriptionDao {
    @Query(
        """
        SELECT * FROM subscriptions
        WHERE is_active = 1 -- true (SQLite Boolean)
        ORDER BY next_billing_date_epoch ASC, name ASC
        """
    )
    abstract fun observeActiveSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    abstract suspend fun getSubscriptionById(id: Long): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun addSubscription(subscription: SubscriptionEntity): Long

    @Update
    abstract suspend fun updateSubscription(subscription: SubscriptionEntity)

    @Query(
        """
        UPDATE subscriptions
        SET is_active = 0, updated_at = :updatedAt
        WHERE id = :id
        """
    )
    abstract suspend fun deactivateSubscription(id: Long, updatedAt: Long)

    @Delete
    abstract suspend fun deleteSubscription(subscription: SubscriptionEntity)

    // 新規登録時に割り当てる、末尾の並び順を返す
    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM subscriptions")
    abstract suspend fun getNextSortOrder(): Int

    @Query("UPDATE subscriptions SET sort_order = :sortOrder WHERE id = :id")
    abstract suspend fun updateSortOrder(id: Long, sortOrder: Int)

    // 渡された ID の並びを、先頭から 0, 1, 2... の並び順として保存する
    @Transaction
    open suspend fun updateSortOrders(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            updateSortOrder(id, index)
        }
    }
}
