package com.subkiru.subkiru.core.domain.model

// 最小通貨単位の金額と通貨コードの組
data class MoneyAmount(
    val amountMinor: Long,
    val currencyCode: String,
)

const val DEFAULT_CURRENCY_CODE = "JPY"

// 通貨の表示順。JPY を先頭にし、それ以外は通貨コード順にする
internal val currencyCodeComparator: Comparator<String> =
    compareBy<String> { it != DEFAULT_CURRENCY_CODE }.thenBy { it }

fun List<MoneyAmount>.multipliedBy(factor: Long): List<MoneyAmount> {
    return map { it.copy(amountMinor = it.amountMinor * factor) }
}
