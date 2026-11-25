package com.grafito.finanzas.model

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class YapeSentParserTest {
    private val confirmation = listOf("¡Yapeaste!", "S/ 12,50", "Nro. de operación:", "12345678", "Ir a inicio")

    @Test fun readsAnUnambiguousConfirmationAndIgnoresOtherData() {
        val result = YapeSentParser.read(confirmation + "Código de seguridad: 456")
        assertEquals(YapeSentStatus.DETECTED, result.status)
        val payment = requireNotNull(result.payment)
        assertEquals(1250L, payment.amountCents)
        assertTrue(payment.id.matches(Regex("[a-f0-9]{64}")))
        assertEquals(payment.id, YapeSentParser.read(confirmation + "Otra etiqueta").payment?.id)
        assertNotEquals(payment.id, YapeSentParser.read(confirmation.map { if (it == "12345678") "12345679" else it }).payment?.id)
    }

    @Test fun supportsSplitCurrencyAndAnInlineOperationNumber() {
        val result = YapeSentParser.read(listOf("¡Yapeaste!", "S/", "1.250,40", "N.º de operación 00123456", "Ir al inicio"))
        assertEquals(125040L, result.payment?.amountCents)
    }

    @Test fun ignoresOpeningTheAppEditingAnAmountAndIncompleteDetails() {
        val drafting = listOf("Yapear", "S/ 12.50", "Confirmar", "Seleccionar contacto")
        assertEquals(YapeSentStatus.YAPE_VISIBLE, YapeSentParser.read(drafting).status)
        val detail = confirmation - "Ir a inicio" + "Volver"
        assertNull(YapeSentParser.read(detail).payment)
    }

    @Test fun readsTheRedmiReceiptWithSpokenSolesAndItsOriginalTime() {
        val receipt = listOf(
            "Cerrar", "¡Yapeaste!", "Compartir", "12.50", "12.50 soles",
            "17 nov. 2026", "08:40 a. m.", "Código de seguridad", "456",
            "Nro. de operación", "12345678"
        )
        val payment = requireNotNull(YapeSentParser.read(receipt).payment)
        assertEquals(1250L, payment.amountCents)
        assertEquals(YapeSentParser.read(confirmation).payment?.id, payment.id)
        assertEquals(
            LocalDateTime.of(2026, 11, 17, 8, 40).atZone(ZoneId.of("America/Lima")).toInstant().toEpochMilli(),
            payment.occurredAt
        )
        assertNull(YapeSentParser.read(receipt - "17 nov. 2026").payment)
        assertNull(YapeSentParser.read(receipt - "08:40 a. m.").payment)
        assertNull(YapeSentParser.read(receipt.map { if (it == "¡Yapeaste!") "¡Te yapearon!" else it }).payment)
        assertNull(YapeSentParser.read(receipt + "10.00 soles").payment)
    }

    @Test fun rejectsMissingOrAmbiguousAmountsAndOperationNumbers() {
        assertNull(YapeSentParser.read(confirmation - "12345678").payment)
        assertNull(YapeSentParser.read(confirmation + "S/ 10").payment)
        assertNull(YapeSentParser.read(confirmation + "N.º de operación: 12349876").payment)
        assertNull(YapeSentParser.read(confirmation - "S/ 12,50" + "S/ 0").payment)
    }
}
