package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.Argon2Variant
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.VaultException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random

/**
 * Feeds randomly damaged copies of corpus vaults to the decoder. Whatever the damage, the decoder must either open
 * the vault or fail with a [VaultException], within a bounded time: never another exception, never a hang.
 * The seed is fixed so a failure reproduces; set KEYNEXA_FUZZ_ITERATIONS to run longer.
 */
class DecoderMutationFuzzTest {
    private val engine = KotpassKdbxEngine()

    @Test
    fun damagedVaultsOnlyFailWithVaultExceptions() {
        val iterations = System.getenv("KEYNEXA_FUZZ_ITERATIONS")?.toIntOrNull() ?: DEFAULT_ITERATIONS
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable).apply { isDaemon = true } }
        try {
            for (name in FAST_VAULTS) {
                val vault = TestVaults.standard.first { it.fileName == name }
                val original = vault.file.readBytes()
                val random = Random(SEED + name.hashCode())
                repeat(iterations) { index ->
                    val damaged = mutate(original, random)
                    decodeWithinTimeLimit(executor, vault, damaged, "$name #$index")
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun absurdKeyDerivationCostsAreRefusedBeforeAnyWork() {
        val base =
            TestVaults.standard
                .first {
                    it.fileName == "rich-aes-aeskdf.kdbx"
                }.let { engine.inspect(it.file.readBytes()) }
        val tooManyRounds = base.copy(kdf = Kdf.AesKdf(MAX_AES_KDF_ROUNDS + 1))
        val tooMuchMemory = base.copy(kdf = argon2(memoryBytes = MAX_ARGON2_MEMORY_BYTES + 1))
        val tooManyIterations = base.copy(kdf = argon2(iterations = MAX_ARGON2_ITERATIONS + 1))
        val tooManyLanes = base.copy(kdf = argon2(parallelism = MAX_ARGON2_PARALLELISM + 1))
        val zeroRounds = base.copy(kdf = Kdf.AesKdf(0))

        for (format in listOf(tooManyRounds, tooMuchMemory, tooManyIterations, tooManyLanes, zeroRounds)) {
            try {
                format.requireAcceptableKdfCost()
                fail("accepted $format")
            } catch (expected: VaultException.UnsupportedFormat) {
                assertEquals(
                    "The key derivation settings of this file are beyond what this app accepts.",
                    expected.message,
                )
            }
        }
        base.requireAcceptableKdfCost()
        base.copy(kdf = argon2()).requireAcceptableKdfCost()
    }

    private fun argon2(
        memoryBytes: Long = ARGON2_TEST_MEMORY_BYTES,
        iterations: Long = 2,
        parallelism: Int = 2,
    ) = Kdf.Argon2(Argon2Variant.Argon2id, memoryBytes, iterations, parallelism, version = ARGON2_VERSION)

    private fun decodeWithinTimeLimit(
        executor: java.util.concurrent.ExecutorService,
        vault: TestVault,
        data: ByteArray,
        label: String,
    ) {
        val credentials = Credentials.of(password = requireNotNull(vault.password).toCharArray())
        val task =
            executor.submit(
                Callable {
                    try {
                        engine.decode(data, credentials)
                    } catch (expected: VaultException) {
                        expected
                    }
                },
            )
        try {
            task.get(TIME_LIMIT_SECONDS, TimeUnit.SECONDS)
        } catch (timeout: TimeoutException) {
            task.cancel(true)
            throw AssertionError("decoding hung on $label (seed $SEED)", timeout)
        } catch (unexpected: java.util.concurrent.ExecutionException) {
            throw AssertionError("decoding $label threw something other than a VaultException (seed $SEED)", unexpected)
        } finally {
            credentials.wipe()
        }
    }

    private fun mutate(
        original: ByteArray,
        random: Random,
    ): ByteArray {
        val bytes = original.copyOf()
        return when (random.nextInt(MUTATION_KINDS)) {
            0 -> bytes.also { repeat(random.nextInt(1, MAX_FLIPS)) { _ -> flip(it, random) } }
            1 -> bytes.copyOf(random.nextInt(bytes.size))
            2 -> insert(bytes, random)
            3 -> remove(bytes, random)
            else -> zeroRange(bytes, random)
        }
    }

    private fun flip(
        bytes: ByteArray,
        random: Random,
    ) {
        val index = random.nextInt(bytes.size)
        bytes[index] = (bytes[index].toInt() xor (1 shl random.nextInt(BITS_PER_BYTE))).toByte()
    }

    private fun insert(
        bytes: ByteArray,
        random: Random,
    ): ByteArray {
        val at = random.nextInt(bytes.size)
        return bytes.copyOfRange(0, at) + random.nextBytes(random.nextInt(1, MAX_CHANGE)) +
            bytes.copyOfRange(at, bytes.size)
    }

    private fun remove(
        bytes: ByteArray,
        random: Random,
    ): ByteArray {
        val at = random.nextInt(bytes.size)
        val end = minOf(bytes.size, at + random.nextInt(1, MAX_CHANGE))
        return bytes.copyOfRange(0, at) + bytes.copyOfRange(end, bytes.size)
    }

    private fun zeroRange(
        bytes: ByteArray,
        random: Random,
    ): ByteArray {
        val at = random.nextInt(bytes.size)
        val end = minOf(bytes.size, at + random.nextInt(1, MAX_CHANGE))
        bytes.fill(0, at, end)
        return bytes
    }

    private companion object {
        const val SEED = 20_261_013L
        const val DEFAULT_ITERATIONS = 150
        const val TIME_LIMIT_SECONDS = 30L
        const val MUTATION_KINDS = 5
        const val MAX_FLIPS = 5
        const val MAX_CHANGE = 64
        const val BITS_PER_BYTE = 8
        const val ARGON2_TEST_MEMORY_BYTES = 8L * 1024 * 1024
        const val ARGON2_VERSION = 0x13

        // Vaults with a cheap key derivation, so hundreds of attempts stay fast.
        val FAST_VAULTS = listOf("rich-aes-aeskdf.kdbx", "rich-chacha20-aeskdf.kdbx", "rich-kdbx31-aes-aeskdf.kdbx")
    }
}
