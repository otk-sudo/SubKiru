package com.subkiru.subkiru.core.data.db.converter

import androidx.room.TypeConverter
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit

class Converters {
    @TypeConverter
    fun fromBillingIntervalUnit(value: BillingIntervalUnit): String {
        return value.name
    }

    @TypeConverter
    fun toBillingIntervalUnit(value: String): BillingIntervalUnit {
        // Room 経由でのみ書き込むため不正値は混入しない想定。
        // Migration でこのカラムを操作する場合は enum 名と一致していることを確認する。
        return BillingIntervalUnit.valueOf(value)
    }
}
