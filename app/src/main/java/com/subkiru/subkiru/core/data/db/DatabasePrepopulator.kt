package com.subkiru.subkiru.core.data.db

import android.content.Context
import android.util.Log
import com.subkiru.subkiru.core.data.db.dao.CategoryDao
import com.subkiru.subkiru.core.data.db.dao.ServiceTemplateDao
import com.subkiru.subkiru.core.data.db.entity.CategoryEntity
import com.subkiru.subkiru.core.data.db.entity.ServiceTemplateEntity
import com.subkiru.subkiru.core.domain.model.BillingIntervalUnit
import org.json.JSONArray
import org.json.JSONException

/**
 * DB 初回作成時にカテゴリとサービステンプレートの初期データを投入するクラス。
 * assets 内の JSON ファイルを読み込み、DAO 経由で Room に挿入する。
 */
object DatabasePrepopulator {

    private const val TAG = "DatabasePrepopulator"

    /**
     * カテゴリとサービステンプレートを assets の JSON から読み込み、DB に挿入する。
     * カテゴリを先に挿入することで FK 制約を満たす。
     *
     * @param context  assets アクセスに使用する Context
     * @param categoryDao  カテゴリ挿入用 DAO
     * @param serviceTemplateDao  サービステンプレート挿入用 DAO
     */
    suspend fun populate(
        context: Context,
        categoryDao: CategoryDao,
        serviceTemplateDao: ServiceTemplateDao,
    ) {
        // カテゴリ JSON を読み込んでパース
        val categoriesJson = context.assets
            .open("categories.json")
            .bufferedReader()
            .use { it.readText() }

        // サービステンプレート JSON を読み込んでパース
        val templatesJson = context.assets
            .open("service_templates.json")
            .bufferedReader()
            .use { it.readText() }

        val categories: List<CategoryEntity> = parseCategoriesJson(categoriesJson)

        // カテゴリが空の場合は FK 制約でテンプレート挿入が失敗するため早期リターン
        if (categories.isEmpty()) {
            Log.w(TAG, "カテゴリデータが空のため初期データ投入をスキップします")
            return
        }

        val templates: List<ServiceTemplateEntity> = parseServiceTemplatesJson(templatesJson)

        // FK 制約のためカテゴリを先に挿入する
        categoryDao.addCategories(categories)
        serviceTemplateDao.addTemplates(templates)
    }

    /**
     * カテゴリ JSON 文字列をパースして [CategoryEntity] のリストを返す。
     * パースに失敗した場合は空リストを返す。
     *
     * テスト容易性のため internal 可視性にして純粋関数として公開する。
     */
    internal fun parseCategoriesJson(json: String): List<CategoryEntity> {
        return try {
            val array = JSONArray(json)
            // Kotlin 慣用的な map で変換する
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                CategoryEntity(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    iconName = obj.getString("icon_name"),
                    colorHex = obj.getString("color_hex"),
                    sortOrder = obj.getInt("sort_order"),
                )
            }
        } catch (e: JSONException) {
            // JSON の形式が不正な場合はログを出力して空リストを返す
            Log.e(TAG, "カテゴリ JSON のパースに失敗しました", e)
            emptyList()
        }
    }

    /**
     * サービステンプレート JSON 文字列をパースして [ServiceTemplateEntity] のリストを返す。
     * オプションフィールド（currency_code / interval_unit / interval_count）が
     * 省略された場合はエンティティのデフォルト値を使用する。
     * パースに失敗した場合は空リストを返す。
     *
     * テスト容易性のため internal 可視性にして純粋関数として公開する。
     */
    internal fun parseServiceTemplatesJson(json: String): List<ServiceTemplateEntity> {
        return try {
            val array = JSONArray(json)
            // Kotlin 慣用的な map で変換する
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)

                // interval_unit が指定されている場合のみ変換する。不正値は MONTHLY にフォールバック。
                val intervalUnit: BillingIntervalUnit = if (obj.has("default_interval_unit")) {
                    BillingIntervalUnit.entries.find { it.name == obj.getString("default_interval_unit") }
                        ?: ServiceTemplateEntity.DEFAULT_INTERVAL_UNIT_ENUM
                } else {
                    ServiceTemplateEntity.DEFAULT_INTERVAL_UNIT_ENUM
                }

                ServiceTemplateEntity(
                    name = obj.getString("name"),
                    defaultAmountMinor = obj.getLong("default_amount_minor"),
                    defaultCurrencyCode = if (obj.has("default_currency_code")) {
                        obj.getString("default_currency_code")
                    } else {
                        ServiceTemplateEntity.DEFAULT_CURRENCY_CODE
                    },
                    defaultIntervalUnit = intervalUnit,
                    defaultIntervalCount = if (obj.has("default_interval_count")) {
                        obj.getInt("default_interval_count")
                    } else {
                        ServiceTemplateEntity.DEFAULT_INTERVAL_COUNT
                    },
                    logoResourceName = obj.getString("logo_resource_name"),
                    categoryId = obj.getLong("category_id"),
                    searchKeywords = obj.getString("search_keywords"),
                    domain = if (obj.has("domain")) obj.getString("domain") else "",
                )
            }
        } catch (e: JSONException) {
            // JSON の形式が不正な場合はログを出力して空リストを返す
            Log.e(TAG, "サービステンプレート JSON のパースに失敗しました", e)
            emptyList()
        }
    }
}
