package com.subkiru.subkiru.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.subkiru.subkiru.core.domain.model.NotifiedBilling
import com.subkiru.subkiru.core.domain.repository.ReminderHistoryRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

// 設定用の DataStore（"settings"）とは別ファイルにして、同じ name を二重定義しない
internal val Context.reminderHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reminder_history",
)

class ReminderHistoryRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : ReminderHistoryRepository {

    override suspend fun getNotifiedBillings(): Set<NotifiedBilling> {
        val preferences = dataStore.data.first()
        return preferences[KEY_NOTIFIED_BILLINGS].orEmpty().mapNotNull(::decode).toSet()
    }

    override suspend fun markAsNotified(billings: Collection<NotifiedBilling>) {
        if (billings.isEmpty()) return
        dataStore.edit { preferences ->
            val current = preferences[KEY_NOTIFIED_BILLINGS].orEmpty()
            preferences[KEY_NOTIFIED_BILLINGS] = current + billings.map(::encode)
        }
    }

    override suspend fun deleteBillingsBefore(date: LocalDate) {
        dataStore.edit { preferences ->
            val current = preferences[KEY_NOTIFIED_BILLINGS] ?: return@edit
            // 解釈できない記録も残さず掃除する
            preferences[KEY_NOTIFIED_BILLINGS] = current.filterTo(mutableSetOf()) { entry ->
                val billing = decode(entry)
                billing != null && billing.billingDate >= date
            }
        }
    }

    private fun encode(billing: NotifiedBilling): String {
        return "${billing.subscriptionId}$SEPARATOR${billing.billingDate.toEpochDay()}"
    }

    private fun decode(entry: String): NotifiedBilling? {
        val parts = entry.split(SEPARATOR)
        if (parts.size != ENTRY_PART_COUNT) return null
        val subscriptionId = parts[0].toLongOrNull() ?: return null
        val epochDay = parts[1].toLongOrNull() ?: return null
        return NotifiedBilling(subscriptionId, LocalDate.ofEpochDay(epochDay))
    }

    companion object {
        private val KEY_NOTIFIED_BILLINGS = stringSetPreferencesKey("notified_billings")
        private const val SEPARATOR = ":"
        private const val ENTRY_PART_COUNT = 2
    }
}
