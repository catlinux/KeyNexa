# Benchmarks

[English](../BENCHMARKS.md) | [Español](BENCHMARKS.md)

## Argon2 con kotpass (Kotlin puro)

kotpass calcula Argon2 en Kotlin, sin código nativo (el [ADR-0005](decisions/0005-reinforced-kotpass.md) mantiene argon2kt como opción si las cifras de abajo no son aceptables en móviles).

Prueba: [`Argon2PerformanceTest`](../../core/kdbx/src/test/kotlin/io/github/catlinux/keynexa/kdbx/Argon2PerformanceTest.kt), omitida salvo que esté definida `KEYNEXA_PERF`. Abre bóvedas del corpus (consulta [Bóvedas de prueba](TEST_VAULTS.md)) e imprime el tiempo.

```sh
KEYNEXA_PERF=1 ./gradlew :core:kdbx:test --tests '*Argon2Performance*' -i
KEYNEXA_TEST_HEAP=512m KEYNEXA_PERF=1 ./gradlew :core:kdbx:test --tests '*Argon2Performance*' -i
```

### JVM de escritorio (2026-10-13)

JDK 17 (Temurin) en Windows 11, 16 procesadores, 2 GiB de heap. Los tiempos incluyen toda la apertura (derivación de clave, descifrado y análisis).

| Bóveda | Argon2 | Tiempo |
| --- | --- | --- |
| `rich-aes-argon2d.kdbx` | Argon2d, 64 MiB, 10 iteraciones, 2 carriles | unos 0,4 s (0,56 s la primera vez, calentamiento del JIT) |
| `argon2-1gib.kdbx` | Argon2id, 1024 MiB, 2 iteraciones, 2 carriles | unos 2,5 s |

Con un heap de 512 MiB la bóveda de 1 GiB falla con `OutOfMemoryError: Java heap space`: kotpass reserva la memoria de Argon2 en el heap de Java. El motor captura ese `OutOfMemoryError` alrededor de la derivación de clave y lo convierte en `VaultException.NotEnoughMemory`, así que la app lo explica en vez de caerse.

### Qué significa para Android

- **No se ha medido en un móvil ni en un emulador.** Son solo cifras de escritorio; un núcleo de móvil de gama media es varias veces más lento, así que cabe esperar para el caso de 64 MiB del orden de segundos, no de milisegundos.
- **La memoria es el mayor riesgo.** Android limita el heap de Java de una app (habitualmente 256-512 MiB, más con `largeHeap`), así que las bóvedas que pidan 512 MiB o 1 GiB de memoria de Argon2 pueden no abrirse con la implementación en Kotlin puro; cuando eso pasa, la app informa de `VaultException.NotEnoughMemory` con un mensaje claro y nunca se cae. El valor por defecto de KeePassXC es 64 MiB, que cabe.
- **Decisión pendiente de datos reales:** medir en un dispositivo real cuando la app pueda abrir una bóveda (fase D). Si una bóveda típica de 64-256 MiB tarda demasiado o se queda sin memoria, se añade argon2kt (nativo, MIT) mediante `BaseKdfProvider` de kotpass con un ADR nuevo. Hasta entonces se mantiene la implementación en Kotlin puro.
