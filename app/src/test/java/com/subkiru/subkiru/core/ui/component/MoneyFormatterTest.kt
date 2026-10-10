package com.subkiru.subkiru.core.ui.component

import com.subkiru.subkiru.core.domain.model.MoneyAmount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MoneyFormatterTest {

    @Test
    fun 金額リストが空の場合は0円と表示する() {
        val result = formatCurrencyAmounts(emptyList())

        assertEquals("¥0", result)
    }

    @Test
    fun JPYのみの場合は従来どおりの円表記になる() {
        val result = formatCurrencyAmounts(listOf(MoneyAmount(1_490L, "JPY")))

        assertEquals("¥1,490", result)
    }

    @Test
    fun 複数通貨の場合は通貨ごとにスラッシュ区切りで併記する() {
        val result = formatCurrencyAmounts(
            listOf(MoneyAmount(1_490L, "JPY"), MoneyAmount(999L, "USD")),
        )

        val parts = result.split(" / ")
        assertEquals("¥1,490", parts[0])
        assertEquals(2, parts.size)
        assertTrue(parts[1].contains("9.99"))
    }
}
