package com.example.lectorpdf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lectorpdf.ui.viewmodel.StatsViewModel

@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Estadísticas", style = MaterialTheme.typography.headlineLarge)
        Text("Tiempo de lectura registrado en este dispositivo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatCard("Hoy", formatDuration(summary.todayMillis), Icons.Outlined.Schedule)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallStat("Esta semana", formatDuration(summary.weekMillis), Modifier.weight(1f))
            SmallStat("Este mes", formatDuration(summary.monthMillis), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallStat("Leyendo", summary.booksStarted.toString(), Modifier.weight(1f), Icons.Outlined.AutoStories)
            SmallStat("Terminados", summary.booksFinished.toString(), Modifier.weight(1f), Icons.Outlined.CheckCircle)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Las sesiones cuentan cuando lees", style = MaterialTheme.typography.titleMedium)
                Text("El lector descartará aperturas accidentales de pocos segundos para mantener estas cifras útiles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.displaySmall) }
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Card(modifier) {
        Column(Modifier.padding(18.dp)) {
            if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}
