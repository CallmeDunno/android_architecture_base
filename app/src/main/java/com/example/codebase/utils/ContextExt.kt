package com.example.codebase.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * The Activity behind this context: an Activity itself, or a Fragment, View or Dialog context wrapping
 * one. `null` for contexts without an Activity, such as the Application.
 */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
