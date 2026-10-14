# ADR-0004: Almacenamiento, Syncthing y guardado seguro

[English](../../decisions/0004-storage-sync-and-safe-save.md) | [Español](0004-storage-sync-and-safe-save.md)

- **Estado:** Aceptada
- **Fecha:** 2026-10-13

## Contexto

Los usuarios guardarán sus bóvedas en carpetas sincronizadas con Syncthing. Hallazgos (fuentes entre paréntesis):

- La aplicación oficial de Syncthing para Android se discontinuó en 2024 y su repositorio está archivado; el fork mantenido es Syncthing-Fork, distribuido a través de F-Droid y GitHub ([anuncio en el foro](https://forum.syncthing.net/t/discontinuing-syncthing-android/23002)). KeyNexa no puede dar por hecho que haya instalada ninguna aplicación de Syncthing.
- Syncthing escribe en un archivo temporal y después sustituye el destino; los conflictos producen archivos `<name>.sync-conflict-<date>-<time>-<device>.<ext>` ([documentación](https://docs.syncthing.net/users/syncing.html)).
- Los parches de seguridad de Android de septiembre de 2026 revocan el permiso de URI de una aplicación sobre un archivo cuando ese archivo se elimina o se renombra, lo cual ocurre cuando Syncthing lo sustituye ([KeePassDX #2573](https://github.com/Kunzisoft/KeePassDX/issues/2573), [Syncthing #10887](https://github.com/syncthing/syncthing/issues/10887)). Según se informa, una concesión de carpeta (árbol) sobrevive.
- Con SAF, el modo de escritura `"w"` puede no truncar en algunos proveedores, lo que corrompe un archivo más corto; se necesitan `"wt"`/`"rwt"` ([cambio en el javadoc de AOSP](https://android.googlesource.com/platform/frameworks/base/+/0ebf92c7d31e%5E%21/)).
- No hay notificaciones de cambios fiables para los archivos del almacenamiento compartido; `FileObserver` no es fiable ahí y la API REST de Syncthing necesita una clave de API.
- `MANAGE_EXTERNAL_STORAGE` no está permitido en Google Play para aplicaciones en las que el usuario elige los archivos.

## Decisión

1. **Solo SAF**, sin permiso amplio de almacenamiento. Preferir la **concesión de una carpeta** (`ACTION_OPEN_DOCUMENT_TREE`) y localizar la bóveda por su nombre; las concesiones de un único archivo se admiten con un flujo de «reconectar archivo» cuando se revoca el acceso.
2. **Detección de cambios por huella de contenido** (SHA-256 del archivo cifrado) al desbloquear, al volver a primer plano, periódicamente mientras es visible y siempre inmediatamente antes de guardar. Sin depender de la API de Syncthing.
3. **Lectura tolerante:** un archivo que falla las comprobaciones de cabecera/HMAC puede ser una transferencia parcial; reintentar con espera progresiva y no escribir nunca encima.
4. **Flujo de guardado seguro** (copia de seguridad privada → nueva comprobación de la huella y fusión → escritura con `"rwt"` → verificación por relectura → restauración si falla), tal como se describe en [ARCHITECTURE](../ARCHITECTURE.md).
5. **Fusión automática** por UUID y horas de modificación, conservando el historial; los casos que no se pueden fusionar nunca sobrescriben.
6. **Archivos de conflicto** (`*.sync-conflict-*`) encontrados junto a la bóveda se ofrecen para fusionar y nunca se eliminan automáticamente.
7. **Copias de seguridad privadas rotativas** de la bóveda cifrada en el almacenamiento de la aplicación, excluidas de la copia de seguridad de Android.

## Reconexión en caliente y restauración privada (F3)

8. **Reconectar sin cerrar la bóveda.** Cuando un guardado falla con `StorageError`, el selector de carpeta se ofrece desde el banner de guardado, sobre la bóveda abierta. `[VaultSession.relocate(newLocation)]` cambia `vault.location` (y `loaded.location`) en la sesión abierta, conserva la bóveda editada y la cargada, las credenciales y la copia privada sin guardar, y vuelve a intentar el guardado pendiente por el flujo normal, escribiendo solo si el archivo elegido es la misma bóveda (en caso contrario `SaveOutcome.Unmergeable`, sin escribir nada). No se vuelve a codificar ni se vuelve a pedir la contraseña.
9. **Los libros indexados por ubicación no se migran.** El libro de huellas de copias de conflicto y el diario de guardado se indexan por `VaultLocation`. Tras una reconexión, el guardado se ejecuta bajo la nueva ubicación y crea allí su propia entrada del diario; la entrada del diario de la ubicación anterior (si existiera) se deja intacta para poder recuperarla como historial de esa ruta, y el libro de huellas para la nueva ubicación empieza vacío. Así se evita registrar o restaurar contra una carpeta equivocada. La lista de bóvedas conocidas (fuera de la sesión) la actualiza quien llama con el mismo nombre.
10. **Restaurar sin volver a pedir nada.** `SaveRecovery` puede informar de un `BackupRef` privado con cambios sin guardar de un guardado interrumpido (`SaveHadNotStarted`, `ChangedElsewhere` o `Restored`). Al abrir se muestra esa referencia pero no se lee automáticamente; el usuario elige *Restaurar* o *Descartar*. *Restaurar* lee la copia cifrada con las credenciales ya abiertas, la fusiona con la bóveda abierta (`VaultMerger`, en empates gana la bóveda abierta), guarda por el flujo normal y borra la copia solo si el resultado es `Saved`; si falla, la referencia sigue ahí y se puede volver a elegir. *Descartar* borra la copia. Una copia que no se puede leer ni abrir se informa y se conserva. `StillDamaged` no tiene acción de restauración. Nada de la copia privada se registra ni sale del almacenamiento privado de la aplicación.

## Consecuencias

- KeyNexa no afirma ofrecer sincronización en tiempo real ni automática; detecta los cambios hechos por cualquier herramienta de sincronización.
- El comportamiento debe validarse en dispositivos reales con Syncthing-Fork antes de anunciarlo (criterio de aceptación AC4 de la [hoja de ruta](../ROADMAP.md)).
- Las concesiones de carpeta requieren un flujo de primer uso algo más largo; la IU debe explicar por qué.
