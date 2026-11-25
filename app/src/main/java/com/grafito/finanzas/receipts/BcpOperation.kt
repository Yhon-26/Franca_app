package com.grafito.finanzas.receipts

import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale

internal object BcpOperation {
    const val WRITTEN_DAY = "[0-9]{1,2} de \\p{L}+ de [0-9]{4}"
    private val bank = Regex("\\b(?:BCP|Banco de Cr[eé]dito del Per[uú])\\b", RegexOption.IGNORE_CASE)
    private val operationLabel = Regex(
        "(?:\\bn[úu]mero|\\bn[úu]m\\.?|\\bnro\\.?|\\bn\\.?[°º]) (?:de )?operaci[óo]n\\s*:?\\s*([0-9]{4,24})(?![\\p{L}\\p{N}]|[.,/-][0-9]|[ ]+[0-9])",
        RegexOption.IGNORE_CASE
    )
    private val receiptDay = Regex(
        "\\bfecha(?: y hora| de (?:la )?operaci[óo]n)?\\s*:?\\s*($WRITTEN_DAY|[0-9]{1,2}/[0-9]{1,2}/[0-9]{4})(?![0-9])",
        RegexOption.IGNORE_CASE
    )
    private val writtenFormat = DateTimeFormatterBuilder().parseCaseInsensitive()
        .appendPattern("d 'de' MMMM 'de' uuuu")
        .toFormatter(Locale.forLanguageTag("es-PE")).withResolverStyle(ResolverStyle.STRICT)
    private val numericFormat = DateTimeFormatterBuilder().appendPattern("d/M/uuuu")
        .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

    fun normalize(text: String): String = text.replace(Regex("[\\s\\p{Z}]+"), " ").trim()

    fun number(text: String): String? =
        operationLabel.findAll(text).map { it.groupValues[1] }.distinct().singleOrNull()

    fun writtenDate(day: String): LocalDate = LocalDate.parse(day, writtenFormat)

    fun fingerprint(day: LocalDate, operation: String): String =
        // Keep the identifier already stored by the Gmail pilot.
        MessageDigest.getInstance("SHA-256").digest("bcp-mail:$day:$operation".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun fromReceipt(receipt: String): String? {
        val text = normalize(receipt)
        if (!bank.containsMatchIn(text)) return null
        val operation = number(text) ?: return null
        val days = receiptDay.findAll(text).map { match ->
            val day = match.groupValues[1]
            runCatching { if ('/' in day) LocalDate.parse(day, numericFormat) else writtenDate(day) }.getOrNull()
        }.distinct()
        val day = days.singleOrNull() ?: return null
        return fingerprint(day, operation)
    }
}
