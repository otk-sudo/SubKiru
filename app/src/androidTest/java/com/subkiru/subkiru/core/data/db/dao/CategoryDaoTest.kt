package com.subkiru.subkiru.core.data.db.dao

import android.content.Context
import app.cash.turbine.test
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subkiru.subkiru.core.data.db.SubKiruDatabase
import com.subkiru.subkiru.core.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {
    private lateinit var database: SubKiruDatabase
    private lateinit var dao: CategoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SubKiruDatabase::class.java).build()
        dao = database.categoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun カテゴリを追加して取得できる() = runBlocking {
        val id = dao.addCategory(category = category())

        val actual = requireNotNull(dao.getCategoryById(id))

        assertEquals(CATEGORY_NAME, actual.name)
        assertEquals(ICON_NAME, actual.iconName)
        assertEquals(COLOR_HEX, actual.colorHex)
        assertEquals(SORT_ORDER, actual.sortOrder)
    }

    @Test
    fun observeAllCategoriesのソート順が正しい() = runBlocking {
        val laterId = dao.addCategory(category = category(name = SORT_LATER_NAME, sortOrder = LATER_SORT_ORDER))
        val sameOrderSecondId = dao.addCategory(
            category = category(name = SORT_SAME_ORDER_SECOND_NAME, sortOrder = FIRST_SORT_ORDER)
        )
        val sameOrderFirstId = dao.addCategory(
            category = category(name = SORT_SAME_ORDER_FIRST_NAME, sortOrder = FIRST_SORT_ORDER)
        )

        val actualIds = dao.observeAllCategories().first().map { it.id }

        assertEquals(listOf(sameOrderFirstId, sameOrderSecondId, laterId), actualIds)
    }

    @Test
    fun updateCategoryで更新後に正しい値が取得できる() = runBlocking {
        val id = dao.addCategory(category = category())
        val updated = requireNotNull(dao.getCategoryById(id)).copy(
            name = UPDATED_CATEGORY_NAME,
            iconName = UPDATED_ICON_NAME,
            colorHex = UPDATED_COLOR_HEX,
            sortOrder = UPDATED_SORT_ORDER,
        )

        dao.updateCategory(updated)

        val actual = requireNotNull(dao.getCategoryById(id))
        assertEquals(UPDATED_CATEGORY_NAME, actual.name)
        assertEquals(UPDATED_ICON_NAME, actual.iconName)
        assertEquals(UPDATED_COLOR_HEX, actual.colorHex)
        assertEquals(UPDATED_SORT_ORDER, actual.sortOrder)
    }

    @Test
    fun deleteCategoryで物理削除後にnullが返る() = runBlocking {
        val id = dao.addCategory(category = category())
        val saved = requireNotNull(dao.getCategoryById(id))

        dao.deleteCategory(saved)

        assertNull(dao.getCategoryById(id))
    }

    @Test
    fun getCategoryByIdに存在しないIDを渡すとnullが返る() = runBlocking {
        assertNull(dao.getCategoryById(NOT_FOUND_ID))
    }

    @Test
    fun カテゴリ追加後にFlowが更新を通知する() = runBlocking {
        dao.observeAllCategories().test {
            assertEquals(emptyList<CategoryEntity>(), awaitItem())

            val id = dao.addCategory(category = category())

            assertEquals(listOf(id), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun category(
        id: Long = CategoryEntity.UNSAVED_ID,
        name: String = CATEGORY_NAME,
        iconName: String = ICON_NAME,
        colorHex: String = COLOR_HEX,
        sortOrder: Int = SORT_ORDER,
    ): CategoryEntity {
        return CategoryEntity(
            id = id,
            name = name,
            iconName = iconName,
            colorHex = colorHex,
            sortOrder = sortOrder,
        )
    }

    companion object {
        private const val CATEGORY_NAME = "動画"
        private const val UPDATED_CATEGORY_NAME = "音楽"
        private const val SORT_SAME_ORDER_FIRST_NAME = "Alpha"
        private const val SORT_SAME_ORDER_SECOND_NAME = "Beta"
        private const val SORT_LATER_NAME = "Zulu"
        private const val ICON_NAME = "movie"
        private const val UPDATED_ICON_NAME = "music"
        private const val COLOR_HEX = "#0F6E56"
        private const val UPDATED_COLOR_HEX = "#9FE1CB"
        private const val FIRST_SORT_ORDER = 1
        private const val SORT_ORDER = 10
        private const val LATER_SORT_ORDER = 20
        private const val UPDATED_SORT_ORDER = 30
        private const val NOT_FOUND_ID = -1L
    }
}
