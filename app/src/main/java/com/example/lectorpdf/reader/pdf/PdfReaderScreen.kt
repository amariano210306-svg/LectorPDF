@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.lectorpdf.reader.pdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private val ReaderBackground = Color(0xFF171918)

@Composable
fun PdfReaderScreen(
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    keepScreenOn: Boolean,
    volumeButtonsTurnPages: Boolean,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetVolumeButtons: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
) {
    var settingsVisible by remember { mutableStateOf(false) }
    var thumbnailsVisible by remember { mutableStateOf(false) }
    var pageDialogVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var bookmarksVisible by remember { mutableStateOf(false) }
    var brightnessOverlay by remember { mutableStateOf<Float?>(null) }
    val currentBrightness by rememberUpdatedState(state.brightness)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri?.let(viewModel::exportReaderData)
    }

    LaunchedEffect(brightnessOverlay) {
        if (brightnessOverlay != null) {
            delay(900)
            brightnessOverlay = null
        }
    }
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(2_800)
            viewModel.clearNotice()
        }
    }

    LaunchedEffect(state.controlsVisible, state.focusMode, state.controlsInteractionToken) {
        if (state.controlsVisible && !state.focusMode) {
            delay(4_500)
            viewModel.hideControls()
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(state.readerTheme.readerBackground())
            .readerBrightnessGesture(
                current = { currentBrightness },
                onChange = { value -> viewModel.setBrightness(value); brightnessOverlay = value },
            ),
    ) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null -> ReaderError(state.error, onBack)
            else -> {
                val centerTap = {
                    if (state.focusMode) viewModel.toggleFocusMode() else viewModel.toggleControls()
                }
                when (state.direction) {
                    PdfPageDirection.CONTINUOUS -> ContinuousPdfReader(state, viewModel, centerTap)
                    PdfPageDirection.PAGED_HORIZONTAL -> HorizontalPdfReader(state, viewModel, centerTap)
                    PdfPageDirection.SPREAD -> SpreadPdfReader(state, viewModel, centerTap)
                }
                AnimatedVisibility(
                    visible = state.controlsVisible && !state.focusMode,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    ReaderTopBar(
                        state = state,
                        onBack = onBack,
                        onSearch = { searchVisible = true; viewModel.noteControlsInteraction() },
                        onSettings = { settingsVisible = true; viewModel.noteControlsInteraction() },
                        onBookmark = viewModel::toggleBookmark,
                        onBookmarks = { bookmarksVisible = true; viewModel.noteControlsInteraction() },
                        onExport = { exportLauncher.launch("anotaciones-${state.currentPage + 1}.md") },
                        onRotate = viewModel::rotate,
                        onFocus = viewModel::toggleFocusMode,
                    )
                }
                AnimatedVisibility(
                    visible = state.controlsVisible && !state.focusMode,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    ReaderBottomBar(
                        state = state,
                        onPage = viewModel::goToPage,
                        onThumbnails = { thumbnailsVisible = true; viewModel.noteControlsInteraction() },
                        onGoTo = { pageDialogVisible = true; viewModel.noteControlsInteraction() },
                        onInteraction = viewModel::noteControlsInteraction,
                    )
                }
                brightnessOverlay?.let { brightness ->
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = .92f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Brightness6, null)
                            Spacer(Modifier.width(8.dp))
                            Text("${(brightness * 100).toInt()}%")
                        }
                    }
                }
                state.notice?.let { notice ->
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp, start = 20.dp, end = 20.dp),
                    ) { Text(notice, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
                }
            }
        }
    }

    if (settingsVisible) {
        ReaderSettingsSheet(
            state = state,
            viewModel = viewModel,
            keepScreenOn = keepScreenOn,
            volumeButtonsTurnPages = volumeButtonsTurnPages,
            onSetKeepScreenOn = onSetKeepScreenOn,
            onSetVolumeButtons = onSetVolumeButtons,
            onOrientation = onOrientation,
            onDismiss = { settingsVisible = false },
        )
    }
    if (thumbnailsVisible) {
        ThumbnailSheet(state, viewModel, onPage = { viewModel.goToPage(it); thumbnailsVisible = false }) {
            thumbnailsVisible = false
        }
    }
    if (pageDialogVisible) {
        GoToPageDialog(state, onDismiss = { pageDialogVisible = false }) {
            viewModel.goToPage(it)
            pageDialogVisible = false
        }
    }
    if (searchVisible) {
        SearchDialog(state, viewModel, onDismiss = { searchVisible = false }) {
            viewModel.goToPage(it)
            searchVisible = false
        }
    }
    if (bookmarksVisible) {
        BookmarkSheet(state, viewModel, onDismiss = { bookmarksVisible = false }) { bookmark ->
            viewModel.goToBookmark(bookmark)
            bookmarksVisible = false
        }
    }
}

@Composable
private fun ContinuousPdfReader(
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    onCenterTap: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentPage)
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(viewport) {
        if (viewport != IntSize.Zero) viewModel.onViewportShapeChanged(viewport.width > viewport.height)
    }

    LaunchedEffect(state.navigationToken, state.pageCount, viewport) {
        if (state.pageCount > 0 && viewport != IntSize.Zero) {
            val currentInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == state.currentPage }
            val estimatedHeight = currentInfo?.size ?: (viewport.width * 1.414f).toInt()
            listState.scrollToItem(state.currentPage, (estimatedHeight * state.pageOffsetFraction).toInt())
        }
    }
    LaunchedEffect(listState, viewport) {
        snapshotFlow {
            val layout = listState.layoutInfo
            VisibleSnapshot(layout.viewportStartOffset, layout.viewportEndOffset, layout.visibleItemsInfo.map {
                PdfVisiblePage(it.index, it.offset, it.size)
            })
        }.collect { snapshot ->
            if (snapshot.pages.isEmpty()) return@collect
            viewModel.setVisiblePages(snapshot.pages.mapTo(mutableSetOf(), PdfVisiblePage::index))
            val position = dominantReadingPosition(snapshot.start, snapshot.end, snapshot.pages) ?: return@collect
            viewModel.updateReadingPosition(position.page, position.offsetFraction)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (scrolling) viewModel.hideControls()
        }
    }

    LazyColumn(
        state = listState,
        userScrollEnabled = state.zoom <= 1.01f,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .onSizeChanged { viewport = it },
    ) {
        items(count = state.pageCount, key = { it }, contentType = { "pdf-page" }) { page ->
            PdfContinuousPage(page, state, viewModel, viewport, onCenterTap)
        }
    }
}

@Composable
private fun PdfContinuousPage(
    page: Int,
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    viewport: IntSize,
    onCenterTap: () -> Unit,
) {
    val bitmap = state.pages[page]
    val aspect = bitmap?.takeUnless { it.isRecycled }?.let { it.width.toFloat() / it.height } ?: .707f
    val fitFraction = if (state.fitMode == PdfFitMode.PAGE && bitmap != null && viewport.width > 0) {
        (bitmap.width / (viewport.width * state.zoom).coerceAtLeast(1f)).coerceIn(.35f, 1f)
    } else {
        1f
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.Center) {
        PdfPageSurface(
            page = page,
            state = state,
            viewModel = viewModel,
            viewport = viewport,
            aspect = aspect,
            modifier = Modifier.fillMaxWidth(fitFraction).aspectRatio(aspect),
            onCenterTap = onCenterTap,
        )
    }
}

@Composable
private fun HorizontalPdfReader(
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    onCenterTap: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = state.currentPage, pageCount = { state.pageCount })
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(state.navigationToken) {
        if (pagerState.currentPage != state.currentPage) pagerState.scrollToPage(state.currentPage)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect {
            viewModel.setVisiblePages(setOf(it))
            viewModel.updateReadingPosition(it, 0f)
        }
    }
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = state.zoom <= 1.01f,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize().onSizeChanged { viewport = it },
    ) { page ->
        val bitmap = state.pages[page]
        val aspect = bitmap?.takeUnless { it.isRecycled }?.let { it.width.toFloat() / it.height } ?: .707f
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            PdfPageSurface(
                page = page,
                state = state,
                viewModel = viewModel,
                viewport = viewport,
                aspect = aspect,
                modifier = Modifier.fillMaxSize(),
                onCenterTap = onCenterTap,
                pageMode = true,
            )
        }
    }
}

@Composable
private fun SpreadPdfReader(
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    onCenterTap: () -> Unit,
) {
    val spreadCount = (state.pageCount + 1) / 2
    val pagerState = rememberPagerState(
        initialPage = (state.currentPage / 2).coerceIn(0, (spreadCount - 1).coerceAtLeast(0)),
        pageCount = { spreadCount },
    )
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(state.navigationToken) {
        val spread = (state.currentPage / 2).coerceIn(0, (spreadCount - 1).coerceAtLeast(0))
        if (pagerState.currentPage != spread) pagerState.scrollToPage(spread)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { spread ->
            val first = spread * 2
            viewModel.setVisiblePages(setOf(first, first + 1).filter { it < state.pageCount }.toSet())
            viewModel.updateReadingPosition(first, 0f)
        }
    }
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        userScrollEnabled = state.zoom <= 1.01f,
        modifier = Modifier.fillMaxSize().onSizeChanged { viewport = it },
    ) { spread ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(2) { side ->
                val page = spread * 2 + side
                if (page < state.pageCount) {
                    val bitmap = state.pages[page]
                    val aspect = bitmap?.takeUnless { it.isRecycled }?.let { it.width.toFloat() / it.height } ?: .707f
                    PdfPageSurface(
                        page = page,
                        state = state,
                        viewModel = viewModel,
                        viewport = IntSize((viewport.width / 2).coerceAtLeast(1), viewport.height),
                        aspect = aspect,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onCenterTap = onCenterTap,
                        pageMode = true,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PdfPageSurface(
    page: Int,
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    viewport: IntSize,
    aspect: Float,
    modifier: Modifier,
    onCenterTap: () -> Unit,
    pageMode: Boolean = false,
) {
    var size by remember(page) { mutableStateOf(IntSize.Zero) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val oldZoom = state.zoom
        val nextZoom = (oldZoom * zoomChange).coerceIn(1f, 4f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val focalPoint = if (centroid.isSpecified) centroid else center
        val ratio = nextZoom / oldZoom
        offset = if (nextZoom <= 1.01f) Offset.Zero else offset * ratio + (focalPoint - center) * (1f - ratio) + panChange
        viewModel.setZoom(nextZoom)
    }
    LaunchedEffect(state.zoom) { if (state.zoom <= 1.01f) offset = Offset.Zero }
    LaunchedEffect(page, viewport, state.zoom, state.rotation, state.fitMode, state.cropMargins, state.manualCrop) {
        if (viewport != IntSize.Zero) {
            delay(110)
            viewModel.requestPage(page, viewport.width.coerceAtLeast(320), viewport.height.coerceAtLeast(320))
        }
    }
    val maxX = size.width * (state.zoom - 1f) / 2f
    val maxY = size.height * (state.zoom - 1f) / 2f
    val clamped = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    Box(
        modifier
            .background(Color.White)
            .clipToBounds()
            .onSizeChanged { size = it }
            .readerTapGestures(state.zoom, size, onCenterTap, viewModel::setZoom)
            .transformable(transformState, canPan = { state.zoom > 1.01f }),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = state.pages[page]
        if (bitmap == null || bitmap.isRecycled) {
            CircularProgressIndicator(Modifier.size(30.dp), color = MaterialTheme.colorScheme.primary)
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Página ${page + 1} de ${state.pageCount}",
                contentScale = if (pageMode) ContentScale.Fit else ContentScale.FillBounds,
                colorFilter = state.readerTheme.colorFilter(),
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = state.zoom
                    scaleY = state.zoom
                    translationX = clamped.x
                    translationY = clamped.y
                },
            )
        }
    }
}

private fun Modifier.readerTapGestures(
    zoom: Float,
    size: IntSize,
    onCenterTap: () -> Unit,
    onZoom: (Float) -> Unit,
): Modifier = pointerInput(zoom, size) {
    detectTapGestures(
        onTap = { position ->
            if (position.x in size.width * .22f..size.width * .78f && position.y in size.height * .15f..size.height * .85f) {
                onCenterTap()
            }
        },
        onDoubleTap = { onZoom(if (zoom > 1.05f) 1f else 2f) },
    )
}

@Composable
private fun ReaderTopBar(
    state: PdfReaderUiState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onExport: () -> Unit,
    onRotate: () -> Unit,
    onFocus: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val bookmarked = state.bookmarks.any { PdfBookmarkCodec.decode(it.locatorJson)?.page == state.currentPage }
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .96f), shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Cerrar lector") }
            Column(Modifier.weight(1f)) {
                Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text("Página ${state.currentPage + 1} de ${state.pageCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSearch, enabled = state.searchSupported) {
                Icon(Icons.Outlined.FindInPage, if (state.searchSupported) "Buscar en el PDF" else "Búsqueda disponible desde Android 15")
            }
            IconButton(onClick = onBookmark) {
                Icon(if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (bookmarked) "Quitar marcador" else "Añadir marcador")
            }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Tune, "Ajustes de lectura") }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, "Más opciones") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Ver marcadores (${state.bookmarks.size})") },
                        leadingIcon = { Icon(Icons.Outlined.Bookmark, null) },
                        onClick = { menuExpanded = false; onBookmarks() },
                    )
                    DropdownMenuItem(
                        text = { Text("Exportar notas y marcadores") },
                        leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null) },
                        onClick = { menuExpanded = false; onExport() },
                    )
                    DropdownMenuItem(
                        text = { Text("Rotar página") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.RotateRight, null) },
                        onClick = { menuExpanded = false; onRotate() },
                    )
                    DropdownMenuItem(
                        text = { Text("Modo enfoque") },
                        leadingIcon = { Icon(Icons.Outlined.CenterFocusStrong, null) },
                        onClick = { menuExpanded = false; onFocus() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    state: PdfReaderUiState,
    onPage: (Int) -> Unit,
    onThumbnails: () -> Unit,
    onGoTo: () -> Unit,
    onInteraction: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember(state.pageCount) { mutableFloatStateOf(state.currentPage.toFloat()) }
    LaunchedEffect(state.currentPage, dragging) { if (!dragging) sliderValue = state.currentPage.toFloat() }
    val targetPage = sliderValue.toInt().coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
    val percentage = if (state.pageCount == 0) 0 else (((state.currentPage + state.pageOffsetFraction) / state.pageCount) * 100).toInt().coerceIn(0, 100)
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .97f), shadowElevation = 8.dp) {
        Column(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            if (dragging) Text("Ir a página ${targetPage + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
            Slider(
                value = sliderValue,
                onValueChange = { dragging = true; sliderValue = it; onInteraction() },
                onValueChangeFinished = { dragging = false; onPage(targetPage) },
                valueRange = 0f..(state.pageCount - 1).coerceAtLeast(1).toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onThumbnails) {
                    Icon(Icons.Outlined.GridView, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Miniaturas")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onGoTo) { Text("${state.currentPage + 1} / ${state.pageCount}") }
                Spacer(Modifier.width(14.dp))
                Text("$percentage%", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ReaderSettingsSheet(
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    keepScreenOn: Boolean,
    volumeButtonsTurnPages: Boolean,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetVolumeButtons: (Boolean) -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.84f),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Opciones de PDF", style = MaterialTheme.typography.titleLarge) }
            item { Text("Desplazamiento", style = MaterialTheme.typography.titleMedium) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(count = PdfPageDirection.entries.size) { index ->
                        val direction = PdfPageDirection.entries[index]
                        FilterChip(
                            selected = state.direction == direction,
                            onClick = { viewModel.setDirection(direction) },
                            label = { Text(direction.label()) },
                        )
                    }
                }
            }
            item { Text("Ajuste", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfFitMode.entries.forEach { fit ->
                        FilterChip(state.fitMode == fit, { viewModel.setFitMode(fit) }, { Text(if (fit == PdfFitMode.WIDTH) "Al ancho" else "Página completa") })
                    }
                }
            }
            item { SettingToggle("Recortar márgenes", "Estima el contenido visible sin modificar el archivo.", state.cropMargins, viewModel::setCropMargins) }
            item { Text("Recorte manual", style = MaterialTheme.typography.titleMedium) }
            item { CropSlider("Izquierdo", state.manualCrop.left) { viewModel.setManualCrop(state.manualCrop.copy(left = it)) } }
            item { CropSlider("Derecho", state.manualCrop.right) { viewModel.setManualCrop(state.manualCrop.copy(right = it)) } }
            item { CropSlider("Superior", state.manualCrop.top) { viewModel.setManualCrop(state.manualCrop.copy(top = it)) } }
            item { CropSlider("Inferior", state.manualCrop.bottom) { viewModel.setManualCrop(state.manualCrop.copy(bottom = it)) } }
            if (!state.manualCrop.isEmpty) {
                item { TextButton(onClick = { viewModel.setManualCrop(PdfCropInsets()) }) { Text("Restablecer recorte manual") } }
            }
            item {
                OutlinedButton(onClick = viewModel::rotate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.RotateRight, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Rotar 90°")
                }
            }
            item { Text("Orientación", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderOrientation.entries.forEach { option ->
                        FilterChip(
                            selected = state.orientation == option,
                            onClick = { viewModel.setOrientation(option); onOrientation(option) },
                            label = { Text(option.label()) },
                        )
                    }
                }
            }
            item { HorizontalDivider() }
            item { Text("Tema", style = MaterialTheme.typography.titleMedium) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(count = PdfReaderTheme.entries.size) { index ->
                        val theme = PdfReaderTheme.entries[index]
                        FilterChip(
                            selected = state.readerTheme == theme,
                            onClick = { viewModel.setReaderTheme(theme) },
                            label = { Text(theme.label()) },
                        )
                    }
                }
            }
            item { SettingToggle("Mantener pantalla activa", "Evita que se apague mientras lees.", keepScreenOn, onSetKeepScreenOn) }
            item { SettingToggle("Botones de volumen", "Avanzan o retroceden una página.", volumeButtonsTurnPages, onSetVolumeButtons) }
            item { SettingToggle("Brillo del sistema", "Usa el brillo normal del dispositivo.", state.brightness < 0f) { viewModel.setBrightness(if (it) -1f else .5f) } }
            if (state.brightness >= 0f) item { Slider(state.brightness, viewModel::setBrightness, valueRange = .05f..1f) }
            item { HorizontalDivider() }
            item { Text("Text To Speech", style = MaterialTheme.typography.titleMedium) }
            if (!state.ttsSupported) {
                item { Text("La extracción de texto PDF requiere Android 15 o posterior.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::previousTtsFragment, enabled = state.ttsFragmentCount > 0) { Icon(Icons.Outlined.SkipPrevious, "Fragmento anterior") }
                        IconButton(onClick = viewModel::playOrPauseTts) { Icon(if (state.ttsPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (state.ttsPlaying) "Pausar" else "Reproducir") }
                        IconButton(onClick = viewModel::stopTts, enabled = state.ttsPlaying || state.ttsPaused) { Icon(Icons.Outlined.Stop, "Detener") }
                        IconButton(onClick = viewModel::nextTtsFragment, enabled = state.ttsFragmentCount > 0) { Icon(Icons.Outlined.SkipNext, "Fragmento siguiente") }
                    }
                }
                state.ttsMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                if (state.ttsPage != null) {
                    item { Text("Página ${state.ttsPage + 1} · fragmento ${state.ttsFragment + 1}/${state.ttsFragmentCount}", style = MaterialTheme.typography.bodySmall) }
                }
                item { ReaderValueSlider("Velocidad", state.ttsRate, .5f..2f, viewModel::setTtsRate) }
                item { ReaderValueSlider("Tono", state.ttsPitch, .5f..1.5f, viewModel::setTtsPitch) }
            }
            item {
                OutlinedButton(onClick = { onDismiss(); viewModel.toggleFocusMode() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CenterFocusStrong, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Activar modo enfoque")
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun CropSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..PdfCropInsets.MAX_SIDE)
    }
}

@Composable
private fun ReaderValueSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text("${(value * 10).toInt() / 10f}x", style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun BookmarkSheet(
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    onDismiss: () -> Unit,
    onPage: (com.example.lectorpdf.data.local.entity.BookmarkEntity) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxHeight(.68f).padding(horizontal = 18.dp)) {
            Text("Marcadores", style = MaterialTheme.typography.titleLarge)
            if (state.bookmarks.isEmpty()) {
                Text(
                    "Añade un marcador desde la barra superior para volver a este punto.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp)) {
                    items(count = state.bookmarks.size, key = { state.bookmarks[it].id }) { index ->
                        val bookmark = state.bookmarks[index]
                        val locator = PdfBookmarkCodec.decode(bookmark.locatorJson)
                        Surface(onClick = { onPage(bookmark) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Bookmark, null)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(bookmark.label ?: "Página ${(locator?.page ?: 0) + 1}")
                                    Text(
                                        "Página ${(locator?.page ?: 0) + 1} · ${(locator?.offsetFraction?.times(100))?.toInt() ?: 0}% de la página",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteBookmark(bookmark.id) }) {
                                    Icon(Icons.Outlined.DeleteOutline, "Eliminar marcador")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailSheet(state: PdfReaderUiState, viewModel: PdfReaderViewModel, onPage: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxHeight(.78f).padding(horizontal = 16.dp)) {
            Text("Miniaturas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(92.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(count = state.pageCount, key = { it }, contentType = { "thumbnail" }) { page ->
                    var size by remember { mutableStateOf(IntSize.Zero) }
                    LaunchedEffect(page, size) { if (size != IntSize.Zero) viewModel.requestPage(page, size.width, size.height, thumbnail = true) }
                    Surface(onClick = { onPage(page) }, tonalElevation = if (page == state.currentPage) 8.dp else 0.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height(128.dp).onSizeChanged { size = it }, contentAlignment = Alignment.Center) {
                                state.thumbnails[page]?.takeUnless { it.isRecycled }?.let {
                                    Image(it.asImageBitmap(), "Página ${page + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                } ?: CircularProgressIndicator(Modifier.size(24.dp))
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
                    items(count = state.searchResults.size, key = { state.searchResults[it].pageIndex }) { index ->
                        val result = state.searchResults[index]
                        Surface(onClick = { onPage(result.pageIndex) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 12.dp)) {
                                Text("Página ${result.pageIndex + 1} · ${result.matchCount} coincidencias")
                                result.snippet?.let {
                                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
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
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No se pudo abrir el documento", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(message, color = Color.LightGray, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onBack) { Text("Volver") }
    }
}

private data class VisibleSnapshot(val start: Int, val end: Int, val pages: List<PdfVisiblePage>)

private fun Modifier.readerBrightnessGesture(current: () -> Float, onChange: (Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x > 36.dp.toPx()) return@awaitEachGesture
            val initial = current()
            var value = if (initial < 0f) .5f else initial
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.positionChange().y
                if (delta != 0f) {
                    value = (value - delta / size.height.coerceAtLeast(1)).coerceIn(.05f, 1f)
                    change.consume()
                    onChange(value)
                }
            }
        }
    }

private fun PdfReaderTheme.readerBackground(): Color = when (this) {
    PdfReaderTheme.DAY -> ReaderBackground
    PdfReaderTheme.NIGHT -> Color.Black
    PdfReaderTheme.SEPIA -> Color(0xFF30291F)
    PdfReaderTheme.CONSOLE -> Color(0xFF00120A)
}

private fun PdfReaderTheme.colorFilter(): ColorFilter? = when (this) {
    PdfReaderTheme.DAY -> null
    PdfReaderTheme.NIGHT -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        -.82f, 0f, 0f, 0f, 225f,
        0f, -.82f, 0f, 0f, 225f,
        0f, 0f, -.82f, 0f, 225f,
        0f, 0f, 0f, 1f, 0f,
    )))
    PdfReaderTheme.SEPIA -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        .393f, .769f, .189f, 0f, -12f,
        .349f, .686f, .168f, 0f, -8f,
        .272f, .534f, .131f, 0f, 4f,
        0f, 0f, 0f, 1f, 0f,
    )))
    PdfReaderTheme.CONSOLE -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        0f, 0f, 0f, 0f, 0f,
        .30f, .59f, .11f, 0f, 0f,
        .05f, .12f, .03f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))
}

private fun PdfPageDirection.label(): String = when (this) {
    PdfPageDirection.CONTINUOUS -> "Continuo"
    PdfPageDirection.PAGED_HORIZONTAL -> "Página horizontal"
    PdfPageDirection.SPREAD -> "Doble página"
}

private fun PdfReaderTheme.label(): String = when (this) {
    PdfReaderTheme.DAY -> "Día"
    PdfReaderTheme.NIGHT -> "Noche"
    PdfReaderTheme.SEPIA -> "Sepia"
    PdfReaderTheme.CONSOLE -> "Consola"
}

private fun ReaderOrientation.label() = when (this) {
    ReaderOrientation.AUTO -> "Automática"
    ReaderOrientation.PORTRAIT -> "Vertical"
    ReaderOrientation.LANDSCAPE -> "Horizontal"
}
