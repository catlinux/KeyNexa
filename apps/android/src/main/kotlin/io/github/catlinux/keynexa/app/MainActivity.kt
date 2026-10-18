package io.github.catlinux.keynexa.app

import android.os.Bundle
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import io.github.catlinux.keynexa.app.ui.theme.KeyNexaTheme
import io.github.catlinux.keynexa.app.ui.theme.resolveDarkTheme
import io.github.catlinux.keynexa.app.ui.unlock.LocalPasswordKeypad

class MainActivity : FragmentActivity() {
    private val container: AppContainer get() = (application as KeyNexaApplication).container

    /**
     * A screen reader is on. The KeyNexa keypad would then speak every key, while the system keyboard follows the
     * Android setting that keeps passwords from being spoken, so the system keyboard is used instead (ADR-0010).
     */
    private var screenReaderOn by mutableStateOf(false)
    private val screenReaderListener = AccessibilityManager.TouchExplorationStateChangeListener { screenReaderOn = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(AccessibilityManager::class.java)?.let { manager ->
            screenReaderOn = manager.isTouchExplorationEnabled
            manager.addTouchExplorationStateChangeListener(screenReaderListener)
        }
        applyScreenProtection(container.settings.values.allowScreenshots)
        // Drops touches while another app draws a window over this one (tapjacking, fake overlays).
        window.decorView.filterTouchesWhenObscured = true
        setContent {
            val allowScreenshots = container.settings.values.allowScreenshots
            LaunchedEffect(allowScreenshots) { applyScreenProtection(allowScreenshots) }
            val dark = resolveDarkTheme(container.settings.values.themeMode, isSystemInDarkTheme())
            KeyNexaTheme(darkTheme = dark) {
                // The app draws edge to edge; the padding keeps content out from under the system bars and the
                // keyboard, while the surface still paints the background behind them.
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.safeDrawingPadding()) {
                        CompositionLocalProvider(
                            LocalPasswordKeypad provides (container.settings.values.passwordKeypad && !screenReaderOn),
                        ) {
                            AppRoot(container)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        getSystemService(AccessibilityManager::class.java)
            ?.removeTouchExplorationStateChangeListener(screenReaderListener)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        container.visible = true
        container.autoLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        container.visible = false
        // A rotation or a theme change recreates the activity; that is not leaving the app.
        if (!isChangingConfigurations) container.autoLock.onBackground()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ClipboardGuard.clearIfDue(this)
    }

    // A secure window cannot be captured or recorded and is blanked in the recent-apps list.
    private fun applyScreenProtection(allowScreenshots: Boolean) {
        if (allowScreenshots) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
