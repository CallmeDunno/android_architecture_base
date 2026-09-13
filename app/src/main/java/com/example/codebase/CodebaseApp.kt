package com.example.codebase

import android.app.Application
import com.example.codebase.utils.ThemeUtils
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CodebaseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ThemeUtils.applySavedTheme(this)
        ThemeUtils.installDynamicColors(this)
    }
}
