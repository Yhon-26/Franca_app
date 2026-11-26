package com.grafito.finanzas.receipts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.bcpScreenPreferences by preferencesDataStore("bcp_screen")

internal data class BcpScreenReport(
    val status: BcpScreenStatus? = null,
    val fields: Int = 0,
    val checkedAt: Long = 0,
    val signals: Int = 0,
    val lastReadAt: Long? = null
)

internal class BcpScreenSettings(context: Context) {
    private val store = context.applicationContext.bcpScreenPreferences
    private val status = stringPreferencesKey("status")
    private val fields = intPreferencesKey("fields")
    private val checkedAt = longPreferencesKey("checked_at")
    private val signals = intPreferencesKey("access_signals")
    private val legacyWindowCount = intPreferencesKey("application_windows")

    private val lastReadAt = longPreferencesKey("last_valid_read_at")
    private fun lastValidRead(prefs: androidx.datastore.preferences.core.Preferences): Long? =
        prefs[lastReadAt] ?: prefs[checkedAt]?.takeIf {
            prefs[status] == BcpScreenStatus.DETECTED.name || prefs[status] == BcpScreenStatus.DUPLICATE.name
        }

    val report = store.data.map { preferences ->
        BcpScreenReport(
            preferences[status]?.let { name -> BcpScreenStatus.entries.firstOrNull { it.name == name } },
            preferences[fields] ?: 0,
            preferences[checkedAt] ?: 0,
            preferences[signals] ?: 0,
            lastValidRead(preferences)
        )
    }

    suspend fun report(result: BcpScreenParser.Result, validRead: Boolean = false) {
        val now = System.currentTimeMillis()
        store.edit { preferences ->
            if (validRead) preferences[lastReadAt] = now
            else lastValidRead(preferences)?.let { preferences[lastReadAt] = it }
            preferences.remove(legacyWindowCount)
            if (preferences[status] != result.status.name || preferences[fields] != result.fields ||
                preferences[signals] != result.signals ||
                now - (preferences[checkedAt] ?: 0) >= 5_000) {
                preferences[status] = result.status.name
                preferences[fields] = result.fields
                preferences[checkedAt] = now
                preferences[signals] = result.signals
            }
        }
    }
}
