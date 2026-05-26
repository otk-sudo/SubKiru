package com.subkiru.subkiru.core.data.repository

import app.cash.turbine.test
import com.subkiru.subkiru.core.data.db.dao.ServiceTemplateDao
import com.subkiru.subkiru.core.data.db.entity.ServiceTemplateEntity
import com.subkiru.subkiru.core.domain.model.BillingInterval
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import com.subkiru.subkiru.core.domain.model.ServiceTemplate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ServiceTemplateRepositoryImplTest {
    private val dao = mockk<ServiceTemplateDao>()
    private val repository = ServiceTemplateRepositoryImpl(dao)

    @Test
    fun observeAllTemplatesでEntityがDomainModelに変換される() = runTest {
        every { dao.observeAllTemplates() } returns flowOf(listOf(serviceTemplateEntity()))

        repository.observeAllTemplates().test {
            val actual = awaitItem().single()
            assertEquals(serviceTemplate(), actual)
            assertEquals(BillingInterval(BillingIntervalUnit.MONTHLY, DEFAULT_INTERVAL_COUNT), actual.defaultInterval)
            awaitComplete()
        }
    }

    @Test
    fun getTemplateByIdでnullの場合はnullを返す() = runTest {
        coEvery { dao.getTemplateById(TEMPLATE_ID) } returns null

        assertNull(repository.getTemplateById(TEMPLATE_ID))
    }

    @Test
    fun getTemplateByIdでEntityがDomainModelに変換される() = runTest {
        coEvery { dao.getTemplateById(TEMPLATE_ID) } returns serviceTemplateEntity()

        assertEquals(serviceTemplate(), repository.getTemplateById(TEMPLATE_ID))
    }

    @Test
    fun searchTemplatesでEntityがDomainModelに変換される() = runTest {
        every { dao.searchTemplates(QUERY) } returns flowOf(listOf(serviceTemplateEntity()))

        repository.searchTemplates(QUERY).test {
            assertEquals(listOf(serviceTemplate()), awaitItem())
            awaitComplete()
        }
    }

    private fun serviceTemplate(): ServiceTemplate {
        return ServiceTemplate(
            id = TEMPLATE_ID,
            name = TEMPLATE_NAME,
            defaultAmountMinor = DEFAULT_AMOUNT_MINOR,
            defaultCurrencyCode = DEFAULT_CURRENCY_CODE,
            defaultInterval = BillingInterval(BillingIntervalUnit.MONTHLY, DEFAULT_INTERVAL_COUNT),
            logoResourceName = LOGO_RESOURCE_NAME,
            categoryId = CATEGORY_ID,
            searchKeywords = SEARCH_KEYWORDS,
        )
    }

    private fun serviceTemplateEntity(): ServiceTemplateEntity {
        return ServiceTemplateEntity(
            id = TEMPLATE_ID,
            name = TEMPLATE_NAME,
            defaultAmountMinor = DEFAULT_AMOUNT_MINOR,
            defaultCurrencyCode = DEFAULT_CURRENCY_CODE,
            defaultIntervalUnit = BillingIntervalUnit.MONTHLY,
            defaultIntervalCount = DEFAULT_INTERVAL_COUNT,
            logoResourceName = LOGO_RESOURCE_NAME,
            categoryId = CATEGORY_ID,
            searchKeywords = SEARCH_KEYWORDS,
        )
    }

    companion object {
        private const val TEMPLATE_ID = 1L
        private const val TEMPLATE_NAME = "Netflix"
        private const val DEFAULT_AMOUNT_MINOR = 1_490L
        private const val DEFAULT_CURRENCY_CODE = "JPY"
        private const val DEFAULT_INTERVAL_COUNT = 1
        private const val LOGO_RESOURCE_NAME = "ic_netflix"
        private const val CATEGORY_ID = 2L
        private const val SEARCH_KEYWORDS = "netflix,動画"
        private const val QUERY = "net"
    }
}
