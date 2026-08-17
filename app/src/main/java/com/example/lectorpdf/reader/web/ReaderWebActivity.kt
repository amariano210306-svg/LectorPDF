@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.lectorpdf.reader.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.lectorpdf.LectorApplication
import com.example.lectorpdf.data.preferences.AppSettings
import com.example.lectorpdf.ui.theme.LectorPDFTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri

class ReaderWebActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = intent.getStringExtra(EXTRA_QUERY)?.trim().orEmpty()
        val mode = intent.getStringExtra(EXTRA_MODE)?.let { raw -> ReaderWebMode.entries.firstOrNull { it.name == raw } }
            ?: ReaderWebMode.DICTIONARY
        if (query.isBlank()) { finish(); return }
        enableEdgeToEdge()
        setContent {
            val settings by (application as LectorApplication).container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            LectorPDFTheme(settings.theme, settings.dynamicColor) {
                ReaderWebScreen(query = query, mode = mode, onClose = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_QUERY = "query"
        private const val EXTRA_MODE = "mode"
        fun intent(context: Context, query: String, mode: ReaderWebMode): Intent =
            Intent(context, ReaderWebActivity::class.java)
                .putExtra(EXTRA_QUERY, query.take(1_500))
                .putExtra(EXTRA_MODE, mode.name)
    }
}

enum class ReaderWebMode { DICTIONARY, TRANSLATE, SEARCH }

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ReaderWebScreen(query: String, mode: ReaderWebMode, onClose: () -> Unit) {
    val initialUrl = remember(query, mode) { mode.urlFor(query) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf(mode.title) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var blockedHost by remember { mutableStateOf<String?>(null) }
    var blockedUrl by remember { mutableStateOf<String?>(null) }

    fun updateNavigation(view: WebView) {
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
        title = view.title?.takeIf(String::isNotBlank) ?: mode.title
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            Text(query.take(70), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Cerrar") } },
                    actions = {
                        IconButton(onClick = {
                            (blockedUrl ?: webView?.url)?.takeIf { it.startsWith("https://") }?.let { url ->
                                startExternalBrowser(webView?.context, url)
                            }
                        }) { Icon(Icons.Outlined.OpenInBrowser, "Abrir en navegador") }
                    },
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás") }
                    IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Adelante") }
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Outlined.Refresh, "Recargar") }
                }
                blockedHost?.let { Text("Enlace externo bloqueado: $it", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error) }
            }
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    settings.setSupportMultipleWindows(false)
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (android.os.Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
                    setDownloadListener { _, _, _, _, _ -> blockedHost = "descarga no permitida" }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val uri = request.url
                            val allowed = uri.scheme == "https" && uri.host?.lowercase() in ALLOWED_HOSTS
                            if (!allowed) {
                                blockedHost = uri.host ?: uri.scheme ?: "enlace"
                                blockedUrl = uri.toString().takeIf { uri.scheme == "https" }
                            }
                            return !allowed
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            blockedHost = null
                            blockedUrl = null
                            updateNavigation(view)
                        }

                        override fun onPageFinished(view: WebView, url: String?) = updateNavigation(view)

                    }
                    loadUrl(initialUrl)
                    webView = this
                }
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }
}

private fun ReaderWebMode.urlFor(query: String): String {
    val encoded = Uri.encode(query.take(1_500))
    return when (this) {
        ReaderWebMode.DICTIONARY -> "https://es.wiktionary.org/wiki/Special:Search?search=$encoded"
        ReaderWebMode.TRANSLATE -> "https://translate.google.com/m?sl=auto&tl=es&q=$encoded"
        ReaderWebMode.SEARCH -> "https://html.duckduckgo.com/html/?q=$encoded"
    }
}

private val ReaderWebMode.title: String get() = when (this) {
    ReaderWebMode.DICTIONARY -> "Diccionario"
    ReaderWebMode.TRANSLATE -> "Traducir"
    ReaderWebMode.SEARCH -> "Búsqueda web"
}

private val ALLOWED_HOSTS = setOf(
    "es.wiktionary.org",
    "wiktionary.org",
    "translate.google.com",
    "html.duckduckgo.com",
    "duckduckgo.com",
)

private fun startExternalBrowser(context: Context?, url: String) {
    context?.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
