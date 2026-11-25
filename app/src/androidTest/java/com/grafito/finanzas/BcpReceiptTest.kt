package com.grafito.finanzas

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.grafito.finanzas.data.*
import com.grafito.finanzas.receipts.BcpReceiptActivity
import com.grafito.finanzas.receipts.BcpMailAlerts
import com.grafito.finanzas.receipts.BcpMailParser
import com.grafito.finanzas.receipts.BcpMailReviewActivity
import com.grafito.finanzas.receipts.SharedReceiptReader
import com.grafito.finanzas.ui.theme.HomePalette
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BcpReceiptTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = FinanceDatabase.get(app)

    @Before fun seedAccounts() = runBlocking {
        check(app.packageName == "com.grafito.finanzas.validation")
        FinanceRepository(db).restore(BackupSnapshot(
            accounts = listOf(AccountEntity(1, "Efectivo"), AccountEntity(2, "BCP")),
            categories = listOf(CategoryEntity(1, "Alimentación", "", EntryType.EXPENSE)),
            transactions = emptyList(), favorites = emptyList(), defaultAccountId = 1
        ))
    }

    @Test fun qrEmailCanBeReviewedAndRecordedOnce() {
        val payment = requireNotNull(BcpMailParser.parse("Monto enviado S/ 12.50 Operación realizada Pago con QR " +
            "Fecha y hora 6 de octubre de 2026 - 08:43 AM Número de operación ${System.nanoTime().toString().takeLast(12)}"))
        val inbox = BcpMailInbox(db)
        assertTrue(runBlocking { inbox.record(payment) })
        assertFalse(runBlocking { inbox.record(payment) })
        assertTrue(runBlocking { db.dao().allTransactions().isEmpty() })
        ActivityScenario.launch<BcpMailReviewActivity>(BcpMailAlerts.reviewIntent(app, payment.id)).use {
            waitForText("Pago BCP detectado")
            waitForText("BCP")
            val save = compose.onNodeWithText("Guardar gasto").performScrollTo().assertIsEnabled()
            val pixels = save.captureToImage().toPixelMap()
            assertEquals(HomePalette.Blue, pixels[pixels.width / 8, pixels.height / 2])
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            try {
                File(app.getExternalFilesDir(null), "franca-revision-bcp.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                screenshot.recycle()
            }
            compose.onNodeWithText("Sin categoría").performScrollTo().performClick()
            compose.onNodeWithText("Buscar categoría").performScrollTo().performTextInput("alimentacion")
            compose.onNodeWithText("Alimentación").performScrollTo().performClick()
            compose.onNodeWithText("Guardar gasto").performScrollTo().performClick()
            val saved = awaitSaved()
            assertEquals(1250L, saved.amountCents)
            assertEquals(2L, saved.accountId)
            assertEquals(1L, saved.categoryId)
            assertEquals(payment.occurredAt, saved.occurredAt)
            assertEquals(payment.id, saved.receiptFingerprint)
            assertFalse(runBlocking { inbox.record(payment) })
        }
    }

    @Test fun paymentAlertUsesHighImportanceAndKeepsItsReviewAction() {
        val manager = app.getSystemService(NotificationManager::class.java)
        val channel = com.grafito.finanzas.notifications.FrancaNotices.bcpExpense.ensure(app)
        val originalImportance = manager.getNotificationChannel(channel)?.importance
        if (Build.VERSION.SDK_INT >= 33) {
            val command = "pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS"
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        val payment = BcpMailSuggestion(java.util.UUID.randomUUID().toString(), 1250, System.currentTimeMillis())
        try {
            BcpMailAlerts.show(app, payment)
            compose.waitUntil(10_000) { manager.activeNotifications.any { it.tag == payment.id } }
            val alert = manager.activeNotifications.single { it.tag == payment.id }.notification
            assertEquals(originalImportance ?: NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(channel).importance)
            assertEquals("Pago BCP · S/ 12.50", alert.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals("Registrar", alert.actions.single().title.toString())
            assertEquals(app.getColor(R.color.franca_mint), alert.color)
            assertEquals(Notification.VISIBILITY_PRIVATE, alert.visibility)
            assertEquals("Tienes un gasto por revisar", alert.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertTrue(alert.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
            assertNotNull(alert.contentIntent)
        } finally {
            BcpMailAlerts.cancel(app, payment.id)
        }
    }

    @Test fun sharedImageSuggestsAmountAndOnlySavesAfterReview() {
        val file = fixtureFile("receipt.png")
        try {
            val bitmap = Bitmap.createBitmap(820, 450, Bitmap.Config.ARGB_8888)
            drawReceipt(Canvas(bitmap))
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()

            ActivityScenario.launch<BcpReceiptActivity>(shareFile(file, "image/png")).use {
                waitForText("Importe en soles")
                compose.onNode(hasSetTextAction() and hasText("12.50")).assertExists()
                assertTrue(runBlocking { db.dao().allTransactions().isEmpty() })
                waitForText("BCP")
                compose.onNodeWithText("Guardar gasto").performScrollTo().performClick()
                val saved = awaitSaved()
                assertEquals(1250L, saved.amountCents)
                assertEquals(2L, saved.accountId)
                assertEquals(EntryType.EXPENSE, saved.type)
                assertNotNull(saved.receiptFingerprint)
            }
            ActivityScenario.launch<BcpReceiptActivity>(shareFile(file, "image/png")).use {
                waitForText("Esta constancia ya está registrada en Franca.")
                assertEquals(1, runBlocking { db.dao().allTransactions().size })
            }
        } finally {
            file.delete()
        }
    }

    @Test fun sharedTextCanBeReviewedAsTransferBetweenOwnAccounts() {
        val intent = Intent(Intent.ACTION_SEND).setClass(app, BcpReceiptActivity::class.java)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Constancia BCP\nMonto transferido S/ 30.00")
        ActivityScenario.launch<BcpReceiptActivity>(intent).use {
            waitForText("BCP")
            compose.onNodeWithText("Entre mis cuentas").performClick()
            compose.onNodeWithText("Elegir cuenta").performScrollTo().performClick()
            compose.onNodeWithText("Efectivo").performClick()
            compose.onNodeWithText("Guardar transferencia").performScrollTo().performClick()
            val saved = awaitSaved()
            assertEquals(EntryType.TRANSFER, saved.type)
            assertEquals(3000L, saved.amountCents)
            assertEquals(2L, saved.accountId)
            assertEquals(1L, saved.toAccountId)
        }
    }

    @Test fun sharedSinglePagePdfIsReadLocally() = runBlocking {
        val file = fixtureFile("receipt.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(820, 450, 1).create())
            drawReceipt(page.canvas)
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
            val receipt = SharedReceiptReader(app).read(shareFile(file, "application/pdf"))
            assertEquals(1250L, receipt.suggestedCents)
            assertEquals(payment("123456").id, receipt.operationFingerprint)
        } finally {
            document.close()
            file.delete()
        }
    }

    @Test fun mailSavedFirstBlocksSharedReceiptEvenAfterEditingTheAmount() {
        val operation = operationNumber()
        val payment = payment(operation)
        val inbox = BcpMailInbox(db)
        runBlocking {
            assertTrue(inbox.record(payment))
            assertEquals(ReceiptSaveResult.SAVED, inbox.confirm(payment.id, 1300, 2, null, "", null))
        }
        ActivityScenario.launch<BcpReceiptActivity>(sharedText(operation)).use {
            waitForText("Esta constancia ya está registrada en Franca.")
            assertEquals(1300L, awaitSaved().amountCents)
        }
    }

    @Test fun sharedReceiptDismissesPendingMailAndKeepsIdentityAfterBackupAndEdit() {
        val operation = operationNumber()
        val payment = payment(operation)
        val inbox = BcpMailInbox(db)
        assertTrue(runBlocking { inbox.record(payment) })
        ActivityScenario.launch<BcpReceiptActivity>(sharedText(operation)).use {
            waitForText("BCP")
            compose.onNodeWithText("Guardar gasto").performScrollTo().performClick()
            assertEquals(payment.id, awaitSaved().bcpOperationFingerprint)
        }
        runBlocking {
            assertNull(inbox.findPending(payment.id))
            assertEquals(ReceiptSaveResult.DUPLICATE, inbox.confirm(payment.id, 1250, 2, null, "", null))
            val freshPayment = payment((operation.toLong() + 1).toString())
            assertTrue(inbox.record(freshPayment))
            val repo = FinanceRepository(db)
            repo.addEntry(EntryType.EXPENSE, 1350, 2, null, "", existing = db.dao().allTransactions().single())
            val restored = BackupSnapshot.decode(repo.snapshot().encode())
            repo.restore(restored)
            assertEquals(1350L, db.dao().allTransactions().single().amountCents)
            assertTrue(db.dao().hasReceipt(payment.id))
            assertFalse(db.dao().hasReceipt(freshPayment.id))
            assertFalse(inbox.record(payment))
        }
    }

    @Test fun concurrentConfirmationsSaveTheOperationOnlyOnce() = runBlocking {
        val operation = operationNumber()
        val receipt = SharedReceiptReader(app).read(sharedText(operation))
        val payment = payment(operation)
        val inbox = BcpMailInbox(db)
        val repo = FinanceRepository(db)
        assertTrue(inbox.record(payment))
        val results = listOf(
            async { inbox.confirm(payment.id, 1250, 2, null, "", null) },
            async { repo.saveBcpReceipt(receipt.fingerprint, 1250, 2, null, "", operationFingerprint = receipt.operationFingerprint) }
        ).awaitAll()
        assertEquals(1, results.count { it == ReceiptSaveResult.SAVED })
        assertEquals(1, results.count { it == ReceiptSaveResult.DUPLICATE })
        assertEquals(1, db.dao().allTransactions().size)
        assertNull(inbox.findPending(payment.id))
    }

    @Test fun sharingTheSameLegacyFileLinksItWithoutAddingOrChangingThePayment() {
        val operation = operationNumber()
        val payment = payment(operation)
        val intent = sharedText(operation)
        val inbox = BcpMailInbox(db)
        runBlocking {
            val repo = FinanceRepository(db)
            val receipt = SharedReceiptReader(app).read(intent)
            assertEquals(ReceiptSaveResult.SAVED, repo.saveBcpReceipt(receipt.fingerprint, 1300, 2, null, "Pago anterior"))
            assertTrue(inbox.record(payment))
        }
        ActivityScenario.launch<BcpReceiptActivity>(intent).use {
            waitForText("Esta constancia ya está registrada en Franca.")
            val saved = awaitSaved()
            assertEquals(1300L, saved.amountCents)
            assertEquals("Pago anterior", saved.note)
            assertEquals(payment.id, saved.bcpOperationFingerprint)
            assertNull(runBlocking { inbox.findPending(payment.id) })
        }
    }

    private fun operationNumber() = System.nanoTime().toString().takeLast(12)

    private fun payment(operation: String) = requireNotNull(BcpMailParser.parse(
        "Monto enviado S/ 12.50 Operación realizada Pago con QR " +
            "Fecha y hora 6 de octubre de 2026 - 08:43 AM Número de operación $operation"
    ))

    private fun sharedText(operation: String) = Intent(Intent.ACTION_SEND).setClass(app, BcpReceiptActivity::class.java)
        .setType("text/plain").putExtra(Intent.EXTRA_TEXT,
            "Constancia BCP\nMonto pagado S/ 12.50\nFecha: 06/10/2026\nN° de operación: $operation")

    private fun waitForText(label: String) = compose.waitUntil(25_000) {
        compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitSaved(): TransactionEntity = runBlocking {
        withTimeout(10_000) { db.dao().transactions().first { it.isNotEmpty() } }.single()
    }

    private fun fixtureFile(name: String) = File(File(app.cacheDir, "receipt-fixtures").apply { mkdirs() }, name)

    private fun shareFile(file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.receipt-test", file)
        return Intent(Intent.ACTION_SEND).setClass(app, BcpReceiptActivity::class.java).setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun drawReceipt(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 50f }
        canvas.drawText("Constancia de pago BCP", 48f, 85f, paint)
        canvas.drawText("Monto pagado", 48f, 175f, paint)
        canvas.drawText("S/ 12.50", 48f, 255f, paint)
        canvas.drawText("Fecha: 06/10/2026", 48f, 330f, paint)
        canvas.drawText("Nro. de operacion: 123456", 48f, 410f, paint)
    }
}
