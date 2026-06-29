package com.subkiru.subkiru.core.data.db.dao

import android.content.Context
import app.cash.turbine.test
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subkiru.subkiru.core.data.db.SubKiruDatabase
import com.subkiru.subkiru.core.data.db.entity.CategoryEntity
import com.subkiru.subkiru.core.data.db.entity.ServiceTemplateEntity
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceTemplateDaoTest {
    private lateinit var database: SubKiruDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var dao: ServiceTemplateDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SubKiruDatabase::class.java).build()
        categoryDao = database.categoryDao()
        dao = database.serviceTemplateDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun テンプレートを追加して取得できる() = runBlocking {
        val categoryId = addCategory()
        val id = dao.addTemplate(template = template(categoryId = categoryId))

        val actual = requireNotNull(dao.getTemplateById(id))

        assertEquals(TEMPLATE_NAME, actual.name)
        assertEquals(DEFAULT_AMOUNT_MINOR, actual.defaultAmountMinor)
        assertEquals(DEFAULT_CURRENCY_CODE, actual.defaultCurrencyCode)
        assertEquals(BillingIntervalUnit.MONTHLY, actual.defaultIntervalUnit)
        assertEquals(DEFAULT_INTERVAL_COUNT, actual.defaultIntervalCount)
        assertEquals(LOGO_RESOURCE_NAME, actual.logoResourceName)
        assertEquals(categoryId, actual.categoryId)
        assertEquals(SEARCH_KEYWORDS, actual.searchKeywords)
    }

    @Test
    fun observeAllTemplatesのソート順が正しい() = runBlocking {
        val categoryId = addCategory()
        val secondId = dao.addTemplate(template = template(name = SORT_SECOND_NAME, categoryId = categoryId))
        val firstId = dao.addTemplate(template = template(name = SORT_FIRST_NAME, categoryId = categoryId))
        val thirdId = dao.addTemplate(template = template(name = SORT_THIRD_NAME, categoryId = categoryId))

        val actualIds = dao.observeAllTemplates().first().map { it.id }

        assertEquals(listOf(firstId, secondId, thirdId), actualIds)
    }

    @Test
    fun addTemplatesで一括追加できる() = runBlocking {
        val categoryId = addCategory()

        dao.addTemplates(
            templates = listOf(
                template(name = SORT_SECOND_NAME, categoryId = categoryId),
                template(name = SORT_FIRST_NAME, categoryId = categoryId),
            )
        )

        assertEquals(listOf(SORT_FIRST_NAME, SORT_SECOND_NAME), dao.observeAllTemplates().first().map { it.name })
    }

    @Test
    fun searchTemplatesで名前から検索できる() = runBlocking {
        val categoryId = addCategory()
        val targetId = dao.addTemplate(template = template(name = TEMPLATE_NAME, categoryId = categoryId))
        dao.addTemplate(
            template = template(
                name = OTHER_TEMPLATE_NAME,
                categoryId = categoryId,
                searchKeywords = OTHER_SEARCH_KEYWORDS,
            )
        )

        val actualIds = dao.searchTemplates(NAME_QUERY).first().map { it.id }

        assertEquals(listOf(targetId), actualIds)
    }

    @Test
    fun searchTemplatesでキーワードから検索できる() = runBlocking {
        val categoryId = addCategory()
        val targetId = dao.addTemplate(
            template = template(searchKeywords = KEYWORD_MATCH_TEXT, categoryId = categoryId)
        )

        val actualIds = dao.searchTemplates(KEYWORD_QUERY).first().map { it.id }

        assertEquals(listOf(targetId), actualIds)
    }

    @Test
    fun searchTemplatesで該当なしの場合は空リストを返す() = runBlocking {
        val categoryId = addCategory()
        dao.addTemplate(template = template(categoryId = categoryId))

        assertEquals(emptyList<ServiceTemplateEntity>(), dao.searchTemplates(NOT_MATCH_QUERY).first())
    }

    @Test
    fun getTemplateByIdに存在しないIDを渡すとnullが返る() = runBlocking {
        assertNull(dao.getTemplateById(NOT_FOUND_ID))
    }

    @Test
    fun テンプレート追加後にFlowが更新を通知する() = runBlocking {
        val categoryId = addCategory()
        dao.observeAllTemplates().test {
            assertEquals(emptyList<ServiceTemplateEntity>(), awaitItem())

            val id = dao.addTemplate(template = template(categoryId = categoryId))

            assertEquals(listOf(id), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun addCategory(): Long {
        return categoryDao.addCategory(
            category = CategoryEntity(
                name = CATEGORY_NAME,
                iconName = ICON_NAME,
                colorHex = COLOR_HEX,
                sortOrder = SORT_ORDER,
            )
        )
    }

    private fun template(
        id: Long = ServiceTemplateEntity.UNSAVED_ID,
        name: String = TEMPLATE_NAME,
        defaultAmountMinor: Long = DEFAULT_AMOUNT_MINOR,
        defaultCurrencyCode: String = DEFAULT_CURRENCY_CODE,
        defaultIntervalUnit: BillingIntervalUnit = BillingIntervalUnit.MONTHLY,
        defaultIntervalCount: Int = DEFAULT_INTERVAL_COUNT,
        logoResourceName: String = LOGO_RESOURCE_NAME,
        categoryId: Long,
        searchKeywords: String = SEARCH_KEYWORDS,
    ): ServiceTemplateEntity {
        return ServiceTemplateEntity(
            id = id,
            name = name,
            defaultAmountMinor = defaultAmountMinor,
            defaultCurrencyCode = defaultCurrencyCode,
            defaultIntervalUnit = defaultIntervalUnit,
            defaultIntervalCount = defaultIntervalCount,
            logoResourceName = logoResourceName,
            categoryId = categoryId,
            searchKeywords = searchKeywords,
        )
    }

    companion object {
        private const val CATEGORY_NAME = "動画"
        private const val ICON_NAME = "movie"
        private const val COLOR_HEX = "#0F6E56"
        private const val SORT_ORDER = 1
        private const val TEMPLATE_NAME = "Netflix"
        private const val OTHER_TEMPLATE_NAME = "Spotify"
        private const val SORT_FIRST_NAME = "Amazon Prime"
        private const val SORT_SECOND_NAME = "Netflix"
        private const val SORT_THIRD_NAME = "YouTube Premium"
        private const val DEFAULT_AMOUNT_MINOR = 1_490L
        private const val DEFAULT_CURRENCY_CODE = "JPY"
        private const val DEFAULT_INTERVAL_COUNT = 1
        private const val LOGO_RESOURCE_NAME = "ic_netflix"
        private const val SEARCH_KEYWORDS = "netflix,ネトフリ,動画"
        private const val OTHER_SEARCH_KEYWORDS = "spotify,音楽,ストリーミング"
        private const val KEYWORD_MATCH_TEXT = "music,音楽,streaming"
        private const val NAME_QUERY = "Net"
        private const val KEYWORD_QUERY = "音楽"
        private const val NOT_MATCH_QUERY = "not_found"
        private const val NOT_FOUND_ID = -1L
    }
}
