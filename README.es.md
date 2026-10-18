# KeyNexa

[English](README.md) | [Español](README.es.md)

*Your keys. Your vault. Your control.*

Un gestor de contraseñas multiplataforma que prioriza la seguridad, construido sobre bibliotecas criptográficas consolidadas, bóvedas locales y sincronización fluida con Syncthing.

> **Estado del proyecto: 1.0 de Android publicada (2026-10-18); después viene el cliente de escritorio.** Puedes crear o abrir una bóveda KDBX, explorarla, buscar, añadir y editar entradas (con adjuntos y borrado de las versiones anteriores de una entrada), usar la papelera, generar contraseñas y ver los códigos de un solo uso (TOTP) de una entrada; el guardado pasa por un flujo seguro, y los cambios hechos en otro sitio (por ejemplo con KeePassXC a través de Syncthing) se fusionan, también las copias de conflicto. Además: desbloqueo biométrico, bloqueo automático (también en segundo plano), protección de pantalla, el teclado de KeyNexa para las contraseñas maestras, y copias cifradas opcionales en la nube (compatibles con S3: Backblaze B2, Cloudflare R2) en la variante `cloud`. Consulta la [revisión de seguridad](docs/es/SECURITY_REVIEW.md).

## Qué será KeyNexa

- Un gestor de contraseñas para Android en primer lugar, con clientes para Windows y Linux más adelante.
- **No previstos: macOS ni iOS (iPhone/iPad).** El proyecto no dispone de hardware de Apple para probar, y un programa de este tipo no se puede construir con responsabilidad solo con emuladores.
- Totalmente compatible con el formato **KDBX** de KeePass (3.1, 4.0 y 4.1), de modo que tu bóveda también se abre en KeePassXC, KeePassDX y otros clientes de KeePass.
- Local primero: tu bóveda es un archivo que te pertenece. Sin cuentas, sin servidores de KeyNexa, sin telemetría.
- Diseñado para vivir en una carpeta sincronizada con [Syncthing](https://syncthing.net/), detectando los cambios externos y fusionando los conflictos en lugar de perder datos en silencio.
- Interfaz bilingüe: inglés (`en-US`) y español (`es-ES`).

Consulta la [hoja de ruta](docs/es/ROADMAP.md) para ver qué está planificado en cada fase.

## Documentación

| Documento | Contenido |
| --- | --- |
| [Arquitectura](docs/es/ARCHITECTURE.md) | Módulos, elecciones tecnológicas, estrategia multiplataforma |
| [Modelo de amenazas](docs/es/THREAT_MODEL.md) | Activos, atacantes, amenazas, mitigaciones, riesgos residuales |
| [Instalar en Windows](docs/es/INSTALL_WINDOWS.md) | MSI y ZIP portable del cliente de Windows (versión preliminar), comprobaciones y carpeta de datos |
| [Privacidad](docs/es/PRIVACY.md) | Qué hace cada variante con tus datos |
| [Dependencias y licencias](docs/es/DEPENDENCIES_AND_LICENSES.md) | Bibliotecas investigadas, licencias verificadas, fuentes |
| [Desarrollo](docs/es/DEVELOPMENT.md) | Requisitos, cadena de herramientas y cómo compilar y probar |
| [Hoja de ruta](docs/es/ROADMAP.md) | MVP, criterios de aceptación y fases posteriores |
| [Decisiones](docs/es/decisions/README.md) | Registros de decisiones de arquitectura (ADR) |
| [Cómo contribuir](docs/es/CONTRIBUTING.md) | Flujo de trabajo, reglas de documentación bilingüe, versionado |
| [Política de seguridad](docs/es/SECURITY.md) | Cómo notificar vulnerabilidades |
| [Registro de cambios](docs/es/CHANGELOG.md) | Cambios por versión |
| [Pendientes](docs/es/TODO.md) | Trabajo pendiente |

## Requisitos, instalación y uso

Las versiones se publican en [GitHub Releases](https://github.com/catlinux/KeyNexa/releases) en dos compilaciones de la misma app, firmadas con la misma clave: `keynexa-<versión>.apk` (**offline**: sin ningún permiso de Internet) y `keynexa-cloud-<versión>.apk` (**cloud**: añade las copias cifradas opcionales en la nube, así que pide el permiso de Internet; consulta [Privacidad](docs/es/PRIVACY.md) y la [guía de las copias en la nube](docs/es/CLOUD_SETUP.md)). Instala una de las dos; instalar después la otra conserva tus bóvedas y ajustes. Descarga el APK y su archivo `.sha256` y, en la carpeta donde estén ambos, comprueba la suma y la firma (para la compilación cloud, usa `keynexa-cloud-1.0.0.apk`):

```sh
sha256sum -c keynexa-1.0.0.apk.sha256
apksigner verify --print-certs keynexa-1.0.0.apk
```

Compara la huella SHA-256 del certificado que muestra `apksigner` con `51:ca:da:3c:d0:8c:35:5f:13:67:90:b1:be:83:09:00:8b:b9:a3:1e:e9:44:b4:e6:e3:74:7e:6a:e9:d0:44:9b`. Solo la compilación `offline` carece del permiso de Internet; el flujo de publicación lo comprueba antes de publicar.

Como alternativa, puedes compilar desde el código fuente. Necesitas JDK 17 y Android SDK 36 (consulta [Desarrollo](docs/es/DEVELOPMENT.md) para indicar a Gradle dónde está el SDK). KeyNexa se distribuye en dos variantes: `offline` (sin acceso a la red) y `cloud` (declara `INTERNET` para las copias en la nube opcionales; consulta la [guía de configuración de las copias en la nube](docs/es/CLOUD_SETUP.md)). Desde la raíz del repositorio:

```sh
./gradlew build
./gradlew test
./gradlew :apps:android:assembleOfflineDebug
./gradlew :apps:android:assembleCloudDebug
```

En Windows usa `gradlew.bat` en lugar de `./gradlew`. Los APK de depuración se generan en `apps/android/build/outputs/apk/offline/debug/keynexa-android-offline-debug.apk` y `apps/android/build/outputs/apk/cloud/debug/keynexa-android-cloud-debug.apk`, y se pueden instalar en un móvil para probar (usa solo bóvedas ficticias). Son una app aparte, *KeyNexa Debug* (`io.github.catlinux.keynexa.debug`), que se instala junto a un KeyNexa publicado sin tocar sus bóvedas ni sus ajustes. Instalar una variante sobre la otra conserva las bóvedas y los ajustes (comprobado en un móvil). Para probar la sincronización, deja la bóveda en una carpeta compartida con Syncthing y añádela a KeyNexa con *Abrir una bóveda existente*, eligiendo la carpeta (no un archivo suelto: Android revoca el permiso sobre un archivo suelto cuando Syncthing lo sustituye).

## Créditos y licencia

**KeyNexa** está desarrollado y mantenido por **CatLinux**.
Copyright (C) 2026 CatLinux.

Distribuido bajo la [GNU General Public License v3.0](LICENSE) (`GPL-3.0-only`): puedes usarlo, estudiarlo, modificarlo y compartirlo, también con fines comerciales, pero cualquier versión distribuida, modificada o no, debe permanecer bajo GPL-3.0 e incluir su código fuente completo.

Conforme a la sección 7 de la GPL, también se aplican los [términos adicionales de NOTICE](NOTICE): las copias y los forks deben conservar esta atribución y enlazar a https://github.com/catlinux/KeyNexa, las versiones modificadas deben indicarse como tales, y el nombre y el logotipo de KeyNexa no se licencian para forks.

### Material de terceros

El esqueleto del proyecto usa los componentes siguientes. Cada uno conserva su propia licencia y sus avisos; la investigación completa está en [Dependencias y licencias](docs/es/DEPENDENCIES_AND_LICENSES.md).

| Componente | Autor | Licencia | Enlace |
| --- | --- | --- | --- |
| Biblioteca estándar de Kotlin y plugins de Gradle | JetBrains | Apache-2.0 | https://kotlinlang.org/ |
| AndroidX Core, AndroidX Activity, Jetpack Compose (IU y Material 3) | The Android Open Source Project | Apache-2.0 | https://developer.android.com/jetpack |
| JUnit 4 (solo pruebas unitarias; no se distribuye) | Equipo de JUnit | EPL-1.0 | https://junit.org/junit4/ |
| kotpass 0.13.0, incluido en `third_party/kotpass` (motor KDBX) | Denis T. | MIT | https://github.com/keemobile/kotpass |
| Okio 3.15.0 | Square, Inc. | Apache-2.0 | https://github.com/square/okio |
| Kotlin XML Builder, incluido en kotpass | Jason Blackwell y colaboradores | Apache-2.0 | https://github.com/redundent/kotlin-xml-builder |
| Apache Commons Lang (partes), incluido en kotpass | The Apache Software Foundation | Apache-2.0 | https://github.com/apache/commons-lang |
| Motores criptográficos portados de Bouncy Castle (Argon2, Blake2b, ChaCha, Salsa20, Twofish, PKCS7), incluidos en kotpass | The Legion of the Bouncy Castle Inc. | Bouncy Castle License (texto MIT) | https://github.com/bcgit/bc-java |

Créditos completos: [CREDITS](docs/es/CREDITS.md).
