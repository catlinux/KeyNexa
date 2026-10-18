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

## Dependencias de tiempo de ejecución del APK de Android

Las entradas siguientes son los módulos externos fijados para los sabores de release de la aplicación (`offlineReleaseRuntimeClasspath` y `cloudReleaseRuntimeClasspath`) en [apps/android/gradle.lockfile](../../apps/android/gradle.lockfile), agrupados por proyecto. Se excluyen las dependencias de pruebas y de compilación. El módulo fuente `:kotpass`, incluido en la app, está vendorizado y no aparece como coordenada externa en el lockfile; sus licencias de código integrado se indican abajo. Las entradas BOM del lockfile alinean versiones y no se empaquetan en el APK.

| Proyecto | Módulos fijados y versiones | Licencia |
| --- | --- | --- |
| AndroidX | androidx.activity: activity-compose, activity-ktx, activity 1.10.1; androidx.annotation: annotation-experimental 1.4.1, annotation-jvm y annotation 1.9.1; androidx.appcompat: appcompat-resources, appcompat 1.2.0; androidx.arch.core: core-common, core-runtime 2.2.0; androidx.autofill: autofill 1.0.0; androidx.biometric: biometric 1.1.0; androidx.collection: collection-jvm, collection-ktx, collection 1.5.0; androidx.concurrent: concurrent-futures 1.1.0; androidx.core: core-ktx, core-viewtree, core 1.18.0; androidx.cursoradapter: cursoradapter 1.0.0; androidx.customview: customview-poolingcontainer, customview 1.0.0; androidx.drawerlayout: drawerlayout 1.0.0; androidx.emoji2: emoji2 1.4.0; androidx.fragment: fragment 1.8.5; androidx.graphics: graphics-path 1.0.1; androidx.interpolator: interpolator 1.0.0; androidx.lifecycle: lifecycle-common-java8, lifecycle-common-jvm, lifecycle-common, lifecycle-livedata-core-ktx, lifecycle-livedata-core, lifecycle-livedata, lifecycle-process, lifecycle-runtime-android, lifecycle-runtime-compose-android, lifecycle-runtime-compose, lifecycle-runtime-ktx-android, lifecycle-runtime-ktx, lifecycle-runtime, lifecycle-viewmodel-android, lifecycle-viewmodel-ktx, lifecycle-viewmodel-savedstate-android, lifecycle-viewmodel-savedstate, lifecycle-viewmodel 2.9.0; androidx.loader: loader 1.0.0; androidx.profileinstaller: profileinstaller 1.4.0; androidx.savedstate: savedstate-android, savedstate-compose-android, savedstate-compose, savedstate-ktx, savedstate 1.3.0; androidx.startup: startup-runtime 1.1.1; androidx.tracing: tracing 1.2.0; androidx.vectordrawable: vectordrawable-animated, vectordrawable 1.1.0; androidx.versionedparcelable: versionedparcelable 1.1.1; androidx.viewpager: viewpager 1.0.0 | Apache-2.0 |
| Jetpack Compose | androidx.compose.animation: animation-android, animation-core-android, animation-core, animation 1.9.1; androidx.compose.foundation: foundation-android, foundation-layout-android, foundation-layout, foundation 1.9.1; androidx.compose.material: material-icons-core-android, material-icons-core 1.7.8; material-ripple-android, material-ripple 1.9.1; androidx.compose.material3: material3-android, material3 1.3.2; androidx.compose.runtime: runtime-android, runtime-annotation-android, runtime-annotation, runtime-saveable-android, runtime-saveable, runtime 1.9.1; androidx.compose.ui: ui-android, ui-geometry-android, ui-geometry, ui-graphics-android, ui-graphics, ui-text-android, ui-text, ui-unit-android, ui-unit, ui-util-android, ui-util, ui 1.9.1; BOM 2025.09.00 | Apache-2.0 |
| Kotlin | `kotlin-stdlib`, `kotlin-stdlib-common` 2.2.21 | Apache-2.0 |
| kotlinx.coroutines | `kotlinx-coroutines-android`, `kotlinx-coroutines-core`, `kotlinx-coroutines-core-jvm` 1.9.0 | Apache-2.0 (ya documentada; licencia de la versión fijada sin reverificar) |
| kotlinx.serialization | `kotlinx-serialization-core`, `kotlinx-serialization-core-jvm` 1.7.3 | Por verificar |
| kotpass (código fuente vendorizado) | Motor KDBX, versión 0.13.0; incluye Kotlin XML Builder 1.9.3, partes de Apache Commons Lang 3.17.0 y código criptográfico portado de Bouncy Castle | MIT; XML Builder y Commons Lang incluidos, Apache-2.0; Bouncy Castle License (texto MIT) |
| Okio | `okio`, `okio-jvm` 3.15.0 | Apache-2.0 |
| Guava | `listenablefuture` 1.0 | Por verificar |
| JetBrains | `annotations` 23.0.0 | Por verificar |
| JSpecify | `jspecify` 1.0.0 | Por verificar |
| Manrope (fuente incluida, no es un módulo de Gradle) | `apps/android/src/main/res/font/manrope.ttf`, fuente variable de google/fonts, commit `b31870a` | SIL OFL 1.1 (verificada en el `OFL.txt` original, conservado en `third_party/manrope/`; compatible con distribuirla dentro de una app GPL-3.0) |

JUnit 4 solo se usa en las pruebas y no aparece en las configuraciones de release. Las herramientas de compilación se enumeran por separado más abajo. El lockfile también contiene androidx.compose:compose-bom 2025.09.00, kotlinx-coroutines-bom 1.9.0 y kotlinx-serialization-bom 1.7.3; son metadatos para alinear versiones, no contenido del APK.

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
| Jazzer | Apache-2.0 | [CodeIntelligenceTesting/jazzer](https://github.com/CodeIntelligenceTesting/jazzer) | 0.30.0; fuzzing guiado por cobertura del decodificador KDBX en el workflow `fuzz`, descargado como CLI con una suma SHA-256 fija; no es una dependencia de Gradle ni se distribuye con la aplicación |
| keepass-rs y la herramienta Rust | keepass 0.15.2 MIT; serde, serde_json y sha2 MIT OR Apache-2.0 | [sseemayer/keepass-rs](https://github.com/sseemayer/keepass-rs), [serde-rs/serde](https://github.com/serde-rs/serde), [RustCrypto/hashes](https://github.com/RustCrypto/hashes) | La herramienta `tools/kdbx-dump-rs` del trabajo `differential` de la CI; sus dependencias Rust (keepass-rs y las suyas) están fijadas en `tools/kdbx-dump-rs/Cargo.lock`, y ni la herramienta ni ellas se distribuyen con la aplicación |

## Proceso para añadir una dependencia

1. Justificar la necesidad en el pull request o en el mensaje del commit.
2. Leer su archivo `LICENSE` en la versión exacta y comprobar sus dependencias transitivas.
3. Añadirla a este documento (en ambos idiomas) y a [CREDITS](CREDITS.md) en el mismo commit.
4. La CI ejecuta un informe de licencias y un análisis de vulnerabilidades en cada push y pull request.

## Cuestiones legales pendientes

- Confirmar si la semántica de fusión al estilo de KeePassXC, reimplementada a partir de documentación pública, plantea algún problema (previsto: no, el comportamiento no está sujeto a copyright; no se copia ningún código).
- Conservar los avisos exactos de Bouncy Castle y de Apache de kotpass en la pantalla «Acerca de» de la aplicación y en CREDITS cuando se añada la dependencia.
