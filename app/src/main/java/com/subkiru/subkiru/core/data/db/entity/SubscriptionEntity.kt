package com.subkiru.subkiru.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit

@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ServiceTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("next_billing_date_epoch"),
        Index("is_active"),
        Index("category_id"),
        Index("template_id"),
    ],
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = UNSAVED_ID,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "currency_code", defaultValue = DEFAULT_CURRENCY_CODE)
    val currencyCode: String = DEFAULT_CURRENCY_CODE,

    @ColumnInfo(name = "billing_interval_unit", defaultValue = DEFAULT_BILLING_INTERVAL_UNIT)
    val billingIntervalUnit: BillingIntervalUnit = DEFAULT_BILLING_INTERVAL_UNIT_ENUM,

    @ColumnInfo(name = "billing_interval_count", defaultValue = DEFAULT_BILLING_INTERVAL_COUNT_TEXT)
    val billingIntervalCount: Int = DEFAULT_BILLING_INTERVAL_COUNT,

    @ColumnInfo(name = "start_date_epoch")
    val startDateEpoch: Long,

    @ColumnInfo(name = "next_billing_date_epoch")
    val nextBillingDateEpoch: Long,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    @ColumnInfo(name = "template_id")
    val templateId: Long? = null,

    @ColumnInfo(name = "logo_uri")
    val logoUri: String? = null,

    @ColumnInfo(name = "memo")
    val memo: String? = null,

    @ColumnInfo(name = "is_active", defaultValue = DEFAULT_IS_ACTIVE_TEXT)
    val isActive: Boolean = DEFAULT_IS_ACTIVE,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val UNSAVED_ID = 0L
        const val DEFAULT_CURRENCY_CODE = "JPY"

        val DEFAULT_BILLING_INTERVAL_UNIT_ENUM = BillingIntervalUnit.MONTHLY

        // Room の defaultValue は const val String が必要なため、
        // この値は BillingIntervalUnit.MONTHLY.name と一致させる。
        const val DEFAULT_BILLING_INTERVAL_UNIT = "MONTHLY"

        const val DEFAULT_BILLING_INTERVAL_COUNT = 1
        const val DEFAULT_BILLING_INTERVAL_COUNT_TEXT = "1"
        const val DEFAULT_IS_ACTIVE = true
        const val DEFAULT_IS_ACTIVE_TEXT = "1"
    }
}
