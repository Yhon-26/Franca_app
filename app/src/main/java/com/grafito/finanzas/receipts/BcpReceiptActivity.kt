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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
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
import com.grafito.finanzas.ui.components.FrancaCue
import com.grafito.finanzas.ui.components.LocalFrancaSounds
import com.grafito.finanzas.ui.components.ProvideFrancaSounds
import com.grafito.finanzas.ui.components.ScreenTitle
import com.grafito.finanzas.ui.screens.PaymentAccountField
import com.grafito.finanzas.ui.screens.RegisterAccountSheet
import com.grafito.finanzas.ui.screens.RegisterCategoryField
import com.grafito.finanzas.ui.screens.RegisterPaymentAccountField
import com.grafito.finanzas.ui.screens.RegisterCategorySheet
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
                            val title = if (gmailReview) "Pago BCP detectado" else "Constancia BCP"
                            when (val review = state) {
                                is ReceiptReview.Ready -> ReceiptForm(
                                    title, review, accounts, categories, ::finish, model::discard, model::save
                                )
                                else -> Column(
                                    Modifier.widthIn(max = 620.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    ScreenTitle(title)
                                    when (review) {
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
                                        is ReceiptReview.Ready -> Unit
                                    }
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
private fun ReceiptForm(title: String, review: ReceiptReview.Ready, accounts: List<AccountEntity>, categories: List<CategoryEntity>,
                        onClose: () -> Unit, onDiscard: () -> Unit, onSave: (Long, Long, Long?, String, Long?) -> Unit) {
    var amount by rememberSaveable(review.receipt.fingerprint) {
        mutableStateOf(review.receipt.suggestedCents?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}" }.orEmpty())
    }
    var note by rememberSaveable { mutableStateOf("") }
    var fromId by rememberSaveable { mutableStateOf<Long?>(null) }
    var toId by rememberSaveable { mutableStateOf<Long?>(null) }
    var transfer by rememberSaveable { mutableStateOf(false) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCategories by rememberSaveable(review.receipt.fingerprint) { mutableStateOf(false) }
    var accountPicker by rememberSaveable { mutableStateOf<PaymentAccountField?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var accountPrefilled by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val expenseCategories = remember(categories) { categories.filter { it.type == EntryType.EXPENSE } }
    val selectedCategory = expenseCategories.find { it.id == categoryId }
    LaunchedEffect(accounts) {
        if (!accountPrefilled && accounts.isNotEmpty()) {
            fromId = accounts.singleOrNull { Regex("\\bbcp\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }?.id
            accountPrefilled = true
        }
    }
    val cents = Money.parse(amount)
    val from = accounts.find { it.id == fromId }
    val to = accounts.find { it.id == toId && it.id != fromId }
    val missing = when {
        cents == null -> "Escribe un monto válido"
        accounts.isEmpty() -> "Añade una cuenta en Ajustes"
        from == null -> "Elige la cuenta de origen"
        transfer && to == null -> "Elige la cuenta de destino"
        else -> null
    }
    fun finishEditing() {
        focus.clearFocus()
        keyboard?.hide()
    }

    Column(Modifier.widthIn(max = 620.dp).fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenTitle(title)
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
                        isError = amount.isNotBlank() && cents == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { finishEditing() }))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !transfer, onClick = { transfer = false }, enabled = !review.saving, label = { Text("Gasto") })
                        FilterChip(selected = transfer, onClick = { transfer = true }, enabled = accounts.size >= 2 && !review.saving,
                            label = { Text("Entre mis cuentas") })
                    }
                    RegisterPaymentAccountField(PaymentAccountField.ORIGIN, from, !review.saving && accounts.isNotEmpty()) {
                        finishEditing()
                        accountPicker = PaymentAccountField.ORIGIN
                    }
                    if (transfer) {
                        RegisterPaymentAccountField(PaymentAccountField.DESTINATION, to,
                            !review.saving && accounts.any { it.id != fromId }) {
                            finishEditing()
                            accountPicker = PaymentAccountField.DESTINATION
                        }
                    }
                    if (review.mail != null && !transfer) {
                        RegisterCategoryField(selectedCategory, enabled = !review.saving) {
                            finishEditing()
                            showCategories = true
                        }
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it.take(160) }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Concepto (opcional)") }, singleLine = true, enabled = !review.saving,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { finishEditing() }))
                }
            }
        }
        HorizontalDivider(color = HomePalette.Line)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            (review.error ?: missing)?.let {
                Text(it, Modifier.padding(bottom = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    if (cents != null && from != null && (!transfer || to != null)) {
                        finishEditing()
                        onSave(cents, from.id, to?.id.takeIf { transfer }, note, selectedCategory?.id.takeUnless { transfer })
                    }
                },
                enabled = !review.saving && missing == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (review.saving) "Guardando…" else if (transfer) "Guardar transferencia" else "Guardar gasto")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                TextButton(onClick = onClose, enabled = !review.saving) { Text("Cancelar") }
                if (review.mail != null) {
                    TextButton(
                        onClick = { finishEditing(); confirmDiscard = true },
                        enabled = !review.saving,
                        colors = ButtonDefaults.textButtonColors(contentColor = HomePalette.Expense)
                    ) { Text("Descartar aviso") }
                }
            }
        }
    }

    accountPicker?.let { field ->
        val origin = field == PaymentAccountField.ORIGIN
        val excluded = if (origin && transfer) to?.id else if (!origin) from?.id else null
        RegisterAccountSheet(accounts.filter { it.id != excluded }, if (origin) from?.id else to?.id,
            onDismiss = { accountPicker = null }, title = field.pickerTitle) { id ->
            if (origin) fromId = id else toId = id
            accountPicker = null
        }
    }
    if (confirmDiscard && review.mail != null) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("¿Descartar aviso?") },
            text = { Text("Se quitará de los avisos pendientes sin guardar un movimiento.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDiscard() }, enabled = !review.saving) {
                    Text("Descartar")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancelar") } }
        )
    }
    if (showCategories && review.mail != null && !transfer) {
        RegisterCategorySheet(
            expenseCategories, selectedCategory?.id, registrationContext = "BCP · Gasto",
            onSelect = { categoryId = it; showCategories = false },
            onDismiss = { showCategories = false }
        )
    }
}
