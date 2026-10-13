package io.github.catlinux.keynexa.app

import android.app.Application

class KeyNexaApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
