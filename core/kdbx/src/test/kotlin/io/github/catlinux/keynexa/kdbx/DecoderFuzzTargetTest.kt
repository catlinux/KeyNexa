package io.github.catlinux.keynexa.kdbx

import org.junit.Test

/** The Jazzer target, exercised on the fast corpus vaults and on truncated copies so `check` covers it. */
class DecoderFuzzTargetTest {
    @Test
    fun theFastVaultsAndTheirTruncationsDoNotThrow() {
        for (name in FAST_VAULTS) {
            val vault = TestVaults.standard.first { it.fileName == name }
            val bytes = vault.file.readBytes()
            DecoderFuzzTarget.fuzzerTestOneInput(bytes)
            for (size in listOf(0, 1, bytes.size / 2, bytes.size - 1)) {
                DecoderFuzzTarget.fuzzerTestOneInput(bytes.copyOf(size))
            }
        }
    }
}
