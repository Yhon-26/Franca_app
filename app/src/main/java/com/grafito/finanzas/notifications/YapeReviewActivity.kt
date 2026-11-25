package com.grafito.finanzas.notifications

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grafito.finanzas.model.FinanceViewModel
import com.grafito.finanzas.ui.components.ProvideFrancaSounds
import com.grafito.finanzas.ui.screens.ReviewYape
import com.grafito.finanzas.ui.theme.GrafitoTheme
import com.grafito.finanzas.ui.theme.HomePalette

class YapeReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.data?.schemeSpecificPart ?: run { finish(); return }
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        setContent {
            ProvideFrancaSounds {
                GrafitoTheme(colorScheme = HomePalette.panelScheme) {
                    val vm: FinanceViewModel = viewModel()
                    val data by vm.state.collectAsStateWithLifecycle()
                    val loadError by vm.loadError.collectAsStateWithLifecycle()
                    Surface(Modifier.fillMaxSize(), color = HomePalette.Background) {
                        if (loadError || data.isLoaded) {
                            ReviewYape(id, data.accounts, data.categories, data.defaultAccountId, data.transactions, loadError = loadError) { message ->
                                if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        } else {
                            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }
    }
}
