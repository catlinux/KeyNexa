# Hoja de ruta de KeyNexa: fases C y D (MVP Android)

Fases A y B terminadas en 0.1.0 (documentación en `docs/`). Este plan lo ejecuta una sesión nueva sin contexto previo: lee primero `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/THREAT_MODEL.md` y `docs/decisions/`.

**Normas para cada paso**

- Un commit (o varios coherentes) por paso, con la versión subida en `VERSION` y su entrada en `CHANGELOG.md` + `docs/es/CHANGELOG.md`. Toda documentación tocada, en los dos idiomas.
- Marca el paso como `[x]` aquí al terminarlo, con la versión.
- Si algo contradice la arquitectura o el plan no lo cubre: PARA, anótalo en «Incidencias» al final y deja que lo resuelva una sesión con Opus.
- «AgentRelay» significa delegar con `agentrelay.cmd run` (ejecutor configurado, hoy DeepSeek) y revisar el diff tú. Divide en tareas de ≤4 archivos.
- Sin push ni tags: se piden al usuario.

## Fase C: base del proyecto

- [x] **C1. Esqueleto Gradle multimódulo** · 0.2.0 (hecho) (menor: primera base compilable) · Orquesta Sonnet (esfuerzo medio), implementa AgentRelay (alto).
  - Archivos: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, wrapper de Gradle, módulos `core-vault`, `core-kdbx`, `core-sync` (Kotlin JVM, toolchain 17), `platform-android` (librería Android), `app-android` (Compose, Material 3; minSdk 26, compile/target 36; `applicationId` `io.github.catlinux.keynexa`).
  - `versionName` se lee de `VERSION` y `versionCode` se deriva de él (MAJOR*10000+MINOR*100+PATCH).
  - Recursos `values/strings.xml` (en-US) y `values-es/strings.xml`, sin textos incrustados. Manifest con `allowBackup="false"`, `dataExtractionRules` y `fullBackupContent` que excluyen todo, sin permiso INTERNET.
  - Terminado: `./gradlew build` y `./gradlew test` pasan en local (anota las órdenes reales en `docs/DEVELOPMENT.md` (y `docs/es/DEVELOPMENT.md`) y README «Instalación/Uso» si ya aplica).
- [x] **C2. Calidad** · 0.2.1 (hecho) (parche: interno) · AgentRelay (medio).
  - ktlint (o Spotless+ktlint), detekt, Android Lint con `HardcodedText`, `MissingTranslation` y los de seguridad como error; verificación de dependencias de Gradle (`gradle/verification-metadata.xml`).
  - Terminado: `./gradlew check` pasa; documentado en `docs/DEVELOPMENT.md` (y `docs/es/DEVELOPMENT.md`).
- [x] **C3. CI** · 0.2.2 (hecho) (parche) · AgentRelay (medio).
  - `.github/workflows/ci.yml`: build, test, check, informe de licencias (p. ej. plugin de licencias de Gradle) y escaneo de vulnerabilidades (p. ej. OSV-Scanner) en push y PR.
  - `scripts/check-docs-i18n.sh`: falla si un `.md` de documentación en inglés no tiene su traducción en `docs/es/` con la misma ruta (o viceversa; `README.md` ↔ `README.es.md`), o si a alguno le falta la línea de idiomas. Se ejecuta en CI. Excluye `AGENTS.md`, `CLAUDE.md`, `.agents/`.
  - Terminado: workflow válido (`actionlint` si está disponible) y script probado en local.
- [x] **C4. Workflow de publicación** · 0.2.3 (hecho, falta compilación firmada de prueba en GitHub) (parche) · Sonnet (medio), sin delegar (maneja secretos).
  - `.github/workflows/release.yml`: en tag `vX.Y.Z`, comprueba que coincide con `VERSION`, compila el APK release, lo firma con secretos del repo (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), publica release (pre-release antes de 1.0) con SHA-256.
  - Documenta en `docs/DEVELOPMENT.md` (y `docs/es/DEVELOPMENT.md`) cómo crear el keystore y los secretos. Keystore y los cuatro secretos ya creados por el usuario el 2026-10-13 (keystore fuera del repo, alias `keynexa`); falta solo el workflow y una compilación firmada de prueba.
- [x] **C5. Motor KDBX y corpus de pruebas** · 0.3.0 (hecho) (menor) · Sonnet (alto), sin delegar la interfaz; AgentRelay para tests repetitivos.
  - `core-vault`: modelo de dominio y puertos `KdbxEngine`, `StoragePort`. `core-kdbx`: adaptador sobre kotpass. Según ADR-0005: importar kotpass en `third_party/kotpass/` (commit `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945`, con su LICENSE.txt, avisos y un README con commit upstream y cambios locales) como módulo Gradle.
  - `testdata/`: bóvedas ficticias (contraseñas documentadas) de KeePassXC y KeePass 2.x con AES/ChaCha20/Twofish × AES-KDF/Argon2d/Argon2id, KDBX 3.1/4.0/4.1, key file, adjuntos, historial, iconos, CustomData. **Acción del usuario** si no hay KeePassXC instalado: generarlas siguiendo una lista que prepares.
  - Tests de ida y vuelta (AC1, AC2): comprobar qué se pierde (XML desconocido) y corregirlo en `third_party/kotpass` (conservar XML desconocido y versión KDBX), documentándolo en su README.
  - Mide Argon2 (memoria 64 MiB y 1 GiB) en emulador y, si el usuario puede, en móvil real; si es inaceptable, propón argon2kt (nuevo ADR).
  - Añade kotpass, okio y los avisos de Bouncy Castle/Apache incluidos en kotpass a `CREDITS.md` (y `docs/es/CREDITS.md`) y a la tabla de terceros de los README.
- [~] **C5b. Refuerzo de kotpass** · 0.3.1 (parcial: fuzzing por mutación y diferencial con keepassxc-cli hechos; faltan keepass-rs y Jazzer, ver TODO) (parche) · Sonnet (alto).
  - Pruebas diferenciales en CI: cada bóveda escrita por los tests se relee con `keepassxc-cli` y con keepass-rs (herramienta pequeña en `tools/`, solo CI) y se comparan contenidos.
  - Fuzzing del decodificador con Jazzer (solo tests). Terminado: CI verde y ningún fallo sin entender.
- [x] **C6. Sistema de diseño** · 0.4.0 (propuesta hecha; falta la aprobación de la identidad por el usuario) (menor) · Sonnet (alto) propone; el usuario aprueba identidad.
  - `docs/DESIGN.md` + `docs/es/DESIGN.md`: principios, paleta (contraste AA), tipografía (Inter o Manrope, OFL), espaciado, componentes, estados (carga, error, bloqueo, vacío), mensajes de seguridad, tema claro/oscuro.
  - Icono original (vector, adaptive icon) sin parecido con KeePassDX/KP2A. Tema Compose en `app-android`.

## Fase D: MVP seguro

- [x] **D1. Guardado seguro y detección de cambios** (`core-sync`) · 0.5.0 (hecho; faltan las implementaciones Android de los puertos, ver TODO) · **Opus (alto)**: diseño delicado.
  - Huella SHA-256, canal de guardado seguro de `docs/ARCHITECTURE.md`, copias privadas rotativas, lectura tolerante con reintentos. `StoragePort` falso con inyección de fallos (corte a mitad, `w` sin truncar, archivo cambiado durante la escritura). Terminado: AC3 y AC5 con tests.
- [x] **D2. Fusión** (`core-sync`) · 0.6.0 (hecho; falta la prueba de AC4 en dispositivo real) · **Opus (alto)**.
  - Semántica de ARCHITECTURE «Merge» (UUID, tiempos, historial, DeletedObjects, ubicación), implementada desde la documentación, sin copiar código GPL. Tests exhaustivos. Terminado: AC4 (parte unitaria).
- [x] **D3. Almacenamiento Android** (`platform-android`) · 0.6.1 (hecho; la prueba en dispositivo con Syncthing queda para E1) · Sonnet (alto).
  - `StoragePort` con SAF: concesión de carpeta (preferida) y de archivo, `"rwt"` con alternativa `"rw"`+truncado, flujo «reconectar archivo», detección de `*.sync-conflict-*`.
- [ ] **D4. UI del MVP** · 0.7.0 · Sonnet (medio) orquesta, AgentRelay implementa pantalla a pantalla.
  - Selección/creación de bóveda, desbloqueo, lista de grupos, búsqueda, detalle, edición, papelera. Strings en/es, accesibilidad. Subpasos (cada uno con su commit; la versión 0.7.0 se sube en D4.5):
  - [ ] **D4.1 Casos de uso puros** (`core-vault`, delegado, esfuerzo alto): `VaultSearch`, `VaultEditor` (alta/edición con historial, grupos, mover, papelera, restaurar, borrado definitivo con `DeletedObject`), `NewVault` (bóveda nueva Argon2id). Reloj inyectado, pruebas exhaustivas, sin Android.
  - [ ] **D4.2 Base de la app** (`app-android`, yo): contenedor de dependencias (SAF, motor, `SafeSaver`, almacenes en `noBackupFilesDir`), `VaultSession` (desbloquear, guardar, bloquear, recuperación tras corte), lista de bóvedas conocidas, navegación por estado.
  - [ ] **D4.3 Pantallas de acceso** (delegado): inicio (bóvedas conocidas, añadir/crear), desbloqueo (contraseña y archivo de clave, errores claros, reconectar archivo).
  - [ ] **D4.4 Pantallas de contenido** (delegado, una tarea por pantalla): grupos y entradas con búsqueda, detalle, edición, papelera, aviso de conflictos `*.sync-conflict-*`.
  - [ ] **D4.5 Cierre**: revisión, accesibilidad, strings en/es, documentación y versión 0.7.0.
- [ ] **D5. Protecciones** · 0.7.1 · Sonnet (alto), sin delegar.
  - Bloqueo manual/temporizador/pantalla apagada, `FLAG_SECURE`, portapapeles sensible con borrado, flags de IME, limpieza de memoria al bloquear. Terminado: AC6, AC7.
- [ ] **D6. Desbloqueo biométrico** · 0.8.0 · **Opus (alto)** diseña y revisa; Sonnet implementa.
  - Diseño cerrado en ADR-0006 (docs/decisions/0006-biometric-unlock.md): seguirlo al pie de la letra. Keystore AES-GCM, `setUserAuthenticationRequired`, `BIOMETRIC_STRONG`, `CryptoObject`, invalidación por nuevas huellas, StrongBox si existe, reintroducir contraseña periódicamente.

## Fase E: revisión

- [ ] **E1. Revisión** · 0.9.0 · **Opus (alto)**: desviaciones de arquitectura, dependencias/licencias, código sensible, pruebas en dispositivo con Syncthing-Fork (AC4), docs al día, riesgos pendientes. Propuesta de primera pre-release.

## Incidencias

(ninguna)
