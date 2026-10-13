# KeyNexa

[English](README.md) | [Español](README.es.md)

*Your keys. Your vault. Your control.*

Un gestor de contraseñas multiplataforma que prioriza la seguridad, construido sobre bibliotecas criptográficas consolidadas, bóvedas locales y sincronización fluida con Syncthing.

> **Estado del proyecto: desarrollo temprano (v0.2.0).** El repositorio contiene la investigación, la arquitectura, el modelo de amenazas y un esqueleto de proyecto compilable, pero todavía no hay ninguna aplicación usable. Nada de lo descrito como *planificado* está implementado. No uses KeyNexa para guardar contraseñas reales hasta que una versión publicada diga lo contrario.

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
| [Dependencias y licencias](docs/es/DEPENDENCIES_AND_LICENSES.md) | Bibliotecas investigadas, licencias verificadas, fuentes |
| [Desarrollo](docs/es/DEVELOPMENT.md) | Requisitos, cadena de herramientas y cómo compilar y probar |
| [Hoja de ruta](docs/es/ROADMAP.md) | MVP, criterios de aceptación y fases posteriores |
| [Decisiones](docs/es/decisions/README.md) | Registros de decisiones de arquitectura (ADR) |
| [Cómo contribuir](docs/es/CONTRIBUTING.md) | Flujo de trabajo, reglas de documentación bilingüe, versionado |
| [Política de seguridad](docs/es/SECURITY.md) | Cómo notificar vulnerabilidades |
| [Registro de cambios](docs/es/CHANGELOG.md) | Cambios por versión |
| [Pendientes](docs/es/TODO.md) | Trabajo pendiente |

## Requisitos, instalación y uso

La aplicación todavía no es usable, pero el proyecto ya se puede compilar. Necesita JDK 17 y el SDK de Android 36 (consulta [Desarrollo](docs/es/DEVELOPMENT.md) para indicar a Gradle dónde está el SDK). Desde la raíz del repositorio:

```sh
./gradlew build
./gradlew test
./gradlew :app-android:assembleDebug
```

En Windows usa `gradlew.bat` en lugar de `./gradlew`. El APK de depuración se genera en `app-android/build/outputs/apk/debug/app-android-debug.apk`.

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
