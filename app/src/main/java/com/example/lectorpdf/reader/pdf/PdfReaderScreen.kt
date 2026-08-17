@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.lectorpdf.reader.pdf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.lectorpdf.reader.web.ReaderWebActivity
import com.example.lectorpdf.reader.web.ReaderWebMode
import kotlin.math.abs
import kotlin.math.roundToInt

private val ReaderBackground = Color(0xFF171918)

@Composable
fun PdfReaderScreen(
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    keepScreenOn: Boolean,
    volumeButtonsTurnPages: Boolean,
    brightnessGestureEnabled: Boolean,
    themeCornerGestureEnabled: Boolean,
    bookmarkCornerGestureEnabled: Boolean,
    showBookmarkInFocus: Boolean,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetVolumeButtons: (Boolean) -> Unit,
    onSetBrightnessGesture: (Boolean) -> Unit,
    onSetThemeCornerGesture: (Boolean) -> Unit,
    onSetBookmarkCornerGesture: (Boolean) -> Unit,
    onSetShowBookmarkInFocus: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
) {
    var settingsVisible by remember { mutableStateOf(false) }
    var thumbnailsVisible by remember { mutableStateOf(false) }
    var pageDialogVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var bookmarksVisible by remember { mutableStateOf(false) }
    var annotationsVisible by remember { mutableStateOf(false) }
    var noteEditorVisible by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    var noteTargetHighlight by remember { mutableStateOf<com.example.lectorpdf.data.local.entity.HighlightEntity?>(null) }
    var editingHighlight by remember { mutableStateOf<com.example.lectorpdf.data.local.entity.HighlightEntity?>(null) }
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
            delay(1_100)
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
            .background(state.readerTheme.readerBackground()),
    ) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null -> ReaderError(state.error, onBack)
            else -> {
                val centerTap = {
                    if (state.focusMode) viewModel.toggleFocusMode() else viewModel.toggleControls()
                }
                val gestureConfig = ReaderGestureConfig(
                    allowed = !state.controlsVisible && state.selection == null && !state.selecting &&
                        !settingsVisible && !thumbnailsVisible && !pageDialogVisible &&
                        !searchVisible && !bookmarksVisible && !annotationsVisible &&
                        !noteEditorVisible && editingHighlight == null,
                    brightnessEnabled = brightnessGestureEnabled,
                    themeEnabled = themeCornerGestureEnabled,
                    bookmarkEnabled = bookmarkCornerGestureEnabled,
                    selectionEnabled = !settingsVisible && !thumbnailsVisible && !pageDialogVisible &&
                        !searchVisible && !bookmarksVisible && !annotationsVisible &&
                        !noteEditorVisible && editingHighlight == null,
                    currentBrightness = { currentBrightness },
                    onBrightness = { value -> viewModel.setBrightness(value); brightnessOverlay = value },
                    onBrightnessFinished = viewModel::persistBrightness,
                    onTheme = viewModel::cycleReaderTheme,
                    onBookmark = viewModel::toggleBookmark,
                    onNote = { noteTargetHighlight = null; noteDraft = ""; noteEditorVisible = true },
                    onDocumentSearch = { query -> viewModel.search(query); searchVisible = true },
                    onEditHighlight = { editingHighlight = it },
                )
                when (state.direction) {
                    PdfPageDirection.CONTINUOUS -> ContinuousPdfReader(state, viewModel, centerTap, gestureConfig)
                    PdfPageDirection.PAGED_HORIZONTAL -> HorizontalPdfReader(state, viewModel, centerTap, gestureConfig)
                    PdfPageDirection.SPREAD -> SpreadPdfReader(state, viewModel, centerTap, gestureConfig)
                }
                val bookmarked = state.bookmarks.any { PdfBookmarkCodec.decode(it.locatorJson)?.page == state.currentPage }
                var bookmarkPulsing by remember { mutableStateOf(false) }
                LaunchedEffect(state.bookmarkPulseToken) {
                    if (state.bookmarkPulseToken > 0) {
                        bookmarkPulsing = true
                        delay(260)
                        bookmarkPulsing = false
                    }
                }
                val bookmarkScale by animateFloatAsState(if (bookmarkPulsing) 1.28f else 1f, label = "bookmark-pulse")
                AnimatedVisibility(
                    visible = bookmarked && (!state.focusMode || showBookmarkInFocus),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                        .padding(12.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .92f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.scale(bookmarkScale),
                    ) {
                        Icon(Icons.Outlined.Bookmark, "La posición actual tiene marcador", Modifier.padding(9.dp).size(22.dp))
                    }
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
                        onAnnotations = { annotationsVisible = true; viewModel.noteControlsInteraction() },
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
            brightnessGestureEnabled = brightnessGestureEnabled,
            themeCornerGestureEnabled = themeCornerGestureEnabled,
            bookmarkCornerGestureEnabled = bookmarkCornerGestureEnabled,
            showBookmarkInFocus = showBookmarkInFocus,
            onSetKeepScreenOn = onSetKeepScreenOn,
            onSetVolumeButtons = onSetVolumeButtons,
            onSetBrightnessGesture = onSetBrightnessGesture,
            onSetThemeCornerGesture = onSetThemeCornerGesture,
            onSetBookmarkCornerGesture = onSetBookmarkCornerGesture,
            onSetShowBookmarkInFocus = onSetShowBookmarkInFocus,
            onOpenSearch = { settingsVisible = false; searchVisible = true },
            onOpenBookmarks = { settingsVisible = false; bookmarksVisible = true },
            onOpenAnnotations = { settingsVisible = false; annotationsVisible = true },
            onOpenThumbnails = { settingsVisible = false; thumbnailsVisible = true },
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
    if (annotationsVisible) {
        AnnotationSheet(state, viewModel, onDismiss = { annotationsVisible = false }) { locator ->
            viewModel.goToAnnotation(locator)
            annotationsVisible = false
        }
    }
    if (noteEditorVisible) {
        AlertDialog(
            onDismissRequest = { noteEditorVisible = false },
            title = { Text("Añadir nota") },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    label = { Text("Nota sobre la selección") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        noteTargetHighlight?.let { viewModel.addNoteToHighlight(it, noteDraft) } ?: viewModel.addNote(noteDraft)
                        noteEditorVisible = false
                        noteTargetHighlight = null
                    },
                    enabled = noteDraft.isNotBlank(),
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { noteEditorVisible = false }) { Text("Cancelar") } },
        )
    }
    editingHighlight?.let { highlight ->
        HighlightEditorDialog(
            highlight = highlight,
            onDismiss = { editingHighlight = null },
            onUpdate = { color, style -> viewModel.updateHighlight(highlight, color, style); editingHighlight = null },
            onNote = {
                noteTargetHighlight = highlight
                noteDraft = ""
                noteEditorVisible = true
                editingHighlight = null
            },
            onDelete = { viewModel.deleteHighlight(highlight.id); editingHighlight = null },
        )
    }
}

@Composable
private fun ContinuousPdfReader(
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    onCenterTap: () -> Unit,
    gestureConfig: ReaderGestureConfig,
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onSizeChanged { viewport = it },
    ) {
        items(count = state.pageCount, key = { it }, contentType = { "pdf-page" }) { page ->
            PdfContinuousPage(page, state, viewModel, viewport, onCenterTap, gestureConfig)
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
    gestureConfig: ReaderGestureConfig,
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
            gestureConfig = gestureConfig,
        )
    }
}

@Composable
private fun HorizontalPdfReader(
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    onCenterTap: () -> Unit,
    gestureConfig: ReaderGestureConfig,
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
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onSizeChanged { viewport = it },
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
                gestureConfig = gestureConfig,
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
    gestureConfig: ReaderGestureConfig,
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
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onSizeChanged { viewport = it },
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
                        gestureConfig = gestureConfig,
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
    gestureConfig: ReaderGestureConfig,
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
    LaunchedEffect(page, viewport, state.zoom, state.rotation, state.fitMode, state.cropMode, state.manualCrop) {
        if (viewport != IntSize.Zero) {
            delay(110)
            viewModel.requestPage(page, viewport.width.coerceAtLeast(320), viewport.height.coerceAtLeast(320))
        }
    }
    val maxX = size.width * (state.zoom - 1f) / 2f
    val maxY = size.height * (state.zoom - 1f) / 2f
    val clamped = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    val pageTransform = state.pageInfo[page]?.takeIf { size != IntSize.Zero }?.let { info ->
        PdfPageTransform(
            pageWidth = info.width,
            pageHeight = info.height,
            crop = info.crop,
            rotation = state.rotation,
            viewportWidth = size.width.toFloat(),
            viewportHeight = size.height.toFloat(),
            fitMode = if (pageMode) PdfFitMode.PAGE else PdfFitMode.WIDTH,
            zoom = state.zoom,
            pan = PdfPoint(clamped.x, clamped.y),
        )
    }
    Box(
        modifier
            .background(Color.White)
            .clipToBounds()
            .onSizeChanged { size = it }
            .readerBrightnessGesture(
                enabled = gestureConfig.allowed && gestureConfig.brightnessEnabled,
                current = gestureConfig.currentBrightness,
                onChange = gestureConfig.onBrightness,
                onFinished = gestureConfig.onBrightnessFinished,
            )
            .readerTapGestures(
                zoom = state.zoom,
                size = size,
                quickActionsEnabled = gestureConfig.allowed,
                themeEnabled = gestureConfig.themeEnabled,
                bookmarkEnabled = gestureConfig.bookmarkEnabled,
                onCenterTap = onCenterTap,
                onZoom = viewModel::setZoom,
                onTheme = gestureConfig.onTheme,
                onBookmark = gestureConfig.onBookmark,
                selectionEnabled = gestureConfig.selectionEnabled,
                onLongPress = { position ->
                    pageTransform?.let { viewModel.selectText(page, it.viewportToPdf(PdfPoint(position.x, position.y))) }
                },
                selectionActive = state.selection != null,
                onClearSelection = viewModel::clearSelection,
                onAnnotationTap = { position ->
                    val info = state.pageInfo[page]
                    val hit = if (pageTransform == null || info == null) null else state.highlights.firstOrNull { entity ->
                        PdfAnnotationCodec.decode(entity.locatorJson)?.takeIf { it.page == page }?.rects?.any { normalized ->
                            val rect = pageTransform.pdfRectToViewport(normalized.fromNormalized(info.width, info.height))
                            position.x in (rect.left - 8f)..(rect.right + 8f) && position.y in (rect.top - 8f)..(rect.bottom + 8f)
                        } == true
                    }
                    hit?.let(gestureConfig.onEditHighlight)
                    hit != null
                },
            )
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
        if (pageTransform != null) {
            PdfAnnotationOverlay(
                page = page,
                pageInfo = state.pageInfo[page],
                highlights = state.highlights,
                notes = state.notes,
                transform = pageTransform,
            )
        }
        if (state.selecting && state.currentPage == page) {
            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
        }
        if (state.selection?.pageIndex == page && pageTransform != null) {
            PdfSelectionOverlay(
                selection = state.selection,
                transform = pageTransform,
                onStart = { viewModel.updateSelection(start = it) },
                onStop = { viewModel.updateSelection(stop = it) },
            )
            PdfSelectionActionBar(
                selection = state.selection,
                transform = pageTransform,
                viewport = size,
                viewModel = viewModel,
                onNote = gestureConfig.onNote,
                onDocumentSearch = gestureConfig.onDocumentSearch,
            )
        }
    }
}

@Composable
private fun PdfAnnotationOverlay(
    page: Int,
    pageInfo: PdfPageInfo?,
    highlights: List<com.example.lectorpdf.data.local.entity.HighlightEntity>,
    notes: List<com.example.lectorpdf.data.local.entity.NoteEntity>,
    transform: PdfPageTransform,
) {
    val info = pageInfo ?: return
    val pageHighlights = highlights.mapNotNull { entity ->
        PdfAnnotationCodec.decode(entity.locatorJson)
            ?.takeIf { it.page == page }
            ?.let { Triple(entity, it, it.rects.map { rect -> transform.pdfRectToViewport(rect.fromNormalized(info.width, info.height)) }) }
    }
    val pageNotes = notes.mapNotNull { entity ->
        PdfAnnotationCodec.decode(entity.locatorJson)
            ?.takeIf { it.page == page }
            ?.let { locator -> locator.rects.firstOrNull()?.let { entity to transform.pdfRectToViewport(it.fromNormalized(info.width, info.height)) } }
    }
    Canvas(Modifier.fillMaxSize()) {
        pageHighlights.forEach { (entity, locator, rects) ->
            val color = Color(entity.colorArgb.toULong())
            rects.forEach { rect ->
                if (locator.style == PdfAnnotationStyle.UNDERLINE) {
                    drawLine(
                        color = color,
                        start = Offset(rect.left, rect.bottom - 1.dp.toPx()),
                        end = Offset(rect.right, rect.bottom - 1.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(),
                    )
                } else {
                    drawRoundRect(
                        color = color.copy(alpha = if (locator.style == PdfAnnotationStyle.QUOTE) .22f else .34f),
                        topLeft = Offset(rect.left, rect.top),
                        size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
        }
        pageNotes.forEach { (_, rect) ->
            drawCircle(
                color = Color(0xFFF2B84B),
                radius = 5.dp.toPx(),
                center = Offset((rect.left - 8.dp.toPx()).coerceAtLeast(5.dp.toPx()), rect.top + 5.dp.toPx()),
            )
        }
    }
}

@Composable
private fun BoxScope.PdfSelectionActionBar(
    selection: PdfTextSelection,
    transform: PdfPageTransform,
    viewport: IntSize,
    viewModel: PdfReaderViewModel,
    onNote: () -> Unit,
    onDocumentSearch: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var highlightMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val selectedBounds = selection.bounds.map(transform::pdfRectToViewport)
    val top = selectedBounds.minOfOrNull(PdfRect::top) ?: 0f
    val bottom = selectedBounds.maxOfOrNull(PdfRect::bottom) ?: 0f
    val barHeight = 58.dp
    val y = with(density) {
        if (top > barHeight.toPx() + 12.dp.toPx()) {
            top - barHeight.toPx() - 8.dp.toPx()
        } else {
            bottom + 8.dp.toPx()
        }.coerceIn(8.dp.toPx(), (viewport.height - barHeight.toPx() - 8.dp.toPx()).coerceAtLeast(8.dp.toPx()))
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = .98f),
        shape = MaterialTheme.shapes.large,
        shadowElevation = 8.dp,
        modifier = Modifier.align(Alignment.TopStart)
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .offset { IntOffset(0, y.roundToInt()) },
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                SelectionAction("Copiar", Icons.Outlined.ContentCopy) {
                    copySelectedText(context, selection.text)
                    viewModel.showNotice("Texto copiado")
                    viewModel.clearSelection()
                }
            }
            item {
                Box {
                    SelectionAction("Resaltar", Icons.Outlined.Highlight) { highlightMenu = true }
                    DropdownMenu(expanded = highlightMenu, onDismissRequest = { highlightMenu = false }) {
                        HIGHLIGHT_COLORS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                leadingIcon = {
                                    Surface(color = Color(option.argb.toULong()), shape = CircleShape, modifier = Modifier.size(22.dp)) { }
                                },
                                onClick = { highlightMenu = false; viewModel.addHighlight(option.argb) },
                            )
                        }
                    }
                }
            }
            item { SelectionAction("Cita", Icons.Outlined.FormatQuote) { viewModel.addHighlight(HIGHLIGHT_QUOTE, PdfAnnotationStyle.QUOTE) } }
            item { SelectionAction("Traducir", Icons.Outlined.Translate) { translateSelectedText(context, selection.text) } }
            item {
                SelectionAction("Diccionario", Icons.AutoMirrored.Outlined.MenuBook) {
                    context.startActivity(ReaderWebActivity.intent(context, selection.text, ReaderWebMode.DICTIONARY))
                }
            }
            item {
                Box {
                    SelectionAction("Más", Icons.Outlined.MoreHoriz) { moreMenu = true }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Compartir") },
                            leadingIcon = { Icon(Icons.Outlined.Share, null) },
                            onClick = { moreMenu = false; shareSelectedText(context, selection.text) },
                        )
                        DropdownMenuItem(
                            text = { Text("Nota") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, null) },
                            onClick = { moreMenu = false; onNote() },
                        )
                        DropdownMenuItem(
                            text = { Text("Subrayar") },
                            leadingIcon = { Icon(Icons.Outlined.FormatUnderlined, null) },
                            onClick = { moreMenu = false; viewModel.addHighlight(HIGHLIGHT_BLUE, PdfAnnotationStyle.UNDERLINE) },
                        )
                        DropdownMenuItem(
                            text = { Text("Buscar en documento") },
                            leadingIcon = { Icon(Icons.Outlined.FindInPage, null) },
                            onClick = { moreMenu = false; onDocumentSearch(selection.text) },
                        )
                        DropdownMenuItem(
                            text = { Text("Búsqueda web") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            onClick = {
                                moreMenu = false
                                context.startActivity(ReaderWebActivity.intent(context, selection.text, ReaderWebMode.SEARCH))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Cancelar selección") },
                            onClick = { moreMenu = false; viewModel.clearSelection() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, label, Modifier.size(19.dp))
        Spacer(Modifier.width(5.dp))
        Text(label)
    }
}

private data class HighlightColorOption(val name: String, val argb: Long)

private val HIGHLIGHT_COLORS = listOf(
    HighlightColorOption("Amarillo", 0xFFFFD54FL),
    HighlightColorOption("Verde", 0xFF66BB6AL),
    HighlightColorOption("Azul", 0xFF42A5F5L),
    HighlightColorOption("Rosa", 0xFFEC6FA4L),
    HighlightColorOption("Morado", 0xFFAB70D6L),
)
private const val HIGHLIGHT_BLUE = 0xFF42A5F5L
private const val HIGHLIGHT_QUOTE = 0xFFF2B84BL

private fun copySelectedText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("Texto seleccionado", text))
}

private fun shareSelectedText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Compartir texto"))
}

private fun translateSelectedText(context: Context, text: String) {
    val process = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }
    if (context.packageManager.queryIntentActivities(process, 0).isNotEmpty()) {
        context.startActivity(Intent.createChooser(process, "Traducir texto"))
    } else {
        context.startActivity(ReaderWebActivity.intent(context, text, ReaderWebMode.TRANSLATE))
    }
}

@Composable
private fun HighlightEditorDialog(
    highlight: com.example.lectorpdf.data.local.entity.HighlightEntity,
    onDismiss: () -> Unit,
    onUpdate: (Long, PdfAnnotationStyle) -> Unit,
    onNote: () -> Unit,
    onDelete: () -> Unit,
) {
    val current = PdfAnnotationCodec.decode(highlight.locatorJson)
    var color by remember(highlight.id) { mutableLongStateOf(highlight.colorArgb) }
    var style by remember(highlight.id) { mutableStateOf(current?.style ?: PdfAnnotationStyle.HIGHLIGHT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar anotación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(highlight.selectedText, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text("Color", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(count = HIGHLIGHT_COLORS.size) { index ->
                        val option = HIGHLIGHT_COLORS[index]
                        IconButton(onClick = { color = option.argb }) {
                            Surface(
                                color = Color(option.argb.toULong()),
                                shape = CircleShape,
                                border = if (color == option.argb) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                                modifier = Modifier.size(34.dp).semantics { contentDescription = option.name },
                            ) { }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(style != PdfAnnotationStyle.UNDERLINE, { style = PdfAnnotationStyle.HIGHLIGHT }, { Text("Resaltado") })
                    FilterChip(style == PdfAnnotationStyle.UNDERLINE, { style = PdfAnnotationStyle.UNDERLINE }, { Text("Subrayado") })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onNote) { Text("Añadir nota") }
                    TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { Button(onClick = { onUpdate(color, style) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun BoxScope.PdfSelectionOverlay(
    selection: PdfTextSelection,
    transform: PdfPageTransform,
    onStart: (PdfPoint) -> Unit,
    onStop: (PdfPoint) -> Unit,
) {
    val viewportRects = selection.bounds.map(transform::pdfRectToViewport)
    Canvas(Modifier.fillMaxSize()) {
        viewportRects.forEach { rect ->
            drawRoundRect(
                color = Color(0xFF5BA9FF).copy(alpha = .34f),
                topLeft = Offset(rect.left, rect.top),
                size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
    viewportRects.firstOrNull()?.let { rect ->
        SelectionHandle(
            position = PdfPoint(rect.left, rect.bottom),
            transform = transform,
            contentDescription = "Inicio de selección",
            onMove = onStart,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
    viewportRects.lastOrNull()?.let { rect ->
        SelectionHandle(
            position = PdfPoint(rect.right, rect.bottom),
            transform = transform,
            contentDescription = "Fin de selección",
            onMove = onStop,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun SelectionHandle(
    position: PdfPoint,
    transform: PdfPageTransform,
    contentDescription: String,
    onMove: (PdfPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val handleSize = 28.dp
    var dragPosition by remember(position) { mutableStateOf(position) }
    Box(
        modifier
            .offset {
                IntOffset(
                    (dragPosition.x - handleSize.toPx() / 2f).roundToInt(),
                    (dragPosition.y - 3.dp.toPx()).roundToInt(),
                )
            }
            .size(handleSize)
            .background(Color(0xFF2F80ED), CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(transform) {
                detectDragGestures(
                    onDrag = { change, amount ->
                        change.consume()
                        dragPosition = PdfPoint(dragPosition.x + amount.x, dragPosition.y + amount.y)
                        onMove(transform.viewportToPdf(dragPosition))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) { }
}

private data class ReaderGestureConfig(
    val allowed: Boolean,
    val brightnessEnabled: Boolean,
    val themeEnabled: Boolean,
    val bookmarkEnabled: Boolean,
    val selectionEnabled: Boolean,
    val currentBrightness: () -> Float,
    val onBrightness: (Float) -> Unit,
    val onBrightnessFinished: () -> Unit,
    val onTheme: () -> Unit,
    val onBookmark: () -> Unit,
    val onNote: () -> Unit,
    val onDocumentSearch: (String) -> Unit,
    val onEditHighlight: (com.example.lectorpdf.data.local.entity.HighlightEntity) -> Unit,
)

private fun Modifier.readerTapGestures(
    zoom: Float,
    size: IntSize,
    quickActionsEnabled: Boolean,
    themeEnabled: Boolean,
    bookmarkEnabled: Boolean,
    onCenterTap: () -> Unit,
    onZoom: (Float) -> Unit,
    onTheme: () -> Unit,
    onBookmark: () -> Unit,
    selectionEnabled: Boolean,
    onLongPress: (Offset) -> Unit,
    selectionActive: Boolean,
    onClearSelection: () -> Unit,
    onAnnotationTap: (Offset) -> Boolean,
): Modifier = pointerInput(zoom, size, quickActionsEnabled, themeEnabled, bookmarkEnabled, selectionEnabled, selectionActive) {
    detectTapGestures(
        onTap = { position ->
            val edgeInset = 12.dp.toPx()
            val cornerSize = 64.dp.toPx().coerceAtMost(size.width * .22f)
            when (pdfQuickTapAction(
                x = position.x,
                y = position.y,
                width = size.width.toFloat(),
                height = size.height.toFloat(),
                edgeInset = edgeInset,
                cornerSize = cornerSize,
                quickActionsEnabled = quickActionsEnabled,
                themeEnabled = themeEnabled,
                bookmarkEnabled = bookmarkEnabled,
            )) {
                PdfQuickTapAction.THEME -> onTheme()
                PdfQuickTapAction.BOOKMARK -> onBookmark()
                PdfQuickTapAction.CENTER -> when {
                    onAnnotationTap(position) -> Unit
                    selectionActive -> onClearSelection()
                    else -> onCenterTap()
                }
                PdfQuickTapAction.NONE -> when {
                    onAnnotationTap(position) -> Unit
                    selectionActive -> onClearSelection()
                }
            }
        },
        onDoubleTap = { onZoom(if (zoom > 1.05f) 1f else 2f) },
        onLongPress = { if (selectionEnabled) onLongPress(it) },
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
    onAnnotations: () -> Unit,
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
                        text = { Text("Notas y resaltados (${state.highlights.size + state.notes.size})") },
                        leadingIcon = { Icon(Icons.Outlined.Highlight, null) },
                        onClick = { menuExpanded = false; onAnnotations() },
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
    brightnessGestureEnabled: Boolean,
    themeCornerGestureEnabled: Boolean,
    bookmarkCornerGestureEnabled: Boolean,
    showBookmarkInFocus: Boolean,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetVolumeButtons: (Boolean) -> Unit,
    onSetBrightnessGesture: (Boolean) -> Unit,
    onSetThemeCornerGesture: (Boolean) -> Unit,
    onSetBookmarkCornerGesture: (Boolean) -> Unit,
    onSetShowBookmarkInFocus: (Boolean) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onOpenThumbnails: () -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.84f),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Opciones de lectura", style = MaterialTheme.typography.titleLarge) }
            item { Text("Visualización", style = MaterialTheme.typography.titleMedium) }
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
                        FilterChip(state.fitMode == fit, { viewModel.setFitMode(fit) }, { Text(fit.label()) })
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(count = PdfCropMode.entries.size) { index ->
                        val mode = PdfCropMode.entries[index]
                        FilterChip(
                            selected = state.cropMode == mode,
                            onClick = { viewModel.setCropMode(mode) },
                            label = { Text(mode.label()) },
                        )
                    }
                }
            }
            if (state.cropMode == PdfCropMode.MANUAL) {
                item { Text("Recorte manual", style = MaterialTheme.typography.titleSmall) }
                item { CropSlider("Izquierdo", state.manualCrop.left) { viewModel.setManualCrop(state.manualCrop.copy(left = it)) } }
                item { CropSlider("Derecho", state.manualCrop.right) { viewModel.setManualCrop(state.manualCrop.copy(right = it)) } }
                item { CropSlider("Superior", state.manualCrop.top) { viewModel.setManualCrop(state.manualCrop.copy(top = it)) } }
                item { CropSlider("Inferior", state.manualCrop.bottom) { viewModel.setManualCrop(state.manualCrop.copy(bottom = it)) } }
                if (!state.manualCrop.isEmpty) {
                    item { TextButton(onClick = { viewModel.setManualCrop(PdfCropInsets()) }) { Text("Restablecer recorte manual") } }
                }
            }
            item {
                OutlinedButton(onClick = viewModel::rotate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.RotateRight, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Rotar 90°")
                }
            }
            item { Text("Pantalla", style = MaterialTheme.typography.titleMedium) }
            item { Text("Orientación", style = MaterialTheme.typography.titleSmall) }
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
            item { SettingToggle("Brillo del sistema", "Usa el brillo normal del dispositivo.", state.brightness < 0f) { viewModel.setBrightnessAndPersist(if (it) -1f else .5f) } }
            if (state.brightness >= 0f) item { Slider(state.brightness, viewModel::setBrightnessAndPersist, valueRange = .05f..1f) }
            item { HorizontalDivider() }
            item { Text("Herramientas", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenSearch, enabled = state.searchSupported, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.FindInPage, "Buscar")
                        Spacer(Modifier.width(6.dp))
                        Text("Buscar")
                    }
                    OutlinedButton(onClick = onOpenBookmarks, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Bookmark, "Marcadores")
                        Spacer(Modifier.width(6.dp))
                        Text("Marcadores")
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenAnnotations, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Highlight, "Notas y resaltados")
                        Spacer(Modifier.width(6.dp))
                        Text("Anotaciones")
                    }
                    OutlinedButton(onClick = onOpenThumbnails, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.GridView, "Miniaturas")
                        Spacer(Modifier.width(6.dp))
                        Text("Miniaturas")
                    }
                }
            }
            item { Text("Text To Speech", style = MaterialTheme.typography.titleSmall) }
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
            item { HorizontalDivider() }
            item { Text("Gestos", style = MaterialTheme.typography.titleMedium) }
            item { SettingToggle("Brillo en borde izquierdo", "Arrastra verticalmente desde la franja segura.", brightnessGestureEnabled, onSetBrightnessGesture) }
            item { SettingToggle("Tema en esquina izquierda", "Alterna Día, Sepia, Noche y Consola.", themeCornerGestureEnabled, onSetThemeCornerGesture) }
            item { SettingToggle("Marcador en esquina derecha", "Añade o quita el marcador de la posición.", bookmarkCornerGestureEnabled, onSetBookmarkCornerGesture) }
            item { SettingToggle("Marcador visible en enfoque", "Muestra el indicador incluso con la interfaz oculta.", showBookmarkInFocus, onSetShowBookmarkInFocus) }
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
private fun AnnotationSheet(
    state: PdfReaderUiState,
    viewModel: PdfReaderViewModel,
    onDismiss: () -> Unit,
    onPage: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxHeight(.74f).padding(horizontal = 18.dp)) {
            Text("Notas y resaltados", style = MaterialTheme.typography.titleLarge)
            if (state.highlights.isEmpty() && state.notes.isEmpty()) {
                Text(
                    "Mantén pulsado texto para crear resaltados, subrayados, citas o notas.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(count = state.highlights.size, key = { "highlight-${state.highlights[it].id}" }) { index ->
                        val highlight = state.highlights[index]
                        val locator = PdfAnnotationCodec.decode(highlight.locatorJson)
                        Surface(onClick = { onPage(highlight.locatorJson) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(highlight.colorArgb.toULong()),
                                    shape = CircleShape,
                                    modifier = Modifier.size(14.dp),
                                ) { }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(
                                        when (locator?.style) {
                                            PdfAnnotationStyle.UNDERLINE -> "Subrayado"
                                            PdfAnnotationStyle.QUOTE -> "Cita"
                                            else -> "Resaltado"
                                        } + " · página ${(locator?.page ?: 0) + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(highlight.selectedText, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { viewModel.deleteHighlight(highlight.id) }) {
                                    Icon(Icons.Outlined.DeleteOutline, "Eliminar anotación")
                                }
                            }
                        }
                    }
                    items(count = state.notes.size, key = { "note-${state.notes[it].id}" }) { index ->
                        val note = state.notes[index]
                        val locator = PdfAnnotationCodec.decode(note.locatorJson)
                        Surface(onClick = { onPage(note.locatorJson) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Outlined.NoteAdd, "Nota")
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text("Nota · página ${(locator?.page ?: 0) + 1}", style = MaterialTheme.typography.labelLarge)
                                    Text(note.content, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                    Icon(Icons.Outlined.DeleteOutline, "Eliminar nota")
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

private fun Modifier.readerBrightnessGesture(
    enabled: Boolean,
    current: () -> Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val edgeInset = 12.dp.toPx()
            val stripEnd = edgeInset + 36.dp.toPx()
            if (down.position.x !in edgeInset..stripEnd) return@awaitEachGesture
            val initial = current()
            var value = if (initial < 0f) .5f else initial
            var totalX = 0f
            var totalY = 0f
            var active = false
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } > 1) {
                    cancelled = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                if (!active && abs(totalY) > viewConfiguration.touchSlop && abs(totalY) > abs(totalX) * 1.25f) {
                    active = true
                }
                if (active && delta.y != 0f) {
                    value = (value - delta.y / size.height.coerceAtLeast(1)).coerceIn(.05f, 1f)
                    change.consume()
                    onChange(value)
                }
            }
            if (active && !cancelled) onFinished()
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

private fun PdfFitMode.label(): String = when (this) {
    PdfFitMode.PAGE -> "Página completa"
    PdfFitMode.WIDTH -> "Ancho"
    PdfFitMode.CONTENT -> "Contenido"
}

private fun PdfCropMode.label(): String = when (this) {
    PdfCropMode.NONE -> "Sin recorte"
    PdfCropMode.AUTOMATIC -> "Automático"
    PdfCropMode.MANUAL -> "Manual"
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
