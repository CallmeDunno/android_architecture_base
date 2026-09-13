package com.example.codebase.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.atomic.AtomicInteger

/** How much of the shared image library the app can read. */
enum class MediaAccess { FULL, PARTIAL, NONE }

/**
 * Runtime permissions: check them, and ask only for what is missing.
 *
 * ```
 * PermissionUtils.request(this, Manifest.permission.CAMERA) { openCamera() }
 * PermissionUtils.request(requireContext(), listOf(CAMERA, RECORD_AUDIO)) { startRecording() }
 * ```
 *
 * - Permissions that don't apply to the running API level are treated as granted (see
 *   [applicablePermissions]). For example, `request(context, POST_NOTIFICATIONS) { ... }` continues right
 *   away below API 33, where notifications need no permission.
 * - A permission must also be declared in `AndroidManifest.xml` by the feature that uses it. Otherwise
 *   the system denies it immediately, without showing a dialog.
 */
object PermissionUtils {

    private const val TAG = "PermissionUtils"
    private val requestCounter = AtomicInteger()

    fun isGranted(context: Context, permission: String): Boolean =
        areAllGranted(context, listOf(permission))

    fun areAllGranted(context: Context, permissions: Collection<String>): Boolean =
        applicablePermissions(permissions, Build.VERSION.SDK_INT).all { context.hasPermission(it) }

    /** Vararg form of [request]. */
    @MainThread
    fun request(context: Context, vararg permissions: String, onGranted: () -> Unit) {
        request(context, permissions.asList(), onGranted)
    }

    /**
     * Runs [onGranted] right away if every permission is already granted. Otherwise asks for the
     * missing ones, and runs [onGranted] only if the user grants all of them.
     *
     * [context] must belong to an Activity: an Activity, or a Fragment, View or Dialog context. With
     * any other context (such as the Application), nothing is requested. This can be called at any
     * time, e.g. from a click listener; unlike `registerForActivityResult`, it doesn't have to be set
     * up before the screen starts.
     *
     * Limitations:
     * - Nothing is called when the user denies. Where a screen needs to explain, check [isGranted] or
     *   [shouldShowRationale] and offer [openAppSettings].
     * - If the Activity is recreated (e.g. rotated) while the system dialog is showing, the result is
     *   dropped and the user has to trigger the action again.
     * - Don't start a second request on the same Activity while one is still showing.
     */
    @MainThread
    fun request(context: Context, permissions: Collection<String>, onGranted: () -> Unit) {
        val missing = applicablePermissions(permissions, Build.VERSION.SDK_INT)
            .filterNot { context.hasPermission(it) }
        if (missing.isEmpty()) {
            onGranted()
            return
        }
        val activity = context.requestingActivity(missing) ?: return
        launchRequest(activity, missing) { results ->
            if (allGranted(missing, results)) onGranted()
        }
    }

    /** Whether the system suggests explaining [permission] before asking again (it was denied once). */
    fun shouldShowRationale(activity: Activity, permission: String): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    /** Opens the system "App info" screen, the only place to grant a permission denied for good. */
    fun openAppSettings(context: Context): Boolean = IntentUtils.openAppSettings(context)

    /** Permissions for reading shared images on the running API level. */
    fun mediaImagePermissions(): List<String> = mediaImagePermissionsFor(Build.VERSION.SDK_INT)

    fun getMediaImageAccess(context: Context): MediaAccess =
        mediaAccessOf(Build.VERSION.SDK_INT) { context.hasPermission(it) }

    /**
     * Like [request] with [mediaImagePermissions], but also continues when the user only allows
     * selected photos (API 34+), which [request] would treat as a denial. [onGranted] receives
     * [MediaAccess.FULL] or [MediaAccess.PARTIAL].
     *
     * With [askAgainIfPartial] set, partial access asks again so the user can change the selection.
     */
    @MainThread
    fun requestMediaImages(
        context: Context,
        askAgainIfPartial: Boolean = false,
        onGranted: (MediaAccess) -> Unit,
    ) {
        val current = getMediaImageAccess(context)
        if (current == MediaAccess.FULL || (current == MediaAccess.PARTIAL && !askAgainIfPartial)) {
            onGranted(current)
            return
        }
        val permissions = mediaImagePermissions()
        val activity = context.requestingActivity(permissions) ?: return
        launchRequest(activity, permissions) {
            val access = getMediaImageAccess(activity)
            if (access != MediaAccess.NONE) onGranted(access)
        }
    }

    // Uses the registry's register() without a LifecycleOwner, which may be called in any state.
    // The launcher is unregistered after the result, or when the Activity is destroyed first.
    private fun launchRequest(
        activity: ComponentActivity,
        permissions: List<String>,
        onResult: (Map<String, Boolean>) -> Unit,
    ) {
        lateinit var launcher: ActivityResultLauncher<Array<String>>
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) launcher.unregister()
        }
        launcher = activity.activityResultRegistry.register(
            "$TAG#${requestCounter.incrementAndGet()}",
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            launcher.unregister()
            activity.lifecycle.removeObserver(observer)
            onResult(results)
        }
        activity.lifecycle.addObserver(observer)
        launcher.launch(permissions.toTypedArray())
    }

    private fun Context.requestingActivity(permissions: List<String>): ComponentActivity? {
        val activity = findActivity() as? ComponentActivity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Not requesting $permissions: the context has no active ComponentActivity")
            return null
        }
        return activity
    }

    private fun Context.hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

// API levels on which each permission exists and needs a runtime grant; unlisted permissions always
// apply. The newer constants are plain strings, and only reach the system on API levels that know them.
@SuppressLint("InlinedApi")
private val PERMISSION_SDK_RANGES: Map<String, IntRange> = mapOf(
    Manifest.permission.POST_NOTIFICATIONS to Build.VERSION_CODES.TIRAMISU..Int.MAX_VALUE,
    Manifest.permission.READ_MEDIA_IMAGES to Build.VERSION_CODES.TIRAMISU..Int.MAX_VALUE,
    Manifest.permission.READ_MEDIA_VIDEO to Build.VERSION_CODES.TIRAMISU..Int.MAX_VALUE,
    Manifest.permission.READ_MEDIA_AUDIO to Build.VERSION_CODES.TIRAMISU..Int.MAX_VALUE,
    Manifest.permission.NEARBY_WIFI_DEVICES to Build.VERSION_CODES.TIRAMISU..Int.MAX_VALUE,
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED to Build.VERSION_CODES.UPSIDE_DOWN_CAKE..Int.MAX_VALUE,
    Manifest.permission.BLUETOOTH_SCAN to Build.VERSION_CODES.S..Int.MAX_VALUE,
    Manifest.permission.BLUETOOTH_CONNECT to Build.VERSION_CODES.S..Int.MAX_VALUE,
    Manifest.permission.BLUETOOTH_ADVERTISE to Build.VERSION_CODES.S..Int.MAX_VALUE,
    Manifest.permission.ACCESS_BACKGROUND_LOCATION to Build.VERSION_CODES.Q..Int.MAX_VALUE,
    Manifest.permission.ACTIVITY_RECOGNITION to Build.VERSION_CODES.Q..Int.MAX_VALUE,
    // Declared with maxSdkVersion="28" in the manifest; scoped storage needs no permission from API 29.
    Manifest.permission.WRITE_EXTERNAL_STORAGE to 0..Build.VERSION_CODES.P,
    // Has no effect from API 33; use READ_MEDIA_* (see PermissionUtils.mediaImagePermissions).
    Manifest.permission.READ_EXTERNAL_STORAGE to 0..Build.VERSION_CODES.S_V2,
)

/** The distinct permissions from [permissions] that exist and need a runtime grant on [sdkInt]. */
internal fun applicablePermissions(permissions: Collection<String>, sdkInt: Int): List<String> =
    permissions.distinct().filter { permission ->
        PERMISSION_SDK_RANGES[permission]?.contains(sdkInt) ?: true
    }

/** Whether every permission in [requested] was granted. A cancelled request returns an empty map. */
internal fun allGranted(requested: Collection<String>, results: Map<String, Boolean>): Boolean =
    requested.all { results[it] == true }

@SuppressLint("InlinedApi")
internal fun mediaImagePermissionsFor(sdkInt: Int): List<String> = when {
    sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    sdkInt >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@SuppressLint("InlinedApi")
internal fun mediaAccessOf(sdkInt: Int, isGranted: (String) -> Boolean): MediaAccess = when {
    sdkInt >= Build.VERSION_CODES.TIRAMISU && isGranted(Manifest.permission.READ_MEDIA_IMAGES) -> MediaAccess.FULL
    sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.PARTIAL
    sdkInt < Build.VERSION_CODES.TIRAMISU && isGranted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.FULL
    else -> MediaAccess.NONE
}
