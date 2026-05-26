package com.subkiru.subkiru.core.data.db

import com.subkiru.subkiru.core.data.db.entity.CategoryEntity
import com.subkiru.subkiru.core.data.db.entity.ServiceTemplateEntity
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * DatabasePrepopulator のユニットテスト。
 * JSON パース関数（parseCategoriesJson / parseServiceTemplatesJson）を
 * Context 不要な純粋関数として検証する。
 */
class DatabasePrepopulatorTest {

    // ---------------------------------------------------------------
    // parseCategoriesJson
    // ---------------------------------------------------------------

    @Nested
    inner class ParseCategoriesJsonTests {

        @Test
        fun `should parse category list correctly when valid json is given`() {
            // Arrange（準備）
            val json = """
                [
                  {
                    "id": 1,
                    "name": "動画配信",
                    "icon_name": "video",
                    "color_hex": "#E05C5C",
                    "sort_order": 1
                  },
                  {
                    "id": 2,
                    "name": "音楽",
                    "icon_name": "music",
                    "color_hex": "#9FE1CB",
                    "sort_order": 2
                  }
                ]
            """.trimIndent()

            // Act（実行）
            val result: List<CategoryEntity> = DatabasePrepopulator.parseCategoriesJson(json)

            // Assert（検証）
            assertEquals(2, result.size)
        }

        @Test
        fun `should map all fields correctly when valid category json is given`() {
            // Arrange（準備）
            val json = """
                [
                  {
                    "id": 3,
                    "name": "クラウドストレージ",
                    "icon_name": "cloud",
                    "color_hex": "#6AADA0",
                    "sort_order": 3
                  }
                ]
            """.trimIndent()

            // Act（実行）
            val result: List<CategoryEntity> = DatabasePrepopulator.parseCategoriesJson(json)
            val entity: CategoryEntity = result[0]

            // Assert（検証）
            assertEquals(3L, entity.id)
            assertEquals("クラウドストレージ", entity.name)
            assertEquals("cloud", entity.iconName)
            assertEquals("#6AADA0", entity.colorHex)
            assertEquals(3, entity.sortOrder)
        }

        @Test
        fun `should return empty list when invalid json is given`() {
            // Arrange（準備）
            val invalidJson = "not a json"

            // Act（実行）
            val result: List<CategoryEntity> = DatabasePrepopulator.parseCategoriesJson(invalidJson)

            // Assert（検証）
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return empty list when empty json array is given`() {
            // Arrange（準備）: 空の JSON 配列
            val emptyArrayJson = "[]"

            // Act（実行）
            val result: List<CategoryEntity> = DatabasePrepopulator.parseCategoriesJson(emptyArrayJson)

            // Assert（検証）
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return empty list when required field name is missing`() {
            // Arrange（準備）: 必須フィールド name が欠落している JSON
            val missingNameJson = """
                [
                  {
                    "id": 1,
                    "icon_name": "video",
                    "color_hex": "#E05C5C",
                    "sort_order": 1
                  }
                ]
            """.trimIndent()

            // Act（実行）
            val result: List<CategoryEntity> = DatabasePrepopulator.parseCategoriesJson(missingNameJson)

            // Assert（検証）: JSONException がスローされ、空リストが返ること
            assertTrue(result.isEmpty())
        }
    }

    // ---------------------------------------------------------------
    // parseServiceTemplatesJson
    // ---------------------------------------------------------------

    @Nested
    inner class ParseServiceTemplatesJsonTests {

        @Test
        fun `should parse service template list correctly when valid json is given`() {
            // Arrange（準備）
            val json = """
                [
                  {
                    "name": "Netflix",
                    "default_amount_minor": 1590,
                    "logo_resource_name": "ic_service_default",
                    "category_id": 1,
                    "search_keywords": "netflix,ネットフリックス"
                  },
                  {
                    "name": "Spotify",
                    "default_amount_minor": 980,
                    "logo_resource_name": "ic_service_default",
                    "category_id": 2,
                    "search_keywords": "spotify,スポティファイ"
                  }
                ]
            """.trimIndent()

            // Act（実行）
            val result: List<ServiceTemplateEntity> = DatabasePrepopulator.parseServiceTemplatesJson(json)

            // Assert（検証）
            assertEquals(2, result.size)
        }

        @Test
        fun `should map all fields correctly when valid service template json is given`() {
            // Arrange（準備）
            val json = """
                [
                  {
                    "name": "Netflix",
                    "default_amount_minor": 1590,
                    "default_currency_code": "JPY",
                    "default_interval_unit": "MONTHLY",
                    "default_interval_count": 1,
                    "logo_resource_name": "ic_service_default",
                    "category_id": 1,
                    "search_keywords": "netflix,ネットフリックス"
                  }
                ]
            """.trimIndent()

            // Act（実行）
            val result: List<ServiceTemplateEntity> = DatabasePrepopulator.parseServiceTemplatesJson(json)
            val entity: ServiceTemplateEntity = result[0]

            // Assert（検証）
            assertEquals("Netflix", entity.name)
            assertEquals(1590L, entity.defaultAmountMinor)
            assertEquals("JPY", entity.defaultCurrencyCode)
            assertEquals(BillingIntervalUnit.MONTHLY, entity.defaultIntervalUnit)
            assertEquals(1, entity.defaultIntervalCount)
            assertEquals("ic_service_default", entity.logoResourceName)
            assertEquals(1L, entity.categoryId)
            assertEquals("netflix,ネットフリックス", entity.searchKeywords)
        }

        @Test
        fun `should apply default values correctly when optional fields are absent`() {
            // Arrange（準備）: オプション項目（currency_code / interval_unit / interval_count）を省略
            val json = """
                [
                  {
                    "name": "TestService",
                    "default_amount_minor": 500,
                    "logo_resource_name": "ic_service_default",
                    "category_id": 7,
                    "search_keywords": "test"
                  }
                ]
            """.trimIndent()

            // Act（実行）
            val result: List<ServiceTemplateEntity> = DatabasePrepopulator.parseServiceTemplatesJson(json)
            val entity: ServiceTemplateEntity = result[0]

            // Assert（検証）: デフォルト値が適用されること
            assertEquals(ServiceTemplateEntity.DEFAULT_CURRENCY_CODE, entity.defaultCurrencyCode)
            assertEquals(ServiceTemplateEntity.DEFAULT_INTERVAL_UNIT_ENUM, entity.defaultIntervalUnit)
            assertEquals(ServiceTemplateEntity.DEFAULT_INTERVAL_COUNT, entity.defaultIntervalCount)
        }

        @Test
        fun `should return empty list when invalid json is given`() {
            // Arrange（準備）
            val invalidJson = "{ broken json ]["

            // Act（実行）
            val result: List<ServiceTemplateEntity> = DatabasePrepopulator.parseServiceTemplatesJson(invalidJson)

            // Assert（検証）
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return empty list when empty json array is given`() {
            // Arrange（準備）: 空の JSON 配列
            val emptyArrayJson = "[]"

            // Act（実行）
            val result: List<ServiceTemplateEntity> = DatabasePrepopulator.parseServiceTemplatesJson(emptyArrayJson)

            // Assert（検証）
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should fallback to default interval unit when unknown interval_unit value is given`() {
            // Arrange（準備）: BillingIntervalUnit に存在しない "BIWEEKLY" を指定
            val json = """
                [
                  {
                    "name": "TestService",
                    "default_amount_minor": 500,
                    "default_interval_unit": "BIWEEKLY",
                    "logo_resource_name": "ic_service_default",
                    "category_id": 1,
                    "search_keywords": "test"
                  }
                ]
            """.trimIndent()

            // Act（実行）
            val result: List<ServiceTemplateEntity> = DatabasePrepopulator.parseServiceTemplatesJson(json)
            val entity: ServiceTemplateEntity = result[0]

            // Assert（検証）: 不明な値は MONTHLY にフォールバックすること
            assertEquals(ServiceTemplateEntity.DEFAULT_INTERVAL_UNIT_ENUM, entity.defaultIntervalUnit)
        }

        @Test
        fun `should return empty list when required field name is missing`() {
            // Arrange（準備）: 必須フィールド name が欠落している JSON
            val missingNameJson = """
                [
                  {
                    "default_amount_minor": 500,
                    "logo_resource_name": "ic_service_default",
                    "category_id": 1,
                    "search_keywords": "test"
                  }
                ]
            """.trimIndent()

            // Act（実行）
            val result: List<ServiceTemplateEntity> = DatabasePrepopulator.parseServiceTemplatesJson(missingNameJson)

            // Assert（検証）: JSONException がスローされ、空リストが返ること
            assertTrue(result.isEmpty())
        }
    }
}
