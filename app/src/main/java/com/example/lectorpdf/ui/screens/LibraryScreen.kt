package com.example.lectorpdf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lectorpdf.domain.model.LibraryFilter
import com.example.lectorpdf.domain.model.LibrarySort
import com.example.lectorpdf.domain.model.LibraryViewMode
import com.example.lectorpdf.ui.components.BookGridCard
import com.example.lectorpdf.ui.components.BookListRow
import com.example.lectorpdf.ui.components.EmptyState
import com.example.lectorpdf.ui.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBookClick: (Long) -> Unit,
    onImport: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Biblioteca", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::toggleViewMode) {
                Icon(
                    if (state.viewMode == LibraryViewMode.GRID) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                    if (state.viewMode == LibraryViewMode.GRID) "Cambiar a lista" else "Cambiar a cuadrícula",
                )
            }
        }
        OutlinedTextField(
            value = state.search,
            onValueChange = viewModel::setSearch,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text("Título, autor o archivo") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listItems(LibraryFilter.entries) { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.label()) },
                )
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                gridItems(state.books, key = { it.id }) { book ->
                    BookGridCard(book, { onBookClick(book.id) }, { viewModel.setFavorite(book, it) })
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 104.dp)) {
                listItems(state.books, key = { it.id }) { book ->
                    BookListRow(book, { onBookClick(book.id) }, { viewModel.setFavorite(book, it) })
                }
            }
        }
    }
}

@Composable
private fun SortMenu(current: LibrarySort, onSort: (LibrarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
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
