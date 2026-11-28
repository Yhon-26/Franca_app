package com.grafito.finanzas.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.grafito.finanzas.data.AccountEntity
import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.TransactionEntity
import com.grafito.finanzas.data.UserPreferences
import com.grafito.finanzas.data.YapeCaptureStatus
import com.grafito.finanzas.data.YapeConfirmation
import com.grafito.finanzas.data.YapeSuggestion
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.model.ActiveYapeReview
import com.grafito.finanzas.model.PendingYapeState
import com.grafito.finanzas.model.YapePaymentsViewModel
import com.grafito.finanzas.model.YapeReviewState
import com.grafito.finanzas.model.YapeSentStatus
import com.grafito.finanzas.model.suggestCategory
import com.grafito.finanzas.notifications.FrancaNotices
import com.grafito.finanzas.notifications.YapeNotificationListener
import com.grafito.finanzas.notifications.YapeSentAccessibilityService
import com.grafito.finanzas.ui.components.francaPageEnter
import com.grafito.finanzas.ui.components.FrancaCue
import com.grafito.finanzas.ui.components.LocalFrancaSounds
import com.grafito.finanzas.ui.components.movementSavedMessage
import com.grafito.finanzas.ui.theme.GrafitoTheme
import com.grafito.finanzas.ui.theme.HomePalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.DateFormat
import java.util.Date

@Composable
internal fun YapePanel(accounts: List<AccountEntity>, categories: List<CategoryEntity>,
                       defaultAccountId: Long?, transactions: List<TransactionEntity>,
                       payments: YapePaymentsViewModel, onSaved: (YapeConfirmation) -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext
    val preferences = remember(application) { UserPreferences(application) }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var reviewId by rememberSaveable { mutableStateOf<String?>(null) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val capture by remember(preferences) { preferences.yapeCapture.catch {
        failed = true
        message = "No se pudo leer la configuración de Yape."
        emit(YapeCaptureStatus())
    } }.collectAsStateWithLifecycle(YapeCaptureStatus())
    val pendingState by payments.pending.collectAsStateWithLifecycle()
    val pending = (pendingState as? PendingYapeState.Available)?.notices.orEmpty()
    val connected by YapeNotificationListener.connected.collectAsStateWithLifecycle()
    val sentConnection = rememberYapeSentConnection()
    val sentConnected = sentConnection == YapeSentConnection.CONNECTED
    val sentStatus by YapeSentAccessibilityService.diagnostics.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(false) }
    val notifications = remember(application) { NotificationManagerCompat.from(application) }
    var canPost by remember { mutableStateOf(notifications.areNotificationsEnabled()) }
    var incomeChannelEnabled by remember { mutableStateOf(FrancaNotices.yapeIncome.enabled(application)) }
    var sentChannelEnabled by remember { mutableStateOf(FrancaNotices.yapeSent.enabled(application)) }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canPost = notifications.areNotificationsEnabled()
    }
    val enabled = capture.enabledSince > 0
    val yapeReady = enabled && granted && connected && sentConnected &&
        canPost && incomeChannelEnabled && sentChannelEnabled
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
        .isAtLeast(Lifecycle.State.RESUMED)
    var showYapeReady by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = application.packageName in NotificationManagerCompat.getEnabledListenerPackages(application)
        canPost = notifications.areNotificationsEnabled()
        incomeChannelEnabled = FrancaNotices.yapeIncome.enabled(application)
        sentChannelEnabled = FrancaNotices.yapeSent.enabled(application)
    }

    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        failed = false
        message = null
        scope.launch {
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                failed = true
                message = "No se pudo completar la operación. Vuelve a intentarlo."
            } finally { busy = false }
        }
    }

    LaunchedEffect(yapeReady, resumed) {
        showYapeReady = false
        if (!yapeReady || !resumed) return@LaunchedEffect
        val claimed = try {
            preferences.claimYapeReadyReaction()
        } catch (_: IOException) {
            false
        }
        if (claimed) {
            showYapeReady = true
            delay(2_600)
            showYapeReady = false
        }
    }

    fun openSettings(intent: Intent, fallback: String) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            failed = true
            message = fallback
        } catch (_: SecurityException) {
            failed = true
            message = fallback
        }
    }

    fun openNotificationAccess() = openSettings(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        "Busca «Acceso a notificaciones» en Ajustes de Android."
    )

    SettingsRows {
        SettingsRow(
            icon = Icons.Outlined.SouthWest,
            title = "Yape recibido",
            summary = when {
                !enabled -> "Desactivado"
                !granted -> "Permiso pendiente"
                !connected -> "Lector desconectado"
                else -> "Activo"
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Switch(checked = enabled, enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "Detectar ingresos de Yape" },
                onCheckedChange = { next -> perform { preferences.setYapeCapture(next) } })
        }
        ReaderLastRead(enabled && granted && connected, capture.lastIncomeReadAt,
            "El lector espera el próximo aviso de ingreso de Yape.",
            Modifier.padding(start = 46.dp, end = 14.dp, bottom = 8.dp))
        if (enabled && (!granted || !connected)) {
            Column(Modifier.padding(start = 46.dp, end = 14.dp, bottom = 6.dp)) {
                if (granted) {
                    TextButton(onClick = { YapeNotificationListener.requestConnection(application) }) {
                        Text("Reconectar lector")
                    }
                } else {
                    Text("Android da acceso a tus avisos. Franca solo lee los de Yape.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = ::openNotificationAccess) { Text("Conceder acceso") }
                }
            }
        }
        SettingsDivider()
        SettingsLink(Icons.Outlined.NorthEast, "Yape enviado", when (sentConnection) {
            YapeSentConnection.CONNECTED -> "Activo"
            YapeSentConnection.DISCONNECTED -> "Lector desconectado"
            YapeSentConnection.DISABLED -> "Activar en Accesibilidad"
            YapeSentConnection.UNAVAILABLE -> "Revisar Accesibilidad"
        }) {
            openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                "Busca «Accesibilidad» en Ajustes de Android.")
        }
        ReaderLastRead(sentConnected, capture.lastSentReadAt, "Puedes abrir un comprobante «¡Yapeaste!» en Yape.",
            Modifier.padding(start = 46.dp, end = 14.dp, bottom = 8.dp))
        if (sentConnection == YapeSentConnection.DISCONNECTED) {
            Text(YAPE_RECONNECT_GUIDE, Modifier.padding(start = 46.dp, end = 14.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    AnimatedVisibility(visible = yapeReady && resumed && showYapeReady) {
        Surface(
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            color = HomePalette.IconBackground,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, HomePalette.Line)
        ) {
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null,
                    tint = HomePalette.Income, modifier = Modifier.size(24.dp))
                Text("Yape está listo", Modifier.weight(1f), color = HomePalette.Navy,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }

    if ((enabled || sentConnected) &&
        (!canPost || (enabled && !incomeChannelEnabled) || (sentConnected && !sentChannelEnabled))) {
        SettingsCard {
            Text("Revisa los avisos de Franca", style = MaterialTheme.typography.titleSmall)
            val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                TextButton(onClick = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Permitir avisos")
                }
            }
            TextButton(onClick = {
                openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName),
                    "Busca «Notificaciones de Franca» en Ajustes de Android.")
            }) { Text("Ajustes de avisos") }
        }
    }
    message?.let {
        Text(it, color = if (failed) MaterialTheme.colorScheme.error else HomePalette.Income,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (pendingState == PendingYapeState.Failed) {
        YapePendingLoadError(payments::refreshPending)
    }
    if (pending.isNotEmpty()) {
        SettingsCard {
            Text("Yapeos por revisar", style = MaterialTheme.typography.titleSmall)
            pending.forEach { candidate ->
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (candidate.outgoing) "Enviado" else "Recibido", style = MaterialTheme.typography.labelMedium)
                        Text(Money.format(candidate.amountCents), color = if (candidate.outgoing) MaterialTheme.colorScheme.onSurface else HomePalette.Income)
                        Text(DateFormat.getDateTimeInstance().format(Date(candidate.receivedAt)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = !busy, onClick = { reviewId = candidate.id; message = null }) { Text("Revisar") }
                }
            }
        }
    }

    SettingsRows {
        SettingsRow(
            icon = Icons.Outlined.HelpOutline,
            title = "Ayuda y diagnóstico",
            modifier = Modifier.clickable(role = Role.Button) { showHelp = !showHelp }
                .semantics { stateDescription = if (showHelp) "Expandido" else "Contraído" }
        ) {
            Icon(if (showHelp) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        if (showHelp) {
            SettingsDivider()
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Franca lee avisos y comprobantes de Yape en este teléfono y conserva el monto y la hora para tu revisión.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (enabled) {
                    capture.lastTime?.let { time ->
                        val description = when (capture.lastStatus) {
                            "detected" -> "Ingreso sugerido"
                            "duplicate" -> "Aviso ya procesado"
                            "error" -> "No se pudo procesar el aviso"
                            else -> "Aviso sin monto reconocible"
                        }
                        Text("Último aviso · ${DateFormat.getDateTimeInstance().format(Date(time))}\n$description",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (sentConnected) {
                    Text(when (sentStatus) {
                        null, YapeSentStatus.YAPE_VISIBLE -> "Abre un comprobante «¡Yapeaste!» para probar el lector."
                        YapeSentStatus.WINDOW_UNAVAILABLE -> "La ventana de Yape no estaba disponible."
                        YapeSentStatus.UNAVAILABLE -> "Android no entregó texto legible de Yape."
                        YapeSentStatus.INCOMPLETE -> "No se pudo leer el comprobante completo."
                        YapeSentStatus.DETECTED -> "Último envío detectado."
                        YapeSentStatus.DUPLICATE -> "El último envío ya estaba detectado."
                        YapeSentStatus.ERROR -> "No se pudo leer la última pantalla de Yape."
                    }, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = ::openNotificationAccess) { Text("Acceso a notificaciones") }
                Text("Si un lector se desconecta al cerrar Franca, revisa sus restricciones de batería.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
                    Text("En HyperOS, también puedes revisar «Inicio automático en segundo plano».",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", application.packageName, null)),
                        "Abre Ajustes de Android → Aplicaciones → Franca → Batería.")
                }) { Text("Ajustes de batería") }
            }
        }
    }

    reviewId?.let { id ->
        ReviewYape(id, payments, accounts, categories, defaultAccountId, transactions, onSaved = { confirmation ->
            reviewId = null
            onSaved(confirmation)
        }) { result ->
            reviewId = null
            failed = false
            message = result
        }
    }
}

@Composable
internal fun ReviewYape(
    id: String, payments: YapePaymentsViewModel, accounts: List<AccountEntity>, categories: List<CategoryEntity>,
    defaultAccountId: Long?, transactions: List<TransactionEntity>, loadError: Boolean = false,
    onSaved: ((YapeConfirmation) -> Unit)? = null, onFinish: (String?) -> Unit
) = GrafitoTheme(colorScheme = HomePalette.panelScheme) {
    val haptic = LocalHapticFeedback.current
    val playSound = LocalFrancaSounds.current
    val review by payments.review.collectAsStateWithLifecycle()
    val current = review?.takeIf { it.id == id } ?: ActiveYapeReview(id, YapeReviewState.Loading)
    LaunchedEffect(id, loadError) {
        if (!loadError) payments.open(id)
    }

    fun close() {
        if (payments.close(id)) onFinish(null)
    }

    when (val state = current.state) {
        is YapeReviewState.Saved -> LaunchedEffect(current) {
            if (payments.consumeResult(current)) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                playSound(FrancaCue.SAVED)
                if (onSaved != null) onSaved(state.confirmation)
                else onFinish(movementSavedMessage(state.confirmation.transaction.type))
            }
        }
        YapeReviewState.Discarded -> LaunchedEffect(current) {
            if (payments.consumeResult(current)) onFinish("Aviso descartado.")
        }
        else -> if (!loadError && state is YapeReviewState.Ready) {
            val suggestion = state.notice
            val entryType = if (suggestion.outgoing) EntryType.EXPENSE else EntryType.INCOME
            YapeForm(suggestion, accounts, categories.filter { it.type == entryType }, defaultAccountId, transactions,
                state.busy, state.error, onClose = ::close, onDiscard = { payments.discard(id) },
                onConfirm = { account, amount, category, other -> payments.confirm(id, account, amount, category, other) })
        } else {
            AlertDialog(
                onDismissRequest = ::close,
                title = { Text("Aviso de Yape") },
                text = {
                    when {
                        loadError -> Text("No se pudieron cargar tus datos.")
                        state == YapeReviewState.Loading -> CircularProgressIndicator()
                        state == YapeReviewState.LoadFailed -> Text("No se pudo abrir el aviso. Vuelve a intentarlo.")
                        else -> Text("Este aviso ya no está pendiente.")
                    }
                },
                confirmButton = {
                    if (!loadError && state == YapeReviewState.LoadFailed) {
                        TextButton(onClick = { payments.retry(id) }) { Text("Reintentar") }
                    } else {
                        TextButton(onClick = ::close) { Text("Cerrar") }
                    }
                },
                dismissButton = if (!loadError && state == YapeReviewState.LoadFailed) {
                    { TextButton(onClick = ::close) { Text("Cerrar") } }
                } else null
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun YapeForm(candidate: YapeSuggestion, accounts: List<AccountEntity>, categories: List<CategoryEntity>,
                           defaultAccountId: Long?, transactions: List<TransactionEntity>,
                           busy: Boolean, error: String?, onClose: () -> Unit, onDiscard: () -> Unit,
                           onConfirm: (Long, Long, Long?, Long?) -> Unit) {
    var amount by rememberSaveable(candidate.id) {
        mutableStateOf("${candidate.amountCents / 100}.${(candidate.amountCents % 100).toString().padStart(2, '0')}")
    }
    var accountId by rememberSaveable(candidate.id) {
        mutableStateOf(accounts.firstOrNull { it.name.trim().equals("Yape", ignoreCase = true) }?.id
            ?: accounts.find { it.id == defaultAccountId }?.id
            ?: accounts.singleOrNull()?.id)
    }
    var categoryId by rememberSaveable(candidate.id) { mutableStateOf<Long?>(null) }
    var transfer by rememberSaveable(candidate.id) { mutableStateOf(false) }
    var otherAccountId by rememberSaveable(candidate.id) { mutableStateOf<Long?>(null) }
    var accountPicker by rememberSaveable(candidate.id) { mutableStateOf<PaymentAccountField?>(null) }
    var showCategories by rememberSaveable(candidate.id) { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable(candidate.id) { mutableStateOf(false) }
    val cents = Money.parse(amount)
    val account = accounts.find { it.id == accountId }
    val otherAccount = accounts.find { it.id == otherAccountId && it.id != accountId }
    val mainField = if (candidate.outgoing) PaymentAccountField.ORIGIN else PaymentAccountField.DESTINATION
    val otherField = if (candidate.outgoing) PaymentAccountField.DESTINATION else PaymentAccountField.ORIGIN
    val amountColor = if (candidate.outgoing) HomePalette.Ink else HomePalette.Income
    val entryLabel = if (candidate.outgoing) "Gasto" else "Ingreso"
    val entryType = if (candidate.outgoing) EntryType.EXPENSE else EntryType.INCOME
    val suggestion = remember(transactions, cents, entryType, categoryId, categories) {
        if (categoryId == null) suggestCategory(transactions, categories, entryType, cents) else null
    }
    val selectedCategory = categories.find { it.id == categoryId }
    val missing = when {
        cents == null -> "Escribe un monto válido"
        account == null -> if (accounts.isEmpty()) "Añade una cuenta en Ajustes" else "Elige una cuenta"
        transfer && otherAccount == null -> "Elige la otra cuenta"
        else -> null
    }
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy }),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = HomePalette.Navy.copy(alpha = 0.28f),
        dragHandle = null
    ) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (candidate.outgoing) "Revisar Yape enviado" else "Revisar ingreso de Yape",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onClose, enabled = !busy) { Icon(Icons.Outlined.Close, "Cerrar revisión") }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Comprueba el monto y elige cómo registrarlo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = amount, onValueChange = { amount = it.take(12) },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    textStyle = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = amountColor,
                        unfocusedTextColor = amountColor,
                        errorTextColor = amountColor),
                    prefix = { Text("S/ ", color = amountColor) },
                    label = { Text("Monto en soles") }, singleLine = true, enabled = !busy,
                    isError = cents == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done))
                if (accounts.size > 1 || transfer) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !transfer, onClick = { transfer = false }, enabled = !busy,
                            label = { Text(entryLabel) })
                        FilterChip(selected = transfer, onClick = { transfer = true }, enabled = !busy && accounts.size > 1,
                            label = { Text("Entre mis cuentas") })
                    }
                }
                fun openAccountPicker(field: PaymentAccountField) {
                    focus.clearFocus()
                    keyboard?.hide()
                    accountPicker = field
                }
                RegisterPaymentAccountField(mainField, account, !busy && accounts.isNotEmpty()) {
                    openAccountPicker(mainField)
                }
                Column(Modifier.fillMaxWidth().francaPageEnter(transfer, forward = transfer)) {
                    if (transfer) {
                        RegisterPaymentAccountField(otherField, otherAccount, !busy && accounts.any { it.id != accountId }) {
                            openAccountPicker(otherField)
                        }
                    } else {
                        RegisterCategoryField(selectedCategory, enabled = !busy) {
                            focus.clearFocus()
                            keyboard?.hide()
                            showCategories = true
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Button(enabled = !busy && missing == null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        if (account != null && cents != null && (!transfer || otherAccount != null)) {
                            focus.clearFocus()
                            keyboard?.hide()
                            onConfirm(account.id, cents, selectedCategory?.id.takeUnless { transfer }, otherAccount?.id.takeIf { transfer })
                        }
                    }) {
                    Text(if (busy) "Guardando…" else if (transfer) "Guardar transferencia"
                        else if (candidate.outgoing) "Guardar gasto" else "Guardar ingreso")
                }
                (missing ?: error)?.let {
                    Text(it, Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); confirmDiscard = true }, enabled = !busy) {
                    Text("Descartar aviso")
                }
            }
        }
    }
    accountPicker?.let { field ->
        val choosingMain = field == mainField
        val excluded = if (choosingMain && transfer) otherAccount?.id else if (!choosingMain) account?.id else null
        RegisterAccountSheet(
            accounts.filter { it.id != excluded },
            if (choosingMain) account?.id else otherAccount?.id,
            onDismiss = { accountPicker = null },
            title = field.pickerTitle
        ) { id ->
            if (choosingMain) accountId = id else otherAccountId = id
            accountPicker = null
        }
    }
    if (showCategories && !transfer) {
        RegisterCategorySheet(
            categories, selectedCategory?.id, registrationContext = "Yape · $entryLabel",
            onSelect = { categoryId = it; showCategories = false },
            onDismiss = { showCategories = false },
            suggestion = suggestion
        )
    }
    if (confirmDiscard) {
        AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("¿Descartar aviso?") },
            text = { Text("Se quitará de los avisos pendientes sin guardar un movimiento.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }, enabled = !busy) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancelar") } })
    }
}
