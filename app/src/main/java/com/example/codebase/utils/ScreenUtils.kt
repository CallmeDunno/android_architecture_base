package com.example.codebase.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Size
import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

/** Unit conversions, window size, system bar sizes and device form factor. */
object ScreenUtils {

    private const val TABLET_MIN_WIDTH_DP = 600

    /** [dp] in pixels, rounded to the nearest pixel. */
    fun dpToPx(context: Context, dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
            .roundToInt()

    /** [sp] in pixels with the user's font scale applied (non-linear for large sizes on API 34+). */
    fun spToPx(context: Context, sp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
            .roundToInt()

    fun pxToDp(context: Context, px: Float): Float {
        val metrics = context.resources.displayMetrics
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_DIP, px, metrics)
        } else {
            px / metrics.density
        }
    }

    /** [px] in sp, reversing the user's font scale. */
    @Suppress("DEPRECATION") // scaledDensity is only read below API 34, where it is still correct.
    fun pxToSp(context: Context, px: Float): Float {
        val metrics = context.resources.displayMetrics
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, px, metrics)
        } else {
            px / metrics.scaledDensity
        }
    }

    /**
     * Size of [activity]'s window in pixels. On API 30+ this is the exact window, including system bars
     * and multi-window resizing. Below that it is the app area from `DisplayMetrics`, which may exclude
     * the navigation bar.
     */
    fun getWindowSize(activity: Activity): Size =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = activity.windowManager.currentWindowMetrics.bounds
            Size(bounds.width(), bounds.height())
        } else {
            val metrics = activity.resources.displayMetrics
            Size(metrics.widthPixels, metrics.heightPixels)
        }

    /** Status bar height from [view]'s window insets; `0` until the view is attached. */
    fun statusBarHeight(view: View): Int =
        ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0

    /**
     * Bottom navigation bar height from [view]'s window insets; `0` until the view is attached, and in
     * landscape when the bar is on the side.
     */
    fun navigationBarHeight(view: View): Int =
        ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0

    /** `true` when the smallest screen width is at least 600dp (the tablet layout breakpoint). */
    fun isTablet(context: Context): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP

    fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
