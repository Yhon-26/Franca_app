package com.grafito.finanzas.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grafito.finanzas.data.BcpMailInbox
import com.grafito.finanzas.data.BcpMailSuggestion
import com.grafito.finanzas.data.FinanceDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal sealed interface BcpPendingState {
    data object Loading : BcpPendingState
    data object Failed : BcpPendingState
    data class Available(val items: List<BcpMailSuggestion>) : BcpPendingState
}

internal class BcpPendingViewModel(observePending: () -> Flow<List<BcpMailSuggestion>>) : ViewModel() {
    private val attempts = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pending = attempts.flatMapLatest {
        flow<BcpPendingState> {
            emit(BcpPendingState.Loading)
            emitAll(observePending().map { BcpPendingState.Available(it) })
        }.catch { emit(BcpPendingState.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BcpPendingState.Loading)

    fun retry() = attempts.update { it + 1 }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val inbox = BcpMailInbox(FinanceDatabase.get(app))
                BcpPendingViewModel { inbox.pending }
            }
        }
    }
}
