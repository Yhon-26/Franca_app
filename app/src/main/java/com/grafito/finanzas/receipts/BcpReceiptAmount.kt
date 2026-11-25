package com.grafito.finanzas.receipts

import com.grafito.finanzas.model.Money
import java.util.Locale

internal object BcpReceiptAmount {
    private val soles = Regex("\\bs\\s*/\\.?\\s*([0-9]+(?:[.,][0-9]+)*)(?![\\p{L}\\p{N}]|[.,][0-9]|\\s+[0-9])", RegexOption.IGNORE_CASE)
    private val usd = Regex("\\busd\\b|\\bus\\s*\\$|\\bd[oó]lares\\b|\\$\\s*[0-9]", RegexOption.IGNORE_CASE)
    private val label = Regex("^(?:monto(?: pagado| total| transferido)?|importe(?: pagado)?|total pagado)(?:\\s*[:.]|\\s+|$)", RegexOption.IGNORE_CASE)
    private val englishGrouping = Regex("[1-9][0-9]{0,2}(?:,[0-9]{3})+\\.[0-9]{1,2}")
    private val spanishGrouping = Regex("[1-9][0-9]{0,2}(?:\\.[0-9]{3})+,[0-9]{1,2}")

    fun parse(text: String): Long? {
        if (usd.containsMatchIn(text)) return null
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val amounts = amountsIn(text)
        val labeled = lines.flatMapIndexed { index, line ->
            if (!label.containsMatchIn(line.lowercase(Locale.ROOT))) emptyList()
            else amountsIn(line).ifEmpty { amountsIn(lines.getOrNull(index + 1).orEmpty()) }
        }.distinct()
        return if (labeled.isNotEmpty()) labeled.singleOrNull() else amounts.distinct().singleOrNull()
    }

    private fun amountsIn(text: String): List<Long?> = soles.findAll(text).map {
        val number = it.groupValues[1]
        Money.parse(when {
            englishGrouping.matches(number) -> number.replace(",", "")
            spanishGrouping.matches(number) -> number.replace(".", "")
            else -> number
        })
    }.toList()
}
