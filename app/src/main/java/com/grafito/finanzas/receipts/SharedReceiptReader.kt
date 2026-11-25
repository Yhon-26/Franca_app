package com.grafito.finanzas.receipts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.IntentCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

internal data class SharedReceipt(val fingerprint: String, val suggestedCents: Long?, val operationFingerprint: String? = null)

internal class SharedReceiptReader(private val context: Context) {
    suspend fun read(intent: Intent): SharedReceipt = withContext(Dispatchers.IO) {
        require(intent.action == Intent.ACTION_SEND)
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
        if (uri == null) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            require(text.isNotBlank() && text.length <= 12_000)
            return@withContext fromText(fingerprint(text.replace(Regex("\\s+"), " ").toByteArray()), text)
        }
        require(uri.scheme == "content")
        val bytes = readLimited(uri)
        val text = try {
            val bitmap = if (bytes.take(5).toByteArray().decodeToString() == "%PDF-") readPdf(bytes) else readImage(bytes)
            bitmap?.let { withTimeoutOrNull(15_000) { recognize(it) } }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        fromText(fingerprint(bytes), text)
    }

    private fun fromText(fingerprint: String, text: String?) = SharedReceipt(
        fingerprint, text?.let(BcpReceiptAmount::parse), text?.let(BcpOperation::fromReceipt)
    )

    private fun readLimited(uri: Uri): ByteArray = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(output.size() + count <= 12 * 1024 * 1024)
            output.write(buffer, 0, count)
        }
        output.toByteArray().also { require(it.isNotEmpty()) }
    }

    private fun readImage(bytes: ByteArray): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val scale = minOf(1f, 2048f / maxOf(width, height))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val largest = maxOf(options.outWidth, options.outHeight)
        if (largest <= 0) return null
        var sample = 1
        while (largest / sample > 2048) sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun readPdf(bytes: ByteArray): Bitmap? {
        val file = File.createTempFile("franca-receipt-", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    if (pdf.pageCount != 1) return null
                    pdf.openPage(0).use { page ->
                        val scale = 2048f / maxOf(page.width, page.height)
                        val bitmap = Bitmap.createBitmap((page.width * scale).roundToInt().coerceAtLeast(1),
                            (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bitmap
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    private suspend fun recognize(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return suspendCancellableCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                    .addOnCanceledListener { continuation.cancel() }
            }
        } finally {
            recognizer.close()
        }
    }

    private fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
