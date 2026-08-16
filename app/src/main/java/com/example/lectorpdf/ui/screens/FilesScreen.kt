package com.example.lectorpdf.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.lectorpdf.ui.viewmodel.FilesViewModel

@Composable
fun FilesScreen(viewModel: FilesViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris)
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.scanDevice()
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.addFolder(it)
        }
    }
    val startDeviceScan = {
        if (Build.VERSION.SDK_INT <= 32 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            viewModel.scanDevice()
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Añadir libros", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth())
        Text(
            "Elige archivos PDF y EPUB desde el dispositivo o un proveedor conectado.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(42.dp))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.FileOpen, null, Modifier.padding(8.dp))
                Text("Tu biblioteca, bajo tu control", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "La app conserva el acceso local al archivo seleccionado. Tus documentos no se envían a servidores.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f),
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { picker.launch(arrayOf("application/pdf", "application/epub+zip")) },
                    enabled = !state.importing && !state.scanning,
                ) {
                    if (state.importing) {
                        CircularProgressIndicator(Modifier.padding(end = 10.dp), strokeWidth = 2.dp)
                        Text("Importando…")
                    } else {
                        Text("Seleccionar PDF o EPUB")
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = startDeviceScan,
                    enabled = !state.importing && !state.scanning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Search, null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Escanear dispositivo")
                }
                OutlinedButton(
                    onClick = { folderPicker.launch(null) },
                    enabled = !state.importing && !state.scanning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.FolderOpen, null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Añadir carpeta autorizada")
                }
            }
        }
        if (state.scanning) {
            Spacer(Modifier.height(22.dp))
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Actualizando biblioteca", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    Text(
                        "${state.progress?.source.orEmpty()} · ${state.progress?.inspected ?: 0} elementos revisados · ${state.progress?.discovered ?: 0} documentos",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        state.result?.let { result ->
            Spacer(Modifier.height(22.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Importación completada", style = MaterialTheme.typography.titleMedium)
                    Text("${result.imported} añadidos · ${result.duplicates} ya existentes · ${result.rejected} no compatibles")
                    result.messages.take(3).forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        state.scanResult?.let { result ->
            Spacer(Modifier.height(22.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Biblioteca actualizada", style = MaterialTheme.typography.titleMedium)
                    Text("${result.imported} nuevos · ${result.updated} actualizados · ${result.unavailable} no disponibles")
                    Text("${result.inspected} elementos revisados", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    result.messages.take(3).forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PrivacyPoint(Icons.Outlined.CloudOff, "Sin nube")
            PrivacyPoint(Icons.Outlined.Lock, "Acceso privado")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PrivacyPoint(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
