package com.subkiru.subkiru.core.domain.model

data class UserSettings(
    val isReminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = DEFAULT_REMINDER_DAYS_BEFORE,
) {
    companion object {
        const val DEFAULT_REMINDER_DAYS_BEFORE = 1
        const val MIN_REMINDER_DAYS_BEFORE = 1
        const val MAX_REMINDER_DAYS_BEFORE = 7
    }
}
