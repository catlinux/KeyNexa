# KeyNexa

[English](README.md) | [Español](README.es.md)

*Your keys. Your vault. Your control.*

Un gestor de contraseñas multiplataforma que prioriza la seguridad, construido sobre bibliotecas criptográficas consolidadas, bóvedas locales y sincronización fluida con Syncthing.

> **Estado del proyecto: desarrollo temprano (0.10.x; versión preliminar v0.10.2 publicada).** Solo Android, para pruebas. Ya se puede crear o abrir una bóveda KDBX, explorarla, buscar, añadir y editar entradas (con archivos adjuntos que se pueden guardar en disco y borrar las versiones anteriores de una entrada), usar la papelera y generar contraseñas; el guardado pasa por un flujo seguro y los cambios hechos en otro sitio (por ejemplo con KeePassXC a través de Syncthing) se fusionan. **También funciona:** bloqueo automático (incluso en segundo plano), protección de pantalla, una pantalla de ajustes, detección en segundos de los cambios hechos en otro sitio y fusión de las copias de conflicto de Syncthing al abrir la bóveda, con opción de borrarlas tras dar consentimiento. **También:** desbloqueo biométrico y el estado de sincronización del archivo de la bóveda con un aviso opcional tras días sin cambios. **Todavía no está hecho:** borrado del portapapeles totalmente fiable y los clientes de Windows y Linux. No guardes contraseñas reales en KeyNexa hasta que se publique una versión final.

## Qué será KeyNexa

- Un gestor de contraseñas para Android en primer lugar, con clientes para Windows y Linux más adelante.
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

Hay una versión preliminar en [GitHub Releases](https://github.com/catlinux/KeyNexa/releases). Descarga `keynexa-0.10.2.apk` y `keynexa-0.10.2.apk.sha256`. En la carpeta donde estén ambos archivos, comprueba la suma del APK y su firma:

```sh
sha256sum -c keynexa-0.10.2.apk.sha256
apksigner verify --print-certs keynexa-0.10.2.apk
```

Compara la huella SHA-256 del certificado que muestra `apksigner` con `51:ca:da:3c:d0:8c:35:5f:13:67:90:b1:be:83:09:00:8b:b9:a3:1e:e9:44:b4:e6:e3:74:7e:6a:e9:d0:44:9b`. La compilación `offline` publicada no solicita el permiso de Internet; la `cloud` lo declara para las copias en la nube opcionales cuando lleguen.

Como alternativa, puedes compilar desde el código fuente. Necesitas JDK 17 y Android SDK 36 (consulta [Desarrollo](docs/es/DEVELOPMENT.md) para indicar a Gradle dónde está el SDK). KeyNexa se distribuye en dos variantes: `offline` (sin acceso a la red) y `cloud` (declara `INTERNET` para las copias en la nube opcionales; consulta la [guía de configuración de las copias en la nube](docs/es/CLOUD_SETUP.md)). Desde la raíz del repositorio:

```sh
./gradlew build
./gradlew test
./gradlew :app-android:assembleOfflineDebug
./gradlew :app-android:assembleCloudDebug
```

En Windows usa `gradlew.bat` en lugar de `./gradlew`. Los APK de depuración se generan en `app-android/build/outputs/apk/offline/debug/app-android-offline-debug.apk` y `app-android/build/outputs/apk/cloud/debug/app-android-cloud-debug.apk`, y se pueden instalar en un móvil para probar (usa solo bóvedas ficticias). Instalar una variante sobre la otra no está verificado. Para probar la sincronización, deja la bóveda en una carpeta compartida con Syncthing y añádela a KeyNexa con *Abrir una bóveda existente*, eligiendo la carpeta (no un archivo suelto: Android revoca el permiso sobre un archivo suelto cuando Syncthing lo sustituye).

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
