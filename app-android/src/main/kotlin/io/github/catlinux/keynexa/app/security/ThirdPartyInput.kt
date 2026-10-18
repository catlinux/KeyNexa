package io.github.catlinux.keynexa.app.security

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager

/**
 * Whether the keyboard in use, or an enabled accessibility service, comes from an app the user installed rather than
 * from the system image (threat T22). Both can read what is typed, but most are harmless (other keyboards, screen
 * readers, automation), so the unlock screen only mentions it quietly. Package names are never shown or logged.
 */
object ThirdPartyInput {
    fun isActive(context: Context): Boolean =
        runCatching { keyboardIsThirdParty(context) || accessibilityIsThirdParty(context) }.getOrDefault(false)

    private fun keyboardIsThirdParty(context: Context): Boolean {
        val id =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        val manager = context.getSystemService(InputMethodManager::class.java) ?: return false
        val keyboard = manager.enabledInputMethodList.firstOrNull { it.id == id } ?: return false
        return !keyboard.serviceInfo.applicationInfo.isSystem()
    }

    private fun accessibilityIsThirdParty(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                !it.resolveInfo.serviceInfo.applicationInfo
                    .isSystem()
            }
    }

    private fun ApplicationInfo.isSystem(): Boolean = flags and ApplicationInfo.FLAG_SYSTEM != 0
}
