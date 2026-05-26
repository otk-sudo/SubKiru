package com.subkiru.subkiru.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SubKiruWidgetTest {

    @Nested
    inner class `JPY金額フォーマット` {

        @Test
        fun `should format 12345 as ¥12,345 when currencyCode is JPY`() {
            // Arrange（準備）
            val amountMinor = 12345L
            val currencyCode = "JPY"

            // Act（実行）
            val result: String = formatAmount(amountMinor, currencyCode)

            // Assert（検証）
            assertEquals("¥12,345", result)
        }

        @Test
        fun `should format 0 as ¥0 when amountMinor is zero`() {
            // Arrange（準備）
            val amountMinor = 0L
            val currencyCode = "JPY"

            // Act（実行）
            val result: String = formatAmount(amountMinor, currencyCode)

            // Assert（検証）
            assertEquals("¥0", result)
        }
    }

    @Nested
    inner class `日数表示フォーマット` {

        @ParameterizedTest(name = "daysUntil={0} → {1}")
        @CsvSource(
            "0, 今日",
            "1, 明日",
            "3, 3日後",
            "-1, 期限超過",
            "-5, 期限超過",
        )
        fun `should format daysUntil correctly for each case`(daysUntil: Long, expected: String) {
            // Arrange（準備）は @CsvSource で完了

            // Act（実行）
            val result: String = formatDaysUntil(daysUntil)

            // Assert（検証）
            assertEquals(expected, result)
        }
    }

    @Nested
    inner class `大きな金額フォーマット` {

        @Test
        fun `should format large amount with comma separators`() {
            // Arrange（準備）
            val amountMinor = 1234567L
            val currencyCode = "JPY"

            // Act（実行）
            val result: String = formatAmount(amountMinor, currencyCode)

            // Assert（検証）
            assertEquals("¥1,234,567", result)
        }
    }
}
