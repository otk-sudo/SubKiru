package com.subkiru.subkiru.core.domain.repository

import com.subkiru.subkiru.core.domain.model.NotifiedBilling
import java.time.LocalDate

interface ReminderHistoryRepository {
    suspend fun getNotifiedBillings(): Set<NotifiedBilling>
    suspend fun markAsNotified(billings: Collection<NotifiedBilling>)

    // 請求日が指定日より前の記録を削除する
    suspend fun deleteBillingsBefore(date: LocalDate)
}
