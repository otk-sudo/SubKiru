package com.subkiru.subkiru.core.domain.repository

import com.subkiru.subkiru.core.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun updateReminderEnabled(enabled: Boolean)
    suspend fun updateReminderDaysBefore(days: Int)
}
