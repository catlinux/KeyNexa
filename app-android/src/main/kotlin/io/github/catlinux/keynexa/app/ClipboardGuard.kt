package io.github.catlinux.keynexa.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.widget.Toast

private const val CLEAR_AFTER_MILLIS = 30_000L

// ClipDescription.EXTRA_IS_SENSITIVE exists from Android 13; older versions ignore the extra.
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/**
 * Copies secrets to the clipboard marked as sensitive (so keyboards and the system preview do not show them) and
 * clears them 30 seconds later. Android only lets the app in the foreground change the clipboard, so a timer that
 * fires while another app is open cannot clear it: the clearing then happens as soon as KeyNexa is back in front
 * ([clearIfDue]) or the vault is locked ([clearNow]).
 */
object ClipboardGuard {
    private var dueAt: Long? = null
    private val handler = Handler(Looper.getMainLooper())

    fun copy(
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
        handler.removeCallbacksAndMessages(null)
        if (sensitive) {
            dueAt = SystemClock.elapsedRealtime() + CLEAR_AFTER_MILLIS
            handler.postDelayed({ clearIfDue(context) }, CLEAR_AFTER_MILLIS)
        } else {
            dueAt = null
        }
    }

    /** Clears the clipboard if a copied secret has been there for 30 seconds or more. */
    fun clearIfDue(context: Context) {
        val due = dueAt ?: return
        if (SystemClock.elapsedRealtime() >= due) clearNow(context)
    }

    /** Clears the clipboard now if KeyNexa put a secret there. */
    fun clearNow(context: Context) {
        if (dueAt == null) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        handler.removeCallbacksAndMessages(null)
        dueAt = null
    }
}
