package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ReorderSubscriptionsUseCaseTest {
    private val repository = mockk<SubscriptionRepository>()
    private val useCase = ReorderSubscriptionsUseCase(repository)

    @Test
    fun 並べ替え後のIDリストがrepositoryに渡される() = runTest {
        // Arrange
        coEvery { repository.reorderSubscriptions(any()) } returns Unit

        // Act
        useCase(listOf(3L, 1L, 2L))

        // Assert
        coVerify(exactly = 1) { repository.reorderSubscriptions(listOf(3L, 1L, 2L)) }
    }

    @Test
    fun 重複したIDは最初の出現位置だけを残して渡される() = runTest {
        // Arrange
        coEvery { repository.reorderSubscriptions(any()) } returns Unit

        // Act
        useCase(listOf(3L, 1L, 3L, 2L))

        // Assert
        coVerify(exactly = 1) { repository.reorderSubscriptions(listOf(3L, 1L, 2L)) }
    }

    @Test
    fun IDリストが空の場合はrepositoryを呼ばない() = runTest {
        // Act
        useCase(emptyList())

        // Assert
        coVerify(exactly = 0) { repository.reorderSubscriptions(any()) }
    }
}
