package com.grafito.finanzas.model

import org.junit.Assert.*
import org.junit.Test

class YapeReceiptParserTest {
    @Test fun parsesReceiptTemplateAndIgnoresSecurityNumber() {
        assertEquals(2500L, YapeReceiptParser.amountCents(listOf(
            "Confirmación de pago", "Persona de prueba te envió un pago por S/25. El cód. de seguridad es:000"
        )))
    }

    @Test fun parsesDecimalsAndUnambiguousGrouping() {
        assertEquals(1250L, YapeReceiptParser.amountCents(listOf("Te yapearon", "S/ 12,50")))
        assertEquals(123456L, YapeReceiptParser.amountCents(listOf("Pago recibido de S/1,234.56")))
        assertEquals(123456L, YapeReceiptParser.amountCents(listOf("Yapeo recibido de S/1.234,56")))
    }

    @Test fun duplicateTitleAndBodyAmountAreOneCandidate() {
        assertEquals(100L, YapeReceiptParser.amountCents(listOf("Yapeo recibido S/1.00", "Te enviaron dinero S/1.00")))
    }

    @Test fun rejectsOutgoingPromotionalRedactedAndUnsuccessfulNotices() {
        listOf("Yapeaste S/10 a una persona", "Ahorra S/50 con Yape", "Contenido oculto",
            "Confirmación de pago", "No recibiste un yapeo por S/15", "Pago pendiente: recibiste un pago por S/15").forEach {
            assertNull(it, YapeReceiptParser.amountCents(listOf(it)))
        }
    }

    @Test fun rejectsAmbiguousZeroNegativeAndMalformedAmounts() {
        listOf("S/1,234", "S/0", "S/-20", "S/1 234", "S/15.567", "S/15 y S/25", "S/9abc", "S/9999999999").forEach {
            assertNull(it, YapeReceiptParser.amountCents(listOf("Pago recibido $it")))
        }
    }
}
