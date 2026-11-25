package com.grafito.finanzas.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable authenticated backup: magic/version + random salt + random nonce + AES-GCM payload. */
object BackupCipher {
    const val MAX_BYTES = 16 * 1024 * 1024
    private val magic = "GRAFITO1".toByteArray(Charsets.US_ASCII)
    private const val HEADER_SIZE = 8 + 16 + 12

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size in 12..256) { "Usa una contraseña de 12 a 256 caracteres." }
        val spec = PBEKeySpec(password, salt, 600_000, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(plain.size <= MAX_BYTES - HEADER_SIZE - 16) { "La copia supera el límite de 16 MB." }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = magic + salt + nonce
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    fun decrypt(encrypted: ByteArray, password: CharArray): ByteArray {
        require(encrypted.size in (HEADER_SIZE + 16)..MAX_BYTES && encrypted.take(8).toByteArray().contentEquals(magic)) {
            "El archivo no es una copia compatible de Franca."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, encrypted.copyOfRange(8, 24)),
            GCMParameterSpec(128, encrypted.copyOfRange(24, 36)))
        cipher.updateAAD(encrypted.copyOfRange(0, HEADER_SIZE))
        return cipher.doFinal(encrypted, HEADER_SIZE, encrypted.size - HEADER_SIZE)
    }
}
