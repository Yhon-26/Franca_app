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
internal val firstMovementReactionKey = booleanPreferencesKey("first_movement_reaction")
internal val yapeReadyReactionKey = booleanPreferencesKey("yape_ready_reaction")

class UserPreferences(private val context: Context) {
    private val openQuickKey = booleanPreferencesKey("open_quick_on_launch")
    private val interfaceSoundsKey = booleanPreferencesKey("interface_sounds")
    private val yapeSince = longPreferencesKey("yape_capture_since")
    private val yapeLastStatus = stringPreferencesKey("yape_capture_last_status")
    private val yapeLastTime = longPreferencesKey("yape_capture_last_time")
    val openQuickOnLaunch: Flow<Boolean> = context.financePreferences.data.map { it[openQuickKey] ?: true }
    val interfaceSounds: Flow<Boolean> = context.financePreferences.data.map { it[interfaceSoundsKey] ?: true }
    suspend fun setOpenQuick(value: Boolean) {
        context.financePreferences.edit { it[openQuickKey] = value }
    }

    suspend fun setInterfaceSounds(enabled: Boolean) {
        context.financePreferences.edit { it[interfaceSoundsKey] = enabled }
    }

    internal suspend fun claimFirstMovementReaction(): Boolean = claimReaction(firstMovementReactionKey)

    internal suspend fun claimYapeReadyReaction(): Boolean = claimReaction(yapeReadyReactionKey)

    private suspend fun claimReaction(key: Preferences.Key<Boolean>): Boolean {
        var claimed = false
        context.financePreferences.edit {
            if (it[key] != true) {
                it[key] = true
                claimed = true
            }
        }
        return claimed
    }

    val yapeCapture: Flow<YapeCaptureStatus> = context.financePreferences.data.map {
        YapeCaptureStatus(it[yapeSince] ?: 0, it[yapeLastStatus], it[yapeLastTime])
    }

    suspend fun setYapeCapture(enabled: Boolean) {
        context.financePreferences.edit {
            it[yapeSince] = if (enabled) System.currentTimeMillis() else 0L
            it.remove(yapeLastStatus)
            it.remove(yapeLastTime)
        }
    }

    internal suspend fun yapeCaptureSince(): Long = yapeCapture.first().enabledSince

    internal suspend fun reportYapeStatus(session: Long, status: String, time: Long) {
        context.financePreferences.edit {
            if (it[yapeSince] == session && time >= (it[yapeLastTime] ?: 0)) {
                it[yapeLastStatus] = status
                it[yapeLastTime] = time
            }
        }
    }
}

data class YapeCaptureStatus(val enabledSince: Long = 0, val lastStatus: String? = null, val lastTime: Long? = null)
