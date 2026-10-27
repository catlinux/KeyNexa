# Registros de decisiones de arquitectura

[English](../../decisions/README.md) | [Español](README.md)

Cada registro explica una decisión importante: contexto, opciones, decisión y consecuencias. Un registro nunca se reescribe una vez aceptado; uno nuevo lo sustituye.

| ADR | Título | Estado |
| --- | --- | --- |
| [0001](0001-license.md) | Licencia: GPL-3.0-only con términos de atribución de la sección 7 | Aceptada |
| [0002](0002-kdbx-engine.md) | Motor KDBX: kotpass tras un adaptador | Aceptada |
| [0003](0003-platform-and-modules.md) | Plataforma: núcleo Kotlin/JVM, IU con Compose, estructura modular | Aceptada |
| [0004](0004-storage-sync-and-safe-save.md) | Almacenamiento, Syncthing y guardado seguro | Aceptada |
| [0005](0005-reinforced-kotpass.md) | kotpass reforzado: módulo integrado, pruebas diferenciales, fuzzing | Aceptada |
| [0006](0006-biometric-unlock.md) | Desbloqueo biométrico: clave compuesta envuelta por el Keystore, biometría en cada uso | Aceptada |
| [0007](0007-conflict-copies.md) | Fusión de copias de conflicto de Syncthing: fusión automática, borrado solo con consentimiento | Aceptada |
| [0008](0008-cloud-backup.md) | Copias de la bóveda en la nube: asistente opcional, variante `cloud` y servicios | Aceptada |
| [0009](0009-cloud-sync.md) | Sincronización a través de la nube: primero local, escrituras condicionales | Aceptada |
| [0010](0010-in-app-password-keypad.md) | Teclado propio de la app para la contraseña maestra: activado por defecto, junto al del sistema | Aceptada |
| [0011](0011-repository-layout.md) | Estructura del repositorio para varios clientes y una sola versión del producto | Aceptada |
| [0012](0012-windows-desktop-client.md) | Cliente de escritorio para Windows | Aceptada |
| [0013](0013-device-sync.md) | Sincronización entre dispositivos sin Syncthing | Aceptada |
| [0014](0014-windows-hello-quick-unlock.md) | Desbloqueo rápido con Windows Hello en el escritorio: secreto solo en memoria, clave de Hello documentada | Aceptada |
| [0015](0015-automatic-updates.md) | Actualizaciones automáticas: a elección del usuario, manifiesto firmado en Windows, firma de la plataforma en Android, ninguna en `offline` | Aceptada |

Plantilla para nuevos registros: `docs/decisions/NNNN-short-title.md` y su traducción `docs/es/decisions/NNNN-short-title.md`, con las secciones *Contexto*, *Opciones*, *Decisión*, *Consecuencias*.
