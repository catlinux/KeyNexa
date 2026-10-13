# KeyNexa

[English](README.md) | [Español](README.es.md)

*Your keys. Your vault. Your control.*

Un gestor de contraseñas multiplataforma que prioriza la seguridad, construido sobre bibliotecas criptográficas consolidadas, bóvedas locales y sincronización fluida con Syncthing.

> **Estado del proyecto: fase de diseño (v0.1.0).** Todavía no hay ninguna aplicación. Este repositorio contiene por ahora la investigación, la arquitectura, el modelo de amenazas y la hoja de ruta. Nada de lo descrito como *planificado* está implementado. No uses KeyNexa para guardar contraseñas reales hasta que una versión publicada diga lo contrario.

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
| [Desarrollo](docs/es/DEVELOPMENT.md) | Requisitos y cómo compilar (cuando exista código) |
| [Hoja de ruta](docs/es/ROADMAP.md) | MVP, criterios de aceptación y fases posteriores |
| [Decisiones](docs/es/decisions/README.md) | Registros de decisiones de arquitectura (ADR) |
| [Cómo contribuir](docs/es/CONTRIBUTING.md) | Flujo de trabajo, reglas de documentación bilingüe, versionado |
| [Política de seguridad](docs/es/SECURITY.md) | Cómo notificar vulnerabilidades |
| [Registro de cambios](docs/es/CHANGELOG.md) | Cambios por versión |
| [Pendientes](docs/es/TODO.md) | Trabajo pendiente |

## Requisitos, instalación y uso

Todavía no disponible: no hay ninguna aplicación que se pueda compilar. [Desarrollo](docs/es/DEVELOPMENT.md) enumera la cadena de herramientas prevista y contendrá instrucciones de compilación verificadas cuando exista el esqueleto del proyecto.

## Créditos y licencia

**KeyNexa** está desarrollado y mantenido por **CatLinux**.
Copyright (C) 2026 CatLinux.

Distribuido bajo la [GNU General Public License v3.0](LICENSE) (`GPL-3.0-only`): puedes usarlo, estudiarlo, modificarlo y compartirlo, también con fines comerciales, pero cualquier versión distribuida, modificada o no, debe permanecer bajo GPL-3.0 e incluir su código fuente completo.

Conforme a la sección 7 de la GPL, también se aplican los [términos adicionales de NOTICE](NOTICE): las copias y los forks deben conservar esta atribución y enlazar a https://github.com/catlinux/KeyNexa, las versiones modificadas deben indicarse como tales, y el nombre y el logotipo de KeyNexa no se licencian para forks.

### Material de terceros

Este repositorio todavía no contiene material de terceros. Las bibliotecas previstas para la aplicación, y sus licencias verificadas, figuran en [Dependencias y licencias](docs/es/DEPENDENCIES_AND_LICENSES.md); se añadirán a la tabla siguiente cuando se incluyan realmente.

| Componente | Autor | Licencia | Enlace |
| --- | --- | --- | --- |

Créditos completos: [CREDITS](docs/es/CREDITS.md).
