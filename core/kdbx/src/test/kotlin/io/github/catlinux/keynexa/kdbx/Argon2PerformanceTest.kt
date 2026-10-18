package io.github.catlinux.keynexa.kdbx

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Measures how long kotpass' pure-Kotlin Argon2 takes to open the corpus vaults. It is skipped unless the
 * environment variable KEYNEXA_PERF is set, because the 1 GiB vault needs a large heap and several seconds.
 * Run: KEYNEXA_PERF=1 ./gradlew :core:kdbx:test --tests '*Argon2Performance*' -i
 */
class Argon2PerformanceTest {
    private fun timeOpening(vault: TestVault): Long {
        val credentials = Credentials.from(EncryptedValue.fromString(requireNotNull(vault.password)))
        val start = System.nanoTime()
        vault.file.inputStream().use { KeePassDatabase.decode(it, credentials) }
        return (System.nanoTime() - start) / NANOS_PER_MILLI
    }

    @Test
    fun opensTheArgon2VaultsAndReportsTheTime() {
        assumeTrue("set KEYNEXA_PERF to run", System.getenv("KEYNEXA_PERF") != null)
        val maxHeapMiB = Runtime.getRuntime().maxMemory() / BYTES_PER_MIB

        println("PERF max heap: $maxHeapMiB MiB, processors: ${Runtime.getRuntime().availableProcessors()}")
        repeat(RUNS) { run ->
            println("PERF run ${run + 1}: Argon2d 64 MiB, 10 iterations: ${timeOpening(TestVaults.standard[0])} ms")
        }
        println("PERF Argon2id 1024 MiB, 2 iterations: ${timeOpening(TestVaults.argon2OneGiB)} ms")
    }

    private companion object {
        const val RUNS = 3
        const val NANOS_PER_MILLI = 1_000_000L
        const val BYTES_PER_MIB = 1024L * 1024L
    }
}
