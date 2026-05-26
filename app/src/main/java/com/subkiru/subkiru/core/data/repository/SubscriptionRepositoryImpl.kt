package com.subkiru.subkiru.core.data.repository

import com.subkiru.subkiru.core.data.db.dao.SubscriptionDao
import com.subkiru.subkiru.core.data.db.entity.SubscriptionEntity
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.Subscription
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class SubscriptionRepositoryImpl(
    private val dao: SubscriptionDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) : SubscriptionRepository {
    override fun observeActiveSubscriptions(): Flow<List<Subscription>> {
        return dao.observeActiveSubscriptions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSubscriptionById(id: Long): Subscription? {
        return dao.getSubscriptionById(id)?.toDomain()
    }

    override suspend fun addSubscription(subscription: Subscription): Long {
        return dao.addSubscription(subscription.toEntity())
    }

    override suspend fun updateSubscription(subscription: Subscription) {
        dao.updateSubscription(subscription.toEntity())
    }

    override suspend fun deactivateSubscription(id: Long) {
        dao.deactivateSubscription(id, updatedAt = Instant.now(clock).toEpochMilli())
    }

    override suspend fun deleteSubscription(subscription: Subscription) {
        dao.deleteSubscription(subscription.toEntity())
    }
}

private fun SubscriptionEntity.toDomain(): Subscription {
    return Subscription(
        id = id,
        name = name,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        billingInterval = BillingInterval(
            unit = billingIntervalUnit,
            count = billingIntervalCount,
        ),
        startDate = LocalDate.ofEpochDay(startDateEpoch),
        nextBillingDate = LocalDate.ofEpochDay(nextBillingDateEpoch),
        categoryId = categoryId,
        templateId = templateId,
        logoUri = logoUri,
        memo = memo,
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )
}

private fun Subscription.toEntity(): SubscriptionEntity {
    return SubscriptionEntity(
        id = id,
        name = name,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        billingIntervalUnit = billingInterval.unit,
        billingIntervalCount = billingInterval.count,
        startDateEpoch = startDate.toEpochDay(),
        nextBillingDateEpoch = nextBillingDate.toEpochDay(),
        categoryId = categoryId,
        templateId = templateId,
        logoUri = logoUri,
        memo = memo,
        isActive = isActive,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )
}
