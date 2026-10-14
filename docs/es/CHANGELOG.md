# Registro de cambios

[English](../../CHANGELOG.md) | [Español](CHANGELOG.md)

Todos los cambios notables de este proyecto se documentan en este archivo. El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto sigue el [Versionado Semántico](https://semver.org/lang/es/).

## [Sin publicar]

## [0.10.0] - 2026-10-14

### Añadido

- Las copias de conflicto de Syncthing se fusionan en la bóveda automáticamente (fase D7, ADR-0007): al abrir una bóveda y al cargar una versión escrita en otro sitio, las copias `*.sync-conflict-*` que hay junto al archivo de la bóveda se descifran con las credenciales de la bóveda abierta y se pliegan con la semántica de fusión, y el resultado pasa por el canal de guardado seguro. Un registro de huellas por bóveda en almacenamiento privado (excluido de las copias de seguridad) evita descifrar dos veces la misma copia, y una copia solo se registra cuando el guardado que la fusionó se completa. La pantalla de la bóveda dice entonces cuántas copias se han fusionado y ofrece borrarlas, y solo se borran las copias cuya huella grabada sigue coincidiendo: nada se borra sin consentimiento, y si se rechaza las copias se conservan y la oferta vuelve en la siguiente apertura. Una copia escrita con otra contraseña, dañada o aún en transferencia, de otra bóveda o ilegible se informa con su motivo y se deja intacta.

### Cambiado

- `StoragePort` incorpora `name(location)` (el nombre real del archivo en el almacenamiento) y `delete(location)`; la implementación de Android usa el nombre visible del documento y `DocumentsContract.deleteDocument`.

### Nota

- Aún no comprobado en un móvil real: la fusión al abrir, la oferta de borrar las copias y el caso de una copia escrita con otra contraseña necesitan la prueba en dispositivo con Syncthing (D7.5).

## [0.9.0] - 2026-10-14

### Añadido

- Desbloqueo biométrico (fase D6, ADR-0006). Tras un desbloqueo con contraseña, la app ofrece activarlo para esa bóveda (activar, ahora no o no volver a preguntar). Guarda, cifrados con una clave del Keystore de Android por bóveda que exige una biometría fuerte en cada uso (StrongBox si existe, invalidada al registrar nuevos datos biométricos o quitar el bloqueo de pantalla), el hash de la contraseña y del archivo de clave, nunca la contraseña; la derivación de clave se sigue ejecutando en cada desbloqueo. La contraseña se pide de nuevo tras 7 días sin escribirla (de 1 a 30 días en los ajustes) y siempre que la clave se invalide o los datos guardados ya no abran la bóveda, con un mensaje que explica el motivo. Un botón en la pantalla de desbloqueo deja de usarlo para esa bóveda.
- Kotpass incorpora `Credentials.fromHashes` (cambio local anotado en su README) y el motor `KdbxEngine.hashedForm`, de modo que una bóveda se abre desde los componentes ya hasheados. Probado con todo el corpus de pruebas, incluidas las bóvedas con archivo de clave.

### Cambiado

- `MainActivity` extiende `FragmentActivity` (lo necesita `BiometricPrompt`) y la app depende de `androidx.biometric` 1.1.0 (Apache-2.0).

### Nota

- Aún no comprobado en un móvil real: las pruebas unitarias cubren las reglas de almacenamiento y el formato de los componentes, pero el Keystore y el diálogo necesitan la matriz de pruebas en dispositivo (en el TODO).

## [0.8.1] - 2026-10-14

### Añadido

- Pantalla de ajustes (engranaje en el inicio): tiempo de bloqueo automático, bloquear al apagar la pantalla, permitir capturas de pantalla y cuánto tiempo queda un secreto copiado en el portapapeles. Comprobado en un Pixel 10 Pro: la bóveda se bloquea tras el tiempo elegido en segundo plano, la ventana no se puede capturar con las capturas desactivadas, un cambio externo aparece en segundos, y ni la contraseña ni ningún texto de entradas aparecen en el registro del sistema ni en los archivos de la app (AC6).

## [0.8.0] - 2026-10-14

### Añadido

- Protecciones (fase D5): la bóveda abierta se bloquea sola tras un tiempo en segundo plano (60 segundos por defecto; opciones desde «inmediatamente» hasta «nunca»), al apagarse la pantalla y a mano; al bloquear se borran las credenciales, se descarta la bóveda de la memoria, se limpia el portapapeles y se vuelve a la pantalla de inicio. La ventana se marca como segura (sin capturas de pantalla y en blanco en la lista de apps recientes) salvo que el ajuste permita las capturas. El tiempo que un secreto copiado queda en el portapapeles es un ajuste (30 segundos por defecto). Los ajustes se guardan en privado y por ahora solo se cambian desde el código: la pantalla de ajustes llega a continuación.
- Los cambios que otro programa (Syncthing, KeePassXC) haga en el archivo de la bóveda se detectan en unos 5 segundos mientras la app está a la vista, y se cargan solos cuando la bóveda abierta no tiene ediciones sin guardar; si las tiene, se fusionan en el siguiente guardado.

### Cambiado

- Un formulario de edición abierto conserva sus campos cuando la bóveda se recarga desde el disco, y se cierra si su entrada se eliminó en otro sitio.

## [0.7.5] - 2026-10-14

### Cambiado

- kotpass vendorizado: `readHeader` comprueba ahora la firma KDBX antes de analizar la cabecera, de modo que lo que no es KDBX se rechaza como tal.
- TODO: se registran las ideas en evaluación (Syncthing integrado, configurar un dispositivo nuevo con una frase semilla, copias de seguridad en la nube, mantener kotpass fuerte). Hay cuatro correcciones de kotpass, con pruebas, preparadas como ramas del fork del proyecto para el mantenedor original.

## [0.7.4] - 2026-10-14

### Cambiado

- El README y la hoja de ruta describen ahora el estado real del proyecto (qué funciona en Android, qué falta, cómo probar una compilación de depuración con Syncthing).

## [0.7.3] - 2026-10-14

### Corregido

- El guardado fallaba después de que Syncthing sustituyera el archivo de la bóveda: Android revoca el permiso sobre un archivo suelto en ese caso (`SecurityException`, comprobado en un Pixel 10 Pro). Las bóvedas se añaden y crean ahora con un permiso de carpeta (`ACTION_OPEN_DOCUMENT_TREE`), que sobrevive a la sustitución, como preveía el ADR-0004. La pantalla de inicio pide la carpeta que contiene la bóveda, lista los archivos `.kdbx` que hay en ella y crea las bóvedas nuevas dentro.
- La fusión de una bóveda editada en otro sitio mientras está abierta se ha comprobado en un dispositivo real: una edición del móvil y otra hecha con KeePassXC entre medias acaban las dos en el archivo (criterio de aceptación AC4).

## [0.7.2] - 2026-10-14

### Corregido

- El formulario de entrada nueva ya no se abre con el error «una entrada necesita un título» a la vista; solo aparece después de escribir el título y vaciarlo.

## [0.7.1] - 2026-10-13

### Corregido

- El contenido ya no queda oculto bajo la barra de estado, la de navegación ni el teclado (el menú con la papelera quedaba fuera de alcance).
- El portapapeles se borra al volver KeyNexa al primer plano pasados 30 segundos y al bloquear la bóveda: Android no deja que una app en segundo plano modifique el portapapeles, así que un temporizador no bastaba.

## [0.7.0] - 2026-10-13

### Añadido

- Pantallas de contenido (fase D4.4): explorar grupos y entradas, búsqueda (sin distinguir mayúsculas ni acentos, nunca en contraseñas), detalle de entrada (contraseña oculta y visible 30 segundos como máximo, copia con marca de portapapeles sensible que se borra a los 30 segundos, abrir direcciones web), crear y editar entradas y campos personalizados, generador de contraseñas integrado (`PasswordGenerator`, aleatorio seguro), papelera con restaurar, borrado definitivo y vaciado, y una barra de estado de guardado que explica cada fallo y ofrece reintentar. Cada cambio se guarda al instante con el guardado seguro.
- Pantallas de acceso (fase D4.3): inicio con las bóvedas conocidas (añadir, crear, quitar de la lista sin tocar el archivo), formulario de bóveda nueva (nombre, contraseña maestra dos veces, archivo de clave opcional, aviso de contraseña irrecuperable) y pantalla de desbloqueo (mensajes claros para cada fallo, progreso mientras se deriva la clave, opción de reconectar el archivo), conectadas en `MainActivity` con los selectores de archivo del sistema y permisos de lectura y escritura duraderos.
- Base de la app (fase D4.2): `AppContainer` que conecta almacenamiento, motor, guardado seguro y almacenes privados; `VaultSession` (desbloquear, crear, editar con guardado seguro automático, reintentar, bloquear borrando las credenciales y recuperación tras un corte al abrir); la lista de bóvedas conocidas; y la navegación por estado.
- Casos de uso de edición en `core-vault` (fase D4.1): `VaultEditor` (alta, edición con historial, mover, borrar a la papelera, restaurar, vaciarla y borrado definitivo con registro de objetos eliminados), `VaultSearch` (sin distinguir mayúsculas ni acentos, nunca lee contraseñas ni valores protegidos, deja fuera la papelera) y `NewVault` (bóveda KDBX 4.1 vacía con ChaCha20 y Argon2id).
- ADR-0006: diseño del desbloqueo biométrico (fase D6): componentes de la clave compuesta envueltos por una clave del Android Keystore por bóveda, ligada a biometría fuerte en cada uso, invalidada al cambiar la biometría y con petición periódica de la contraseña maestra.

## [0.6.1] - 2026-10-13

### Añadido

- Almacenamiento Android (fase D3) en `platform-android`: `SafStoragePort` (Storage Access Framework; escribe con `"rwt"`, recurre a `"rw"` y siempre trunca de forma explícita, convierte cualquier fallo del proveedor o permiso revocado en `IOException`), `SafVaultAccess` (concesiones persistentes de carpeta y archivo, localizar la bóveda por nombre dentro de una carpeta concedida, `isReachable` para el flujo «reconectar archivo»), y `FileBackupStore` y `FileSaveJournal` (escritura atómica en `noBackupFilesDir`; un diario ilegible es un error, nunca «nada pendiente»).
- `conflictCopiesOf` en `core-sync`: reconoce las copias `*.sync-conflict-*` de Syncthing junto a la bóveda, las más nuevas primero, para que la interfaz las ofrezca para fusionar.

### Eliminado

- El `PlatformModule` de relleno.

## [0.6.0] - 2026-10-13

### Añadido

- Fusión (fase D2): `KeePassMerger` en `core-sync`, una fusión a dos bandas con la semántica de sincronización de KeePass escrita a partir de su comportamiento documentado: emparejamiento por UUID, gana la modificación más reciente y la otra versión queda en el historial, historiales unidos y recortados a los límites de la bóveda, ubicación según el cambio de ubicación más reciente, borrados solo mediante registros de objetos eliminados y nunca sobre cambios posteriores, metadata campo a campo. Los bucles por movimientos cruzados y los registros de borrado del grupo raíz se resuelven sin perder datos. `SafeSaver` la usa para incorporar los cambios externos antes de escribir.
- 44 pruebas de fusión: escenarios, invariantes comprobadas sobre pares de bóvedas generados al azar (no se pierde ningún objeto, sin bucles, sin adjuntos colgantes, resultados simétricos) y guardados completos a través de `SafeSaver` (AC4, parte unitaria).

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
