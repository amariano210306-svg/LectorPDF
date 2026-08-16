@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.lectorpdf.reader.pdf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun PdfReaderScreen(
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    onBack: () -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
) {
    var settingsVisible by remember { mutableStateOf(false) }
    var thumbnailsVisible by remember { mutableStateOf(false) }
    var pageDialogVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.controlsVisible, state.focusMode) {
        if (state.controlsVisible && !state.focusMode) { delay(4_000); viewModel.hideControls() }
    }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF101211))) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null -> ReaderError(state.error, onBack)
            else -> {
                PdfPager(state, viewModel)
                AnimatedVisibility(
                    visible = state.controlsVisible && !state.focusMode,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    ReaderTopBar(
                        state = state,
                        onBack = onBack,
                        onSearch = { searchVisible = true },
                        onRotate = viewModel::rotate,
                        onFocus = viewModel::toggleFocusMode,
                        onSettings = { settingsVisible = true },
                    )
                }
                AnimatedVisibility(
                    visible = state.controlsVisible && !state.focusMode,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    ReaderBottomBar(
                        state,
                        onPrevious = viewModel::previousPage,
                        onNext = viewModel::nextPage,
                        onPage = viewModel::goToPage,
                        onThumbnails = { thumbnailsVisible = true },
                        onGoTo = { pageDialogVisible = true },
                    )
                }
                if (state.focusMode) {
                    AssistChip(
                        onClick = viewModel::toggleFocusMode,
                        label = { Text("Salir de enfoque") },
                        leadingIcon = { Icon(Icons.Outlined.Close, null) },
                        modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(10.dp),
                    )
                }
            }
        }
    }

    if (settingsVisible) ReaderSettingsSheet(state, viewModel, onOrientation) { settingsVisible = false }
    if (thumbnailsVisible) ThumbnailSheet(state, viewModel, onPage = { viewModel.goToPage(it); thumbnailsVisible = false }) { thumbnailsVisible = false }
    if (pageDialogVisible) GoToPageDialog(state, onDismiss = { pageDialogVisible = false }) { viewModel.goToPage(it); pageDialogVisible = false }
    if (searchVisible) SearchDialog(state, viewModel, onDismiss = { searchVisible = false }) { viewModel.goToPage(it); searchVisible = false }
}

@Composable
private fun PdfPager(state: PdfReaderUiState, viewModel: PdfReaderViewModel) {
    val pagerState = rememberPagerState(initialPage = state.currentPage, pageCount = { state.pageCount })
    LaunchedEffect(state.currentPage, state.direction) {
        if (pagerState.currentPage != state.currentPage) pagerState.scrollToPage(state.currentPage)
    }
    LaunchedEffect(pagerState, state.direction) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect(viewModel::goToPage)
    }
    val content: @Composable (Int) -> Unit = { page -> PdfPage(page, state, viewModel) }
    if (state.direction == PdfPageDirection.VERTICAL) {
        VerticalPager(
            state = pagerState,
            userScrollEnabled = state.zoom <= 1.01f,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { content(it) }
    } else {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = state.zoom <= 1.01f,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { content(it) }
    }
}

@Composable
private fun PdfPage(page: Int, state: PdfReaderUiState, viewModel: PdfReaderViewModel) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val oldZoom = state.zoom
        val nextZoom = (oldZoom * zoomChange).coerceIn(1f, 4f)
        viewModel.setZoom(nextZoom)
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val effectiveCentroid = if (centroid.isSpecified) centroid else center
        val ratio = nextZoom / oldZoom
        offset = if (nextZoom <= 1.01f) Offset.Zero
        else offset * ratio + (effectiveCentroid - center) * (1f - ratio) + panChange
    }
    LaunchedEffect(page, viewport, state.zoom, state.rotation, state.fitMode) {
        if (viewport != IntSize.Zero) {
            delay(160)
            viewModel.requestPage(page, viewport.width, viewport.height)
        }
    }
    Box(
        Modifier.fillMaxSize().onSizeChanged { viewport = it }
            .pointerInputWithReaderGestures(state.zoom, viewModel)
            .transformable(transformState),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = state.pages[page]
        if (bitmap == null || bitmap.isRecycled) {
            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
        } else {
            val maxX = viewport.width * (state.zoom - 1f) / 2f
            val maxY = viewport.height * (state.zoom - 1f) / 2f
            val clampedOffset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Página ${page + 1} de ${state.pageCount}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(vertical = 10.dp, horizontal = 8.dp).graphicsLayer {
                    scaleX = state.zoom; scaleY = state.zoom
                    translationX = clampedOffset.x; translationY = clampedOffset.y
                },
            )
        }
    }
}

private fun Modifier.pointerInputWithReaderGestures(zoom: Float, viewModel: PdfReaderViewModel): Modifier =
    this.then(Modifier.pointerInput(zoom) {
        detectTapGestures(
            onTap = { viewModel.toggleControls() },
            onDoubleTap = { viewModel.setZoom(if (zoom > 1.05f) 1f else 2f) },
        )
    })

@Composable
private fun ReaderTopBar(
    state: PdfReaderUiState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onRotate: () -> Unit,
    onFocus: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .96f), shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Cerrar lector") }
            Column(Modifier.weight(1f)) {
                Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text("Página ${state.currentPage + 1} de ${state.pageCount}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSearch, enabled = state.searchSupported) { Icon(Icons.Outlined.FindInPage, if (state.searchSupported) "Buscar en el PDF" else "Búsqueda disponible desde Android 15") }
            IconButton(onClick = onRotate) { Icon(Icons.AutoMirrored.Outlined.RotateRight, "Rotar página") }
            IconButton(onClick = onFocus) { Icon(Icons.Outlined.CenterFocusStrong, "Modo enfoque") }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.MoreVert, "Opciones del lector") }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    state: PdfReaderUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPage: (Int) -> Unit,
    onThumbnails: () -> Unit,
    onGoTo: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .96f), shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = state.currentPage > 0) { Icon(Icons.AutoMirrored.Outlined.NavigateBefore, "Página anterior") }
                Slider(
                    value = state.currentPage.toFloat(),
                    onValueChange = { onPage(it.toInt()) },
                    valueRange = 0f..(state.pageCount - 1).coerceAtLeast(1).toFloat(),
                    steps = (state.pageCount - 2).coerceIn(0, 100),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNext, enabled = state.currentPage < state.pageCount - 1) { Icon(Icons.AutoMirrored.Outlined.NavigateNext, "Página siguiente") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onThumbnails) { Icon(Icons.Outlined.GridView, null); Spacer(Modifier.width(6.dp)); Text("Miniaturas") }
                TextButton(onClick = onGoTo) { Text("${state.currentPage + 1} / ${state.pageCount}") }
                Text("${((state.currentPage + 1f) / state.pageCount * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ReaderSettingsSheet(
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    onOrientation: (ReaderOrientation) -> Unit,
    onDismiss: () -> Unit,
) {
    var orientation by remember { mutableStateOf(ReaderOrientation.AUTO) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Opciones de PDF", style = MaterialTheme.typography.titleLarge)
            Text("Desplazamiento", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfPageDirection.entries.forEach { direction -> FilterChip(state.direction == direction, { viewModel.setDirection(direction) }, { Text(if (direction == PdfPageDirection.VERTICAL) "Vertical" else "Horizontal") }) }
            }
            Text("Ajuste", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfFitMode.entries.forEach { fit -> FilterChip(state.fitMode == fit, { viewModel.setFitMode(fit) }, { Text(if (fit == PdfFitMode.WIDTH) "Al ancho" else "Página completa") }) }
            }
            Text("Orientación", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderOrientation.entries.forEach { option ->
                    FilterChip(orientation == option, { orientation = option; onOrientation(option) }, { Text(option.label()) })
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Brillo del sistema", style = MaterialTheme.typography.titleMedium); Text("No cambia el brillo global", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(checked = state.brightness < 0f, onCheckedChange = { viewModel.setBrightness(if (it) -1f else .5f) })
            }
            if (state.brightness >= 0f) Slider(state.brightness, viewModel::setBrightness, valueRange = .05f..1f)
        }
    }
}

@Composable
private fun ThumbnailSheet(state: PdfReaderUiState, viewModel: PdfReaderViewModel, onPage: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxHeight(.78f).padding(horizontal = 16.dp)) {
            Text("Miniaturas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(92.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                gridItems((0 until state.pageCount).toList(), key = { it }) { page ->
                    var size by remember { mutableStateOf(IntSize.Zero) }
                    LaunchedEffect(page, size) { if (size != IntSize.Zero) viewModel.requestPage(page, size.width, size.height, thumbnail = true) }
                    Surface(onClick = { onPage(page) }, tonalElevation = if (page == state.currentPage) 8.dp else 0.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height(128.dp).onSizeChanged { size = it }, contentAlignment = Alignment.Center) {
                                state.thumbnails[page]?.let { Image(it.asImageBitmap(), "Página ${page + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } ?: CircularProgressIndicator(Modifier.size(24.dp))
                            }
                            Text("${page + 1}", modifier = Modifier.padding(5.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoToPageDialog(state: PdfReaderUiState, onDismiss: () -> Unit, onPage: (Int) -> Unit) {
    var value by remember { mutableStateOf((state.currentPage + 1).toString()) }
    val page = value.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ir a una página") },
        text = { OutlinedTextField(value, { value = it.filter(Char::isDigit) }, label = { Text("1–${state.pageCount}") }, singleLine = true) },
        confirmButton = { Button(onClick = { onPage(checkNotNull(page) - 1) }, enabled = page != null && page in 1..state.pageCount) { Text("Ir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SearchDialog(state: PdfReaderUiState, viewModel: PdfReaderViewModel, onDismiss: () -> Unit, onPage: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar en el PDF") },
        text = {
            Column {
                OutlinedTextField(state.searchQuery, viewModel::search, label = { Text("Texto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                LazyColumn(Modifier.fillMaxWidth().height(260.dp).padding(top = 10.dp)) {
                    items(state.searchResults, key = { it.pageIndex }) { result ->
                        Surface(onClick = { onPage(result.pageIndex) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Página ${result.pageIndex + 1} · ${result.matchCount} coincidencias", modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun ReaderError(message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("No se pudo abrir el documento", style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
        Text(message, color = androidx.compose.ui.graphics.Color.LightGray, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onBack) { Text("Volver") }
    }
}

private fun ReaderOrientation.label() = when (this) {
    ReaderOrientation.AUTO -> "Automática"
    ReaderOrientation.PORTRAIT -> "Vertical"
    ReaderOrientation.LANDSCAPE -> "Horizontal"
}
