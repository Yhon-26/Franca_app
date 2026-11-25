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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.BackupSnapshot
import com.grafito.finanzas.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupPanel(vm: FinanceViewModel) {
    var mode by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupSnapshot?>(null) }
    val scope = rememberCoroutineScope()
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val secret = password.toCharArray()
        password = ""; confirmation = ""
        if (uri != null) {
            busy = true
            scope.launch {
                try { vm.exportBackup(uri, secret); message = "Copia cifrada guardada. Conserva tu contraseña en un lugar seguro." }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = "No se pudo guardar la copia. Revisa el espacio y el destino; elimina el archivo incompleto si se creó." }
                finally { secret.fill('\u0000'); busy = false; mode = null }
            }
        } else { secret.fill('\u0000'); mode = null }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { source = uri; mode = "restore"; message = null }
    }
    SettingsCard(Modifier.fillMaxWidth()) {
        Text("Guarda cuentas, movimientos, categorías, atajos y tu cuenta predeterminada en un archivo cifrado con contraseña. Sin ella no se puede recuperar la copia.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { mode = "export"; message = null }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Crear copia cifrada") }
        OutlinedButton(onClick = { open.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text("Restaurar una copia") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    }
    if (mode in listOf("export", "restore") && !busy && preview == null) {
        val exporting = mode == "export"
        AlertDialog(
            onDismissRequest = { mode = null; source = null; password = ""; confirmation = "" },
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
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = password.length >= 12 && (!exporting || password == confirmation), onClick = {
                    if (exporting) {
                        mode = "picker"
                        create.launch("Franca-${java.time.LocalDate.now()}.grafito")
                    } else {
                        val uri = source ?: return@TextButton
                        val secret = password.toCharArray(); password = ""; busy = true
                        scope.launch {
                            try { preview = vm.previewBackup(uri, secret); mode = null; source = null; message = null }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { message = "No se pudo abrir la copia: contraseña incorrecta, archivo dañado o formato no compatible. No se modificaron tus datos." }
                            finally { secret.fill('\u0000'); busy = false }
                        }
                    }
                }) { Text(if (exporting) "Elegir dónde guardar" else "Revisar copia") }
            },
            dismissButton = { TextButton(onClick = { mode = null; source = null; password = ""; confirmation = "" }) { Text("Cancelar") } }
        )
    }
    if (busy) AlertDialog(onDismissRequest = {}, title = { Text("Procesando copia") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Espera a que termine la operación.") } }, confirmButton = {})
    preview?.let { snapshot ->
        if (!busy) AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Revisar restauración") },
            text = {
                val total = FinanceMath.accountBalances(snapshot.accounts, snapshot.transactions).values.sum()
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Copia del ${DateFormat.getDateTimeInstance().format(Date(snapshot.createdAt))}")
                    Text("${snapshot.accounts.size} cuentas · ${snapshot.transactions.size} movimientos · ${snapshot.favorites.size} atajos\nSaldo total: ${Money.format(total)}")
                    Text("Restaurar reemplaza todos los datos financieros actuales. No combina historiales. Crea primero una copia de tus datos actuales si quieres conservarlos.")
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = {
                busy = true; message = null
                scope.launch {
                    try { vm.restoreBackup(snapshot); preview = null; message = "Copia restaurada. Los saldos y movimientos se actualizaron." }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = "No se pudo restaurar. Se conservan los datos anteriores." }
                    finally { busy = false }
                }
            }) { Text("Reemplazar y restaurar") } },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancelar") } }
        )
    }
}
