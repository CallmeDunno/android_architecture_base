package com.example.codebase.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/**
 * Starts common system and third-party screens.
 *
 * Every function returns `false` instead of crashing when nothing on the device can handle the Intent,
 * so no `<queries>` declaration is needed. A non-Activity context (e.g. the Application) also works:
 * `FLAG_ACTIVITY_NEW_TASK` is added for it.
 */
object IntentUtils {

    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    private const val DEFAULT_MIME_TYPE = "application/octet-stream"

    /**
     * Opens a web link or deep link (`myapp://...`) in a matching app. A link without a scheme gets
     * `https://`. Use [sendEmail] and [dial] for `mailto:` and `tel:`.
     */
    fun openUrl(context: Context, url: String): Boolean {
        val normalized = normalizeUrl(url) ?: return false
        return context.startActivitySafely(Intent(Intent.ACTION_VIEW, normalized.toUri()))
    }

    /** The system "App info" screen, where the user can grant permissions that were denied for good. */
    fun openAppSettings(context: Context): Boolean = context.startActivitySafely(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    )

    /** The app's notification settings on API 26+, falling back to [openAppSettings]. */
    fun openNotificationSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            if (context.startActivitySafely(intent)) return true
        }
        return openAppSettings(context)
    }

    /** Shows the share sheet for plain text. */
    fun shareText(context: Context, text: String, chooserTitle: CharSequence? = null): Boolean {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        return context.startActivitySafely(Intent.createChooser(intent, chooserTitle))
    }

    /**
     * `content://` Uri for [file] through the app's FileProvider, or `null` if [file] is outside the
     * folders declared in `res/xml/file_paths.xml` ([InternalStorageUtils.sharedDir],
     * [CacheUtils.sharedCacheDir]).
     */
    fun getUriForFile(context: Context, file: File): Uri? = try {
        FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * Shows the share sheet for [file], granting the receiving app temporary read access. [file] must be
     * inside a shared folder (see [getUriForFile]), otherwise this returns `false`.
     */
    fun shareFile(
        context: Context,
        file: File,
        mimeType: String = getMimeType(file),
        chooserTitle: CharSequence? = null,
    ): Boolean {
        val uri = getUriForFile(context, file) ?: return false
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // createChooser copies the ClipData and grant flags to the chooser; ClipData also enables the
        // share sheet preview.
        intent.clipData = ClipData.newRawUri(null, uri)
        return context.startActivitySafely(Intent.createChooser(intent, chooserTitle))
    }

    /** MIME type from the file extension, or `application/octet-stream` if it is unknown. */
    fun getMimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileUtils.getExtension(file.name))
            ?: DEFAULT_MIME_TYPE

    /** Opens an email app with the fields filled in. Only email apps handle `mailto:`. */
    fun sendEmail(
        context: Context,
        to: List<String>,
        subject: String? = null,
        body: String? = null,
    ): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
            .putExtra(Intent.EXTRA_EMAIL, to.toTypedArray())
        subject?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        body?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }
        return context.startActivitySafely(intent)
    }

    /** Opens the dialer with [phoneNumber] filled in. Needs no `CALL_PHONE` permission. */
    fun dial(context: Context, phoneNumber: String): Boolean =
        context.startActivitySafely(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phoneNumber, null)))

    /** Opens the Play Store page of [packageName], falling back to the web page. */
    fun openPlayStore(context: Context, packageName: String = context.packageName): Boolean {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        val web = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$packageName".toUri()
        )
        return context.startActivitySafely(market) || context.startActivitySafely(web)
    }

    private fun Context.startActivitySafely(intent: Intent): Boolean {
        if (findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            // The resolved Activity isn't exported or requires a permission this app doesn't hold.
            false
        }
    }
}

private val URL_WITH_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/** Trims [url] and prefixes `https://` when it has no `scheme://`. Returns `null` for blank input. */
internal fun normalizeUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    return if (URL_WITH_SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
}
