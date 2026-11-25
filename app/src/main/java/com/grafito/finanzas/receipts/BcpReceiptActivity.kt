package com.grafito.finanzas.receipts

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.grafito.finanzas.data.AccountEntity
import com.grafito.finanzas.data.BcpMailInbox
import com.grafito.finanzas.data.BcpMailSuggestion
import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.FinanceDatabase
import com.grafito.finanzas.data.FinanceRepository
import com.grafito.finanzas.data.ReceiptSaveResult
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.ui.components.CategorySelector
import com.grafito.finanzas.ui.components.FrancaCue
import com.grafito.finanzas.ui.components.LocalFrancaSounds
import com.grafito.finanzas.ui.components.ProvideFrancaSounds
import com.grafito.finanzas.ui.components.ScreenTitle
import com.grafito.finanzas.ui.theme.GrafitoTheme
import com.grafito.finanzas.ui.theme.HomePalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

open class BcpReceiptActivity : ComponentActivity() {
    private val model: BcpReceiptViewModel by viewModels()
    protected open val gmailReview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        model.receive(intent, gmailReview)
        setContent {
            ProvideFrancaSounds {
                val state by model.state.collectAsStateWithLifecycle()
                val accounts by model.accounts.collectAsStateWithLifecycle()
                val categories by model.categories.collectAsStateWithLifecycle()
                val playSound = LocalFrancaSounds.current
                BackHandler(enabled = (state as? ReceiptReview.Ready)?.saving == true) {}
                LaunchedEffect(state) {
                    if (state == ReceiptReview.Saved) {
                        playSound(FrancaCue.SAVED)
                        Toast.makeText(this@BcpReceiptActivity, "Movimiento guardado en Franca", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    if (state == ReceiptReview.Dismissed) finish()
                }
                GrafitoTheme(colorScheme = HomePalette.panelScheme) {
                    Scaffold(containerColor = HomePalette.Background, contentColor = HomePalette.Navy) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                            Column(Modifier.widthIn(max = 620.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ScreenTitle(if (gmailReview) "Pago BCP detectado" else "Constancia BCP")
                                when (val review = state) {
                                    ReceiptReview.Reading -> {
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                        Text("Leyendo la constancia…")
                                        OutlinedButton(onClick = ::finish) { Text("Cancelar") }
                                    }
                                    ReceiptReview.Unreadable -> ReceiptMessage("No pudimos abrir la constancia. Vuelve a compartirla desde BCP.", ::finish)
                                    ReceiptReview.Duplicate -> ReceiptMessage("Esta constancia ya está registrada en Franca.", ::finish)
                                    ReceiptReview.Unavailable -> ReceiptMessage("Este aviso ya no está pendiente o no pudo abrirse.", ::finish)
                                    ReceiptReview.Saved -> Text("Movimiento guardado.")
                                    ReceiptReview.Dismissed -> Text("Aviso descartado.")
                                    is ReceiptReview.Ready -> ReceiptForm(review, accounts, categories, ::finish, model::discard, model::save)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class BcpMailReviewActivity : BcpReceiptActivity() {
    override val gmailReview = true
}

internal sealed interface ReceiptReview {
    data object Reading : ReceiptReview
    data object Unreadable : ReceiptReview
    data object Duplicate : ReceiptReview
    data object Unavailable : ReceiptReview
    data object Saved : ReceiptReview
    data object Dismissed : ReceiptReview
    data class Ready(val receipt: SharedReceipt, val mail: BcpMailSuggestion? = null,
                     val saving: Boolean = false, val error: String? = null) : ReceiptReview
}

internal class BcpReceiptViewModel(application: Application) : AndroidViewModel(application) {
    private val database = FinanceDatabase.get(application)
    private val repository = FinanceRepository(database)
    private val inbox = BcpMailInbox(database)
    private val reader = SharedReceiptReader(application)
    private var started = false
    val state = MutableStateFlow<ReceiptReview>(ReceiptReview.Reading)
    val accounts = database.dao().accounts().catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = database.dao().categories().catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun receive(intent: Intent, gmailReview: Boolean) {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                database.initialize()
                if (gmailReview) {
                    val mail = intent.data?.schemeSpecificPart?.let { inbox.findPending(it) }
                    state.value = if (mail == null) ReceiptReview.Unavailable
                    else ReceiptReview.Ready(SharedReceipt(mail.id, mail.amountCents), mail = mail)
                    return@launch
                }
                val receipt = reader.read(intent)
                state.value = if (repository.isDuplicateSharedReceipt(receipt.fingerprint, receipt.operationFingerprint)) {
                    receipt.operationFingerprint?.let { if (inbox.findPending(it) == null) BcpMailAlerts.cancel(getApplication(), it) }
                    ReceiptReview.Duplicate
                } else ReceiptReview.Ready(receipt)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value = if (gmailReview) ReceiptReview.Unavailable else ReceiptReview.Unreadable
            }
        }
    }

    fun save(cents: Long, from: Long, to: Long?, note: String, category: Long?) {
        val current = state.value as? ReceiptReview.Ready ?: return
        if (current.saving) return
        state.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val mail = current.mail
                val result = if (mail != null) inbox.confirm(mail.id, cents, from, to, note, category)
                else repository.saveBcpReceipt(current.receipt.fingerprint, cents, from, to, note,
                    operationFingerprint = current.receipt.operationFingerprint)
                val operation = mail?.id ?: current.receipt.operationFingerprint
                if (operation != null && result != ReceiptSaveResult.INVALID && inbox.findPending(operation) == null)
                    BcpMailAlerts.cancel(getApplication(), operation)
                state.value = when (result) {
                    ReceiptSaveResult.SAVED -> ReceiptReview.Saved
                    ReceiptSaveResult.DUPLICATE -> ReceiptReview.Duplicate
                    ReceiptSaveResult.INVALID -> current.copy(error = "Revisa el importe y las cuentas seleccionadas.")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value = current.copy(error = "No se pudo guardar. Inténtalo de nuevo.")
            }
        }
    }

    fun discard() {
        val current = state.value as? ReceiptReview.Ready ?: return
        val mail = current.mail ?: return
        if (current.saving) return
        state.value = current.copy(saving = true)
        viewModelScope.launch {
            try {
                database.bcpMailSuggestions().dismiss(mail.id)
                BcpMailAlerts.cancel(getApplication(), mail.id)
                state.value = ReceiptReview.Dismissed
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value = current.copy(error = "No se pudo descartar el aviso. Inténtalo de nuevo.")
            }
        }
    }
}

@Composable
private fun ReceiptMessage(message: String, onClose: () -> Unit) {
    Text(message)
    OutlinedButton(onClick = onClose) { Text("Volver") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiptForm(review: ReceiptReview.Ready, accounts: List<AccountEntity>, categories: List<CategoryEntity>,
                        onClose: () -> Unit, onDiscard: () -> Unit, onSave: (Long, Long, Long?, String, Long?) -> Unit) {
    var amount by rememberSaveable(review.receipt.fingerprint) {
        mutableStateOf(review.receipt.suggestedCents?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}" }.orEmpty())
    }
    var note by rememberSaveable { mutableStateOf("") }
    var fromId by rememberSaveable { mutableStateOf<Long?>(null) }
    var toId by rememberSaveable { mutableStateOf<Long?>(null) }
    var transfer by rememberSaveable { mutableStateOf(false) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var accountPrefilled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(accounts) {
        if (!accountPrefilled && accounts.isNotEmpty()) {
            fromId = accounts.singleOrNull { Regex("\\bbcp\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }?.id
            accountPrefilled = true
        }
    }
    val cents = Money.parse(amount)
    val from = accounts.find { it.id == fromId }
    val to = accounts.find { it.id == toId && it.id != fromId }
    Text("Comprueba el monto y elige cómo registrarlo.", color = HomePalette.Muted, style = MaterialTheme.typography.bodySmall)
    if (review.mail == null && review.receipt.operationFingerprint == null) {
        Text("Para detectar duplicados de Gmail hacen falta la fecha y el número de operación BCP. Revisa el historial antes de guardar.",
            style = MaterialTheme.typography.bodySmall)
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = HomePalette.Paper, contentColor = HomePalette.Navy),
        border = BorderStroke(1.dp, HomePalette.Line)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (review.receipt.suggestedCents == null) {
                Text("Escribe el importe en soles.", style = MaterialTheme.typography.bodySmall, color = HomePalette.Muted)
            }
            OutlinedTextField(value = amount, onValueChange = { amount = it.take(12) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Importe en soles") }, singleLine = true, enabled = !review.saving,
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                prefix = { Text("S/ ", color = HomePalette.Muted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !transfer, onClick = { transfer = false }, enabled = !review.saving, label = { Text("Gasto") })
                FilterChip(selected = transfer, onClick = { transfer = true }, enabled = accounts.size >= 2 && !review.saving,
                    label = { Text("Entre mis cuentas") })
            }
            ReceiptAccountMenu("Cuenta de origen", from, accounts, !review.saving) { fromId = it.id }
            if (transfer) ReceiptAccountMenu("Cuenta de destino", to, accounts.filter { it.id != fromId }, !review.saving) { toId = it.id }
            if (review.mail != null && !transfer) {
                CategorySelector(categories.filter { it.type == EntryType.EXPENSE }, categoryId,
                    onSelect = { categoryId = it }, enabled = !review.saving)
            }
            OutlinedTextField(value = note, onValueChange = { note = it.take(160) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Concepto (opcional)") }, singleLine = true, enabled = !review.saving)
        }
    }
    review.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        if (cents != null && from != null) onSave(cents, from.id, if (transfer) to?.id else null, note,
            if (transfer) null else categoryId)
    }, enabled = !review.saving && cents != null && from != null && (!transfer || to != null),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
        Text(if (review.saving) "Guardando…" else if (transfer) "Guardar transferencia" else "Guardar gasto")
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        TextButton(onClick = onClose, enabled = !review.saving) { Text("Cancelar") }
        if (review.mail != null) {
            TextButton(
                onClick = onDiscard,
                enabled = !review.saving,
                colors = ButtonDefaults.textButtonColors(contentColor = HomePalette.Expense)
            ) { Text("Descartar aviso") }
        }
    }
}

@Composable
private fun ReceiptAccountMenu(label: String, selected: AccountEntity?, choices: List<AccountEntity>, enabled: Boolean,
                               onSelect: (AccountEntity) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelLarge)
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, enabled = enabled && choices.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(selected?.name ?: "Elegir cuenta", Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = HomePalette.Paper) {
            choices.forEach { account ->
                DropdownMenuItem(text = { Text(account.name) }, onClick = { onSelect(account); open = false })
            }
        }
    }
}
