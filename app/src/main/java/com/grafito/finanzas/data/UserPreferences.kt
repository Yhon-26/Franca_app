package com.grafito.finanzas.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.financePreferences by preferencesDataStore(name = "grafito_preferences")
internal val yapeReadyReactionKey = booleanPreferencesKey("yape_ready_reaction")

class UserPreferences(private val context: Context) {
    private val openQuickKey = booleanPreferencesKey("open_quick_on_launch")
    private val interfaceSoundsKey = booleanPreferencesKey("interface_sounds")
    private val yapeSince = longPreferencesKey("yape_capture_since")
    private val yapeLastStatus = stringPreferencesKey("yape_capture_last_status")
    private val yapeLastTime = longPreferencesKey("yape_capture_last_time")
    private val incomeReadAt = longPreferencesKey("yape_income_read_at")
    private val sentReadAt = longPreferencesKey("yape_sent_read_at")

    private fun lastIncomeRead(prefs: Preferences): Long? = prefs[incomeReadAt]
        ?: prefs[yapeLastTime]?.takeIf { prefs[yapeLastStatus] in setOf("detected", "duplicate") }

    internal suspend fun reportYapeSentRead() {
        context.financePreferences.edit { it[sentReadAt] = System.currentTimeMillis() }
    }

    val openQuickOnLaunch: Flow<Boolean> = context.financePreferences.data.map { it[openQuickKey] ?: true }
    val interfaceSounds: Flow<Boolean> = context.financePreferences.data.map { it[interfaceSoundsKey] ?: true }
    suspend fun setOpenQuick(value: Boolean) {
        context.financePreferences.edit { it[openQuickKey] = value }
    }

    suspend fun setInterfaceSounds(enabled: Boolean) {
        context.financePreferences.edit { it[interfaceSoundsKey] = enabled }
    }

    internal suspend fun claimYapeReadyReaction(): Boolean {
        var claimed = false
        context.financePreferences.edit {
            if (it[yapeReadyReactionKey] != true) {
                it[yapeReadyReactionKey] = true
                claimed = true
            }
        }
        return claimed
    }

    val yapeCapture: Flow<YapeCaptureStatus> = context.financePreferences.data.map {
        YapeCaptureStatus(it[yapeSince] ?: 0, it[yapeLastStatus], it[yapeLastTime], lastIncomeRead(it), it[sentReadAt])
    }

    suspend fun setYapeCapture(enabled: Boolean) {
        context.financePreferences.edit {
            it[yapeSince] = if (enabled) System.currentTimeMillis() else 0L
            lastIncomeRead(it)?.let { time -> it[incomeReadAt] = time }
            it.remove(yapeLastStatus)
            it.remove(yapeLastTime)
        }
    }

    internal suspend fun yapeCaptureSince(): Long = yapeCapture.first().enabledSince

    internal suspend fun reportYapeStatus(session: Long, status: String, time: Long) {
        context.financePreferences.edit {
            if (session <= 0 || it[yapeSince] != session) return@edit
            val previous = lastIncomeRead(it)
            if (status == "detected" || status == "duplicate") {
                it[incomeReadAt] = maxOf(previous ?: 0, time)
            } else if (previous != null) {
                it[incomeReadAt] = previous
            }
            if (time >= (it[yapeLastTime] ?: 0)) {
                it[yapeLastStatus] = status
                it[yapeLastTime] = time
            }
        }
    }
}

data class YapeCaptureStatus(
    val enabledSince: Long = 0,
    val lastStatus: String? = null,
    val lastTime: Long? = null,
    val lastIncomeReadAt: Long? = null,
    val lastSentReadAt: Long? = null
)
