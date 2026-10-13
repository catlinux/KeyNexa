# Desarrollo

[English](DEVELOPMENT.md) | [Español](DEVELOPMENT.es.md)

> Estado: todavía no hay código. Los comandos de compilación y de pruebas se añadirán aquí, verificados, cuando exista el esqueleto del proyecto (fase C).

## Cadena de herramientas prevista

| Herramienta | Versión | Notas |
| --- | --- | --- |
| JDK | 17 (Temurin) | Requerido por el Android Gradle Plugin |
| Android SDK | compileSdk/targetSdk 36, minSdk 26 | minSdk 26: framework de autorrelleno y `java.time` sin desugaring |
| Gradle | Wrapper incluido en el repositorio | Kotlin DSL y catálogo de versiones (`gradle/libs.versions.toml`) |
| Kotlin | La estable vigente en el momento de la fase C | |

## Herramientas de calidad (previstas en la fase C)

- Formato y estilo: ktlint (o Spotless con ktlint).
- Análisis estático: Android Lint (con las advertencias tratadas como errores para las comprobaciones de seguridad e i18n) y detekt.
- Pruebas: JUnit para los módulos `core-*` en la JVM; pruebas de IU con Compose y pruebas instrumentadas para `app-android`.
- Seguridad de las dependencias: verificación de dependencias de Gradle, más un analizador de vulnerabilidades en la CI.
- Informe de licencias generado en la CI y comparado con [Dependencias y licencias](DEPENDENCIES_AND_LICENSES.es.md).

## Datos de prueba

- Las bóvedas de prueba viven en `testdata/` y contienen solo entradas ficticias; sus contraseñas se documentan junto a ellas.
- No subas nunca bóvedas reales, contraseñas reales, archivos de clave que uses ni claves de firma.

## Integración continua y publicaciones (previstas)

- GitHub Actions: compilación, pruebas, lint y comprobaciones de licencias y vulnerabilidades en cada push y pull request.
- Flujo de publicación: se activa con una etiqueta `vX.Y.Z`; compila y firma el APK con secretos almacenados en los ajustes del repositorio, y lo publica con sumas de verificación como una release de GitHub (preliminar antes de la 1.0).
