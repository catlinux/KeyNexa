
package io.github.catlinux.keynexa.app.vault

import android.content.Context
import androidx.core.content.edit
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.session.KnownVaults
import io.github.catlinux.keynexa.session.KnownVaultsCodec
import io.github.catlinux.keynexa.vault.VaultLocation

/** The list of known vaults in the app's private preferences (excluded from backup by the backup rules). */
class KnownVaultsStore(
    context: Context,
) : KnownVaults {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun all(): List<KnownVault> = KnownVaultsCodec.decode(preferences.getString(KEY, "").orEmpty())

    override fun add(vault: KnownVault) = write(all().filterNot { it.location == vault.location } + vault)

    override fun remove(location: VaultLocation) = write(all().filterNot { it.location == location })

    private fun write(vaults: List<KnownVault>) {
        preferences.edit { putString(KEY, KnownVaultsCodec.encode(vaults)) }
    }

    private companion object {
        const val FILE = "known_vaults"
        const val KEY = "list"
    }
}
