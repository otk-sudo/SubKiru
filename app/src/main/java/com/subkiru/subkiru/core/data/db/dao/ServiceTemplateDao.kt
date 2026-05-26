package com.subkiru.subkiru.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.subkiru.subkiru.core.data.db.entity.ServiceTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceTemplateDao {
    @Query(
        """
        SELECT * FROM service_templates
        ORDER BY name ASC
        """
    )
    fun observeAllTemplates(): Flow<List<ServiceTemplateEntity>>

    @Query("SELECT * FROM service_templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateById(id: Long): ServiceTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addTemplate(template: ServiceTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addTemplates(templates: List<ServiceTemplateEntity>)

    @Query(
        """
        SELECT * FROM service_templates
        WHERE name LIKE '%' || :query || '%'
            OR search_keywords LIKE '%' || :query || '%'
        ORDER BY name ASC
        """
    )
    fun searchTemplates(query: String): Flow<List<ServiceTemplateEntity>>
}
