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
| `./gradlew test` | Ejecuta las pruebas unitarias de todos los módulos |
| `./gradlew :app-android:assembleDebug` | Genera el APK de depuración en `app-android/build/outputs/apk/debug/app-android-debug.apk` |

Estos comandos se verificaron en local, en Windows, con JDK 17 (Temurin) y el SDK de Android 36.

## Estructura de módulos

Las dependencias solo apuntan hacia abajo; los módulos `core-*` son Kotlin/JVM puro y no importan Android. Consulta [Arquitectura](ARCHITECTURE.md).

## Herramientas de calidad (previstas en la fase C)

- Formato y estilo: ktlint (o Spotless con ktlint).
- Análisis estático: Android Lint (con las advertencias tratadas como errores para las comprobaciones de seguridad e i18n) y detekt.
- Pruebas: JUnit para los módulos `core-*` en la JVM; pruebas de IU con Compose y pruebas instrumentadas para `app-android`.
- Seguridad de las dependencias: verificación de dependencias de Gradle, más un analizador de vulnerabilidades en la CI.
- Informe de licencias generado en la CI y comparado con [Dependencias y licencias](DEPENDENCIES_AND_LICENSES.md).

## Datos de prueba

- Las bóvedas de prueba viven en `testdata/` y contienen solo entradas ficticias; sus contraseñas se documentan junto a ellas.
- No subas nunca bóvedas reales, contraseñas reales, archivos de clave que uses ni claves de firma.

## Integración continua y publicaciones (previstas)

- GitHub Actions: compilación, pruebas, lint y comprobaciones de licencias y vulnerabilidades en cada push y pull request.
- Flujo de publicación: se activa con una etiqueta `vX.Y.Z`; compila y firma el APK con secretos almacenados en los ajustes del repositorio, y lo publica con sumas de verificación como una release de GitHub (preliminar antes de la 1.0).
