package com.grafito.finanzas

import android.graphics.Bitmap
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.grafito.finanzas.data.*
import com.grafito.finanzas.ui.screens.YapeForm
import com.grafito.finanzas.ui.theme.HomePalette
import com.grafito.finanzas.ui.theme.GrafitoTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Form-only checks: no writes to the user's database or inbox. */
class YapeReviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val cash = AccountEntity(1, "Efectivo")
    private val bank = AccountEntity(2, "BCP")
    private val expenseCategories = listOf(
        "Alimentación", "Transporte", "Compras", "Servicios", "Salud",
        "Entretenimiento", "Educación", "Mascotas", "Supermercado"
    ).mapIndexed { index, name -> CategoryEntity(index + 1L, name, "", EntryType.EXPENSE) }
    private var savedAccount: Long? = null
    private var savedCategory: Long? = null
    private var discarded = false

    private fun focusedWindow() = WindowInspector.getGlobalWindowViews().last { it.hasWindowFocus() }

    private fun capture(name: String) {
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val output = File(compose.activity.getExternalFilesDir(null), name)
        output.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    private fun captureReviewSheet(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10_000) {
            val image = automation.takeScreenshot() ?: return@waitUntil false
            try {
                // The dialog window can exist before Android presents the sheet's first frame.
                val buttonVisible = (image.height * 8 / 10 until image.height step 4).any { y ->
                    image.getPixel(image.width / 4, y) == HomePalette.Blue.toArgb()
                }
                if (buttonVisible) File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                buttonVisible
            } finally {
                image.recycle()
            }
        }
    }

    private fun show(
        accounts: List<AccountEntity>, defaultId: Long? = null,
        categories: List<CategoryEntity> = emptyList(), history: List<TransactionEntity> = emptyList(),
        outgoing: Boolean = true, error: String? = null
    ) {
        compose.setContent {
            GrafitoTheme(colorScheme = HomePalette.panelScheme) {
                YapeForm(YapeSuggestion("ui-review", 1250, 1, outgoing = outgoing), accounts, categories,
                    defaultId, history, busy = false, error = error, onClose = {},
                    onDiscard = { discarded = true },
                    onConfirm = { account, _, category, _ -> savedAccount = account; savedCategory = category })
            }
        }
    }

    @Test fun yapeNameTakesPriorityOverDefaultIgnoringCase() {
        show(listOf(cash, bank, AccountEntity(3, " yApE ")), defaultId = 2)
        compose.onNodeWithText("yApE", substring = true).assertIsSelected()
        compose.onNodeWithText("Guardar gasto").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(3L, savedAccount) }
    }

    @Test fun defaultAccountAllowsSavingWithoutYapeOrCategory() {
        show(listOf(cash, bank), defaultId = 2)
        compose.onNodeWithText("BCP").assertIsSelected()
        compose.onNodeWithText("Guardar gasto").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2L, savedAccount); assertNull(savedCategory) }
    }

    @Test fun singleAccountIsUsedWhenDefaultIsStale() {
        show(listOf(cash), defaultId = 999, outgoing = false)
        compose.onNodeWithText("Efectivo").assertIsSelected()
        captureReviewSheet("yape-recibido.png")
        compose.onNodeWithText("Guardar ingreso").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1L, savedAccount) }
    }

    @Test fun missingAccountAndInvalidAmountExplainDisabledSave() {
        show(listOf(cash, bank), error = "No se pudo completar la operación. Vuelve a intentarlo.")
        compose.onNodeWithText("Guardar gasto").assertIsNotEnabled()
        compose.onNodeWithText("Elige una cuenta").assertIsDisplayed()
        compose.onNodeWithText("BCP").performScrollTo().performClick()
        compose.onNodeWithText("Guardar gasto").assertIsEnabled()
        compose.onNodeWithText("Monto en soles").performTextReplacement("0")
        compose.onNodeWithText("Guardar gasto").assertIsNotEnabled()
        compose.onNodeWithText("Escribe un monto válido").assertIsDisplayed()
    }

    @Test fun emptyAccountsExplainHowToContinue() {
        show(emptyList())
        compose.onNodeWithText("Guardar gasto").assertIsNotEnabled()
        compose.onNodeWithText("Añade una cuenta en Ajustes").assertIsDisplayed()
    }

    @Test fun discardRequiresConfirmationAndCanBeCancelled() {
        show(listOf(cash))
        compose.onNodeWithText("Descartar aviso").performClick()
        compose.runOnIdle { assertFalse(discarded) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertFalse(discarded) }
        compose.onNodeWithText("Guardar gasto").assertIsEnabled()
        compose.onNodeWithText("Descartar aviso").performClick()
        compose.onNodeWithText("Descartar", substring = false).performClick()
        compose.runOnIdle { assertTrue(discarded) }
    }

    @Test fun categoriesUseTheRegistrationPickerAndShowHistorySuggestions() {
        val history = listOf(TransactionEntity(1, EntryType.EXPENSE, 1250, 1, categoryId = 9))
        show(listOf(cash), categories = expenseCategories, history = history)
        compose.onNodeWithText("Transporte").assertDoesNotExist()
        compose.onNodeWithText("Categoría").performScrollTo().performClick()
        compose.onNodeWithText("Elegir categoría").assertIsDisplayed()
        compose.onNodeWithText("Yape · Gasto").assertIsDisplayed()
        compose.onNodeWithText("Sugerida · Supermercado").assertIsDisplayed()
        capture("yape-categorias.png")
        compose.onNodeWithContentDescription("Buscar categoría").performTextInput("educacion")
        compose.onNodeWithText("Educación").performClick()
        compose.onNodeWithText("Elegir categoría").assertDoesNotExist()
        compose.onNode(hasText("Categoría") and hasText("Educación")).assertIsDisplayed()
        compose.onNodeWithText("Guardar gasto").performClick()
        compose.runOnIdle { assertEquals(7L, savedCategory) }
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun footerStaysAboveVisibleKeyboardWhenFormScrolls() {
        show(listOf(cash, bank), defaultId = 2, categories = expenseCategories)
        compose.waitForIdle()
        captureReviewSheet("yape-formulario.png")
        compose.onNodeWithText("Monto en soles").performClick()
        compose.waitUntil(10_000) {
            var visible = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                visible = ViewCompat.getRootWindowInsets(focusedWindow())
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            visible
        }
        compose.onNodeWithText("Guardar gasto").assertIsDisplayed()
        compose.onNodeWithText("Descartar aviso").assertIsDisplayed()
        val before = compose.onNodeWithText("Guardar gasto").fetchSemanticsNode().boundsInWindow
        compose.onNodeWithText("Categoría").performScrollTo()
        val after = compose.onNodeWithText("Guardar gasto").fetchSemanticsNode().boundsInWindow
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.bottom, after.bottom, 1f)
        var keyboardTop = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = focusedWindow()
            val ime = ViewCompat.getRootWindowInsets(view)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
            keyboardTop = view.height - ime
        }
        assertTrue("The fixed save button overlaps the keyboard", after.bottom <= keyboardTop)
        capture("yape-footer-teclado.png")
    }
}
