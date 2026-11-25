package com.grafito.finanzas.receipts

import com.grafito.finanzas.data.BcpMailSuggestion
import java.text.Normalizer
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

internal enum class BcpScreenStatus {
    VISIBLE, WINDOW_UNAVAILABLE, UNAVAILABLE, INCOMPLETE, DETECTED, DUPLICATE, ERROR
}

internal object BcpScreenParser {
    const val AMOUNT = 1
    const val DATE_TIME = 2
    const val OPERATION = 4
    const val OUTGOING = 8
    const val SUCCESS_TITLE = 16
    const val HISTORY_SCREEN = 32
    const val UNSUCCESSFUL_SCREEN = 64
    private val formatCharacters = Regex("\\p{Cf}+")
    private val success = Regex("\\boperaci[óo]n exitosa\\b!?", RegexOption.IGNORE_CASE)
    private val outgoing = Regex("\\benviado a\\b", RegexOption.IGNORE_CASE)
    private val outgoingField = Regex("^enviado a\\b", RegexOption.IGNORE_CASE)
    private val origin = Regex("^desde(?:$|\\s+(?:cuenta|tarjeta)\\b)", RegexOption.IGNORE_CASE)
    private val history = Regex("\\bdetalle de(?:l)? movimiento\\b", RegexOption.IGNORE_CASE)
    private val unsuccessful = Regex(
        "\\b(?:operaci[óo]n|pago|transferencia)\\s+(?:pendiente|programad[oa]|rechazad[oa]|cancelad[oa]|fallid[oa]|anulad[oa]|revertid[oa]|recibid[oa])\\b|" +
            "\\b(?:no se pudo|recibiste|recibido de)\\b",
        RegexOption.IGNORE_CASE
    )
    private val moment = Regex(
        "\\b([0-9]{1,2})\\s+(?:de\\s+)?(\\p{L}+)\\s+(?:de\\s+)?([0-9]{4})\\s*[-–]\\s*([0-9]{1,2}):([0-9]{2})\\s*([ap])\\.?\\s*m\\.?",
        RegexOption.IGNORE_CASE
    )
    private val lima = ZoneId.of("America/Lima")

    data class Result(
        val status: BcpScreenStatus,
        val fields: Int = 0,
        val payment: BcpMailSuggestion? = null,
        val signals: Int = 0
    )

    fun read(parts: List<String>): Result {
        val lines = parts.flatMap { part ->
            Normalizer.normalize(part, Normalizer.Form.NFKC).replace(formatCharacters, "").lines()
        }.map(BcpOperation::normalize).filter(String::isNotEmpty)
        val text = lines.joinToString(" ")
        if (text.isBlank()) return Result(BcpScreenStatus.UNAVAILABLE)
        val heading = success.find(text)
        var fields = 0
        if (heading != null) fields = fields or SUCCESS_TITLE
        if (history.containsMatchIn(text)) fields = fields or HISTORY_SCREEN
        if (unsuccessful.containsMatchIn(text)) fields = fields or UNSUCCESSFUL_SCREEN
        if (fields and (HISTORY_SCREEN or UNSUCCESSFUL_SCREEN) != 0) {
            return Result(BcpScreenStatus.VISIBLE, fields)
        }

        val dateMatch = moment.findAll(text).distinctBy { it.value.lowercase(Locale.ROOT) }.singleOrNull()
        val amountStart = heading?.range?.last?.plus(1) ?: 0
        val sentLabel = outgoing.find(text, amountStart)
        val amountEnd = dateMatch?.range?.first ?: sentLabel?.range?.first ?: text.length
        // The advertisement below the receipt can contain another price.
        val amount = if (amountEnd > amountStart) {
            BcpReceiptAmount.parse(text.substring(amountStart, amountEnd))
        } else null
        val operation = BcpOperation.number(text)
        val afterHeading = text.substring(amountStart).trimStart(' ', ':', '-', '–')
        val confirmedOutgoing = (lines.any(outgoingField::containsMatchIn) || outgoingField.containsMatchIn(afterHeading)) &&
            lines.any(origin::containsMatchIn)
        val time = dateMatch?.let { match ->
            runCatching {
                val (day, month, year, hourText, minute, period) = match.destructured
                val hour = hourText.toInt()
                require(hour in 1..12)
                val date = BcpOperation.writtenDate("$day de $month de $year")
                date.atTime(LocalTime.of(hour % 12 + if (period.equals("p", true)) 12 else 0, minute.toInt()))
            }.getOrNull()
        }
        if (amount != null) fields = fields or AMOUNT
        if (time != null) fields = fields or DATE_TIME
        if (operation != null) fields = fields or OPERATION
        if (confirmedOutgoing) fields = fields or OUTGOING
        if (heading == null) return Result(BcpScreenStatus.VISIBLE, fields)
        if (amount == null || operation == null || time == null || !confirmedOutgoing) {
            return Result(BcpScreenStatus.INCOMPLETE, fields)
        }
        val payment = BcpMailSuggestion(
            BcpOperation.fingerprint(time.toLocalDate(), operation),
            amount, time.atZone(lima).toInstant().toEpochMilli()
        )
        return Result(BcpScreenStatus.DETECTED, fields, payment)
    }
}
