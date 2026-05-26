package com.subkiru.subkiru.core.domain.repository

import com.subkiru.subkiru.core.domain.model.ServiceTemplate
import kotlinx.coroutines.flow.Flow

interface ServiceTemplateRepository {
    fun observeAllTemplates(): Flow<List<ServiceTemplate>>
    suspend fun getTemplateById(id: Long): ServiceTemplate?
    fun searchTemplates(query: String): Flow<List<ServiceTemplate>>
}
