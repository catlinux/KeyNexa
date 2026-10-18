# Desarrollo

[English](../DEVELOPMENT.md) | [Español](DEVELOPMENT.md)

## Requisitos

- JDK 17 (en local usamos Temurin). Los módulos core y las compilaciones Android usan una cadena de herramientas Java 17.
- SDK de Android con la plataforma 36 y build-tools 36.0.0. Indica a Gradle dónde está con `local.properties` (`sdk.dir=...`, ignorado por git) o con la variable de entorno `ANDROID_HOME`.
- No hace falta instalar Gradle: el wrapper incluido descarga Gradle 8.14.6.

## Cadena de herramientas

| Herramienta | Versión |
| --- | --- |
| JDK | 17 (Temurin) |
| Gradle | 8.14 (wrapper) |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.2.21 |
| Compose BOM | 2025.09.00 (Material 3) |
| SDK de Android | compileSdk/targetSdk 36, minSdk 26 |

minSdk 26 da acceso al framework de autorrelleno y a `java.time` sin desugaring.

## Compilación y pruebas

Ejecuta estos comandos desde la raíz del repositorio. En Windows usa `gradlew.bat` en lugar de `./gradlew`.

| Comando | Qué hace |
| --- | --- |
| `./gradlew build` | Compila todos los módulos, ejecuta las pruebas unitarias y el lint |
| `./gradlew check` | Ejecuta las comprobaciones de calidad (pruebas unitarias, Android Lint, detekt, ktlint) |
| `./gradlew test` | Ejecuta las pruebas unitarias de todos los módulos |
| `./gradlew :core-kdbx:test` | Ejecuta las pruebas del motor KDBX contra el corpus de bóvedas de `testdata/` |
| `./gradlew :app-android:assembleOfflineDebug` | Genera el APK de depuración offline en `app-android/build/outputs/apk/offline/debug/app-android-offline-debug.apk` |
| `./gradlew :app-android:assembleCloudDebug` | Genera el APK de depuración cloud en `app-android/build/outputs/apk/cloud/debug/app-android-cloud-debug.apk` |

La aplicación tiene dos sabores de la dimensión `distribution`: `offline` (sin `INTERNET`, el que se publica) y `cloud` (declara `INTERNET` para las copias en la nube que aún están por llegar). El manifiesto fusionado de offline se comprueba en `check` (`:app-android:verifyOfflineHasNoInternet`) y el de cloud a la inversa. Instalar un sabor sobre el otro no está verificado.

Estos comandos se verificaron en local, en Windows, con JDK 17 (Temurin) y el SDK de Android 36.

### Pruebas del motor KDBX

`core-kdbx` prueba el motor contra las bóvedas ficticias de `testdata/` ([Bóvedas de prueba](TEST_VAULTS.md)):

- Corpus (AC1): cada bóveda se abre con sus credenciales, informa del formato esperado y contiene lo esperado.
- Errores: contraseña incorrecta, problemas con el archivo de clave, datos ajenos y archivos truncados o alterados. Un archivo KDBX 4 dañado se informa como dañado, nunca como contraseña incorrecta.
- Ida y vuelta (AC2): decodificar, codificar y decodificar de nuevo da la misma bóveda para todas las bóvedas y para cada combinación de cifrado y derivación de clave.
- Diferencial (AC2): la salida del motor se exporta a XML con `keepassxc-cli` y se compara con la exportación del original. Se omite sin error si KeePassXC no está instalado.
- Diferencial contra keepass-rs (AC2, ADR-0005): `tools/kdbx-dump-rs` es una pequeña herramienta en Rust (`keepass = "=0.15.2"`) que vuelca una bóveda a JSON canónico. Compílala una vez con `cargo build --release --locked` (dentro de `tools/kdbx-dump-rs`) y ejecuta `KEEPASS_RS_DUMP=tools/kdbx-dump-rs/target/release/kdbx-dump ./gradlew :core-kdbx:test --tests '*KeepassRsDifferential*'`. En Windows el binario es `tools\kdbx-dump-rs\target\release\kdbx-dump.exe`; asigna `$env:KEEPASS_RS_DUMP` a esa ruta. Sin la variable, la prueba se omite; el trabajo `differential` de la CI la ejecuta con Rust 1.99.0.
- Fuzzing: copias dañadas al azar de las bóvedas (bits cambiados, cortes, inserciones, borrados) solo pueden fallar con una `VaultException`, dentro de un límite de tiempo. La semilla es fija; `KEYNEXA_FUZZ_ITERATIONS=3000 ./gradlew :core-kdbx:test --tests '*DecoderMutationFuzz*'` lo ejecuta durante mucho más tiempo.
- Fuzzing guiado por cobertura (Jazzer): `DecoderFuzzTarget` es un punto de entrada de Jazzer (un `fuzzerTestOneInput(ByteArray)` estático) sobre el decodificador, y `DecoderFuzzTargetTest` lo ejercita con las bóvedas rápidas del corpus dentro de `check`. `./gradlew :core-kdbx:writeFuzzClasspath` escribe el classpath de pruebas en `core-kdbx/build/fuzz-classpath.txt`. El workflow `fuzz` ([fuzz.yml](../../.github/workflows/fuzz.yml)) se ejecuta cada lunes y a demanda, descarga la CLI de Jazzer 0.30.0 y comprueba su SHA-256 antes de ejecutarla; Jazzer no es una dependencia de Gradle ni forma parte de la app. En Linux o WSL, descárgala igual y ejecuta `./jazzer --cp="$(cat core-kdbx/build/fuzz-classpath.txt)" --target_class=io.github.catlinux.keynexa.kdbx.DecoderFuzzTarget -max_total_time=300 -timeout=20 -rss_limit_mb=3072 corpus`.
- Límites de la derivación de clave: un archivo que pida una memoria o iteraciones de Argon2 absurdas, o un número absurdo de rondas de AES-KDF, se rechaza antes de hacer ningún trabajo (`UnsupportedFormat`), porque los parámetros se leen de la cabecera sin cifrar.
- Tiempos de Argon2, desactivados por defecto: `KEYNEXA_PERF=1 ./gradlew :core-kdbx:test --tests '*Argon2Performance*' -i` (consulta [Benchmarks](BENCHMARKS.md)). `KEYNEXA_TEST_HEAP` fija el heap de las pruebas (por defecto 2g).

## Estructura de módulos

Las dependencias solo apuntan hacia abajo; los módulos `core-*` son Kotlin/JVM puro y no importan Android. Consulta [Arquitectura](ARCHITECTURE.md).

## Comprobaciones de calidad

`./gradlew check` ejecuta todas estas comprobaciones y falla en el primer problema:

| Comando | Qué comprueba |
| --- | --- |
| `./gradlew check` | Todas las comprobaciones siguientes (pruebas unitarias, Android Lint, detekt, ktlint) |
| `./gradlew test` | Las pruebas unitarias de todos los módulos |
| `./gradlew lint` | Android Lint en `platform-android` y `app-android` |
| `./gradlew detekt` | Análisis estático de Kotlin |
| `./gradlew ktlintCheck` | Estilo del código Kotlin |
| `./gradlew ktlintFormat` | Aplica las correcciones automáticas de ktlint a las fuentes |

Configuración:

- **Android Lint**: `config/lint/lint.xml`, compartido por los dos módulos Android. Los módulos fijan `warningsAsErrors = true` y `abortOnError = true`; `HardcodedText`, `MissingTranslation`, `ExtraTranslation` y las comprobaciones de seguridad (`SetJavaScriptEnabled`, `TrustAllX509TrustManager`, `WorldReadableFiles`, etc.) son errores.
- **detekt**: `config/detekt/detekt.yml`, sobre los valores por defecto de detekt.
- **ktlint**: `.editorconfig` en la raíz del repositorio.

### Verificación de dependencias

`gradle/verification-metadata.xml` fija la suma SHA-256 de cada dependencia, plugin y herramienta (incluidos ktlint y detekt) con `verify-metadata=true`. Tras cambiar una dependencia, la cadena de herramientas o las versiones de los plugins, regenéralo con las tareas que resuelvan los nuevos artefactos, revisa el diff y confírmalo:

```sh
./gradlew --write-verification-metadata sha256 check build
```

No inventes sumas a mano.

## Integración continua

[GitHub Actions](../.github/workflows/ci.yml) se ejecuta en cada push a `master` y en cada pull request, con `contents: read` y un grupo de concurrencia que cancela las ejecuciones reemplazadas de la misma referencia. Tiene cinco jobs:

| Job | Qué hace |
| --- | --- |
| `build` | Prepara JDK 17, el SDK de Android 36 y KeePassXC (para la prueba diferencial) y ejecuta `./gradlew build check`; si falla, sube los informes de lint, detekt y pruebas como artefacto |
| `docs-i18n` | Ejecuta `bash scripts/check-docs-i18n.sh` |
| `licenses` | Ejecuta `./gradlew generateLicenseReport` y sube `build/reports/dependency-license` |
| `vulnerabilities` | Analiza solo las dependencias de runtime que se distribuyen con OSV-Scanner, desde los archivos `gradle.lockfile`, y falla ante vulnerabilidades conocidas |
| `build-tooling-vulnerabilities` | Informativo (nunca hace fallar el build): analiza `gradle/verification-metadata.xml`, donde aparecen las herramientas de build; no se distribuyen con la aplicación |

Ejecuta las mismas comprobaciones en local:

```sh
bash scripts/check-docs-i18n.sh
./gradlew generateLicenseReport
```

`scripts/check-docs-i18n.sh` falla si un documento en inglés no tiene su traducción al castellano o al revés, o si a un documento le falta la línea de idiomas `[English](…) | [Español](…)` en sus seis primeras líneas. Las traducciones viven en `docs/es/` con las mismas rutas que el inglés; la única excepción es `README.es.md`, junto a `README.md`.

### Bloqueo de dependencias

El bloqueo de dependencias está activo solo para las configuraciones de runtime que se distribuyen con la aplicación (`runtimeClasspath` en los módulos JVM; `releaseRuntimeClasspath` en las bibliotecas Android; y `offlineReleaseRuntimeClasspath` y `cloudReleaseRuntimeClasspath` en `app-android`, cuyo nombre lleva el sabor) y en modo `STRICT`, de modo que los archivos `gradle.lockfile` de cada módulo contienen únicamente dependencias distribuidas. `./gradlew build check` falla si un lockfile está desactualizado. Tras cambiar una dependencia de runtime, regenera los lockfiles, revisa el diff y confírmalo junto a `gradle/verification-metadata.xml`:

```sh
./gradlew build --write-locks
```

## Datos de prueba

- Las bóvedas de prueba viven en `testdata/` y contienen solo entradas ficticias; sus contraseñas se documentan junto a ellas.
- No subas nunca bóvedas reales, contraseñas reales, archivos de clave que uses ni claves de firma.

## Publicaciones

[`release.yml`](../../.github/workflows/release.yml) se ejecuta al subir una etiqueta `vX.Y.Z`, en dos trabajos. **build** tiene acceso de solo lectura y ningún secreto: se detiene si la etiqueta no es `v` + el contenido de `VERSION`, ejecuta `./gradlew build check :app-android:assembleOfflineRelease :app-android:assembleCloudRelease`, falla salvo que el APK offline no tenga el permiso de Internet y el cloud sí (`aapt2 dump permissions`), y pasa los dos APK sin firmar al siguiente trabajo como artefacto. **publish** no ejecuta Gradle ni código de terceros: alinea los dos APK con `zipalign`, los firma con `apksigner` usando los secretos del repositorio, verifica las firmas y publica una release de GitHub con `keynexa-<versión>.apk` (offline), `keynexa-cloud-<versión>.apk`, sus archivos SHA-256 y la entrada del changelog como notas (se detiene si `CHANGELOG.md` no tiene entrada para esa versión). Al separarlos, nada de lo que ejecuta la compilación comparte máquina con el almacén de claves. Todas las acciones están fijadas a un commit. Las versiones `0.y.z` se publican como pre-releases. El almacén de claves se decodifica en el directorio temporal del runner y se borra al final del trabajo; las contraseñas llegan a `apksigner` por variables de entorno, nunca por la línea de órdenes.

Para publicar: sube `VERSION` y `CHANGELOG.md`, haz commit y crea y sube la etiqueta (`git tag vX.Y.Z && git push origin vX.Y.Z`). Tanto el push como la etiqueta necesitan la aprobación del responsable.

### Clave de firma y secretos

Crea el keystore una sola vez, fuera del repositorio, y guarda una copia de seguridad: si se pierde, las instalaciones existentes ya no podrán actualizarse.

```sh
keytool -genkeypair -v -keystore keynexa-release.jks -alias keynexa   -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 keynexa-release.jks > keynexa-release.jks.base64
```

Después añade cuatro secretos en *Settings → Secrets and variables → Actions* del repositorio de GitHub:

| Secreto | Contenido |
| --- | --- |
| `KEYSTORE_BASE64` | El contenido de `keynexa-release.jks.base64` |
| `KEYSTORE_PASSWORD` | La contraseña del keystore |
| `KEY_ALIAS` | El alias de la clave (`keynexa`) |
| `KEY_PASSWORD` | La contraseña de la clave |

Borra después el archivo `.base64`. No subas nunca al repositorio el keystore, las contraseñas ni el texto en base64.

Verifica un APK descargado con `apksigner verify --print-certs keynexa-X.Y.Z.apk` y `sha256sum -c keynexa-X.Y.Z.apk.sha256`.
