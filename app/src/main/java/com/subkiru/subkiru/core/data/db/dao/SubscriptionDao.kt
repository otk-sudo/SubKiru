package com.subkiru.subkiru.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.subkiru.subkiru.core.data.db.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query(
        """
        SELECT * FROM subscriptions
        WHERE is_active = 1 -- true (SQLite Boolean)
        ORDER BY next_billing_date_epoch ASC, name ASC
        """
    )
    fun observeActiveSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getSubscriptionById(id: Long): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addSubscription(subscription: SubscriptionEntity): Long

    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)

    @Query(
        """
        UPDATE subscriptions
        SET is_active = 0, updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun deactivateSubscription(id: Long, updatedAt: Long)

    @Delete
    suspend fun deleteSubscription(subscription: SubscriptionEntity)
}
