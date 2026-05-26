package com.subkiru.subkiru.core.domain.repository

import com.subkiru.subkiru.core.domain.model.Subscription
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    fun observeActiveSubscriptions(): Flow<List<Subscription>>
    suspend fun getSubscriptionById(id: Long): Subscription?
    suspend fun addSubscription(subscription: Subscription): Long
    suspend fun updateSubscription(subscription: Subscription)
    suspend fun deactivateSubscription(id: Long)
    suspend fun deleteSubscription(subscription: Subscription)
}
