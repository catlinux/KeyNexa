package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.VaultException

/**
 * Jazzer entry point for the KDBX decoder: a static `fuzzerTestOneInput(ByteArray)`, the convention of the CLI.
 *
 * It decodes the bytes with the public password of the test corpus. A damaged or hostile file is expected to fail
 * with a [VaultException]; anything else (another exception, a hang) is a finding and must propagate. Entries whose
 * key derivation is too costly are skipped so the fuzzer stays fast; the absolute limits are checked by the engine.
 */
object DecoderFuzzTarget {
    @JvmStatic
    fun fuzzerTestOneInput(data: ByteArray) {
        try {
            val engine = KotpassKdbxEngine()
            val format = engine.inspect(data)
            format.requireAcceptableKdfCost()
            if (tooExpensiveForFuzzing(format.kdf)) return
            engine.decode(data, CREDENTIALS)
        } catch (expected: VaultException) {
            // A file that cannot be read is the normal outcome for most inputs.
        }
    }

    private fun tooExpensiveForFuzzing(kdf: Kdf): Boolean =
        when (kdf) {
            is Kdf.AesKdf -> kdf.rounds > FUZZ_MAX_AES_KDF_ROUNDS
            is Kdf.Argon2 ->
                kdf.memoryBytes > FUZZ_MAX_ARGON2_MEMORY_BYTES || kdf.iterations > FUZZ_MAX_ARGON2_ITERATIONS
        }

    private const val TEST_PASSWORD = "test-password-1"
    private const val FUZZ_MAX_ARGON2_MEMORY_BYTES = 16L * 1024 * 1024
    private const val FUZZ_MAX_ARGON2_ITERATIONS = 3L
    private const val FUZZ_MAX_AES_KDF_ROUNDS = 100_000L

    private val CREDENTIALS = Credentials.of(password = TEST_PASSWORD.toCharArray())
}
