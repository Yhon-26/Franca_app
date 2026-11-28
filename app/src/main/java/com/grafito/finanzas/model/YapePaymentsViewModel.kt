package com.grafito.finanzas.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grafito.finanzas.data.FinanceDatabase
import com.grafito.finanzas.data.YapeConfirmation
import com.grafito.finanzas.data.YapeInbox
import com.grafito.finanzas.data.YapeReviewRepository
import com.grafito.finanzas.data.YapeSuggestion
import com.grafito.finanzas.notifications.YapeAlerts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface PendingYapeState {
    data object Loading : PendingYapeState
    data object Failed : PendingYapeState
    data class Available(val notices: List<YapeSuggestion>) : PendingYapeState
}

internal sealed interface YapeReviewState {
    data object Loading : YapeReviewState
    data object Unavailable : YapeReviewState
    data object LoadFailed : YapeReviewState
    data class Ready(val notice: YapeSuggestion, val busy: Boolean = false, val error: String? = null) : YapeReviewState
    data class Saved(val confirmation: YapeConfirmation) : YapeReviewState
    data object Discarded : YapeReviewState
}

internal data class ActiveYapeReview(val id: String, val state: YapeReviewState)

internal class YapePaymentsViewModel(
    private val repository: YapeReviewRepository,
    private val cancelAlert: (String) -> Unit
) : ViewModel() {
    private val pendingRefresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pending = pendingRefresh.flatMapLatest {
        flow<PendingYapeState> {
            emit(PendingYapeState.Loading)
            repository.expireOld()
            emitAll(repository.pending.map { PendingYapeState.Available(it) })
        }.catch { emit(PendingYapeState.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PendingYapeState.Loading)

    private val activeReview = MutableStateFlow<ActiveYapeReview?>(null)
    val review = activeReview.asStateFlow()
    private var loading: Job? = null

    fun refreshPending() {
        pendingRefresh.update { it + 1 }
    }

    fun open(id: String) {
        if (activeReview.value?.id != id) load(id)
    }

    fun retry(id: String) {
        if (activeReview.value == ActiveYapeReview(id, YapeReviewState.LoadFailed)) load(id)
    }

    private fun load(id: String) {
        if ((activeReview.value?.state as? YapeReviewState.Ready)?.busy == true) return
        loading?.cancel()
        activeReview.value = ActiveYapeReview(id, YapeReviewState.Loading)
        loading = viewModelScope.launch {
            val state = try {
                repository.expireOld()
                repository.findPending(id)?.let { YapeReviewState.Ready(it) } ?: YapeReviewState.Unavailable
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                YapeReviewState.LoadFailed
            }
            activeReview.value = ActiveYapeReview(id, state)
        }
    }

    fun close(id: String): Boolean {
        val current = activeReview.value ?: return true
        if (current.id != id || (current.state as? YapeReviewState.Ready)?.busy == true) return false
        loading?.cancel()
        activeReview.value = null
        return true
    }

    fun consumeResult(result: ActiveYapeReview): Boolean = activeReview.compareAndSet(result, null)

    fun confirm(id: String, account: Long, amount: Long, category: Long?, other: Long?) = complete(id) {
        repository.confirmForReview(id, account, amount, category, other)?.let { YapeReviewState.Saved(it) }
    }

    fun discard(id: String) = complete(id) {
        YapeReviewState.Discarded.takeIf { repository.discard(id) == 1 }
    }

    private fun complete(id: String, operation: suspend () -> YapeReviewState?) {
        val current = activeReview.value?.takeIf { it.id == id }?.state as? YapeReviewState.Ready ?: return
        if (current.busy) return
        activeReview.value = ActiveYapeReview(id, current.copy(busy = true, error = null))
        viewModelScope.launch {
            val state = try {
                operation() ?: if (repository.findPending(id) == null) YapeReviewState.Unavailable
                    else current.copy(error = "Revisa el aviso, las cuentas o la categoría seleccionadas.")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                current.copy(error = "No se pudo completar la operación. Vuelve a intentarlo.")
            }
            if (state is YapeReviewState.Saved || state == YapeReviewState.Discarded) {
                // The transaction already succeeded, even if Android cannot remove the notification.
                runCatching { cancelAlert(id) }
            }
            activeReview.value = ActiveYapeReview(id, state)
        }
    }

    suspend fun undo(confirmation: YapeConfirmation): Boolean = try {
        repository.undoConfirmation(confirmation)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                val inbox = YapeInbox(FinanceDatabase.get(application))
                YapePaymentsViewModel(inbox) { id -> YapeAlerts.cancel(application, id) }
            }
        }
    }
}
