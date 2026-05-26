package com.subkiru.subkiru.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.subkiru.subkiru.core.data.db.entity.PaymentHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentHistoryDao {
    @Query(
        """
        SELECT * FROM payment_history
        WHERE subscription_id = :subscriptionId
        ORDER BY billing_date_epoch DESC
        """
    )
    fun observeBySubscriptionId(subscriptionId: Long): Flow<List<PaymentHistoryEntity>>

    @Query("SELECT * FROM payment_history WHERE id = :id LIMIT 1")
    suspend fun getPaymentById(id: Long): PaymentHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addPayment(payment: PaymentHistoryEntity): Long

    @Update
    suspend fun updatePayment(payment: PaymentHistoryEntity)

    @Delete
    suspend fun deletePayment(payment: PaymentHistoryEntity)

    @Query(
        """
        SELECT * FROM payment_history
        WHERE billing_date_epoch >= :startEpoch
            AND billing_date_epoch <= :endEpoch
        ORDER BY billing_date_epoch DESC
        """
    )
    fun observeByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<PaymentHistoryEntity>>
}
