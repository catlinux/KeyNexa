# Dependencias y licencias

[English](../DEPENDENCIES_AND_LICENSES.md) | [Español](DEPENDENCIES_AND_LICENSES.md)

> Fecha de la investigación: **2026-10-13**. Las licencias se comprobaron en el archivo `LICENSE`, el archivo de compilación o el POM de Maven Central de cada proyecto, según se cita. Los elementos marcados como *no verificado* no se pudieron confirmar. Esto es una investigación técnica, no asesoramiento legal.

## Licencia de KeyNexa y regla de compatibilidad

El código propio de KeyNexa se distribuye bajo **GPL-3.0-only** con los términos adicionales de la sección 7 de [NOTICE](../../NOTICE) ([ADR-0001](decisions/0001-license.md)). Todo componente distribuido con la aplicación debe ser compatible con GPL-3.0:

- Compatibles: MIT, BSD, ISC, Apache-2.0, Bouncy Castle License (texto MIT), SIL OFL 1.1 (fuentes tipográficas), GPL-3.0, LGPL-3.0.
- No compatibles: GPL-2.0-only, código propietario o de «todos los derechos reservados», licencias con restricciones de ámbito de uso.
- Cada componente de terceros conserva su propia licencia y sus avisos, que se enumeran en [CREDITS](CREDITS.md) cuando se incluye.

## Bibliotecas KDBX evaluadas

| Biblioteca | Licencia (verificada) | Plataforma | Lectura/escritura KDBX | Cifrados / KDF | Mantenimiento (oct. 2026) | Veredicto |
| --- | --- | --- | --- | --- | --- | --- |
| [kotpass](https://github.com/keemobile/kotpass) (Maven `app.keemobile:kotpass`) | MIT (`LICENSE.txt`, «Copyright (c) 2021 Denis T.») | JVM (no KMP) | 3.x y 4.x, escribe 3.1/4.1 | AES, ChaCha20, Twofish; AES-KDF, Argon2d, Argon2id | 0.13.0 (2025-09-28); un único mantenedor, que la considera «feature complete» (incidencia #42, 2026-09-03) | **Seleccionada**, integrada y reforzada ([ADR-0005](decisions/0005-reinforced-kotpass.md)) |
| [KeePassJava2](https://github.com/jorabin/KeePassJava2) | Apache-2.0 | JVM (Java 17 para la 3.1.0) | 3.1, 4, 4.1 (incidencia abierta: la 4.1 se reescribe como 4.0, #114) | AES, ChaCha20; AES-KDF, solo Argon2d (según el código fuente) | 3.1.0 (2026-10-05), API con cambios incompatibles muy reciente; correcciones recientes de integridad de adjuntos | Candidata de reserva |
| [hudeany/kdbx](https://github.com/hudeany/kdbx) | MIT | JVM | 3.x/4.x | AES, ChaCha20; AES-KDF, Argon2d/id | Primera versión en Maven 26.1.5 (2026-10-06), 2 estrellas | Demasiado joven |
| [openkeepass](https://github.com/cternes/openkeepass) | Apache-2.0 | JVM | Solo KDBX 3 | Sin Argon2/ChaCha20 | Obsoleta según su autor | Rechazada |
| [keepass-rs](https://github.com/sseemayer/keepass-rs) | MIT | Rust | Lee todo; la escritura está tras la característica `save_kdbx4` y se describe como experimental | Completo | Muy activa (0.15.2, 2026-10-11); incidencias abiertas de pérdida de datos en adjuntos/historial (#360, #375) | Rechazada por ahora ([ARCHITECTURE](ARCHITECTURE.md)) |
| [gokeepasslib](https://github.com/tobischo/gokeepasslib) | MIT | Go | 3.1/4.0/4.1 | — | Activa | Rechazada: cadena de herramientas de Go en Android |
| [kdbxweb](https://github.com/keeweb/kdbxweb) | MIT | JavaScript | — | Argon2 proporcionado externamente | Último push 2024-12 | Rechazada: ecosistema equivocado |
| [KeePassDX](https://github.com/Kunzisoft/KeePassDX) | GPL-3.0-or-later | Aplicación Android | — | — | Activa | No es una biblioteca publicada; JNI nativo. Se usa solo como referencia de comportamiento |
| KeePassXC, Keepass2Android, KeePassium | GPL | C++/C#/Swift | — | — | — | No utilizables como bibliotecas JVM |

### Detalles de kotpass (seleccionada)

- Una única dependencia en tiempo de ejecución: `com.squareup.okio:okio` 3.15.0, Apache-2.0 ([POM](https://repo1.maven.org/maven2/com/squareup/okio/okio/3.15.0/okio-3.15.0.pom)); más la biblioteca estándar de Kotlin (Apache-2.0).
- Código fuente de terceros incluido dentro de kotpass, cada uno con su archivo de licencia en el repositorio: Kotlin XML Builder 1.9.3 (Apache-2.0), partes de Apache Commons Lang 3.17.0 (Apache-2.0) y motores criptográficos portados de Bouncy Castle (Argon2, Blake2b, ChaCha, Salsa20, Twofish) con la cabecera de la Bouncy Castle License (texto MIT). Estos avisos deben conservarse en los créditos de KeyNexa.
- Sin código nativo. Usa `java.time.Instant` (requiere API 26+ o desugaring).
- Desde la 0.13.0 la KDF puede sustituirse por una implementación nativa (incidencia #38).
- Pruebas: unos 62 archivos de prueba, vectores de prueba criptográficos y 7 archivos `.kdbx` de ejemplo; no se encontró fuzzing.
- **Puntos por verificar en la fase C/D:** los elementos XML desconocidos probablemente no se conservan al guardar (no hay campo en el modelo; sin probar); rendimiento de Argon2 en Kotlin puro en teléfonos de gama baja con parámetros de memoria del tamaño de los de KeePassXC.
- Commit verificado: `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945`.
- Importado al repositorio en la fase C5 como módulo Gradle `:kotpass` (`third_party/kotpass/`): solo las fuentes principales, con los archivos de licencia originales intactos. Consulta su [README](../../third_party/kotpass/README.es.md) para los cambios locales.

## Dependencias de Android y escritorio

El esqueleto del proyecto (fase C1) ya usa Kotlin, AndroidX Core, AndroidX Activity, Compose (IU y Material 3) y JUnit; las demás filas siguen previstas.

| Componente | Licencia | Fuente | Notas |
| --- | --- | --- | --- |
| Kotlin stdlib, kotlinx.coroutines | Apache-2.0 | JetBrains | *Licencia de coroutines no reverificada para este documento* |
| Jetpack Compose, AndroidX (activity, lifecycle, navigation) | Apache-2.0 | POM de Google Maven | |
| JUnit 4 | EPL-1.0 | [junit-team/junit4](https://github.com/junit-team/junit4) | Solo pruebas unitarias; no se distribuye con la aplicación |
| `androidx.biometric` 1.1.0 (con sus librerías AndroidX transitivas: appcompat 1.2.0, fragment 1.2.5, lifecycle-livedata, loader, drawerlayout y otras) | Apache-2.0 | POM de `biometric-1.1.0` | En uso desde 0.9.0 (ADR-0006). Versión estable; las líneas 1.2/1.4 son alfa. `BiometricPrompt` necesita un `FragmentActivity`, así que `MainActivity` lo extiende. |
| Compose Multiplatform (escritorio, futuro) | Apache-2.0 | [JetBrains/compose-multiplatform](https://github.com/JetBrains/compose-multiplatform) | Estable 1.12.1 (2026-09-22) |
| Bouncy Castle (solo si hace falta además de kotpass) | Bouncy Castle License (texto MIT) | [bc-java LICENSE.md](https://github.com/bcgit/bc-java) | 1.86 (2026-09-11) |
| argon2kt (opcional, Argon2 nativo en Android) | MIT; el código C de referencia de Argon2 incluido es CC0-1.0 o Apache-2.0 | [lambdapioneer/argon2kt](https://github.com/lambdapioneer/argon2kt) | Solo si las pruebas de rendimiento lo justifican; última versión 1.6.0 (2024-09-06) |
| Inter o Manrope (fuente tipográfica) | SIL OFL 1.1 | [rsms/inter](https://github.com/rsms/inter), [google/fonts](https://github.com/google/fonts/tree/main/ofl/manrope) | Elección final en el sistema de diseño |
| Material Symbols (iconos) | Apache-2.0 (según el README del repositorio; *archivo de licencia exacto de Symbols no verificado*) | [google/material-design-icons](https://github.com/google/material-design-icons) | Alternativas: Lucide (ISC), Phosphor (MIT) |

### Evitados explícitamente

| Componente | Motivo |
| --- | --- |
| `androidx.security:security-crypto` | Obsoleto según Google, a favor de usar directamente el Android Keystore |
| `signalapp/argon2` | GPL-3.0 y archivado desde 2022 |
| UniFFI | Solo necesario para un núcleo en Rust, que no se ha elegido ([ADR-0005](decisions/0005-reinforced-kotpass.md)); su licencia MPL-2.0 es compatible con la GPL-3.0 |
| Cualquier SDK de analítica, informe de fallos o publicidad | Privacidad: KeyNexa no tiene telemetría |

## Herramientas de compilación

Solo se ejecutan durante la compilación y no se distribuyen con la aplicación.

| Herramienta | Licencia (verificada) | Origen | Notas |
| --- | --- | --- | --- |
| ktlint | MIT | [pinterest/ktlint](https://github.com/pinterest/ktlint) | Formato y estilo de Kotlin, mediante el plugin ktlint-gradle |
| detekt | Apache-2.0 | [detekt/detekt](https://github.com/detekt/detekt) | Análisis estático de Kotlin |
| Gradle License Report | Apache-2.0 (`LICENSE.txt`) | [jk1/Gradle-License-Report](https://github.com/jk1/Gradle-License-Report) | 3.1.4; la tarea `generateLicenseReport` |

## Proceso para añadir una dependencia

1. Justificar la necesidad en el pull request o en el mensaje del commit.
2. Leer su archivo `LICENSE` en la versión exacta y comprobar sus dependencias transitivas.
3. Añadirla a este documento (en ambos idiomas) y a [CREDITS](CREDITS.md) en el mismo commit.
4. La CI ejecuta un informe de licencias y un análisis de vulnerabilidades en cada push y pull request.

## Cuestiones legales pendientes

- Confirmar si la semántica de fusión al estilo de KeePassXC, reimplementada a partir de documentación pública, plantea algún problema (previsto: no, el comportamiento no está sujeto a copyright; no se copia ningún código).
- Conservar los avisos exactos de Bouncy Castle y de Apache de kotpass en la pantalla «Acerca de» de la aplicación y en CREDITS cuando se añada la dependencia.
