package io.github.catlinux.keynexa.session

import io.github.catlinux.keynexa.vault.VaultLocation
import java.net.URLDecoder
import java.net.URLEncoder

/** A vault the user added to the app: the name to show and where it lives. Holds nothing secret. */
data class KnownVault(
    val name: String,
    val location: VaultLocation,
)

/** The list of vaults the user added to the app. */
interface KnownVaults {
    /** Every known vault, in the order it should be shown. */
    fun all(): List<KnownVault>

    /** Adds [vault], or replaces the one with the same location. */
    fun add(vault: KnownVault)

    /** Removes the vault at [location], if it is there. */
    fun remove(location: VaultLocation)
}

/** Text form of the list of known vaults: one line per vault, `name` and `location` percent-encoded. */
object KnownVaultsCodec {
    private const val CHARSET = "UTF-8"
    private const val SEPARATOR = '\t'

    fun encode(vaults: List<KnownVault>): String =
        vaults.joinToString("\n") { "${escape(it.name)}$SEPARATOR${escape(it.location.value)}" }

    fun decode(text: String): List<KnownVault> =
        text.lines().mapNotNull { line ->
            val parts = line.split(SEPARATOR)
            if (parts.size != 2) {
                null
            } else {
                KnownVault(unescape(parts[0]), VaultLocation(unescape(parts[1])))
            }
        }

    private fun escape(text: String) = URLEncoder.encode(text, CHARSET)

    private fun unescape(text: String) = URLDecoder.decode(text, CHARSET)
}
