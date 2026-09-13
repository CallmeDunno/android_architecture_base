package com.example.codebase.utils

import android.app.Activity
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Soft keyboard helpers. The View-based functions use the window of the given view, so they work the
 * same in Activities, Fragments, `BaseDialog` and bottom sheets.
 */
object KeyboardUtils {

    /** Focuses [view] (usually an EditText) and shows the keyboard for it. */
    fun show(view: View) {
        view.requestFocus()
        // showSoftInput is ignored until the view is attached and its window has focus,
        // so it runs after the current frame.
        view.post {
            view.context.getSystemService<InputMethodManager>()?.showSoftInput(view, 0)
        }
    }

    /** Hides the keyboard in [view]'s window. */
    fun hide(view: View) {
        view.context.getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /** Hides the keyboard in [activity]'s window. */
    fun hide(activity: Activity) {
        val window = activity.window
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
    }

    /**
     * Whether the keyboard is showing in [view]'s window; `false` until the view is attached. Exact on
     * API 30+. Below that, AndroidX estimates it from the window insets.
     */
    fun isVisible(view: View): Boolean =
        ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
}
