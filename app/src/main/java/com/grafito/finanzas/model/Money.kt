package com.grafito.finanzas.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Los importes se guardan SIEMPRE como centavos enteros, nunca como Double. */
object Money {
    const val MAX_CENTS = 99_999_999_999L
    fun parse(text: String): Long? {
        val normalized = text.trim().replace(',', '.')
        if (!normalized.matches(Regex("\\d{1,9}(\\.\\d{1,2})?"))) return null
        return try {
            val cents = BigDecimal(normalized).multiply(BigDecimal(100))
                .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
            cents.takeIf { it > 0 }
        } catch (_: ArithmeticException) { null }
    }

    fun format(cents: Long): String {
        val formatter = NumberFormat.getNumberInstance(Locale("es", "PE"))
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return "S/ ${formatter.format(BigDecimal.valueOf(cents, 2))}"
    }
}
