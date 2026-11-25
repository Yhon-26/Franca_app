package com.grafito.finanzas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.grafito.finanzas.notifications.FrancaNoticeChannel
import com.grafito.finanzas.notifications.FrancaNotices
import org.junit.Assert.*
import org.junit.Test
import java.io.FileNotFoundException
import java.util.UUID

class FrancaNoticeSoundsTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val manager = app.getSystemService(NotificationManager::class.java)

    @Test fun androidCanReadAndDecodeEachBuiltinTone() {
        check(app.packageName.endsWith(".validation"))
        for (notice in FrancaNotices.channels) {
            val uri = notice.soundUri(app)
            assertEquals("audio/wav", app.contentResolver.getType(uri))
            val descriptor = requireNotNull(app.contentResolver.openFileDescriptor(uri, "r"))
            val fileBytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            assertEquals("RIFF", fileBytes.copyOf(4).decodeToString())
            assertTrue(fileBytes.size > 24_000)
            val player = MediaPlayer()
            try {
                player.setDataSource(app, uri)
                player.prepare()
                assertTrue(player.duration in 500..800)
            } finally {
                player.release()
            }
            assertThrows(FileNotFoundException::class.java) {
                app.contentResolver.openInputStream(uri.buildUpon().appendPath("otro").build())?.close()
            }
        }
    }

    @Test fun newAndDefaultChannelsUseFrancaWhileMutedOrBlockedChannelsKeepTheirIds() {
        check(app.packageName.endsWith(".validation"))
        val defaultId = "franca_test_default_${UUID.randomUUID()}"
        val mutedId = "franca_test_muted_${UUID.randomUUID()}"
        val blockedId = "franca_test_blocked_${UUID.randomUUID()}"
        val newId = "franca_test_new_${UUID.randomUUID()}"
        val originalTone = FrancaNotices.yapeIncome
        fun notice(id: String) = FrancaNoticeChannel(id, "Prueba Franca", originalTone.tonePath, originalTone.tone)
        fun create(id: String, importance: Int = NotificationManager.IMPORTANCE_HIGH, silent: Boolean = false) {
            manager.createNotificationChannel(NotificationChannel(id, "Prueba Franca", importance).apply {
                if (silent) setSound(null, null)
            })
        }
        try {
            create(defaultId)
            create(mutedId, silent = true)
            create(blockedId, NotificationManager.IMPORTANCE_NONE)
            val newChannel = notice(newId).ensure(app)
            assertEquals(originalTone.soundUri(app), manager.getNotificationChannel(newChannel).sound)
            val defaultChannel = notice(defaultId).ensure(app)
            if (Build.VERSION.SDK_INT >= 30) {
                assertNotEquals(defaultId, defaultChannel)
                assertEquals(originalTone.soundUri(app), manager.getNotificationChannel(defaultChannel).sound)
            } else {
                assertEquals(defaultId, defaultChannel)
            }
            assertEquals(mutedId, notice(mutedId).ensure(app))
            assertNull(manager.getNotificationChannel(mutedId).sound)
            assertEquals(blockedId, notice(blockedId).ensure(app))
            assertFalse(notice(blockedId).enabled(app))
        } finally {
            listOf(defaultId, mutedId, blockedId, newId).forEach { id ->
                manager.deleteNotificationChannel(id)
                manager.deleteNotificationChannel("${id}_franca")
            }
        }
    }
}
