package com.grafito.finanzas.receipts

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BcpScreenParserTest {
    private val receipt = listOf(
        "¡Operación exitosa!", "S/ 12.50", "Martes 17 Noviembre 2026 - 11:06 am.",
        "Descargar", "Compartir", "Enviado a", "Persona de prueba", "YAPE",
        "Comisión", "Gratis", "Desde", "Cuenta de prueba", "**** 0000",
        "Número de operación", "00123456", "Asegúrala desde S/0.20 al día",
        "Yapear a otro celular"
    )

    @Test fun readsTheConfirmedAmountAndIgnoresTheAdvertisement() {
        val result = BcpScreenParser.read(receipt)
        val payment = requireNotNull(result.payment)
        assertEquals(BcpScreenStatus.DETECTED, result.status)
        assertEquals(15 or BcpScreenParser.SUCCESS_TITLE, result.fields)
        assertEquals(1250L, payment.amountCents)
        assertEquals(
            LocalDateTime.of(2026, 11, 17, 11, 6).atZone(ZoneId.of("America/Lima")).toInstant().toEpochMilli(),
            payment.occurredAt
        )
        val mail = requireNotNull(BcpMailParser.parse(
            "Monto enviado S/ 12.50 Operación realizada Pago a celular " +
                "Fecha y hora 17 de noviembre de 2026 - 11:06 AM Número de operación 00123456"
        ))
        assertEquals(mail.id, payment.id)
        assertEquals(mail.occurredAt, payment.occurredAt)
    }

    @Test fun requiresAConfirmedOutgoingPayment() {
        assertNull(BcpScreenParser.read(receipt - "¡Operación exitosa!" + "Confirmar operación").payment)
        assertNull(BcpScreenParser.read(receipt - "Enviado a" + "Recibido de").payment)
        assertNull(BcpScreenParser.read(receipt - "Desde").payment)
        assertNull(BcpScreenParser.read(receipt + "Transferencia pendiente").payment)
        assertNull(BcpScreenParser.read(receipt + "Transferencia programada").payment)
        assertNull(BcpScreenParser.read(receipt + "Transferencia recibida").payment)
        assertNull(BcpScreenParser.read(receipt + "Operación fallida").payment)
        assertNull(BcpScreenParser.read(listOf("Ingresar clave", "S/ 12.50", "Confirmar")).payment)
    }

    @Test fun recognizesTheSplitTitleWithTheSameReceiptFingerprint() {
        val split = listOf("¡Operacio\u0301n\u00A0", "\u200Eexitosa！") + receipt.drop(1)
        val result = BcpScreenParser.read(split)
        assertEquals(BcpScreenStatus.DETECTED, result.status)
        assertEquals(1250L, result.payment?.amountCents)
        assertEquals(requireNotNull(BcpScreenParser.read(receipt).payment).id, result.payment?.id)
    }

    @Test fun recognizesTheTitleJoinedToAnAmountLabel() {
        val joined = listOf("Confirmación · ¡Operación exitosa!\u202FMonto: S/ 12.50") + receipt.drop(2)
        val result = BcpScreenParser.read(joined)
        assertEquals(BcpScreenStatus.DETECTED, result.status)
        assertEquals(1250L, result.payment?.amountCents)
    }

    @Test fun reportsReceiptFieldsWithoutSuggestingAnUnconfirmedPayment() {
        val result = BcpScreenParser.read(receipt.drop(1))
        assertEquals(BcpScreenStatus.VISIBLE, result.status)
        assertEquals(15, result.fields)
        assertNull(result.payment)
    }

    @Test fun rejectsHistoryEvenWhenReceiptFieldsArePresent() {
        val history = listOf(
            "Detalle de Movimiento", "Yapear a celular", "Monto enviado", "S/ 12.50",
            "Miércoles, 18 noviembre 2026", "Descargar", "Compartir", "Número de operación", "123456"
        )
        val result = BcpScreenParser.read(history)
        assertEquals(BcpScreenStatus.VISIBLE, result.status)
        assertEquals(BcpScreenParser.HISTORY_SCREEN, result.fields)
        assertNull(result.payment)
        val mixed = BcpScreenParser.read(listOf("Detalle", "de Movimiento") + receipt)
        assertEquals(BcpScreenParser.HISTORY_SCREEN or BcpScreenParser.SUCCESS_TITLE, mixed.fields)
        assertNull(mixed.payment)
        val failed = BcpScreenParser.read(receipt + "Operación fallida")
        assertEquals(BcpScreenParser.UNSUCCESSFUL_SCREEN or BcpScreenParser.SUCCESS_TITLE, failed.fields)
        assertNull(failed.payment)
    }

    @Test fun reportsAnEmptySnapshotAsUnreadable() {
        assertEquals(BcpScreenStatus.UNAVAILABLE, BcpScreenParser.read(emptyList()).status)
        assertEquals(BcpScreenStatus.UNAVAILABLE, BcpScreenParser.read(listOf("\u00A0\u200E")).status)
    }

    @Test fun reportsMissingFieldsAndRejectsAmbiguousOperations() {
        val missingOperation = BcpScreenParser.read(receipt - "00123456")
        assertEquals(BcpScreenStatus.INCOMPLETE, missingOperation.status)
        assertEquals(0, missingOperation.fields and BcpScreenParser.OPERATION)
        assertNull(missingOperation.payment)
        assertNull(BcpScreenParser.read(receipt + listOf("Número de operación", "00123457")).payment)
        assertNull(BcpScreenParser.read(receipt - "S/ 12.50").payment)
        assertNull(BcpScreenParser.read(receipt - "S/ 12.50" + "US$ 12.50").payment)
        assertNull(BcpScreenParser.read(receipt - "Martes 17 Noviembre 2026 - 11:06 am.").payment)
    }

    @Test fun supportsNoonMidnightAndSplitAccessibilityText() {
        fun time(clock: String) = requireNotNull(BcpScreenParser.read(
            receipt.map { if (it == "Martes 17 Noviembre 2026 - 11:06 am.") "17 Noviembre 2026 - $clock" else it }
        ).payment).occurredAt
        val date = LocalDateTime.of(2026, 11, 17, 12, 0)
        assertEquals(date.atZone(ZoneId.of("America/Lima")).toInstant().toEpochMilli(), time("12:00 pm."))
        assertEquals(date.withHour(0).atZone(ZoneId.of("America/Lima")).toInstant().toEpochMilli(), time("12:00 am."))
        val split = receipt.flatMap {
            if (it == "S/ 12.50") listOf("S/", "12.50") else listOf(it)
        }
        assertEquals(1250L, BcpScreenParser.read(split).payment?.amountCents)
    }

    @Test fun rejectsInvalidOrConflictingDatesAndAmounts() {
        val invalid = receipt.map {
            if (it == "Martes 17 Noviembre 2026 - 11:06 am.") "31 Noviembre 2026 - 11:06 am." else it
        }
        assertNull(BcpScreenParser.read(invalid).payment)
        assertNull(BcpScreenParser.read(receipt + "18 Noviembre 2026 - 11:06 am.").payment)
        assertNull(BcpScreenParser.read(receipt.map { if (it == "S/ 12.50") "S/ 0" else it }).payment)
        val ambiguousAmount = receipt.toMutableList().apply { add(2, "S/ 15.00") }
        assertNull(BcpScreenParser.read(ambiguousAmount).payment)
    }
}
