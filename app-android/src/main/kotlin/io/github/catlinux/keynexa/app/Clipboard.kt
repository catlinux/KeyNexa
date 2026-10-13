package io.github.catlinux.keynexa.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast

private const val CLEAR_AFTER_MILLIS = 30_000L

// ClipDescription.EXTRA_IS_SENSITIVE exists from Android 13; older versions ignore the extra.
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/**
 * Copies [value] to the clipboard marked as sensitive when it is a secret (so keyboards and the system preview do
 * not show it) and clears the clipboard after 30 seconds. Phase D5 refines this with the lock rules.
 */
fun copyToClipboard(
    context: Context,
    label: String,
    value: String,
    sensitive: Boolean,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText(label, value)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    }
    clipboard.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.clipboard_copied, label), Toast.LENGTH_SHORT).show()
    }
    if (sensitive) {
        Handler(Looper.getMainLooper()).postDelayed({ clearClipboard(clipboard) }, CLEAR_AFTER_MILLIS)
    }
}

private fun clearClipboard(clipboard: ClipboardManager) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        clipboard.clearPrimaryClip()
    } else {
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
