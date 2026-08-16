package com.example.lectorpdf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lectorpdf.domain.model.AppTheme
import com.example.lectorpdf.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text("Ajustes", style = MaterialTheme.typography.headlineLarge)
            Text("Apariencia y comportamiento general", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SectionTitle("Apariencia") }
        item {
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AppTheme.entries) { theme ->
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { viewModel.setTheme(theme) },
                        label = { Text(theme.label()) },
                    )
                }
            }
        }
        item { SettingSwitch(Icons.Outlined.ColorLens, "Color dinámico", "Usa los colores del sistema cuando estén disponibles.", settings.dynamicColor, viewModel::setDynamicColor) }
        item { SettingSwitch(Icons.Outlined.Animation, "Animaciones", "Transiciones suaves en la interfaz.", settings.animationsEnabled, viewModel::setAnimations) }
        item { HorizontalDivider(Modifier.padding(vertical = 10.dp)) }
        item { SectionTitle("Lectura") }
        item { SettingSwitch(Icons.Outlined.ScreenLockPortrait, "Mantener pantalla activa", "Evita que la pantalla se apague mientras lees.", settings.keepScreenOn, viewModel::setKeepScreenOn) }
        item { SettingSwitch(Icons.AutoMirrored.Outlined.VolumeUp, "Botones de volumen", "Permite usarlos para avanzar o retroceder páginas.", settings.volumeButtonsTurnPages, viewModel::setVolumeButtons) }
        item {
            Column(Modifier.padding(vertical = 18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Contrast, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Preferencias del lector", style = MaterialTheme.typography.titleMedium)
                        Text("El lector PDF ya está disponible. Las preferencias de EPUB se habilitarán junto con su lector.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable private fun SectionTitle(value: String) { Text(value, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) }

private fun AppTheme.label() = when (this) {
    AppTheme.SYSTEM -> "Sistema"
    AppTheme.LIGHT -> "Claro"
    AppTheme.DARK -> "Oscuro"
    AppTheme.AMOLED -> "AMOLED"
}
