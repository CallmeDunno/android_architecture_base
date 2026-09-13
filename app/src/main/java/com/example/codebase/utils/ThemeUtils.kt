package com.example.codebase.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

/** App-wide night mode choice. [storageKey] is what gets persisted, so never rename an existing key. */
enum class ThemeMode(val storageKey: String, val nightMode: Int) {
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES),
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

    companion object {
        /** Unknown or missing keys fall back to [SYSTEM]. */
        fun fromStorageKey(key: String?): ThemeMode = entries.firstOrNull { it.storageKey == key } ?: SYSTEM
    }
}

/**
 * Light/dark/system theme persisted with [PrefsUtils], plus opt-in Material You dynamic colors.
 *
 * [applySavedTheme] and [installDynamicColors] run once from `CodebaseApp.onCreate`, so every Activity
 * starts with the saved choice.
 */
object ThemeUtils {

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"

    fun applySavedTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getThemeMode(context).nightMode)
    }

    /** Saves [mode] and applies it. AppCompat recreates the running activities when the mode changes. */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        PrefsUtils.putString(context, KEY_THEME_MODE, mode.storageKey)
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    fun getThemeMode(context: Context): ThemeMode =
        ThemeMode.fromStorageKey(PrefsUtils.getString(context, KEY_THEME_MODE))

    /**
     * Whether [context] currently renders dark, from the saved mode or the system setting. Pass an
     * Activity context: the Application configuration doesn't reflect AppCompat's night mode.
     */
    fun isDarkMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** Dynamic colors need API 31+ (and a supporting device). */
    fun isDynamicColorAvailable(): Boolean = DynamicColors.isDynamicColorAvailable()

    /** Off by default, so the app keeps its own palette until the user opts in. */
    fun isDynamicColorEnabled(context: Context): Boolean =
        PrefsUtils.getBoolean(context, KEY_DYNAMIC_COLOR, false)

    /**
     * Saves the choice and recreates [activity] so it takes effect right away. Activities further back
     * in the stack pick it up the next time they are recreated.
     */
    fun setDynamicColorEnabled(activity: Activity, enabled: Boolean) {
        PrefsUtils.putBoolean(activity, KEY_DYNAMIC_COLOR, enabled)
        activity.recreate()
    }

    /**
     * Applies dynamic colors to every Activity created while the user has them enabled. The flag is
     * read per Activity, so toggling doesn't require restarting the app. No-op below API 31.
     */
    fun installDynamicColors(application: Application) {
        DynamicColors.applyToActivitiesIfAvailable(
            application,
            DynamicColorsOptions.Builder()
                .setPrecondition { activity, _ -> isDynamicColorEnabled(activity) }
                .build()
        )
    }
}
