package io.github.catlinux.keynexa.session

import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class KnownVaultsCodecTest {
    @Test
    fun roundTripKeepsSpecialCharacters() {
        val vaults =
            listOf(
                KnownVault("Claus\ttab\nnewline ñ €", VaultLocation("content://x/tree/a%3Ab/document/a%3Ab%2FC.kdbx")),
                KnownVault("Other", VaultLocation("content://y/document/1")),
            )
        assertEquals(vaults, KnownVaultsCodec.decode(KnownVaultsCodec.encode(vaults)))
    }

    @Test
    fun emptyAndBrokenTextGiveNoVaults() {
        assertEquals(emptyList<KnownVault>(), KnownVaultsCodec.decode(""))
        assertEquals(emptyList<KnownVault>(), KnownVaultsCodec.decode("only-one-column"))
    }
}
