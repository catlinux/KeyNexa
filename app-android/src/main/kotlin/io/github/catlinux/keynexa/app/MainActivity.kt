package io.github.catlinux.keynexa.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import io.github.catlinux.keynexa.app.ui.theme.KeyNexaTheme

class MainActivity : FragmentActivity() {
    private val container: AppContainer get() = (application as KeyNexaApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val allowScreenshots = container.settings.values.allowScreenshots
            LaunchedEffect(allowScreenshots) { applyScreenProtection(allowScreenshots) }
            KeyNexaTheme {
                // The app draws edge to edge; the padding keeps content out from under the system bars and the
                // keyboard, while the surface still paints the background behind them.
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.safeDrawingPadding()) {
                        AppRoot(container)
                    }
                }
            }
        }
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
