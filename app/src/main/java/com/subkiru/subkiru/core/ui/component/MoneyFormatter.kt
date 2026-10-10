package com.subkiru.subkiru.core.ui.component

import com.subkiru.subkiru.core.domain.model.MoneyAmount
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
        // setCurrency だけでは小数桁が JPY のまま（0桁）になるため、通貨の桁数に合わせる
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        format(amount)
    }
}

// 通貨ごとの金額を併記する。JPY のみなら従来どおりの表記で、空の場合は 0 円とする
internal fun formatCurrencyAmounts(amounts: List<MoneyAmount>): String {
    if (amounts.isEmpty()) return formatAmountJpy(0L)
    return amounts.joinToString(separator = CURRENCY_SEPARATOR) { amount ->
        formatCurrencyAmount(amount.amountMinor, amount.currencyCode)
    }
}

private const val JPY_CURRENCY_CODE = "JPY"
private const val CURRENCY_SEPARATOR = " / "
