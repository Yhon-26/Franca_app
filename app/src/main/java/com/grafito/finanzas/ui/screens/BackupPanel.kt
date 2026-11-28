package com.grafito.finanzas.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.model.BackupViewModel
import com.grafito.finanzas.model.FinanceMath
import com.grafito.finanzas.model.Money
import java.text.DateFormat
import java.util.Date

private enum class BackupMode { EXPORT, PICKER, RESTORE }

@Composable
internal fun BackupPanel(onBeforeRestore: () -> Unit, vm: BackupViewModel = viewModel(factory = BackupViewModel.Factory)) {
    var mode by rememberSaveable { mutableStateOf<BackupMode?>(null) }
    var source by rememberSaveable { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val state by vm.state.collectAsStateWithLifecycle()
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        mode = null
        vm.finishExport(uri)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            source = uri
            mode = BackupMode.RESTORE
            vm.clearMessage()
        }
    }
    LaunchedEffect(state.preview) {
        if (state.preview != null) {
            mode = null
            source = null
        }
    }
    fun closePassword() {
        mode = null
        source = null
        password = ""
        confirmation = ""
        vm.clearMessage()
    }

    SettingsCard(Modifier.fillMaxWidth()) {
        Text("Guarda cuentas, movimientos, categorías, atajos y tu cuenta predeterminada en un archivo cifrado con contraseña. Sin ella no se puede recuperar la copia.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { mode = BackupMode.EXPORT; vm.clearMessage() }, enabled = !state.busy && mode != BackupMode.PICKER, modifier = Modifier.fillMaxWidth()) { Text("Crear copia cifrada") }
        OutlinedButton(onClick = { open.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = !state.busy && mode != BackupMode.PICKER,
            modifier = Modifier.fillMaxWidth()) { Text("Restaurar una copia") }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    }
    if (mode in listOf(BackupMode.EXPORT, BackupMode.RESTORE) && !state.busy && state.preview == null) {
        val exporting = mode == BackupMode.EXPORT
        AlertDialog(
            onDismissRequest = ::closePassword,
            title = { Text(if (exporting) "Proteger tu copia" else "Abrir copia cifrada") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (exporting) "Elige una contraseña de al menos 12 caracteres. No se guarda en la app. Guarda la copia fuera del almacenamiento privado de Franca."
                         else "Introduce la contraseña de la copia. Primero podrás revisar su contenido; tus datos actuales todavía no cambiarán.")
                    OutlinedTextField(value = password, onValueChange = { password = it.take(256) }, label = { Text("Contraseña de la copia") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                    if (exporting) OutlinedTextField(value = confirmation, onValueChange = { confirmation = it.take(256) },
                        label = { Text("Repetir contraseña") }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = password.length >= 12 && (!exporting || password == confirmation), onClick = {
                    if (exporting) {
                        vm.prepareExport(password.toCharArray())
                        password = ""
                        confirmation = ""
                        mode = BackupMode.PICKER
                        create.launch("Franca-${java.time.LocalDate.now()}.grafito")
                    } else {
                        val uri = source ?: return@TextButton
                        val secret = password.toCharArray()
                        password = ""
                        vm.preview(uri, secret)
                    }
                }) { Text(if (exporting) "Elegir dónde guardar" else "Revisar copia") }
            },
            dismissButton = { TextButton(onClick = ::closePassword) { Text("Cancelar") } }
        )
    }
    if (state.busy) AlertDialog(onDismissRequest = {}, title = { Text("Procesando copia") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Espera a que termine la operación.") } }, confirmButton = {})
    state.preview?.let { snapshot ->
        if (!state.busy) AlertDialog(
            onDismissRequest = vm::closePreview,
            title = { Text("Revisar restauración") },
            text = {
                val total = FinanceMath.accountBalances(snapshot.accounts, snapshot.transactions).values.sum()
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Copia del ${DateFormat.getDateTimeInstance().format(Date(snapshot.createdAt))}")
                    Text("${snapshot.accounts.size} cuentas · ${snapshot.transactions.size} movimientos · ${snapshot.favorites.size} atajos\nSaldo total: ${Money.format(total)}")
                    Text("Restaurar reemplaza todos los datos financieros actuales. No combina historiales. Crea primero una copia de tus datos actuales si quieres conservarlos.")
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.restore(onBeforeRestore) }) { Text("Reemplazar y restaurar") }
            },
            dismissButton = { TextButton(onClick = vm::closePreview) { Text("Cancelar") } }
        )
    }
}
