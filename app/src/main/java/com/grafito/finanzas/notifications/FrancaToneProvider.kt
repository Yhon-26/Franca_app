package com.grafito.finanzas.notifications

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/** Provides stable, read-only URIs for the three public sounds served to Android. */
class FrancaToneProvider : ContentProvider() {
    private val cachedTones = mutableMapOf<String, File>()

    override fun onCreate() = true

    private fun tone(uri: Uri): FrancaNoticeChannel? =
        FrancaNotices.channels.firstOrNull { it.tonePath == uri.pathSegments.singleOrNull() }

    override fun getType(uri: Uri): String? = tone(uri)?.let { "audio/wav" }

    @Synchronized
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val tone = tone(uri) ?: throw FileNotFoundException()
        if (mode != "r") throw FileNotFoundException()
        val app = requireNotNull(context)
        val file = cachedTones[tone.tonePath]?.takeIf { it.isFile } ?: run {
            val dir = File(app.cacheDir, "franca_tones")
            dir.mkdirs()
            val target = AtomicFile(File(dir, "${tone.tonePath}.wav"))
            val output = target.startWrite()
            try {
                app.resources.openRawResource(tone.tone).use { it.copyTo(output) }
                target.finishWrite(output)
            } catch (error: Exception) {
                target.failWrite(output)
                throw error
            }
            target.baseFile.also { cachedTones[tone.tonePath] = it }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val tone = tone(uri) ?: return null
        val columns = projection?.toList()?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, MediaStore.Audio.Media.TITLE)
        return MatrixCursor(columns, 1).apply {
            val row = newRow()
            for (column in columns) {
                row.add(when (column) {
                    OpenableColumns.DISPLAY_NAME -> "${tone.tonePath}.wav"
                    MediaStore.Audio.Media.TITLE -> "Franca · ${tone.title}"
                    OpenableColumns.SIZE -> openFile(uri, "r").use { it.statSize }
                    else -> null
                })
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}
