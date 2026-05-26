package com.subkiru.subkiru.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payment_history",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscription_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("subscription_id"),
        Index("billing_date_epoch"),
    ],
)
data class PaymentHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = UNSAVED_ID,

    @ColumnInfo(name = "subscription_id")
    val subscriptionId: Long,

    @ColumnInfo(name = "billing_date_epoch")
    val billingDateEpoch: Long,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String,

    @ColumnInfo(name = "is_paid")
    val isPaid: Boolean,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,
) {
    companion object {
        const val UNSAVED_ID = 0L
    }
}
