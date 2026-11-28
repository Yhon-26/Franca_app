package com.grafito.finanzas.model

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grafito.finanzas.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Una única fuente de verdad reactiva para las pantallas del MVP. */
data class FinanceState(
    val accounts: List<AccountEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val isLoaded: Boolean = false,
    val defaultAccountId: Long? = null
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val database = FinanceDatabase.get(application)
    private val dao = database.dao()
    private val preferences = UserPreferences(application)
    private val repository = FinanceRepository(database)
    private val yapeInbox = YapeInbox(database)
    internal val pendingYape = yapeInbox.pending.catch { emit(emptyList()) }
    private val initialized = MutableStateFlow(false)
    private val refresh = MutableStateFlow(0)
    val dataGeneration = MutableStateFlow(0)
    val loadError = MutableStateFlow(false)
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors = _errors.asSharedFlow()

    val openQuickOnLaunch = preferences.openQuickOnLaunch.map { it as Boolean? }
        .catch { _errors.emit("No se pudieron leer tus preferencias."); emit(false) }.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = refresh.flatMapLatest { combine(dao.accounts(), dao.transactions(), dao.categories(), dao.favorites(), dao.settings()) { accounts, entries, categories, favorites, settings ->
        FinanceState(accounts, entries, categories, favorites, defaultAccountId = settings?.defaultAccountId ?: accounts.firstOrNull()?.id)
    }.catch { loadError.value = true } }.combine(initialized) { data, ready -> data.copy(isLoaded = ready) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceState())

    init { retryInitialization() }

    fun retryInitialization() { viewModelScope.launch {
        loadError.value = false
        refresh.value += 1
        safely(false) { database.initialize(); initialized.value = true; true }.also { if (!it) loadError.value = true }
    } }

    private suspend fun <T> safely(fallback: T, action: suspend () -> T): T = try { action() }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { _errors.emit("No se pudo completar la operación. Tus datos guardados se conservan. Inténtalo de nuevo."); fallback }

    fun updateOpenQuick(value: Boolean) { viewModelScope.launch { safely(Unit) { preferences.setOpenQuick(value) } } }
    fun updateDefaultAccount(id: Long) { viewModelScope.launch { safely(Unit) { repository.setDefaultAccount(id) } } }
    fun updateFavoriteAccount(favorite: FavoriteEntity, id: Long) { viewModelScope.launch { safely(Unit) { repository.setFavoriteAccount(favorite, id) } } }

    suspend fun addAccount(name: String, openingBalance: Long = 0L): Boolean {
        return safely(false) { repository.addAccount(name, openingBalance) }
    }

    suspend fun addEntry(
        type: String,
        cents: Long,
        accountId: Long,
        categoryId: Long?,
        note: String,
        existing: TransactionEntity? = null,
        favoriteTitle: String? = null,
        sourceFavoriteId: Long? = null
    ): TransactionEntity? {
        return safely(null) { repository.addEntry(type, cents, accountId, categoryId, note, existing, favoriteTitle, sourceFavoriteId) }
    }

    suspend fun transfer(cents: Long, from: Long, to: Long, note: String): TransactionEntity? {
        return safely(null) { repository.transfer(cents, from, to, note) }
    }

    suspend fun addFavorite(title: String, cents: Long, accountId: Long, categoryId: Long?): Boolean =
        safely(false) { repository.addFavorite(title, cents, accountId, categoryId) }

    suspend fun removeFavorite(favorite: FavoriteEntity) = safely(Unit) { repository.removeFavorite(favorite) }
    suspend fun deleteEntry(entry: TransactionEntity): Boolean = safely(false) { repository.deleteEntry(entry); true }
    suspend fun restoreEntry(entry: TransactionEntity): Boolean = safely(false) { repository.restoreEntry(entry); true }
    internal suspend fun expireYapeNotices() = safely(Unit) { yapeInbox.expireOld() }
    internal suspend fun undoYapeConfirmation(confirmation: YapeConfirmation): Boolean = safely(false) {
        val undone = yapeInbox.undoConfirmation(confirmation)
        if (!undone) _errors.emit("El movimiento o el aviso cambió. Revisa tus movimientos.")
        undone
    }

    suspend fun deleteAccount(account: AccountEntity): Boolean {
        return safely(false) { repository.deleteAccount(account) }
    }

    suspend fun exportBackup(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        try {
            val plain = repository.snapshot().encode()
            val encrypted = try { BackupCipher.encrypt(plain, password) } finally { plain.fill(0) }
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(encrypted) }
        } finally { password.fill('\u0000') }
    }

    suspend fun previewBackup(uri: Uri, password: CharArray): BackupSnapshot = withContext(Dispatchers.IO) {
        try {
            val encrypted = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    require(output.size() + count <= BackupCipher.MAX_BYTES) { "La copia supera 16 MB." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val plain = BackupCipher.decrypt(encrypted, password)
            try { BackupSnapshot.decode(plain) } finally { plain.fill(0) }
        } finally { password.fill('\u0000') }
    }

    suspend fun restoreBackup(snapshot: BackupSnapshot) = withContext(Dispatchers.IO) {
        dataGeneration.value += 1
        repository.restore(snapshot)
    }
}
