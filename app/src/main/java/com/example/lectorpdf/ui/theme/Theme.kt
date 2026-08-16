package com.example.lectorpdf.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.lectorpdf.domain.model.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = ForestLight,
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF075044),
    secondary = Color(0xFFD7C4A8),
    tertiary = Color(0xFFFFB4A5),
    background = Night,
    surface = NightSurface,
    surfaceVariant = Color(0xFF26302E),
    outline = NightOutline,
)

private val AmoledColorScheme = DarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF111716),
)

private val LightColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB5F2DF),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF705B3E),
    secondaryContainer = Sand,
    tertiary = Coral,
    background = WarmPaper,
    surface = Color(0xFFFFFCF8),
    surfaceVariant = MintSurface,
    outline = Color(0xFF717A77),
)

@Composable
fun LectorPDFTheme(
    theme: AppTheme = AppTheme.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = theme == AppTheme.DARK || theme == AppTheme.AMOLED || (theme == AppTheme.SYSTEM && systemDark)
    val context = LocalContext.current
    val colorScheme = when {
        theme == AppTheme.AMOLED -> AmoledColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && theme != AppTheme.AMOLED ->
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        useDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
