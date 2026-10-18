# Benchmarks

[English](BENCHMARKS.md) | [Español](es/BENCHMARKS.md)

## Argon2 with kotpass (pure Kotlin)

kotpass computes Argon2 in Kotlin, with no native code ([ADR-0005](decisions/0005-reinforced-kotpass.md) keeps argon2kt as an option if the numbers below are not acceptable on phones).

Test: [`Argon2PerformanceTest`](../core/kdbx/src/test/kotlin/io/github/catlinux/keynexa/kdbx/Argon2PerformanceTest.kt), skipped unless `KEYNEXA_PERF` is set. It opens corpus vaults (see [Test vaults](TEST_VAULTS.md)) and prints the time.

```sh
KEYNEXA_PERF=1 ./gradlew :core:kdbx:test --tests '*Argon2Performance*' -i
KEYNEXA_TEST_HEAP=512m KEYNEXA_PERF=1 ./gradlew :core:kdbx:test --tests '*Argon2Performance*' -i
```

### Desktop JVM (2026-10-13)

JDK 17 (Temurin) on Windows 11, 16 processors, 2 GiB heap. Times include the whole opening (key derivation, decryption and parsing).

| Vault | Argon2 | Time |
| --- | --- | --- |
| `rich-aes-argon2d.kdbx` | Argon2d, 64 MiB, 10 iterations, 2 lanes | about 0.4 s (0.56 s the first time, JIT warm-up) |
| `argon2-1gib.kdbx` | Argon2id, 1024 MiB, 2 iterations, 2 lanes | about 2.5 s |

With a 512 MiB heap the 1 GiB vault fails with `OutOfMemoryError: Java heap space`: kotpass allocates the Argon2 memory on the Java heap. The engine catches that `OutOfMemoryError` around the key derivation and turns it into `VaultException.NotEnoughMemory`, so the app explains it instead of crashing.

### What this means for Android

- **Not measured on a phone or emulator.** These are desktop numbers only; a mid-range phone core is several times slower, so expect the 64 MiB case in the order of seconds, not milliseconds.
- **Memory is the bigger risk.** Android limits an app's Java heap (commonly 256-512 MiB, more with `largeHeap`), so vaults that ask for 512 MiB or 1 GiB of Argon2 memory may not open with the pure-Kotlin implementation at all; when that happens the app reports `VaultException.NotEnoughMemory` with a clear message and never crashes. KeePassXC's default is 64 MiB, which fits.
- **Decision pending real data:** measure on a real device when the app can open a vault (phase D). If a typical 64-256 MiB vault takes too long or runs out of memory, add argon2kt (native, MIT) through kotpass's `BaseKdfProvider` with a new ADR. Until then the pure-Kotlin implementation stays.
