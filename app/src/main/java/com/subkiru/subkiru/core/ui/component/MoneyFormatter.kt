package com.subkiru.subkiru.core.ui.component

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

internal fun formatCurrencyAmount(
    amountMinor: Long,
    currencyCode: String,
): String {
    if (currencyCode == JPY_CURRENCY_CODE) return formatAmountJpy(amountMinor)

    val currency = try {
        Currency.getInstance(currencyCode)
    } catch (_: IllegalArgumentException) {
        return "$currencyCode ${NumberFormat.getNumberInstance(Locale.JAPAN).format(amountMinor)}"
    }
    val fractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
    val amount = BigDecimal.valueOf(amountMinor).movePointLeft(fractionDigits)
    return NumberFormat.getCurrencyInstance(Locale.JAPAN).run {
        this.currency = currency
        format(amount)
    }
}

private const val JPY_CURRENCY_CODE = "JPY"
