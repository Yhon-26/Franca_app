package com.grafito.finanzas.receipts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.grafito.finanzas.data.BcpMailInbox
import com.grafito.finanzas.data.FinanceDatabase
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

private val Context.bcpGmailPreferences by preferencesDataStore("bcp_gmail")

internal data class GmailConnection(val email: String? = null, val since: Long = 0, val status: String? = null, val lastSync: Long? = null)

internal class BcpGmailSettings(context: Context) {
    private val store = context.applicationContext.bcpGmailPreferences
    private val email = stringPreferencesKey("email")
    private val since = longPreferencesKey("since")
    private val status = stringPreferencesKey("status")
    private val lastSync = longPreferencesKey("last_sync")
    val connection = store.data.map { GmailConnection(it[email], it[since] ?: 0, it[status], it[lastSync]) }

    suspend fun connect(account: String) = store.edit {
        it[email] = account
        it[since] = System.currentTimeMillis()
        it.remove(status)
        it.remove(lastSync)
    }

    suspend fun disconnect() = store.edit { it.clear() }

    suspend fun report(session: GmailConnection, result: String) = store.edit {
        if (it[email] == session.email && it[since] == session.since) {
            it[status] = result
            if (result == "ok") it[lastSync] = System.currentTimeMillis()
        }
    }
}

class BcpGmailWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = BcpGmailSync.checkMutex.withLock { checkGmail() }

    private suspend fun checkGmail(): Result {
        val settings = BcpGmailSettings(applicationContext)
        val session = settings.connection.first()
        val email = session.email ?: return Result.success()
        // Ignore the previous experimental quick checks when upgrading an existing installation.
        if (inputData.keyValueMap.containsKey("bcp_requested_at")) return Result.success()
        val inbox = BcpMailInbox(FinanceDatabase.get(applicationContext))
        try {
            val token = getGmailAccessToken(applicationContext, email)
            if (token == null) {
                settings.report(session, "authorization")
                return Result.success()
            }
            BcpGmailClient(token).collectPayments { payment ->
                val current = settings.connection.first()
                if (current.since == session.since && current.email == email && inbox.record(payment)) {
                    BcpMailAlerts.show(applicationContext, payment)
                    if (inbox.findPending(payment.id) == null) BcpMailAlerts.cancel(applicationContext, payment.id)
                }
            }
            settings.report(session, "ok")
            return Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: ApiException) {
            settings.report(session, "authorization")
        } catch (error: GmailHttpError) {
            if (error.status == 401 || error.status == 403) settings.report(session, "authorization")
            else {
                settings.report(session, "error")
                if (error.status == 429 || error.status >= 500) return Result.retry()
            }
        } catch (_: IOException) {
            settings.report(session, "network")
            return Result.retry()
        } catch (_: Exception) {
            settings.report(session, "error")
        }
        return Result.success()
    }
}

internal object BcpGmailSync {
    internal const val PERIODIC = "bcp_gmail_periodic"
    internal const val MANUAL = "bcp_gmail_manual"
    internal const val AFTER_BCP = "bcp_gmail_after_bcp"
    internal val checkMutex = Mutex()
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BcpGmailWorker>(15, TimeUnit.MINUTES).setInitialDelay(15, TimeUnit.MINUTES)
                .setConstraints(network).build())
        checkNow(context)
    }

    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BcpGmailWorker>().setConstraints(network).build())
    }

    suspend fun disconnect(context: Context) {
        BcpGmailSettings(context.applicationContext).disconnect()
        stop(context)
    }

    fun stop(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(PERIODIC)
        manager.cancelUniqueWork(MANUAL)
        manager.cancelUniqueWork(AFTER_BCP)
    }
}
