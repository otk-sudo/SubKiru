package com.subkiru.subkiru.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.subkiru.subkiru.core.domain.model.UserSettings
import com.subkiru.subkiru.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {

    override fun observeSettings(): Flow<UserSettings> {
        return context.dataStore.data.map { preferences ->
            UserSettings(
                isReminderEnabled = preferences[KEY_REMINDER_ENABLED] ?: false,
                reminderDaysBefore = preferences[KEY_REMINDER_DAYS_BEFORE]
                    ?: UserSettings.DEFAULT_REMINDER_DAYS_BEFORE,
            )
        }
    }

    override suspend fun updateReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_REMINDER_ENABLED] = enabled
        }
    }

    override suspend fun updateReminderDaysBefore(days: Int) {
        val clampedDays = days.coerceIn(
            UserSettings.MIN_REMINDER_DAYS_BEFORE,
            UserSettings.MAX_REMINDER_DAYS_BEFORE,
        )
        context.dataStore.edit { preferences ->
            preferences[KEY_REMINDER_DAYS_BEFORE] = clampedDays
        }
    }

    companion object {
        private val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val KEY_REMINDER_DAYS_BEFORE = intPreferencesKey("reminder_days_before")
    }
}
