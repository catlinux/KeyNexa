package io.github.catlinux.keynexa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.catlinux.keynexa.app.ui.theme.KeyNexaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as KeyNexaApplication).container
        setContent {
            KeyNexaTheme {
                AppRoot(container)
            }
        }
    }
}
