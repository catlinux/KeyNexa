package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.update.SemVer
import java.io.IOException

/**
 * The version this build was made from, read from the `keynexa-version.txt` resource the build generates out of the
 * repository `VERSION` file.
 *
 * A missing or unreadable resource reads as `0.0.0`, which makes every signed release look newer: the update check
 * still refuses to install anything unless it verifies.
 */
internal object AppVersion {
    private const val RESOURCE = "keynexa-version.txt"

    private val FALLBACK = SemVer(0, 0, 0)

    val current: SemVer = read()

    @Suppress("SwallowedException")
    private fun read(): SemVer {
        val text =
            try {
                AppVersion::class.java.classLoader.getResourceAsStream(RESOURCE)?.use {
                    String(it.readBytes(), Charsets.UTF_8)
                }
            } catch (failure: IOException) {
                null
            } ?: return FALLBACK
        return SemVer.parse(text.trim()) ?: FALLBACK
    }
}
