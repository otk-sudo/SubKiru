package com.subkiru.subkiru.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit

@Entity(
    tableName = "service_templates",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("category_id"),
    ],
)
data class ServiceTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = UNSAVED_ID,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "default_amount_minor")
    val defaultAmountMinor: Long,

    @ColumnInfo(name = "default_currency_code", defaultValue = DEFAULT_CURRENCY_CODE)
    val defaultCurrencyCode: String = DEFAULT_CURRENCY_CODE,

    @ColumnInfo(name = "default_interval_unit", defaultValue = DEFAULT_INTERVAL_UNIT)
    val defaultIntervalUnit: BillingIntervalUnit = DEFAULT_INTERVAL_UNIT_ENUM,

    @ColumnInfo(name = "default_interval_count", defaultValue = DEFAULT_INTERVAL_COUNT_TEXT)
    val defaultIntervalCount: Int = DEFAULT_INTERVAL_COUNT,

    @ColumnInfo(name = "logo_resource_name")
    val logoResourceName: String,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "search_keywords")
    val searchKeywords: String,
) {
    companion object {
        const val UNSAVED_ID = 0L
        const val DEFAULT_CURRENCY_CODE = "JPY"

        val DEFAULT_INTERVAL_UNIT_ENUM = BillingIntervalUnit.MONTHLY

        // Room の defaultValue は const val String が必要なため、
        // この値は BillingIntervalUnit.MONTHLY.name と一致させる。
        const val DEFAULT_INTERVAL_UNIT = "MONTHLY"

        const val DEFAULT_INTERVAL_COUNT = 1
        const val DEFAULT_INTERVAL_COUNT_TEXT = "1"
    }
}
