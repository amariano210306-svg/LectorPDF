package com.example.lectorpdf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lectorpdf.domain.model.BookFormat
import com.example.lectorpdf.domain.model.LibraryBook
import com.example.lectorpdf.data.cover.PdfCoverCache
import java.text.DateFormat
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun BookCover(book: LibraryBook, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coverCache = remember(context) { PdfCoverCache(context) }
    val pdfCover by produceState<android.graphics.Bitmap?>(initialValue = null, book.uri, book.lastModified, book.sizeBytes) {
        value = if (book.format == BookFormat.PDF) runCatching { coverCache.loadOrCreate(book) }.getOrNull() else null
    }
    Box(
        modifier = modifier
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        pdfCover?.let { bitmap ->
            Image(bitmap.asImageBitmap(), "Portada de ${book.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } ?: Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(book.format.name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(book.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text(book.author ?: "Documento local", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BookGridCard(
    book: LibraryBook,
    onClick: () -> Unit,
    onFavorite: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(onClick = onClick)) {
        Box {
            BookCover(book, Modifier.fillMaxWidth())
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .9f),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) {
                IconButton(onClick = { onFavorite(!book.isFavorite) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (book.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (book.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                        tint = if (book.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(book.author ?: book.fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { book.progress },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
        )
        Text("${(book.progress * 100).toInt()}% · ${formatFileSize(book.sizeBytes)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BookListRow(
    book: LibraryBook,
    onClick: () -> Unit,
    onFavorite: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book, Modifier.size(width = 72.dp, height = 106.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.author ?: "Autor no disponible", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.fillMaxWidth().height(3.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                "${book.format.name} · ${formatFileSize(book.sizeBytes)} · ${(book.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onFavorite(!book.isFavorite) }) {
            Icon(
                if (book.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                if (book.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                tint = if (book.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(20.dp)); action() }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "Tamaño desconocido"
    val units = listOf("B", "KB", "MB", "GB")
    val group = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    return String.format(if (group == 0) "%.0f %s" else "%.1f %s", bytes / 1024.0.pow(group), units[group])
}

fun formatDate(timestamp: Long?): String = timestamp?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "Nunca"
