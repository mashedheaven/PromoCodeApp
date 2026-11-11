package com.promocodeapp.core.model

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    fun format(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        runCatching { format.currency = Currency.getInstance(currencyCode) }
        if (amount % 1.0 == 0.0) format.maximumFractionDigits = 0
        return format.format(amount)
    }

    fun defaultCurrencyCode(locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("USD")

    /** Parses user-typed decimals, accepting either "," or "." as separator. */
    fun parse(text: String): Double? =
        text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}
