package com.example.lectorpdf.reader.pdf

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.lectorpdf.LectorApplication
import com.example.lectorpdf.data.preferences.AppSettings
import com.example.lectorpdf.ui.theme.LectorPDFTheme
import kotlinx.coroutines.launch

class PdfReaderActivity : ComponentActivity() {
    private val bookId: Long by lazy { intent.getLongExtra(EXTRA_BOOK_ID, -1L) }
    private val viewModel: PdfReaderViewModel by viewModels { PdfReaderViewModel.Factory(application, bookId) }
    private var volumeButtonsTurnPages = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (bookId <= 0) { finish(); return }
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as LectorApplication).container.settingsRepository.settings.collect {
                    volumeButtonsTurnPages = it.volumeButtonsTurnPages
                }
            }
        }
        setContent {
            val settings by (application as LectorApplication).container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val readerState by viewModel.uiState.collectAsStateWithLifecycle()
            LectorPDFTheme(settings.theme, settings.dynamicColor) {
                LaunchedEffect(readerState.loading) { if (!readerState.loading && readerState.error == null) viewModel.beginSession() }
                DisposableEffect(readerState.brightness, readerState.focusMode, settings.keepScreenOn) {
                    applyReaderWindow(readerState.brightness, readerState.focusMode, settings.keepScreenOn)
                    onDispose { }
                }
                PdfReaderScreen(
                    state = readerState,
                    viewModel = viewModel,
                    onBack = ::finish,
                    onOrientation = ::setReaderOrientation,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.beginSession()
    }

    override fun onStop() {
        viewModel.endSession()
        viewModel.flushProgress()
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeButtonsTurnPages) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> { viewModel.nextPage(); return true }
                KeyEvent.KEYCODE_VOLUME_UP -> { viewModel.previousPage(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun applyReaderWindow(brightness: Float, focusMode: Boolean, keepScreenOn: Boolean) {
        window.attributes = window.attributes.apply { screenBrightness = brightness }
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (focusMode) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun setReaderOrientation(mode: ReaderOrientation) {
        requestedOrientation = when (mode) {
            ReaderOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    companion object { const val EXTRA_BOOK_ID = "book_id" }
}

enum class ReaderOrientation { AUTO, PORTRAIT, LANDSCAPE }
