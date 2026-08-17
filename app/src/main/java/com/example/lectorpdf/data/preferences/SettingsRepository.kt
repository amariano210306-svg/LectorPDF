package com.example.lectorpdf.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    val initialStorageScanCompleted: Boolean = false,
    val scanFolderUris: Set<String> = emptySet(),
    val resumeLastReading: Boolean = true,
    val lastOpenedBookId: Long? = null,
    val pdfTtsRate: Float = 1f,
    val pdfTtsPitch: Float = 1f,
    val pdfReaderBrightness: Float = -1f,
    val pdfBrightnessGesture: Boolean = true,
    val pdfThemeCornerGesture: Boolean = true,
    val pdfBookmarkCornerGesture: Boolean = true,
    val pdfShowBookmarkInFocus: Boolean = false,
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
        val initialStorageScanCompleted = booleanPreferencesKey("initial_storage_scan_completed")
        val scanFolderUris = stringSetPreferencesKey("scan_folder_uris")
        val resumeLastReading = booleanPreferencesKey("resume_last_reading")
        val lastOpenedBookId = longPreferencesKey("last_opened_book_id")
        val pdfTtsRate = floatPreferencesKey("pdf_tts_rate")
        val pdfTtsPitch = floatPreferencesKey("pdf_tts_pitch")
        val pdfReaderBrightness = floatPreferencesKey("pdf_reader_brightness")
        val pdfBrightnessGesture = booleanPreferencesKey("pdf_brightness_gesture")
        val pdfThemeCornerGesture = booleanPreferencesKey("pdf_theme_corner_gesture")
        val pdfBookmarkCornerGesture = booleanPreferencesKey("pdf_bookmark_corner_gesture")
        val pdfShowBookmarkInFocus = booleanPreferencesKey("pdf_show_bookmark_in_focus")
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
                initialStorageScanCompleted = preferences[Keys.initialStorageScanCompleted] ?: false,
                scanFolderUris = preferences[Keys.scanFolderUris] ?: emptySet(),
                resumeLastReading = preferences[Keys.resumeLastReading] ?: true,
                lastOpenedBookId = preferences[Keys.lastOpenedBookId],
                pdfTtsRate = (preferences[Keys.pdfTtsRate] ?: 1f).coerceIn(.5f, 2f),
                pdfTtsPitch = (preferences[Keys.pdfTtsPitch] ?: 1f).coerceIn(.5f, 1.5f),
                pdfReaderBrightness = (preferences[Keys.pdfReaderBrightness] ?: -1f).let { if (it < 0f) -1f else it.coerceIn(.05f, 1f) },
                pdfBrightnessGesture = preferences[Keys.pdfBrightnessGesture] ?: true,
                pdfThemeCornerGesture = preferences[Keys.pdfThemeCornerGesture] ?: true,
                pdfBookmarkCornerGesture = preferences[Keys.pdfBookmarkCornerGesture] ?: true,
                pdfShowBookmarkInFocus = preferences[Keys.pdfShowBookmarkInFocus] ?: false,
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
    suspend fun setInitialStorageScanCompleted(value: Boolean) = set(Keys.initialStorageScanCompleted, value)
    suspend fun addScanFolder(uri: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.scanFolderUris] = (preferences[Keys.scanFolderUris] ?: emptySet()) + uri
        }
    }
    suspend fun setResumeLastReading(value: Boolean) = set(Keys.resumeLastReading, value)
    suspend fun setLastOpenedBook(bookId: Long) = set(Keys.lastOpenedBookId, bookId)
    suspend fun setPdfTtsRate(value: Float) = set(Keys.pdfTtsRate, value.coerceIn(.5f, 2f))
    suspend fun setPdfTtsPitch(value: Float) = set(Keys.pdfTtsPitch, value.coerceIn(.5f, 1.5f))
    suspend fun setPdfReaderBrightness(value: Float) = set(Keys.pdfReaderBrightness, if (value < 0f) -1f else value.coerceIn(.05f, 1f))
    suspend fun setPdfBrightnessGesture(value: Boolean) = set(Keys.pdfBrightnessGesture, value)
    suspend fun setPdfThemeCornerGesture(value: Boolean) = set(Keys.pdfThemeCornerGesture, value)
    suspend fun setPdfBookmarkCornerGesture(value: Boolean) = set(Keys.pdfBookmarkCornerGesture, value)
    suspend fun setPdfShowBookmarkInFocus(value: Boolean) = set(Keys.pdfShowBookmarkInFocus, value)
    suspend fun clearLastOpenedBook() {
        context.settingsDataStore.edit { it.remove(Keys.lastOpenedBookId) }
    }

    private suspend fun <T> set(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
