package com.grafito.finanzas.data

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BackupFiles(private val resolver: ContentResolver, private val repository: FinanceRepository) {
    suspend fun export(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        val plain = repository.snapshot().encode()
        val encrypted = try { BackupCipher.encrypt(plain, password) } finally { plain.fill(0) }
        checkNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(encrypted) }
    }

    suspend fun preview(uri: Uri, password: CharArray): BackupSnapshot = withContext(Dispatchers.IO) {
        val encrypted = checkNotNull(resolver.openInputStream(uri)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val size = input.read(buffer)
                if (size == -1) break
                require(output.size() + size <= BackupCipher.MAX_BYTES) { "La copia supera 16 MB." }
                output.write(buffer, 0, size)
            }
            output.toByteArray()
        }
        val plain = BackupCipher.decrypt(encrypted, password)
        try { BackupSnapshot.decode(plain) } finally { plain.fill(0) }
    }

    suspend fun restore(snapshot: BackupSnapshot) = withContext(Dispatchers.IO) {
        repository.restore(snapshot)
    }
}
