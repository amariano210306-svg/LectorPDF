package com.example.lectorpdf.ui.screens

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.example.lectorpdf.ui.components.BookCover
import com.example.lectorpdf.ui.components.formatDate
import com.example.lectorpdf.ui.components.formatFileSize
import com.example.lectorpdf.ui.viewmodel.BookDetailsViewModel
import com.example.lectorpdf.reader.pdf.PdfReaderActivity

@Composable
fun BookDetailsScreen(viewModel: BookDetailsViewModel, onBack: () -> Unit) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    val item = book
    if (item == null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
            Text("El libro ya no está en la biblioteca.", style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.setFavorite(!item.isFavorite) }) {
                Icon(if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (item.isFavorite) "Quitar de favoritos" else "Añadir a favoritos", tint = if (item.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = {
                val uri = item.uri.toUri()
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = if (item.format.name == "PDF") "application/pdf" else "application/epub+zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, item.fileName, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Compartir libro"))
            }) { Icon(Icons.Outlined.Share, "Compartir archivo") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            BookCover(item, Modifier.width(190.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(item.title, style = MaterialTheme.typography.headlineMedium)
        Text(item.author ?: "Autor no disponible", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (item.format.name == "PDF") {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { context.startActivity(Intent(context, PdfReaderActivity::class.java).putExtra(PdfReaderActivity.EXTRA_BOOK_ID, item.id)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Leer PDF") }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { rename = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(8.dp)); Text("Renombrar") }
            OutlinedButton(onClick = { confirmRemove = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Quitar") }
        }
        Spacer(Modifier.height(26.dp))
        Text("Información", style = MaterialTheme.typography.titleLarge)
        DetailRow("Formato", item.format.name)
        DetailRow("Tamaño", formatFileSize(item.sizeBytes))
        DetailRow("Archivo", item.fileName)
        DetailRow("Progreso", "${(item.progress * 100).toInt()}%")
        DetailRow("Añadido", formatDate(item.dateAdded))
        DetailRow("Última lectura", formatDate(item.lastOpenedAt))
        DetailRow("Tiempo leído", formatDuration(item.totalReadingTimeMillis))
        Text("La ubicación exacta del proveedor se conserva de forma privada como un permiso del sistema.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp))
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Quitar de la biblioteca") },
            text = { Text("Se eliminarán el registro y sus datos de lectura locales. El archivo original no se borrará del dispositivo.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; viewModel.remove(onBack) }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } },
        )
    }
    if (rename) {
        var title by remember(item.id) { mutableStateOf(item.title) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Nombre en la biblioteca") },
            text = { OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Título") }) },
            confirmButton = { Button(onClick = { viewModel.rename(title); rename = false }, enabled = title.isNotBlank()) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 18.dp))
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes == 0L) "Sin sesiones" else if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}
