package com.grafito.finanzas.model

import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

internal object YapeSentParser {
    private val marks = Regex("\\p{M}+")
    private val spaces = Regex("[\\s\\p{Z}]+")
    private val headings = setOf("yapeaste", "yapeaste el servicio", "yapeo exitoso")
    private val finishButtons = setOf("ir a inicio", "ir al inicio", "volver al inicio", "yapear otro servicio")
    private val amountLine = Regex("(?:monto(?: enviado| pagado)?\\s*:?\\s*)?s\\s*/\\.?\\s*([0-9]+(?:[.,][0-9]+)*)")
    private val spokenAmountLine = Regex("([0-9]+(?:[.,][0-9]+)*) soles?")
    private val dateLine = Regex("([0-9]{1,2}) (ene|feb|mar|abr|may|jun|jul|ago|set|sep|sept|oct|nov|dic)\\.? ([0-9]{4})")
    private val clockLine = Regex("([0-9]{1,2}):([0-9]{2}) ([ap])\\.? ?m\\.?")
    private val months = mapOf(
        "ene" to 1, "feb" to 2, "mar" to 3, "abr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "ago" to 8, "set" to 9, "sep" to 9, "sept" to 9,
        "oct" to 10, "nov" to 11, "dic" to 12
    )
    private val lima = ZoneId.of("America/Lima")
    private val operationLabel = Regex("(?:numero|num\\.?|nro\\.?|n\\.?[°ºo]) (?:de )?operacion\\s*:?\\s*([0-9]{4,24})?")
    private val digits = Regex("[0-9]{4,24}")
    private val englishGrouping = Regex("[1-9][0-9]{0,2}(?:,[0-9]{3})+\\.[0-9]{1,2}")
    private val spanishGrouping = Regex("[1-9][0-9]{0,2}(?:\\.[0-9]{3})+,[0-9]{1,2}")

    data class Result(val status: YapeSentStatus, val payment: Payment? = null)
    data class Payment(val id: String, val amountCents: Long, val occurredAt: Long? = null)

    fun read(parts: List<String>): Result {
        val lines = parts.flatMap { it.lines() }.map { line ->
            spaces.replace(marks.replace(Normalizer.normalize(line, Normalizer.Form.NFD), "")
                .lowercase(Locale.ROOT), " ").trim()
        }.filter(String::isNotBlank)
        val labels = lines.map { it.trim(' ', '¡', '!') }
        if (labels.none { it in headings }) return Result(YapeSentStatus.YAPE_VISIBLE)
        val hasFinishButton = labels.any { it in finishButtons }
        if (!hasFinishButton && "cerrar" !in labels) return Result(YapeSentStatus.INCOMPLETE)

        val amounts = lines.flatMapIndexed { index, line ->
            val value = amountLine.matchEntire(line)?.groupValues?.get(1)
                ?: spokenAmountLine.matchEntire(line)?.groupValues?.get(1)
                ?: if (line in setOf("s/", "s/.")) {
                    lines.getOrNull(index + 1)?.let { amountLine.matchEntire("s/ $it")?.groupValues?.get(1) }
                } else null
            if (value == null) emptyList() else listOf(parseAmount(value))
        }
        val amount = amounts.distinct().singleOrNull() ?: return Result(YapeSentStatus.INCOMPLETE)
        val operation = lines.mapIndexedNotNull { index, line ->
            val match = operationLabel.matchEntire(line) ?: return@mapIndexedNotNull null
            match.groups[1]?.value ?: lines.getOrNull(index + 1)?.takeIf(digits::matches)
        }.distinct().singleOrNull() ?: return Result(YapeSentStatus.INCOMPLETE)
        val occurredAt = receiptTime(lines)
        if (!hasFinishButton && occurredAt == null) return Result(YapeSentStatus.INCOMPLETE)

        val id = MessageDigest.getInstance("SHA-256").digest("yape-sent:$operation".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return Result(YapeSentStatus.DETECTED, Payment(id, amount, occurredAt))
    }

    private fun receiptTime(lines: List<String>): Long? {
        val date = lines.mapNotNull(dateLine::matchEntire).map { match ->
            runCatching {
                val (day, month, year) = match.destructured
                LocalDate.of(year.toInt(), months.getValue(month), day.toInt())
            }.getOrNull()
        }.distinct().singleOrNull() ?: return null
        val time = lines.mapNotNull(clockLine::matchEntire).map { match ->
            runCatching {
                val (hourText, minute, period) = match.destructured
                val hour = hourText.toInt()
                require(hour in 1..12)
                LocalTime.of(hour % 12 + if (period == "p") 12 else 0, minute.toInt())
            }.getOrNull()
        }.distinct().singleOrNull() ?: return null
        return date.atTime(time).atZone(lima).toInstant().toEpochMilli()
    }

    private fun parseAmount(value: String): Long? = Money.parse(when {
        englishGrouping.matches(value) -> value.replace(",", "")
        spanishGrouping.matches(value) -> value.replace(".", "")
        else -> value
    })
}

internal enum class YapeSentStatus { YAPE_VISIBLE, WINDOW_UNAVAILABLE, UNAVAILABLE, INCOMPLETE, DETECTED, DUPLICATE, ERROR }
