package com.grafito.finanzas.model

import java.text.Normalizer
import java.util.Locale

internal object YapeReceiptParser {
    private val marks = Regex("\\p{M}+")
    private val whitespace = Regex("[\\s\\p{Z}]+")
    private val received = Regex(
        "\\b(?:te (?:ha )?yapeo|te yapearon|te (?:envio|ha enviado|enviaron) (?:un pago|un yapeo|dinero)|" +
            "(?:has )?(?:recibiste|recibido) (?:(?:un |una |nuevo )?(?:yapeo|pago|transferencia|dinero)|s(?=\\s*/))|" +
            "(?:yapeo|pago|transferencia) recibid[oa])\\b"
    )
    private val outgoingOrUnsuccessful = Regex(
        "\\b(?:yapeaste|enviaste|pagaste|pago enviado|yapeo enviado|pago rechazado|" +
            "pago pendiente|no se pudo|no recibiste|no has recibido|no te yapeo|anulad[oa]|revertid[oa])\\b"
    )
    private val amount = Regex("\\bs\\s*/\\.?\\s*([0-9]+(?:[.,][0-9]+)*)(?![\\p{L}\\p{N}]|[.,][0-9]|\\s+[0-9])")
    private val groupedDotDecimal = Regex("[1-9][0-9]{0,2}(?:,[0-9]{3})+\\.[0-9]{1,2}")
    private val groupedCommaDecimal = Regex("[1-9][0-9]{0,2}(?:\\.[0-9]{3})+,[0-9]{1,2}")

    fun amountCents(parts: List<String>): Long? {
        val text = whitespace.replace(
            marks.replace(Normalizer.normalize(parts.joinToString(" "), Normalizer.Form.NFKD), "")
                .lowercase(Locale.ROOT), " "
        )
        if (!received.containsMatchIn(text) || outgoingOrUnsuccessful.containsMatchIn(text)) return null
        val matches = amount.findAll(text).map { parseAmount(it.groupValues[1]) }.distinct().toList()
        return matches.singleOrNull()
    }

    private fun parseAmount(value: String): Long? = Money.parse(when {
        groupedDotDecimal.matches(value) -> value.replace(",", "")
        groupedCommaDecimal.matches(value) -> value.replace(".", "")
        else -> value
    })
}
