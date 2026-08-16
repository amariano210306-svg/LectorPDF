package com.example.lectorpdf.reader.pdf

import android.content.pm.ActivityInfo
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.addCallback
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.lectorpdf.LectorApplication
import com.example.lectorpdf.MainActivity
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
        onBackPressedDispatcher.addCallback(this) { exitReader() }
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
                LaunchedEffect(readerState.loading, readerState.orientation) {
                    if (!readerState.loading) setReaderOrientation(readerState.orientation)
                }
                DisposableEffect(readerState.brightness, readerState.focusMode, readerState.controlsVisible, settings.keepScreenOn) {
                    applyReaderWindow(
                        readerState.brightness,
                        readerState.controlsVisible && !readerState.focusMode,
                        settings.keepScreenOn,
                    )
                    onDispose { }
                }
                PdfReaderScreen(
                    state = readerState,
                    viewModel = viewModel,
                    keepScreenOn = settings.keepScreenOn,
                    volumeButtonsTurnPages = settings.volumeButtonsTurnPages,
                    onSetKeepScreenOn = { value -> lifecycleScope.launch { (application as LectorApplication).container.settingsRepository.setKeepScreenOn(value) } },
                    onSetVolumeButtons = { value -> lifecycleScope.launch { (application as LectorApplication).container.settingsRepository.setVolumeButtons(value) } },
                    onBack = ::exitReader,
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

    override fun onPause() {
        viewModel.flushProgress()
        super.onPause()
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

    private fun applyReaderWindow(brightness: Float, systemBarsVisible: Boolean, keepScreenOn: Boolean) {
        window.attributes = window.attributes.apply { screenBrightness = brightness }
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (systemBarsVisible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setReaderOrientation(mode: ReaderOrientation) {
        requestedOrientation = when (mode) {
            ReaderOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun exitReader() {
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_HOME, false) || isTaskRoot) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SKIP_AUTO_RESUME, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_RETURN_TO_HOME = "return_to_home"
    }
}

enum class ReaderOrientation { AUTO, PORTRAIT, LANDSCAPE }
