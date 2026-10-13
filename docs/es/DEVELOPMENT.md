# Desarrollo

[English](../DEVELOPMENT.md) | [Español](DEVELOPMENT.md)

## Requisitos

- JDK 17 (en local usamos Temurin). Los módulos core y las compilaciones Android usan una cadena de herramientas Java 17.
- SDK de Android con la plataforma 36 y build-tools 36.0.0. Indica a Gradle dónde está con `local.properties` (`sdk.dir=...`, ignorado por git) o con la variable de entorno `ANDROID_HOME`.
- No hace falta instalar Gradle: el wrapper incluido descarga Gradle 8.14.

## Cadena de herramientas

| Herramienta | Versión |
| --- | --- |
| JDK | 17 (Temurin) |
| Gradle | 8.14 (wrapper) |
| Android Gradle Plugin | 8.13.1 |
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
| `./gradlew :app-android:assembleDebug` | Genera el APK de depuración en `app-android/build/outputs/apk/debug/app-android-debug.apk` |

Estos comandos se verificaron en local, en Windows, con JDK 17 (Temurin) y el SDK de Android 36.

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

No inventes sumas a mano. La CI (compilación, pruebas, lint, informe de licencias, análisis de vulnerabilidades) está prevista para la fase C3; consulta la [hoja de ruta](ROADMAP.md).

## Datos de prueba

- Las bóvedas de prueba viven en `testdata/` y contienen solo entradas ficticias; sus contraseñas se documentan junto a ellas.
- No subas nunca bóvedas reales, contraseñas reales, archivos de clave que uses ni claves de firma.

## Integración continua y publicaciones (previstas)

- GitHub Actions: compilación, pruebas, lint y comprobaciones de licencias y vulnerabilidades en cada push y pull request.
- Flujo de publicación: se activa con una etiqueta `vX.Y.Z`; compila y firma el APK con secretos almacenados en los ajustes del repositorio, y lo publica con sumas de verificación como una release de GitHub (preliminar antes de la 1.0).
