package com.subkiru.subkiru.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.subkiru.subkiru.core.domain.model.NotifiedBilling
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderHistoryRepositoryImplTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: ReminderHistoryRepositoryImpl

    @BeforeEach
    fun setUp() {
        // 実ファイルの DataStore を使う（外部 API ではないためモックしない）
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { File(tempDir, "reminder_history.preferences_pb") },
        )
        repository = ReminderHistoryRepositoryImpl(dataStore)
    }

    @Test
    fun 何も記録していない場合は空の集合を返す() = runTest {
        // Act
        val result = repository.getNotifiedBillings()

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun 通知済みとして記録した組を取得できる() = runTest {
        // Arrange
        val billing = NotifiedBilling(subscriptionId = 1L, billingDate = LocalDate.of(2026, 2, 1))

        // Act
        repository.markAsNotified(listOf(billing))
        val result = repository.getNotifiedBillings()

        // Assert
        assertEquals(setOf(billing), result)
    }

    @Test
    fun 記録を重ねても既存の記録が残る() = runTest {
        // Arrange
        val first = NotifiedBilling(subscriptionId = 1L, billingDate = LocalDate.of(2026, 2, 1))
        val second = NotifiedBilling(subscriptionId = 2L, billingDate = LocalDate.of(2026, 2, 5))

        // Act
        repository.markAsNotified(listOf(first))
        repository.markAsNotified(listOf(second))
        val result = repository.getNotifiedBillings()

        // Assert
        assertEquals(setOf(first, second), result)
    }

    @Test
    fun 指定日より前の請求日の記録だけが削除される() = runTest {
        // Arrange
        val past = NotifiedBilling(subscriptionId = 1L, billingDate = LocalDate.of(2026, 1, 14))
        val today = NotifiedBilling(subscriptionId = 2L, billingDate = LocalDate.of(2026, 1, 15))
        val future = NotifiedBilling(subscriptionId = 3L, billingDate = LocalDate.of(2026, 1, 16))
        repository.markAsNotified(listOf(past, today, future))

        // Act
        repository.deleteBillingsBefore(LocalDate.of(2026, 1, 15))
        val result = repository.getNotifiedBillings()

        // Assert
        assertEquals(setOf(today, future), result)
    }
}
