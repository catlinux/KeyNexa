# Pendientes

[English](../../TODO.md) | [Español](TODO.md)

Pasos detallados: consulta la [hoja de ruta](ROADMAP.md).

## Prioridad alta (fase C — base del proyecto)

- [ ] Primera publicación firmada de prueba: subir una etiqueta (con aprobación del responsable) y comprobar en GitHub el workflow, la firma y la suma de verificación.
- [ ] Prueba diferencial de las bóvedas escritas por KeyNexa contra keepass-rs, y fuzzing con Jazzer (ADR-0005; el fuzzing por mutación y la comparación con keepassxc-cli ya se ejecutan).
- [ ] Medir Argon2 de kotpass en un móvil real cuando la app abra una bóveda; decidir sobre argon2kt (consulta `docs/es/BENCHMARKS.md`).
- [ ] Aprobar el resto de la identidad propuesta en `docs/DESIGN.md` (paleta, fuente, interruptor manual claro/oscuro); la marca ya está decidida.

## Prioridad media (fase D — MVP seguro)

- [ ] Apertura de bóvedas, bloqueo y bloqueo automático.
- [ ] Ofrecer en la interfaz las copias `*.sync-conflict-*` para fusionar.
- [ ] Cuando un guardado falle por haber perdido el permiso, ofrecer reconectar la carpeta sin cerrar la bóveda; ofrecer restaurar la copia privada sin guardar tras un reinicio.
- [ ] Detectar cambios externos al volver la app al primer plano (sin esperar al siguiente guardado).
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
