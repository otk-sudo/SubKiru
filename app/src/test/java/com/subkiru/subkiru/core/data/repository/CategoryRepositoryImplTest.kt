package com.subkiru.subkiru.core.data.repository

import app.cash.turbine.test
import com.subkiru.subkiru.core.data.db.dao.CategoryDao
import com.subkiru.subkiru.core.data.db.entity.CategoryEntity
import com.subkiru.subkiru.core.domain.model.Category
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CategoryRepositoryImplTest {
    private val dao = mockk<CategoryDao>()
    private val repository = CategoryRepositoryImpl(dao)

    @Test
    fun observeAllCategoriesでEntityがDomainModelに変換される() = runTest {
        every { dao.observeAllCategories() } returns flowOf(listOf(categoryEntity()))

        repository.observeAllCategories().test {
            assertEquals(listOf(category()), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun getCategoryByIdでnullの場合はnullを返す() = runTest {
        coEvery { dao.getCategoryById(CATEGORY_ID) } returns null

        assertNull(repository.getCategoryById(CATEGORY_ID))
    }

    @Test
    fun getCategoryByIdでEntityがDomainModelに変換される() = runTest {
        coEvery { dao.getCategoryById(CATEGORY_ID) } returns categoryEntity()

        assertEquals(category(), repository.getCategoryById(CATEGORY_ID))
    }

    @Test
    fun addCategoryでDomainModelがEntityに変換されてDAOに渡される() = runTest {
        val entitySlot = slot<CategoryEntity>()
        coEvery { dao.addCategory(capture(entitySlot)) } returns ADDED_ID

        val id = repository.addCategory(category())

        assertEquals(ADDED_ID, id)
        assertEquals(categoryEntity(), entitySlot.captured)
    }

    @Test
    fun updateCategoryでDomainModelがEntityに変換されてDAOに渡される() = runTest {
        val entitySlot = slot<CategoryEntity>()
        coEvery { dao.updateCategory(capture(entitySlot)) } returns Unit

        repository.updateCategory(category())

        assertEquals(categoryEntity(), entitySlot.captured)
    }

    @Test
    fun deleteCategoryでDomainModelがEntityに変換されてDAOに渡される() = runTest {
        val entitySlot = slot<CategoryEntity>()
        coEvery { dao.deleteCategory(capture(entitySlot)) } returns Unit

        repository.deleteCategory(category())

        assertEquals(categoryEntity(), entitySlot.captured)
    }

    private fun category(): Category {
        return Category(
            id = CATEGORY_ID,
            name = CATEGORY_NAME,
            iconName = ICON_NAME,
            colorHex = COLOR_HEX,
            sortOrder = SORT_ORDER,
        )
    }

    private fun categoryEntity(): CategoryEntity {
        return CategoryEntity(
            id = CATEGORY_ID,
            name = CATEGORY_NAME,
            iconName = ICON_NAME,
            colorHex = COLOR_HEX,
            sortOrder = SORT_ORDER,
        )
    }

    companion object {
        private const val CATEGORY_ID = 1L
        private const val ADDED_ID = 10L
        private const val CATEGORY_NAME = "動画"
        private const val ICON_NAME = "movie"
        private const val COLOR_HEX = "#0F6E56"
        private const val SORT_ORDER = 1
    }
}
