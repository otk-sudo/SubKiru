package com.subkiru.subkiru.core.data.repository

import com.subkiru.subkiru.core.data.db.dao.CategoryDao
import com.subkiru.subkiru.core.data.db.entity.CategoryEntity
import com.subkiru.subkiru.core.domain.model.Category
import com.subkiru.subkiru.core.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepositoryImpl(
    private val dao: CategoryDao,
) : CategoryRepository {
    override fun observeAllCategories(): Flow<List<Category>> {
        return dao.observeAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCategoryById(id: Long): Category? {
        return dao.getCategoryById(id)?.toDomain()
    }

    override suspend fun addCategory(category: Category): Long {
        return dao.addCategory(category.toEntity())
    }

    override suspend fun updateCategory(category: Category) {
        dao.updateCategory(category.toEntity())
    }

    override suspend fun deleteCategory(category: Category) {
        dao.deleteCategory(category.toEntity())
    }
}

private fun CategoryEntity.toDomain(): Category {
    return Category(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        sortOrder = sortOrder,
    )
}

private fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        sortOrder = sortOrder,
    )
}
