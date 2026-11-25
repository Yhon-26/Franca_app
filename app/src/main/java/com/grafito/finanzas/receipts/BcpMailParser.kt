package com.grafito.finanzas.receipts

import com.grafito.finanzas.data.BcpMailSuggestion
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

internal object BcpMailParser {
    private val bankDomains = setOf("notificacionesbcp.com.pe", "viabcp.com", "bcp.com.pe")
    val senderQuery = bankDomains.joinToString(" ", "{", "}") { "from:($it)" }
    private const val solesAmount = "(S\\s*/\\.?\\s*[0-9]+(?:[.,][0-9]+)*)"
    private val outgoingAmount = Regex("\\b(?:monto|importe) (?:enviado|pagado|transferido)\\s*:?\\s*$solesAmount", RegexOption.IGNORE_CASE)
    private val genericAmount = Regex("\\b(?:monto|importe)\\s*:?\\s*$solesAmount", RegexOption.IGNORE_CASE)
    private val outgoingAmountLabel = Regex("\\b(?:monto|importe) (?:enviado|pagado|transferido)\\b", RegexOption.IGNORE_CASE)
    private val dateLabel = Regex("\\bfecha y hora\\s*:?\\s*(${BcpOperation.WRITTEN_DAY})\\s*[-–]?\\s*([0-9]{1,2}:[0-9]{2})\\s*(AM|PM)?", RegexOption.IGNORE_CASE)
    private val address = Regex("[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@([A-Z0-9.-]+)", RegexOption.IGNORE_CASE)
    private val outgoingOperation = Regex("\\boperaci[óo]n realizada\\s*:?\\s*(?:pago (?:con qr|a celular|de servicios?|de tarjeta)|yapeo (?:a celular|enviado)|transferencia (?:enviada|realizada|a (?:otra cuenta|terceros)))(?=\\s|[.,;]|$)", RegexOption.IGNORE_CASE)
    private val outgoingSummary = Regex("\\b(?:realizaste (?:un (?:pago|yapeo)|una transferencia)|enviaste (?:un yapeo|dinero)|pagaste)\\s+(?:[^.!?]{0,100}?\\s+)?$solesAmount", RegexOption.IGNORE_CASE)
    private val qr = Regex("\\bpago con qr\\b", RegexOption.IGNORE_CASE)
    private val lima = ZoneId.of("America/Lima")

    fun isBankMail(from: String, authentication: String): Boolean {
        val sender = address.matchEntire(from.substringAfterLast('<').substringBefore('>').trim()) ?: return false
        val domain = sender.groupValues[1].lowercase(Locale.ROOT)
        if (domain !in bankDomains || !authentication.trimStart().startsWith("mx.google.com;", ignoreCase = true)) return false
        return Regex("(?:^|;)\\s*dmarc=pass\\b[^;]*\\bheader\\.from=${Regex.escape(domain)}(?=\\s|;|$)",
            RegexOption.IGNORE_CASE).containsMatchIn(authentication)
    }

    fun parse(body: String): BcpMailSuggestion? {
        val text = BcpOperation.normalize(body)
        val summaryAmounts = outgoingSummary.findAll(text).map { BcpReceiptAmount.parse(it.groupValues[1]) }.toList()
        if (!outgoingOperation.containsMatchIn(text) && summaryAmounts.isEmpty() && !qr.containsMatchIn(text)) return null
        val labeledAmounts = outgoingAmount.findAll(text).map { BcpReceiptAmount.parse(it.groupValues[1]) }.toList()
        if (labeledAmounts.isEmpty() && outgoingAmountLabel.containsMatchIn(text)) return null
        val amounts = if (labeledAmounts.isNotEmpty()) labeledAmounts else {
            val genericAmounts = genericAmount.findAll(text).map { BcpReceiptAmount.parse(it.groupValues[1]) }.toList()
            if (genericAmounts.isNotEmpty()) genericAmounts else summaryAmounts
        }
        val amount = (amounts + summaryAmounts).distinct().singleOrNull() ?: return null
        val operation = BcpOperation.number(text) ?: return null
        val date = dateLabel.find(text) ?: return null
        val (day, clock, period) = date.destructured
        val time = runCatching {
            val hour = clock.substringBefore(':').toInt()
            val minute = clock.substringAfter(':').toInt()
            val hourOfDay = if (period.isEmpty()) hour else {
                require(hour in 1..12)
                hour % 12 + if (period.equals("PM", ignoreCase = true)) 12 else 0
            }
            BcpOperation.writtenDate(day).atTime(LocalTime.of(hourOfDay, minute))
        }.getOrNull() ?: return null
        val id = BcpOperation.fingerprint(time.toLocalDate(), operation)
        return BcpMailSuggestion(id, amount, time.atZone(lima).toInstant().toEpochMilli())
    }
}
