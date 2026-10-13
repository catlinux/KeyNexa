package io.github.catlinux.keynexa.app.vault

import android.content.Context
import androidx.core.content.edit
import io.github.catlinux.keynexa.vault.VaultLocation
import java.net.URLDecoder
import java.net.URLEncoder

/** A vault the user added to the app: the name to show and where it lives. Holds nothing secret. */
data class KnownVault(
    val name: String,
    val location: VaultLocation,
)

/** Text form of the list of known vaults: one line per vault, `name` and `location` percent-encoded. */
internal object KnownVaultsCodec {
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

/** The list of known vaults in the app's private preferences (excluded from backup by the backup rules). */
class KnownVaultsStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun all(): List<KnownVault> = KnownVaultsCodec.decode(preferences.getString(KEY, "").orEmpty())

    /** Adds [vault], or replaces the one with the same location. */
    fun add(vault: KnownVault) = write(all().filterNot { it.location == vault.location } + vault)

    fun remove(location: VaultLocation) = write(all().filterNot { it.location == location })

    private fun write(vaults: List<KnownVault>) {
        preferences.edit { putString(KEY, KnownVaultsCodec.encode(vaults)) }
    }

    private companion object {
        const val FILE = "known_vaults"
        const val KEY = "list"
    }
}
