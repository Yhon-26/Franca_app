package com.grafito.finanzas.model

import com.grafito.finanzas.receipts.BcpMailParser
import com.grafito.finanzas.receipts.BcpOperation
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class BcpMailParserTest {
    private val payment = """
        Realizaste un yapeo a celular de S/ 1.25 desde tu Cuenta de ahorro Soles.
        Monto enviado
        S/ 1.25
        Operación realizada
        Pago con QR
        Fecha y hora
        03 de octubre de 2026 - 08:43 AM
        Número de operación
        00821122
    """.trimIndent()

    @Test fun readsAmountAndLimaTimeAndIdentifiesRepeatedOperation() {
        val parsed = requireNotNull(BcpMailParser.parse(payment))
        assertEquals(125L, parsed.amountCents)
        assertEquals(Instant.parse("2026-10-03T13:43:00Z").toEpochMilli(), parsed.occurredAt)
        assertEquals(parsed.id, BcpMailParser.parse(payment.replace("08:43 AM", "08:43"))?.id)
        assertNotEquals(parsed.id, BcpMailParser.parse(payment.replace("00821122", "00821123"))?.id)
    }

    @Test fun recognizesCellphonePaymentsAndPreservesTheOperationId() {
        val byPhone = payment.replace("Pago con QR", "Pago a celular")
        assertEquals(BcpMailParser.parse(payment), BcpMailParser.parse(byPhone))
        assertEquals(125L, BcpMailParser.parse(byPhone.replace("Operación realizada\nPago a celular", ""))?.amountCents)
        assertEquals(125L, BcpMailParser.parse(byPhone.replace("Monto enviado\nS/ 1.25", ""))?.amountCents)
    }

    @Test fun matchesReceiptToTheExistingMailIdOnlyWithAnUnambiguousDateAndNumber() {
        val mail = requireNotNull(BcpMailParser.parse(payment))
        val receipt = "Constancia BCP\nFecha de operación: 03/10/2026\nN° de operación: 00821122\nMonto S/ 1.25"
        assertEquals("6b3c6e30ad2f28995b5fb3b05f4744368b0e100fcc9709cb5b880c8b7aa9756e", mail.id)
        assertEquals(mail.id, BcpOperation.fromReceipt(receipt))
        assertEquals(mail.id, BcpOperation.fromReceipt(receipt.replace("03/10/2026", "3 de octubre de 2026")))
        assertNotEquals(mail.id, BcpOperation.fromReceipt(receipt.replace("00821122", "00821123")))
        assertNotEquals(mail.id, BcpOperation.fromReceipt(receipt.replace("03/10/2026", "04/10/2026")))
        assertNull(BcpOperation.fromReceipt(receipt.replace("03/10/2026", "31/11/2026")))
        assertNull(BcpOperation.fromReceipt(receipt.replace("BCP", "otro banco")))
        assertNull(BcpOperation.fromReceipt(receipt.replace("N° de operación", "N° de cuenta")))
        assertNull(BcpOperation.fromReceipt(receipt.replace("00821122", "0082 1122")))
        assertNull(BcpOperation.fromReceipt("$receipt\nNúmero de operación: 00999999"))
        assertNull(BcpOperation.fromReceipt("$receipt\nFecha: 04/10/2026"))
    }

    @Test fun requiresAnOutgoingPaymentWithAnUnambiguousAmountDateAndOperation() {
        val deposit = payment.substringAfter('\n').replace("Pago con QR", "Depósito")
        assertNull(BcpMailParser.parse(deposit))
        assertNull(BcpMailParser.parse(payment.replace("00821122", "???")))
        assertNull(BcpMailParser.parse(payment.replace("03 de octubre", "33 de octubre")))
        assertNull(BcpMailParser.parse(payment.replace("Monto enviado\nS/", "Monto enviado\nUSD")))
        assertNull(BcpMailParser.parse(payment.replace("Monto enviado\nS/ 1.25", "Monto enviado\nS/ 9.25")))
    }

    @Test fun recognizesOtherExplicitOutgoingOperations() {
        val receipt = payment.substringAfter('\n').replace("Monto enviado", "Importe")
        for (operation in listOf("Pago de servicios", "Pago de tarjeta", "Transferencia enviada")) {
            assertEquals(125L, BcpMailParser.parse(receipt.replace("Pago con QR", operation))?.amountCents)
        }
    }

    @Test fun requiresBankDomainAndAlignedGmailAuthentication() {
        val gmail = "mx.google.com; dkim=pass; dmarc=pass (p=REJECT) header.from=notificacionesbcp.com.pe"
        val bank = "BCP <notificaciones@notificacionesbcp.com.pe>"
        assertTrue(BcpMailParser.isBankMail(bank, gmail))
        assertFalse(BcpMailParser.isBankMail(bank, gmail.replace("dmarc=pass", "dmarc=fail")))
        assertFalse(BcpMailParser.isBankMail("BCP <falso@example.com>", gmail))
    }
}
