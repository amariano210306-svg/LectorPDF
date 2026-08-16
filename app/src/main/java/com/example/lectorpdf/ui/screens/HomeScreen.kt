package com.example.lectorpdf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lectorpdf.ui.components.BookCover
import com.example.lectorpdf.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onBookClick: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onImport: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 24.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Text("Tu espacio de lectura", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (state.totalBooks == 0) "Importa tu primer libro y empieza a leer sin conexión." else "${state.totalBooks} libros disponibles, siempre contigo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.continueReading?.let { book ->
            item {
                Text("Continuar leyendo", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Card(
                    onClick = { onBookClick(book.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        BookCover(book, Modifier.size(width = 88.dp, height = 130.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(book.author ?: "Documento local", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f))
                            Text("${(book.progress * 100).toInt()}% completado", fontWeight = FontWeight.SemiBold)
                            Button(onClick = { onBookClick(book.id) }) { Text("Ver libro") }
                        }
                    }
                }
            }
        }

        item {
            Text("Accesos rápidos", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Importar", Icons.Outlined.FolderOpen, onImport, Modifier.weight(1f))
                QuickAction("Biblioteca", Icons.Outlined.AutoStories, onOpenLibrary, Modifier.weight(1f))
                QuickAction("Buscar", Icons.Outlined.Search, onOpenLibrary, Modifier.weight(1f))
            }
        }

        if (state.recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Recientes", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = onOpenLibrary) { Text("Ver todos") }
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(state.recent, key = { it.id }) { book ->
                        Column(Modifier.size(width = 132.dp, height = 250.dp).clickable { onBookClick(book.id) }) {
                            BookCover(book, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        item {
            Text("Colecciones", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            if (state.collections.isEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CollectionsBookmark, null)
                        Column {
                            Text("Organiza a tu manera", style = MaterialTheme.typography.titleMedium)
                            Text("Las colecciones aparecerán aquí cuando las crees desde la biblioteca.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                state.collections.take(4).forEach { collection ->
                    Text("${collection.name} · ${collection.bookCount} libros", modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
