package com.subkiru.subkiru.core.domain.usecase

import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DeleteSubscriptionUseCaseTest {
    private val repository = mockk<SubscriptionRepository>()
    private val useCase = DeleteSubscriptionUseCase(repository)

    @Test
    fun deactivateSubscriptionが呼ばれる() = runTest {
        coEvery { repository.deactivateSubscription(SUBSCRIPTION_ID) } returns Unit

        useCase(SUBSCRIPTION_ID)

        coVerify(exactly = 1) { repository.deactivateSubscription(SUBSCRIPTION_ID) }
    }

    companion object {
        private const val SUBSCRIPTION_ID = 1L
    }
}
