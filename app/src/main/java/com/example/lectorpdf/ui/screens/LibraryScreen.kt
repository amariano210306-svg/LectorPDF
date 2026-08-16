package com.example.lectorpdf.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lectorpdf.data.local.dao.LibraryFolderRow
import com.example.lectorpdf.data.local.dao.LibrarySourceRow
import com.example.lectorpdf.domain.model.LibraryFilter
import com.example.lectorpdf.domain.model.LibrarySort
import com.example.lectorpdf.domain.model.LibraryViewMode
import com.example.lectorpdf.ui.components.BookGridCard
import com.example.lectorpdf.ui.components.BookListRow
import com.example.lectorpdf.ui.components.EmptyState
import com.example.lectorpdf.ui.viewmodel.LibrarySection
import com.example.lectorpdf.ui.viewmodel.LibraryUiState
import com.example.lectorpdf.ui.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBookClick: (Long) -> Unit,
    onImport: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.section == LibrarySection.FOLDERS && state.currentFolderId != null) { viewModel.navigateUp() }

    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.section == LibrarySection.FOLDERS && state.currentFolderId != null) {
                IconButton(onClick = { viewModel.navigateUp() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Subir un nivel") }
            }
            Text("Biblioteca", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::toggleViewMode) {
                Icon(
                    if (state.viewMode == LibraryViewMode.GRID) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                    if (state.viewMode == LibraryViewMode.GRID) "Cambiar a lista" else "Cambiar a cuadrícula",
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.section == LibrarySection.FOLDERS,
                onClick = { viewModel.setSection(LibrarySection.FOLDERS) },
                label = { Text("Carpetas") },
                leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
            )
            FilterChip(
                selected = state.section == LibrarySection.ALL_BOOKS,
                onClick = { viewModel.setSection(LibrarySection.ALL_BOOKS) },
                label = { Text("Todos los libros") },
            )
        }
        if (state.section == LibrarySection.FOLDERS) {
            FolderLibrary(state, viewModel, onBookClick, onImport)
        } else {
            AllBooksLibrary(state, viewModel, onBookClick, onImport)
        }
    }
}

@Composable
private fun FolderLibrary(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    onBookClick: (Long) -> Unit,
    onImport: () -> Unit,
) {
    if (state.currentFolderId == null) {
        OutlinedTextField(
            value = state.search,
            onValueChange = viewModel::setSearch,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text("Buscar fuente o carpeta") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        )
        val sources = state.sources.filter { state.search.isBlank() || it.displayName.contains(state.search, ignoreCase = true) }
        if (sources.isEmpty()) {
            EmptyState(
                title = "Sin carpetas autorizadas",
                message = "Añade una carpeta desde Archivos para conservar su organización real.",
                modifier = Modifier.fillMaxSize(),
                action = { Button(onClick = onImport) { Text("Añadir carpeta") } },
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sources, key = { "source-${it.id}" }) { source -> SourceCard(source) { viewModel.openSource(source) } }
            }
        }
        return
    }

    Breadcrumb(state, viewModel)
    OutlinedTextField(
        value = state.search,
        onValueChange = viewModel::setSearch,
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        placeholder = { Text(if (state.includeSubfolders) "Buscar en esta carpeta y subcarpetas" else "Buscar en esta carpeta") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = viewModel::toggleIncludeSubfolders,
            label = { Text(if (state.includeSubfolders) "Incluyendo subcarpetas" else "Solo esta carpeta") },
        )
        Spacer(Modifier.weight(1f))
        SortMenu(state.sort, viewModel::setSort)
    }
    val folders = state.folders.filter {
        (it.totalBookCount > 0 || it.subfolderCount > 0) && (state.search.isBlank() || it.displayName.contains(state.search, true))
    }
    if (state.viewMode == LibraryViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("Carpetas", style = MaterialTheme.typography.titleMedium) }
                gridItems(folders, key = { "folder-${it.id}" }) { folder -> FolderCard(folder) { viewModel.openFolder(folder.id) } }
            }
            if (state.books.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("Libros", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
                gridItems(state.books, key = { "book-${it.id}" }) { book ->
                    BookGridCard(book, { onBookClick(book.id) }, { viewModel.setFavorite(book, it) })
                }
            }
            if (folders.isEmpty() && state.books.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyFolder() }
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            items(folders, key = { "folder-${it.id}" }) { folder -> FolderListRow(folder) { viewModel.openFolder(folder.id) } }
            items(state.books, key = { "book-${it.id}" }) { book ->
                BookListRow(book, { onBookClick(book.id) }, { viewModel.setFavorite(book, it) })
            }
            if (folders.isEmpty() && state.books.isEmpty()) item { EmptyFolder() }
        }
    }
}

@Composable
private fun Breadcrumb(state: LibraryUiState, viewModel: LibraryViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            Text("Biblioteca", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { viewModel.showFolderSources() }.padding(6.dp))
            Text("›", color = MaterialTheme.colorScheme.outline)
        }
        items(state.breadcrumb, key = { it.id }) { folder ->
            Text(
                folder.displayName,
                color = if (folder.id == state.currentFolderId) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                fontWeight = if (folder.id == state.currentFolderId) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { viewModel.openFolder(folder.id) }.padding(6.dp),
            )
            if (folder.id != state.currentFolderId) Text("›", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SourceCard(source: LibrarySourceRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Outlined.FolderOpen, null, Modifier.padding(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(source.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${source.totalBooks} libros", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FolderCard(folder: LibraryFolderRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(folder.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${folder.subfolderCount} carpetas · ${folder.totalBookCount} libros", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FolderListRow(folder: LibraryFolderRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(folder.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${folder.subfolderCount} carpetas · ${folder.totalBookCount} libros", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AllBooksLibrary(state: LibraryUiState, viewModel: LibraryViewModel, onBookClick: (Long) -> Unit, onImport: () -> Unit) {
    OutlinedTextField(
        value = state.search,
        onValueChange = viewModel::setSearch,
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        placeholder = { Text("Título, autor o archivo") },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
    )
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(LibraryFilter.entries) { filter ->
            FilterChip(selected = state.filter == filter, onClick = { viewModel.setFilter(filter) }, label = { Text(filter.label()) })
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${state.books.size} libros", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        SortMenu(state.sort, viewModel::setSort)
    }
    if (state.books.isEmpty()) {
        EmptyState(
            title = if (state.search.isBlank() && state.filter == LibraryFilter.ALL) "Tu biblioteca está lista" else "Sin coincidencias",
            message = if (state.search.isBlank() && state.filter == LibraryFilter.ALL) "Añade archivos PDF o EPUB desde el almacenamiento del dispositivo." else "Prueba otra búsqueda o cambia los filtros.",
            modifier = Modifier.fillMaxSize(),
            action = if (state.search.isBlank() && state.filter == LibraryFilter.ALL) ({ Button(onClick = onImport) { Text("Seleccionar libros") } }) else null,
        )
    } else if (state.viewMode == LibraryViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            gridItems(state.books, key = { it.id }) { book -> BookGridCard(book, { onBookClick(book.id) }, { viewModel.setFavorite(book, it) }) }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            items(state.books, key = { it.id }) { book -> BookListRow(book, { onBookClick(book.id) }, { viewModel.setFavorite(book, it) }) }
        }
    }
}

@Composable private fun EmptyFolder() = Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
    Text("Esta carpeta no contiene libros disponibles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SortMenu(current: LibrarySort, onSort: (LibrarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { expanded = true }, label = { Text(current.label()) }, trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { sort ->
                DropdownMenuItem(text = { Text(sort.label()) }, onClick = { expanded = false; onSort(sort) })
            }
        }
    }
}

private fun LibraryFilter.label() = when (this) {
    LibraryFilter.ALL -> "Todos"
    LibraryFilter.PDF -> "PDF"
    LibraryFilter.EPUB -> "EPUB"
    LibraryFilter.READING -> "Leyendo"
    LibraryFilter.UNREAD -> "Sin empezar"
    LibraryFilter.FINISHED -> "Terminados"
    LibraryFilter.FAVORITES -> "Favoritos"
}

private fun LibrarySort.label() = when (this) {
    LibrarySort.TITLE -> "Título"
    LibrarySort.AUTHOR -> "Autor"
    LibrarySort.DATE_ADDED -> "Añadidos"
    LibrarySort.LAST_OPENED -> "Última apertura"
    LibrarySort.SIZE -> "Tamaño"
    LibrarySort.PROGRESS -> "Progreso"
}
