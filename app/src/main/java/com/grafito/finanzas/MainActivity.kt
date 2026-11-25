package com.grafito.finanzas

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grafito.finanzas.data.*
import com.grafito.finanzas.model.*
import com.grafito.finanzas.notifications.YapeNotificationListener
import com.grafito.finanzas.notifications.YapeListenerRecovery
import com.grafito.finanzas.ui.screens.*
import com.grafito.finanzas.ui.components.DeskMascotScene
import com.grafito.finanzas.ui.components.FloatingNavigationBar
import com.grafito.finanzas.ui.components.FrancaCue
import com.grafito.finanzas.ui.components.NavigationDestination
import com.grafito.finanzas.ui.components.FrancaSplash
import com.grafito.finanzas.ui.components.FirstMovementFeedback
import com.grafito.finanzas.ui.components.MovementFeedbackHost
import com.grafito.finanzas.ui.components.LocalFrancaSounds
import com.grafito.finanzas.ui.components.ProvideFrancaSounds
import com.grafito.finanzas.ui.components.TransferDetailDialog
import com.grafito.finanzas.ui.components.movementSavedMessage
import com.grafito.finanzas.ui.components.rememberDeskMascotScene
import com.grafito.finanzas.ui.theme.GrafitoTheme
import com.grafito.finanzas.ui.theme.HomePalette
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import androidx.lifecycle.lifecycleScope

private enum class Tab(val label: String, val development: DevelopmentSection? = null) {
    HOME("Inicio"),
    HISTORY("Historial"),
    ACCOUNTS("Cuentas"),
    REPORTS("Reportes", DevelopmentSection.REPORTS),
    SETTINGS("Ajustes")
}

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        YapeNotificationListener.requestConnection(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            UserPreferences(applicationContext).yapeCapture.map { it.enabledSince > 0 }
                .distinctUntilChanged().catch { if (it !is java.io.IOException) throw it }
                .collect { YapeListenerRecovery.schedule(applicationContext, it) }
        }
        setContent {
            ProvideFrancaSounds {
                GrafitoTheme {
                    val vm: FinanceViewModel = viewModel()
                    val deskScene = rememberDeskMascotScene()
                    var splashFinished by rememberSaveable { mutableStateOf(false) }
                    if (splashFinished) MainApp(vm, window, deskScene) else FrancaSplash { splashFinished = true }
                }
            }
        }
    }
}

@Composable
private fun MainApp(vm: FinanceViewModel, window: Window, deskScene: DeskMascotScene?) {
    val data by vm.state.collectAsStateWithLifecycle()
    val openQuickOnLaunch by vm.openQuickOnLaunch.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    var saving by remember { mutableStateOf(false) }
    var currentTab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val tabStates = rememberSaveableStateHolder()
    var showQuick by rememberSaveable { mutableStateOf(false) }
    var createShortcut by rememberSaveable { mutableStateOf(false) }
    var sourceShortcutId by rememberSaveable { mutableStateOf<Long?>(null) }
    var registerAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var transferId by rememberSaveable { mutableStateOf<Long?>(null) }
    var yapeReviewId by rememberSaveable { mutableStateOf<String?>(null) }
    var highlightedEntryId by remember { mutableStateOf<Long?>(null) }
    val editing = data.transactions.find { it.id == editingId }
    val sourceShortcut = data.favorites.find { it.id == sourceShortcutId }
    val transferSelected = data.transactions.find { it.id == transferId }
    val snackbars = remember { SnackbarHostState() }
    val saveNotices = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val playSound = LocalFrancaSounds.current
    var checkedLaunch by rememberSaveable { mutableStateOf(false) }
    val isHome = currentTab == Tab.HOME
    SideEffect { updateSystemBars(window) }
    LaunchedEffect(vm) { vm.errors.collect { snackbars.showSnackbar(it) } }
    val generation by vm.dataGeneration.collectAsStateWithLifecycle()
    LaunchedEffect(generation) {
        snackbars.currentSnackbarData?.dismiss()
        saveNotices.currentSnackbarData?.dismiss()
        highlightedEntryId = null
    }
    LaunchedEffect(highlightedEntryId) {
        if (highlightedEntryId != null) {
            delay(4_000)
            highlightedEntryId = null
        }
    }
    if (isHome && data.isLoaded) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { vm.expireYapeNotices() } }
    }
    val destinations = remember {
        listOf(
            NavigationDestination(Tab.HOME.label, Icons.Outlined.Home),
            NavigationDestination(Tab.HISTORY.label, Icons.AutoMirrored.Outlined.ReceiptLong),
            NavigationDestination(Tab.ACCOUNTS.label, Icons.Outlined.AccountBalanceWallet),
            NavigationDestination(Tab.REPORTS.label, Icons.Outlined.BarChart),
            NavigationDestination(Tab.SETTINGS.label, Icons.Outlined.Settings)
        )
    }

    LaunchedEffect(data.isLoaded, data.accounts.size, openQuickOnLaunch) {
        if (data.isLoaded && data.accounts.isNotEmpty() && openQuickOnLaunch != null && !checkedLaunch) {
            checkedLaunch = true
            if (openQuickOnLaunch == true) showQuick = true
        }
    }

    fun closeRegister() {
        showQuick = false
        editingId = null
        sourceShortcutId = null
        registerAccountId = null
        createShortcut = false
    }

    fun openCreate(asShortcut: Boolean = false, fromShortcut: FavoriteEntity? = null, accountId: Long? = null) {
        playSound(FrancaCue.TAP)
        editingId = null
        sourceShortcutId = fromShortcut?.id
        registerAccountId = accountId
        createShortcut = asShortcut
        showQuick = true
    }
    fun openEdit(item: TransactionEntity) {
        playSound(FrancaCue.TAP)
        if (item.type == EntryType.TRANSFER) transferId = item.id
        else {
            editingId = item.id
            sourceShortcutId = null
            registerAccountId = null
            createShortcut = false
            showQuick = true
        }
    }

    suspend fun feedbackWithUndo(
        entry: TransactionEntity,
        message: String,
        undo: suspend () -> Boolean = { vm.deleteEntry(entry) }
    ) {
        highlightedEntryId = entry.id
        val generation = vm.dataGeneration.value
        val result = if (isHome && vm.claimFirstMovementReaction(entry.id)) {
            saveNotices.showSnackbar(FirstMovementFeedback(message))
        } else {
            saveNotices.showSnackbar(message, actionLabel = "Deshacer", withDismissAction = true,
                duration = SnackbarDuration.Short)
        }
        if (result == SnackbarResult.ActionPerformed && generation == vm.dataGeneration.value) {
            if (undo() && highlightedEntryId == entry.id) highlightedEntryId = null
        }
    }

    fun feedbackForYape(confirmation: YapeConfirmation) {
        scope.launch {
            feedbackWithUndo(confirmation.transaction, movementSavedMessage(confirmation.transaction.type)) {
                vm.undoYapeConfirmation(confirmation)
            }
        }
    }

    val pageColors = when (currentTab) {
        Tab.HOME, Tab.REPORTS -> HomePalette.scheme
        Tab.HISTORY, Tab.ACCOUNTS, Tab.SETTINGS -> HomePalette.panelScheme
    }
    MaterialTheme(colorScheme = HomePalette.scheme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
            Scaffold(
                modifier = (if (isHome) Modifier.widthIn(max = 620.dp) else Modifier).fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                snackbarHost = { SnackbarHost(snackbars) },
                floatingActionButton = {
                    if (isHome && !loadError && data.isLoaded && data.accounts.isNotEmpty()) {
                        HomeRegisterButton(onClick = { openCreate() })
                    }
                },
                bottomBar = {
                    FloatingNavigationBar(destinations, currentTab.ordinal) { currentTab = Tab.entries[it] }
                }
            ) { padding ->
                tabStates.SaveableStateProvider(currentTab.name) {
                    MaterialTheme(colorScheme = pageColors) {
                        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                            val page = Modifier.widthIn(max = 620.dp)
                            val development = currentTab.development
                            when {
                                development != null -> DevelopmentScreen(development, deskScene, page)
                                loadError -> Column(page.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No se pudieron cargar tus datos.")
                                    Button(onClick = vm::retryInitialization) { Text("Reintentar") }
                                }
                                !data.isLoaded || data.accounts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                                else -> when (currentTab) {
                                    Tab.HOME -> {
                                        val pending by vm.pendingYape.collectAsStateWithLifecycle(emptyList())
                                        HomeScreen(
                                            data = data,
                                            pendingYape = pending,
                                            highlightedEntryId = highlightedEntryId,
                                            modifier = page,
                                            onReviewYape = { playSound(FrancaCue.TAP); yapeReviewId = it },
                                            onNewFavorite = { openCreate(asShortcut = true) },
                                            onSeeAll = { currentTab = Tab.HISTORY },
                                            onSelect = { openEdit(it) },
                                            onFavorite = { openCreate(fromShortcut = it) }
                                        )
                                    }
                                    Tab.HISTORY -> HistoryScreen(data, page, highlightedEntryId = highlightedEntryId,
                                        onSelect = { openEdit(it) }, onQuickAdd = { openCreate() })
                                    Tab.ACCOUNTS -> AccountsScreen(
                                        data = data,
                                        modifier = page,
                                        onQuickAdd = { accountId -> openCreate(accountId = accountId) },
                                        onAddAccount = { name, opening ->
                                            scope.launch {
                                                if (!vm.addAccount(name, opening)) snackbars.showSnackbar("Revisa el nombre y el saldo inicial")
                                            }
                                        },
                                        onTransfer = { cents, from, to, note ->
                                            scope.launch {
                                                val entry = vm.transfer(cents, from, to, note)
                                                if (entry == null) snackbars.showSnackbar("No se pudo registrar la transferencia")
                                                else {
                                                    playSound(FrancaCue.SAVED)
                                                    feedbackWithUndo(entry, "Transferencia registrada")
                                                }
                                            }
                                        },
                                        onDeleteAccount = { account ->
                                            scope.launch {
                                                if (!vm.deleteAccount(account)) snackbars.showSnackbar("No se puede eliminar una cuenta con movimientos ni la única cuenta")
                                            }
                                        }
                                    )
                                    Tab.REPORTS -> ReportsScreen(data, page, onQuickAdd = { openCreate() })
                                    Tab.SETTINGS -> SettingsScreen(
                                        data = data,
                                        openQuickOnLaunch = openQuickOnLaunch ?: true,
                                        modifier = page,
                                        onOpenQuickChanged = vm::updateOpenQuick,
                                        onRemoveFavorite = { item -> scope.launch { vm.removeFavorite(item) } },
                                        onDefaultAccountChanged = vm::updateDefaultAccount,
                                        onFavoriteAccountChanged = vm::updateFavoriteAccount,
                                        backupViewModel = vm,
                                        onYapeSaved = ::feedbackForYape
                                    )
                                }
                            }
                        }
                    }
                }
            }
            MovementFeedbackHost(
                state = saveNotices,
                showMascotInHome = isHome,
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    yapeReviewId?.takeIf { data.isLoaded || loadError }?.let { id ->
        ReviewYape(id, data.accounts, data.categories, data.defaultAccountId, data.transactions,
            loadError = loadError, onSaved = { confirmation ->
                yapeReviewId = null
                feedbackForYape(confirmation)
            }
        ) { message ->
            yapeReviewId = null
            if (message != null) scope.launch { snackbars.showSnackbar(message) }
        }
    }

    transferSelected?.let { entry ->
        TransferDetailDialog(
            amountCents = entry.amountCents,
            from = data.accounts.find { it.id == entry.accountId }?.name ?: "Cuenta de origen",
            to = data.accounts.find { it.id == entry.toAccountId }?.name ?: "Cuenta de destino",
            note = entry.note,
            onClose = { transferId = null },
            onDelete = {
                transferId = null
                scope.launch {
                    if (!vm.deleteEntry(entry)) return@launch
                    val generation = vm.dataGeneration.value
                    val result = snackbars.showSnackbar("Transferencia eliminada", actionLabel = "Deshacer")
                    if (result == SnackbarResult.ActionPerformed && generation == vm.dataGeneration.value) vm.restoreEntry(entry)
                }
            }
        )
    }

    if (showQuick && data.accounts.isNotEmpty() && (sourceShortcutId == null || sourceShortcut != null)) {
        QuickAddSheet(
            data = data,
            editing = editing,
            sourceShortcut = sourceShortcut,
            initialAccountId = registerAccountId,
            saving = saving,
            favoriteOnly = createShortcut,
            onDismiss = { if (!saving) closeRegister() },
            onSave = { old, type, cents, accountId, categoryId, note, favorite ->
                if (saving) return@QuickAddSheet
                saving = true
                scope.launch {
                    val favoriteTitle = note.ifBlank { data.categories.find { it.id == categoryId }?.name ?: "Gasto frecuente" }
                    if (createShortcut) {
                        val created = try { vm.addFavorite(favoriteTitle, cents, accountId, categoryId) }
                            finally { saving = false }
                        if (created) {
                            closeRegister()
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            playSound(FrancaCue.SAVED)
                            saveNotices.showSnackbar("Atajo guardado", withDismissAction = true)
                        } else {
                            snackbars.showSnackbar("No se pudo guardar el atajo")
                        }
                        return@launch
                    }
                    val title = favoriteTitle.takeIf { favorite && old == null && type == EntryType.EXPENSE }
                    val entry = try { vm.addEntry(type, cents, accountId, categoryId, note, old, title, sourceShortcut?.id) }
                        finally { saving = false }
                    if (entry == null) {
                        snackbars.showSnackbar("No se pudo guardar el movimiento")
                    } else {
                        closeRegister()
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        playSound(FrancaCue.SAVED)
                        if (old == null) feedbackWithUndo(entry, movementSavedMessage(entry.type))
                        else {
                            highlightedEntryId = entry.id
                            saveNotices.showSnackbar("Movimiento actualizado", withDismissAction = true)
                        }
                    }
                }
            },
            onDelete = { item ->
                closeRegister()
                scope.launch {
                    if (!vm.deleteEntry(item)) return@launch
                    val generation = vm.dataGeneration.value
                    val result = snackbars.showSnackbar("Movimiento eliminado", actionLabel = "Deshacer")
                    if (result == SnackbarResult.ActionPerformed && generation == vm.dataGeneration.value) vm.restoreEntry(item)
                }
            }
        )
    }
}

@Suppress("DEPRECATION")
private fun updateSystemBars(window: Window) {
    val background = HomePalette.Background.toArgb()
    window.statusBarColor = background
    window.navigationBarColor = background
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
}
