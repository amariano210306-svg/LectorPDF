package com.example.lectorpdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lectorpdf.ui.LectorApp
import com.example.lectorpdf.ui.screens.OnboardingScreen
import com.example.lectorpdf.ui.theme.LectorPDFTheme
import com.example.lectorpdf.ui.viewmodel.AppViewModelProvider
import com.example.lectorpdf.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val allowAutoResume = savedInstanceState == null
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel(factory = AppViewModelProvider.Factory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            var autoResumeAttempted by rememberSaveable { mutableStateOf(!allowAutoResume) }
            LectorPDFTheme(theme = state.settings.theme, dynamicColor = state.settings.dynamicColor) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        !state.settings.onboardingCompleted -> OnboardingScreen(viewModel::completeOnboarding)
                        else -> {
                            LaunchedEffect(state.loading, state.settings.onboardingCompleted) {
                                if (!state.loading && state.settings.onboardingCompleted && !autoResumeAttempted) {
                                    autoResumeAttempted = true
                                    viewModel.prepareAutoResume()?.let { bookId ->
                                        startActivity(android.content.Intent(this@MainActivity, com.example.lectorpdf.reader.pdf.PdfReaderActivity::class.java).putExtra(com.example.lectorpdf.reader.pdf.PdfReaderActivity.EXTRA_BOOK_ID, bookId))
                                    }
                                }
                            }
                            LectorApp(state.message, viewModel::dismissMessage)
                        }
                    }
                }
            }
        }
    }
}
