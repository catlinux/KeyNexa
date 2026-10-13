# Pendientes

[English](../../TODO.md) | [Español](TODO.md)

Pasos detallados: consulta la [hoja de ruta](ROADMAP.md).

## Prioridad alta (fase C — base del proyecto)

- [ ] Esqueleto multimódulo de Gradle (`core-vault`, `core-kdbx`, `core-sync`, `platform-android`, `app-android`) que lea la versión de `VERSION`.
- [ ] Primera publicación firmada de prueba: subir una etiqueta (con aprobación del responsable) y comprobar en GitHub el workflow, la firma y la suma de verificación.
- [ ] Prueba de concepto: fidelidad de ida y vuelta de kotpass y rendimiento de Argon2 en un dispositivo de gama baja.
- [ ] Sistema de diseño: icono original, paleta, tipografía, espaciado, componentes, temas claro y oscuro.
- [ ] Activar la notificación privada de vulnerabilidades de GitHub en los ajustes del repositorio.

## Prioridad media (fase D — MVP seguro)

- [ ] Apertura de bóvedas, bloqueo y bloqueo automático.
- [ ] Flujo de guardado seguro y copias de seguridad privadas.
- [ ] Detección de cambios externos, fusión y gestión de archivos de conflicto.
- [ ] Edición de entradas y grupos; búsqueda.
- [ ] Desbloqueo biométrico.

## Prioridad baja (más adelante)

- [ ] Servicio de autorrelleno y proveedor de credenciales (passkeys).
- [ ] TOTP.
- [ ] Clientes de escritorio (Windows, Linux).

## Descartado

- Núcleo en Rust con UniFFI: la escritura de KDBX en keepass-rs es experimental y UniFFI añade una cadena de herramientas NDK; se reevaluará antes de 1.0 ([ARCHITECTURE](ARCHITECTURE.md#estrategia-multiplataforma)).
- Permiso `MANAGE_EXTERNAL_STORAGE`: no es necesario con SAF y no está permitido en Google Play para este uso.
- Depender de la API REST de Syncthing: requiere una clave de API y es posible que Syncthing no esté instalado.
