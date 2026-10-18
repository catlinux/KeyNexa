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

private const val DEFAULT_CLEAR_SECONDS = 30
private const val MILLIS_PER_SECOND = 1_000L
private const val CLIP_MARKER = "io.github.catlinux.keynexa.CLIP"

// ClipDescription.EXTRA_IS_SENSITIVE exists from Android 13; older versions ignore the extra.
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/**
 * Copies secrets to the clipboard marked as sensitive (so keyboards and the system preview do not show them) and
 * clears them after the chosen time (30 seconds by default). Android only lets the app in the foreground change the
 * clipboard, so a timer that fires while another app is open cannot clear it: the clearing then happens as soon as
 * KeyNexa is back in front ([clearIfDue]) or the vault is locked ([clearNow]).
 */
object ClipboardGuard {
    private var dueAt: Long? = null
    private val handler = Handler(Looper.getMainLooper())

    fun copy(
        context: Context,
        label: String,
        value: String,
        sensitive: Boolean,
        clearAfterSeconds: Int = DEFAULT_CLEAR_SECONDS,
    ) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras =
            PersistableBundle().apply {
                putBoolean(CLIP_MARKER, true)
                if (sensitive) putBoolean(EXTRA_IS_SENSITIVE, true)
            }
        clipboard.setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, context.getString(R.string.clipboard_copied, label), Toast.LENGTH_SHORT).show()
        }
        handler.removeCallbacksAndMessages(null)
        if (sensitive) {
            val delay = clearAfterSeconds * MILLIS_PER_SECOND
            dueAt = SystemClock.elapsedRealtime() + delay
            handler.postDelayed({ clearIfDue(context) }, delay)
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
        val shouldClear =
            try {
                val description = clipboard.primaryClipDescription
                description == null || description.extras?.getBoolean(CLIP_MARKER) == true
            } catch (_: Exception) {
                true
            }
        try {
            if (shouldClear) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        } finally {
            handler.removeCallbacksAndMessages(null)
            dueAt = null
        }
    }
}
