package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pre-hashed form of the credentials (biometric unlock, ADR-0006) must open exactly what the originals open. */
class HashedCredentialsTest {
    private val engine = KotpassKdbxEngine()

    @Test
    fun hashedFormOpensEveryVaultOfTheCorpus() {
        for (vault in TestVaults.standard) {
            val original =
                Credentials.of(
                    password = vault.password?.toCharArray(),
                    keyFile = vault.keyFile?.readBytes(),
                )
            val hashed = engine.hashedForm(original)
            try {
                assertTrue(hashed.isPreHashed)
                val data = vault.file.readBytes()
                val fromHashes = engine.decode(data, hashed)

                val again = Credentials.of(vault.password?.toCharArray(), vault.keyFile?.readBytes())
                try {
                    assertEquals(vault.fileName, engine.decode(data, again), fromHashes)
                } finally {
                    again.wipe()
                }
            } finally {
                original.wipe()
                hashed.wipe()
            }
        }
    }

    @Test
    fun hashedFormCanSaveAndTheResultOpensWithThePassword() {
        val password = "test-password-1"
        val original = Credentials.of(password = password.toCharArray())
        val hashed = engine.hashedForm(original)
        try {
            val vault =
                engine.decode(
                    TestVaults.standard
                        .first {
                            it.password == password && it.keyFileName == null
                        }.file
                        .readBytes(),
                    original,
                )
            val rewritten = engine.encode(vault, hashed)
            val reopened = Credentials.of(password = password.toCharArray())
            try {
                assertEquals(vault.root.name, engine.decode(rewritten, reopened).root.name)
            } finally {
                reopened.wipe()
            }
        } finally {
            original.wipe()
            hashed.wipe()
        }
    }

    @Test
    fun wipingErasesTheHashes() {
        val hashed = Credentials.ofHashes(ByteArray(32) { 1 }, ByteArray(32) { 2 })

        hashed.wipe()

        assertFalse(hashed.isPreHashed)
    }
}
