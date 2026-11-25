package com.grafito.finanzas.data

import org.junit.Assert.*
import org.junit.Test

class BackupCipherTest {
    private val password get() = "clave-solo-para-pruebas".toCharArray()
    @Test fun roundTripUsesFreshSaltAndNonce() {
        val plain = "historial privado S/ 1.50".toByteArray()
        val a = BackupCipher.encrypt(plain, password)
        val b = BackupCipher.encrypt(plain, password)
        assertFalse(a.contentEquals(b))
        assertFalse(String(a).contains("historial privado"))
        assertArrayEquals(plain, BackupCipher.decrypt(a, password))
    }
    @Test fun wrongPasswordAndTamperingCannotDecrypt() {
        val encrypted = BackupCipher.encrypt("privado".toByteArray(), password)
        assertThrows(Exception::class.java) { BackupCipher.decrypt(encrypted, "otra-clave-incorrecta".toCharArray()) }
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { BackupCipher.decrypt(encrypted, password) }
    }
    @Test fun rejectsTruncationWrongVersionAndShortPassword() {
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.decrypt(ByteArray(15), password) }
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.decrypt(ByteArray(100), password) }
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.encrypt(byteArrayOf(1), "corta".toCharArray()) }
    }
}
