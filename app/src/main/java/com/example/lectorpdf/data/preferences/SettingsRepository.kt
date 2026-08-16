package com.example.lectorpdf.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.lectorpdf.domain.model.AppTheme
import com.example.lectorpdf.domain.model.LibrarySort
import com.example.lectorpdf.domain.model.LibraryViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "reader_settings")

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val theme: AppTheme = AppTheme.SYSTEM,
    val dynamicColor: Boolean = true,
    val animationsEnabled: Boolean = true,
    val libraryViewMode: LibraryViewMode = LibraryViewMode.GRID,
    val librarySort: LibrarySort = LibrarySort.DATE_ADDED,
    val keepScreenOn: Boolean = true,
    val volumeButtonsTurnPages: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val animationsEnabled = booleanPreferencesKey("animations_enabled")
        val libraryViewMode = stringPreferencesKey("library_view_mode")
        val librarySort = stringPreferencesKey("library_sort")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val volumeButtons = booleanPreferencesKey("volume_buttons_turn_pages")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { preferences ->
            AppSettings(
                onboardingCompleted = preferences[Keys.onboardingCompleted] ?: false,
                theme = preferences[Keys.theme].toEnumOrDefault(AppTheme.SYSTEM),
                dynamicColor = preferences[Keys.dynamicColor] ?: true,
                animationsEnabled = preferences[Keys.animationsEnabled] ?: true,
                libraryViewMode = preferences[Keys.libraryViewMode].toEnumOrDefault(LibraryViewMode.GRID),
                librarySort = preferences[Keys.librarySort].toEnumOrDefault(LibrarySort.DATE_ADDED),
                keepScreenOn = preferences[Keys.keepScreenOn] ?: true,
                volumeButtonsTurnPages = preferences[Keys.volumeButtons] ?: false,
            )
        }

    suspend fun completeOnboarding() = set(Keys.onboardingCompleted, true)
    suspend fun setTheme(value: AppTheme) = set(Keys.theme, value.name)
    suspend fun setDynamicColor(value: Boolean) = set(Keys.dynamicColor, value)
    suspend fun setAnimationsEnabled(value: Boolean) = set(Keys.animationsEnabled, value)
    suspend fun setLibraryViewMode(value: LibraryViewMode) = set(Keys.libraryViewMode, value.name)
    suspend fun setLibrarySort(value: LibrarySort) = set(Keys.librarySort, value.name)
    suspend fun setKeepScreenOn(value: Boolean) = set(Keys.keepScreenOn, value)
    suspend fun setVolumeButtons(value: Boolean) = set(Keys.volumeButtons, value)

    private suspend fun <T> set(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
