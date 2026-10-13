# Registro de cambios

[English](../../CHANGELOG.md) | [Español](CHANGELOG.md)

Todos los cambios notables de este proyecto se documentan en este archivo. El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto sigue el [Versionado Semántico](https://semver.org/lang/es/).

## [Sin publicar]

## [0.5.0] - 2026-10-13

### Añadido

- Canal de guardado seguro y detección de cambios en `core-sync` (fase D1): `SafeSaver` solo escribe tras copiar el archivo anterior y los bytes nuevos al almacenamiento privado y anotar el guardado en un diario, y solo informa de éxito tras volver a leer el archivo con la huella SHA-256 esperada; un archivo roto por su propia escritura se restaura byte a byte, y uno sustituido por otro programa se deja como está. `SaveRecovery` termina o deshace un guardado interrumpido por un cierre inesperado, sin necesitar la contraseña. `VaultReader` reintenta los archivos que pueden estar aún en transferencia. Los cambios externos o no fusionables nunca sobrescriben el archivo: la versión del usuario se conserva como copia privada (AC5).
- Los puertos `BackupStore` y `SaveJournal`, una operación `write` en `StoragePort` y la interfaz `VaultMerger` que implementa la fase D2.
- Pruebas de inyección de fallos (53) con almacenamiento falso: escrituras cortadas, escrituras que no truncan, proveedores que mienten, cambios externos antes, durante y después de la escritura, y cierres inesperados en cada punto (AC3 y AC5).

### Cambiado

- detekt admite hasta cuatro `return` (sin contar las cláusulas de guarda) y trata como gestionadas las excepciones de E/S y de bóveda que se traducen a propósito.

## [0.4.1] - 2026-10-13

### Cambiado

- El icono de la app es ahora la marca del responsable (hexágono, escudo y cerradura; original en `design/icon/`), trazada a vectores y con variante monocroma para iconos temáticos. Los colores primario y secundario siguen el azul marino y el celeste del icono, manteniendo WCAG AA o mejor.

## [0.4.0] - 2026-10-13

### Añadido

- Propuesta del sistema de diseño (fase C6): `docs/DESIGN.md` con principios, identidad, una paleta de colores accesible para los temas claro y oscuro, tipografía, espaciado, componentes, estados y los mensajes de seguridad. La identidad visual espera la aprobación del responsable.
- La paleta y el tema Material 3 en `app-android`, con una prueba unitaria que rompe la compilación si algún par de texto baja de WCAG AA (4,5:1) o algún borde de la interfaz de 3:1.
- Un icono de lanzador adaptativo original (una llave con una red de tres nodos) con una variante monocroma para iconos temáticos.

### Cambiado

- Se elimina la excepción de lint de «falta el icono del lanzador», ahora que la app tiene icono.

## [0.3.1] - 2026-10-13

### Añadido

- Fuzzing por mutación del decodificador (fase C5b, primera parte): copias dañadas al azar de las bóvedas del corpus solo pueden fallar con una `VaultException`, dentro de un límite de tiempo. Nueve mil mutaciones se ejecutaron sin fallos.
- La CI instala KeePassXC para que la prueba diferencial se ejecute allí.

### Seguridad

- El motor rechaza ajustes de derivación de clave fuera de límites razonables (memoria de Argon2 por encima de 2 GiB, más de 10000 iteraciones o 256 carriles, más de 500 millones de rondas de AES-KDF) antes de hacer ningún trabajo: los parámetros están en la cabecera sin cifrar, así que un archivo dañado o malicioso podría bloquear o hacer caer la app.

## [0.3.0] - 2026-10-13

### Añadido

- Motor KDBX (fase C5). `core-vault` define el modelo de dominio (bóveda, grupos, entradas, historial, adjuntos, datos personalizados, XML desconocido), las interfaces `KdbxEngine` y `StoragePort`, `Credentials` (secretos que se pueden borrar) y `VaultException`. `core-kdbx` implementa el motor sobre kotpass: `KotpassKdbxEngine`, con un mapeo completo en los dos sentidos para que cargar y guardar no pierda datos, y con los fallos traducidos a `VaultException` sin secretos en los mensajes.
- kotpass 0.13.0 (commit upstream `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945`) importado como módulo Gradle `:kotpass` en `third_party/kotpass/`, con Okio 3.15.0 como única dependencia; créditos de kotpass, Okio y los avisos que incluye.
- Pruebas de aceptación AC1 y AC2 sobre el corpus de 18 bóvedas: apertura, contenido esperado, errores, ida y vuelta para cada cifrado y derivación de clave, y comparación diferencial con `keepassxc-cli`.
- `docs/BENCHMARKS.md`: tiempos de Argon2 de la implementación en Kotlin puro en una JVM de escritorio, y la medición pendiente en móvil.

### Cambiado

- Correcciones locales en el kotpass incluido, listadas en su README: los elementos XML desconocidos se conservan y se reescriben; un icono personalizado o dato personalizado sin fecha de modificación ya no recibe una falsa; un archivo KDBX 4 truncado o manipulado se informa como dañado en lugar de como clave incorrecta; la comprobación de arranque del flujo de KDBX 3.x ahora sí lanza el error (fallo de upstream); un lector público de la cabecera.
- ktlint y detekt comparten un límite de 120 caracteres por línea.

## [0.2.9] - 2026-10-13

### Corregido

- La verificación de dependencias cubre ahora también las variantes de Linux y macOS de `aapt2`, que necesita el ejecutor Linux de la CI; el metadata solo tenía la de Windows.

### Añadido

- `scripts/ci-gradle-error.sh`: la CI publica el bloque «What went wrong» de una ejecución fallida de Gradle como anotación, legible sin iniciar sesión.

## [0.2.8] - 2026-10-13

### Añadido

- Tres bóvedas de prueba más guardadas desde la interfaz de KeePassXC: Twofish con Argon2d, ChaCha20 con Argon2id y Argon2d con una etiqueta y un campo TOTP. El corpus de `testdata/` queda completo (18 bóvedas).

## [0.2.7] - 2026-10-13

### Corregido

- Los workflows de CI y de publicación localizan `sdkmanager` dentro de `ANDROID_HOME` en lugar de depender del `PATH`, donde el ejecutor no lo ofrece (código de salida 127).

## [0.2.6] - 2026-10-13

### Añadido

- Corpus de bóvedas de prueba ficticias en `testdata/` (15 bóvedas y 3 archivos de clave) escritas por KeePass 2.61 y KeePassXC 2.7.12: AES-256 y ChaCha20, AES-KDF, Argon2d y Argon2id, KDBX 3.1, 4.0 y 4.1, archivos de clave, contraseña Unicode, historial, adjuntos, iconos, etiquetas y una bóveda Argon2 de 1 GiB.
- Scripts generadores e inspector de cabeceras en `tools/testdata/`, documentados en `docs/TEST_VAULTS.md` junto con las tres bóvedas que aún requieren la interfaz gráfica de KeePassXC.

## [0.2.5] - 2026-10-13

### Cambiado

- El análisis de vulnerabilidades de la CI pasa a cubrir solo las dependencias que se distribuyen con la aplicación, mediante el bloqueo de dependencias de Gradle (los archivos `gradle.lockfile` de las configuraciones de runtime). Las vulnerabilidades de las herramientas de build las informa un job nuevo que no bloquea.

## [0.2.4] - 2026-10-13

### Añadido

- `docs/TEST_VAULTS.md`: lista de bóvedas ficticias que hay que crear con KeePassXC y KeePass 2.x para el corpus de compatibilidad.

### Corregido

- El paso de licencias del SDK de Android en los workflows de CI y de publicación ya no falla con SIGPIPE bajo `pipefail`.

## [0.2.3] - 2026-10-13

### Añadido

- Flujo de publicación (`.github/workflows/release.yml`): con una etiqueta `vX.Y.Z` comprueba la etiqueta contra `VERSION` y `CHANGELOG.md`, compila el APK release, lo firma con los secretos del repositorio y publica una release de GitHub (preliminar antes de la 1.0) con el APK y su archivo SHA-256.
- Documentación de cómo crear el keystore de firma y los cuatro secretos del repositorio.

## [0.2.2] - 2026-10-13

### Añadido

- Integración continua con GitHub Actions (`.github/workflows/ci.yml`): compilación y comprobaciones de calidad, comprobación de la documentación bilingüe, informe de licencias de dependencias y análisis de vulnerabilidades con OSV.
- `scripts/check-docs-i18n.sh`, que falla si un documento en inglés no tiene su traducción al castellano o al revés, o si falta la línea de idiomas.
- El plugin Gradle License Report, conectado a `generateLicenseReport` para todos los subproyectos.

## [0.2.1] - 2026-10-13

### Añadido

- Comprobaciones de calidad integradas en `./gradlew check`: ktlint (con `ktlintFormat` para autocorregir), detekt y Android Lint endurecido en ambos módulos Android, donde `HardcodedText`, `MissingTranslation`, `ExtraTranslation` y las comprobaciones de seguridad son errores.
- Verificación de dependencias de Gradle: todas las dependencias, plugins y herramientas se comprueban con las sumas SHA-256 de `gradle/verification-metadata.xml`.

## [0.2.0] - 2026-10-13

### Añadido

- Esqueleto Gradle multimódulo (fase C1): los módulos Kotlin/JVM 17 `core-vault`, `core-kdbx` y `core-sync`, la librería Android `platform-android` y la aplicación Compose + Material 3 `app-android`, con catálogo de versiones y una prueba JUnit trivial por módulo.
- `app-android` toma su `versionName` de `VERSION` y deriva de él el `versionCode`; el manifest desactiva las copias de seguridad y no declara ningún permiso, y los textos de la interfaz se ofrecen en `en-US` y castellano solo mediante recursos de cadena.
- Instrucciones de compilación, pruebas y empaquetado verificadas en `docs/es/DEVELOPMENT.md`.

## [0.1.2] - 2026-10-13

### Añadido

- ADR-0005: se mantiene kotpass, integrado como módulo y reforzado (pruebas diferenciales contra KeePassXC y keepass-rs, fuzzing, correcciones para conservar el XML, Argon2 nativo opcional), tras reevaluar kotpass, KeePassJava2, keepass-rs y kdbxweb.

## [0.1.1] - 2026-10-13

### Cambiado

- Las traducciones al español pasan a `docs/es/`, con la misma estructura que la versión inglesa; solo `README.es.md` se queda en la raíz. Actualizados los enlaces de idioma y las reglas bilingües de CONTRIBUTING.

## [0.1.0] - 2026-10-13

### Añadido

- Fase de investigación y arquitectura (fases A y B): arquitectura, modelo de amenazas, investigación de dependencias y licencias, hoja de ruta con criterios de aceptación del MVP, plan de desarrollo y registros de decisiones de arquitectura 0001–0004.
- Licencia: GPL-3.0-only con términos adicionales de atribución y marca (sección 7) en `NOTICE`; `CREDITS.md`.
- Documentación del proyecto en inglés y español: README, CONTRIBUTING, SECURITY, CHANGELOG, TODO.
- Archivo `VERSION`.
