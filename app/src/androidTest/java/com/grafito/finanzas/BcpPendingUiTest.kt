package com.grafito.finanzas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.lifecycle.viewModelScope
import com.grafito.finanzas.data.BcpMailSuggestion
import com.grafito.finanzas.model.BcpPendingViewModel
import com.grafito.finanzas.ui.screens.BcpPendingPanel
import com.grafito.finanzas.ui.theme.GrafitoTheme
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test

class BcpPendingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun failedReadCanBeRetriedAndPendingPaymentsAppear() {
        val attempts = AtomicInteger()
        val notices = MutableStateFlow<List<BcpMailSuggestion>>(emptyList())
        val model = BcpPendingViewModel {
            flow {
                if (attempts.incrementAndGet() == 1) throw IOException("Fixture")
                emitAll(notices)
            }
        }
        try {
            compose.setContent { GrafitoTheme { BcpPendingPanel(model) } }
            compose.waitUntil(5_000) { compose.onNodeWithText("Reintentar").isDisplayed() }
            compose.onNodeWithText("Sin gastos pendientes.").assertDoesNotExist()
            compose.onNodeWithText("Reintentar").performClick()
            compose.waitUntil(5_000) { compose.onNodeWithText("Sin gastos pendientes.").isDisplayed() }
            notices.value = listOf(BcpMailSuggestion("fixture", 1250, 1))
            compose.waitUntil(5_000) { compose.onNodeWithText("Revisar").isDisplayed() }
            compose.onNodeWithText("Revisar").assertIsDisplayed()
        } finally {
            model.viewModelScope.cancel()
        }
    }
}
