package com.atuy.note.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class TabLayoutMode {
    HORIZONTAL,
    VERTICAL,
}

enum class ToolbarDock {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
}

@Stable
class UiPreferencesState internal constructor(
    private val preferences: SharedPreferences,
) {
    var themeMode by mutableStateOf(enumPreference(KEY_THEME, ThemeMode.SYSTEM))
        private set

    var tabLayoutMode by mutableStateOf(
        enumPreference(KEY_TAB_LAYOUT, TabLayoutMode.HORIZONTAL),
    )
        private set

    var toolbarDock by mutableStateOf(enumPreference(KEY_TOOLBAR_DOCK, ToolbarDock.TOP))
        private set

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        preferences.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun updateTabLayoutMode(mode: TabLayoutMode) {
        tabLayoutMode = mode
        preferences.edit().putString(KEY_TAB_LAYOUT, mode.name).apply()
    }

    fun updateToolbarDock(dock: ToolbarDock) {
        toolbarDock = dock
        preferences.edit().putString(KEY_TOOLBAR_DOCK, dock.name).apply()
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T {
        val stored = preferences.getString(key, null) ?: return fallback
        return enumValues<T>().firstOrNull { it.name == stored } ?: fallback
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_TAB_LAYOUT = "tab_layout"
        const val KEY_TOOLBAR_DOCK = "toolbar_dock"
    }
}

@Composable
fun rememberUiPreferencesState(): UiPreferencesState {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        UiPreferencesState(
            context.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE),
        )
    }
}
