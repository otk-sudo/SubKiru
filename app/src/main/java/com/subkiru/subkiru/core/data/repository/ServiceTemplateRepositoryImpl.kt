package com.subkiru.subkiru.core.data.repository

import com.subkiru.subkiru.core.data.db.dao.ServiceTemplateDao
import com.subkiru.subkiru.core.data.db.entity.ServiceTemplateEntity
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.ServiceTemplate
import com.subkiru.subkiru.core.domain.repository.ServiceTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ServiceTemplateRepositoryImpl(
    private val dao: ServiceTemplateDao,
) : ServiceTemplateRepository {
    override fun observeAllTemplates(): Flow<List<ServiceTemplate>> {
        return dao.observeAllTemplates().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTemplateById(id: Long): ServiceTemplate? {
        return dao.getTemplateById(id)?.toDomain()
    }

    override fun searchTemplates(query: String): Flow<List<ServiceTemplate>> {
        return dao.searchTemplates(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}

private fun ServiceTemplateEntity.toDomain(): ServiceTemplate {
    return ServiceTemplate(
        id = id,
        name = name,
        defaultAmountMinor = defaultAmountMinor,
        defaultCurrencyCode = defaultCurrencyCode,
        defaultInterval = BillingInterval(
            unit = defaultIntervalUnit,
            count = defaultIntervalCount,
        ),
        logoResourceName = logoResourceName,
        categoryId = categoryId,
        searchKeywords = searchKeywords,
    )
}
