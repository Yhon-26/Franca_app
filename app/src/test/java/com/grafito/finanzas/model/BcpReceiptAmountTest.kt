package com.grafito.finanzas.model

import com.grafito.finanzas.receipts.BcpReceiptAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BcpReceiptAmountTest {
    @Test fun usesThePaymentAmountBeforeOtherAmounts() {
        assertEquals(1250L, BcpReceiptAmount.parse("Constancia BCP\nMonto pagado\nS/ 12,50\nComisión S/ 0.00"))
        assertEquals(1250L, BcpReceiptAmount.parse("Pago exitoso S/ 12.50\nOperación 123456"))
    }

    @Test fun acceptsUnambiguousGroupingWithBothConventions() {
        assertEquals(123456L, BcpReceiptAmount.parse("Monto: S/ 1.234,56"))
        assertEquals(123456L, BcpReceiptAmount.parse("Total pagado: S/ 1,234.56"))
    }

    @Test fun leavesConflictingOrInvalidAmountsForManualReview() {
        listOf("S/ 12.50\nS/ 20.00", "Monto S/ 12\nImporte S/ 20", "S/ 1,234", "S/ 1 234",
            "S/ 12.3456", "S/ 0.00", "S/ 12.50\nUSD 4.00", "Operación 123456").forEach {
            assertNull(it, BcpReceiptAmount.parse(it))
        }
    }
}
