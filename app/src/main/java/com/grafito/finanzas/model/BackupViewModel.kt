package com.grafito.finanzas.model

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grafito.finanzas.data.BackupFiles
import com.grafito.finanzas.data.BackupSnapshot
import com.grafito.finanzas.data.FinanceDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class BackupUiState(
    val busy: Boolean = false,
    val preview: BackupSnapshot? = null,
    val message: String? = null
)

internal class BackupViewModel(private val files: BackupFiles) : ViewModel() {
    private val mutableState = MutableStateFlow(BackupUiState())
    val state = mutableState.asStateFlow()
    private var exportPassword: CharArray? = null

    fun prepareExport(password: CharArray) {
        exportPassword?.fill('\u0000')
        exportPassword = password
    }

    fun finishExport(uri: Uri?) {
        val secret = exportPassword
        exportPassword = null
        if (uri == null) {
            secret?.fill('\u0000')
            return
        }
        if (secret == null) {
            mutableState.update { it.copy(message = "Vuelve a iniciar la copia y elige una contraseña.") }
            return
        }
        runOperation("No se pudo guardar la copia. Revisa el espacio y el destino y elimina el archivo incompleto si se creó.", secret) {
            files.export(uri, secret)
            mutableState.update { it.copy(message = "Copia cifrada guardada. Conserva tu contraseña en un lugar seguro.") }
        }
    }

    fun preview(uri: Uri, password: CharArray) = runOperation(
        "No se pudo abrir la copia. Revisa la contraseña y el archivo.", password
    ) {
        val snapshot = files.preview(uri, password)
        mutableState.update { it.copy(preview = snapshot) }
    }

    fun restore(onBeforeRestore: () -> Unit) {
        val snapshot = mutableState.value.preview ?: return
        runOperation("No se pudo restaurar. Se conservan los datos anteriores.") {
            onBeforeRestore()
            files.restore(snapshot)
            mutableState.update { it.copy(preview = null, message = "Copia restaurada. Los saldos y movimientos se actualizaron.") }
        }
    }

    fun clearMessage() = mutableState.update { it.copy(message = null) }

    fun closePreview() = mutableState.update { it.copy(preview = null, message = null) }

    private fun runOperation(failure: String, secret: CharArray? = null, action: suspend () -> Unit) {
        if (mutableState.value.busy) {
            secret?.fill('\u0000')
            return
        }
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(message = failure) }
            } finally {
                secret?.fill('\u0000')
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCleared() {
        exportPassword?.fill('\u0000')
        exportPassword = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                BackupViewModel(BackupFiles(app.contentResolver, FinanceDatabase.get(app).repository))
            }
        }
    }
}
