# ADR-0009: Sincronización a través de la nube: primero local, escrituras condicionales

[English](../../decisions/0009-cloud-sync.md) | [Español](0009-cloud-sync.md)

- **Estado:** Propuesta
- **Fecha:** 2026-10-15
- **Amplía:** [ADR-0004](0004-storage-sync-and-safe-save.md) y [ADR-0008](0008-cloud-backup.md), cuya decisión 9 dejó este paso para su propio registro

## Contexto

El [ADR-0008](0008-cloud-backup.md) añadió **copias** opcionales en la nube: instantáneas de solo escritura, rotadas, que la app nunca vuelve a leer. El propietario quiere además que el mismo servicio sirva de punto de encuentro, de modo que dos dispositivos que nunca están encendidos a la vez (un móvil y un ordenador) acaben con la misma bóveda sin Syncthing.

Lo que el proyecto ya tiene:

- Un proceso de guardado seguro en `core-sync` (copia privada, nueva comprobación de la huella, fusión por UUID y fechas de modificación con historial, escritura y lectura de comprobación) y un motor de fusión que nunca sobrescribe lo que no sabe fusionar.
- Una interfaz `CloudBackend` (poner, obtener, listar y borrar por nombre) con un indicador de capacidad de escrituras condicionales, y un cliente S3 que hoy declara `conditionalWrites = false`.
- Credenciales cifradas, una pantalla de consentimiento y una variante de compilación `cloud`.

Lo que necesita un archivo remoto compartido y no un archivo local: dos dispositivos pueden leer la versión A, editarla los dos y subirla los dos. Sin una escritura «solo si no ha cambiado», la segunda subida destruye en silencio la primera.

## Opciones

1. **La nube como el `StoragePort` de la propia bóveda.** La ubicación de la bóveda es un objeto remoto y cada lectura y escritura va a la red. Modelo sencillo, pero la bóveda no se puede usar sin conexión, cada desbloqueo espera a la red y una subida fallida no deja ninguna copia local a la que volver.
2. **Primero local, con la nube como punto de encuentro (este registro).** La bóveda sigue donde está hoy (un archivo SAF). Un paso de sincronización descarga el objeto remoto, lo fusiona con el archivo local con el motor de fusión existente, escribe el resultado fusionado en local por el proceso de guardado seguro y lo sube solo si el objeto remoto no ha cambiado desde que se leyó. El uso sin conexión no se ve afectado.
3. **Un servidor propio.** Descartado en el [ADR-0008](0008-cloud-backup.md): ni servidores ni cuentas.
4. **Dejar Syncthing como única sincronización.** Sigue disponible y recomendado para quien ya lo usa; no sirve para dispositivos que nunca están en línea a la vez.

## Decisión

1. **Opción 2: primero local.** El archivo local sigue siendo la fuente de verdad para el usuario. La nube guarda un objeto más por bóveda, `KeyNexa/sync/<id de la bóveda>.kdbx`, aparte de las copias rotadas del ADR-0008, que la app nunca rota ni borra (salvo con una acción explícita «dejar de sincronizar y borrar»).
2. **Un único paso de reconciliación**, que usan todos los disparadores: leer el objeto remoto y su etiqueta de entidad; si difiere de la última que vio este dispositivo, fusionarlo con la bóveda local con el motor de fusión existente (una fusión fallida nunca sobrescribe, como hoy); guardar en local por el proceso de guardado seguro; y subir el resultado con una escritura condicional contra la etiqueta de entidad leída. Si el servicio responde «ha cambiado mientras tanto» (fallo de precondición), se repite desde la lectura, unas pocas veces como máximo, y después se avisa con discreción y se conserva el estado local.
3. **Un servicio se ofrece para sincronizar solo si su escritura condicional se ha probado** con la API real (una subida con una etiqueta caducada debe fallar y no cambiar nada). Para los demás, incluido cualquier servicio compatible con S3 que no la respete, la nube sigue siendo una copia de seguridad, como decidió el ADR-0008. `CloudCapabilities.conditionalWrites` pasa a ser verdadero por servicio solo tras esa prueba, y la interfaz dice qué servicios pueden sincronizar.
4. **Disparadores de la primera versión:** tras un guardado local correcto (como la copia diaria del ADR-0008, pero sin el límite de una vez al día), al volver la app a primer plano con una bóveda abierta, y un botón «Sincronizar ahora». Sin servicio en segundo plano ni consulta periódica en esta versión; antes se mide el consumo de batería (véase la tarea del TODO sobre la detección de cambios más barata).
5. **Primera conexión de un segundo dispositivo:** se descarga el objeto remoto y se fusiona con la bóveda local; si el usuario aún no tiene ninguna bóveda local, elige una carpeta (SAF) donde guardarla. Si las dos bóvedas tienen contraseñas maestras distintas, la fusión no puede leer la otra: se reutiliza el flujo existente para copias de conflicto con otra contraseña (F1), nunca una sobrescritura silenciosa.
6. **Los borrados** viajan como la propia lista de objetos borrados de KDBX, como en el motor de fusión actual; el historial de las entradas se conserva. No se inventa nada nuevo para ellos.
7. **El objeto remoto es el mismo `.kdbx` cifrado**: la nube solo ve texto cifrado, exactamente como en el ADR-0008. Valen la misma pantalla de consentimiento, el mismo almacén de credenciales y el mismo «Desconectar»; la sincronización es un interruptor aparte dentro del asistente de la nube, desactivado por defecto, con su propia explicación (qué hace, que las versiones antiguas quedan en el historial de las entradas y que borrar la bóveda en un dispositivo la borra en todos tras la siguiente sincronización).
8. **Los fallos nunca bloquean guardar ni desbloquear**: una sincronización fallida o rechazada muestra un estado discreto («última sincronización…», «no se pudo sincronizar: …») y deja la bóveda local exactamente como estaba.

## Consecuencias

- Los dispositivos que nunca están encendidos a la vez pueden converger por cualquier servicio cuya escritura condicional supere la prueba; los demás siguen funcionando como copias de seguridad.
- Un objeto más por bóveda en la cuenta de nube del usuario, y más tráfico que con las copias (cada guardado y cada vuelta a primer plano sube o baja el archivo entero; una bóveda ocupa poco).
- Dos dispositivos que editan la misma entrada sin conexión siguen produciendo una fusión por fechas de modificación, como con Syncthing: gana la edición más reciente y la anterior queda en el historial de la entrada.
- El objeto remoto es un único punto de divergencia: si un servicio devuelve una lectura caducada tras una escritura (consistencia débil), un dispositivo puede trabajar un momento con una versión vieja; la escritura condicional es lo que evita que sobrescriba una más nueva.
- El modelo de amenazas debe añadir: un atacante con la cuenta de la nube puede borrar o revertir el objeto sincronizado (la app conserva su archivo local y sus copias privadas, y el usuario puede restaurar desde las copias rotadas), y puede ver su tamaño, nombre y fechas.
- El paso de reconciliación es lógica de `core-sync` sobre `CloudBackend`; necesita un servicio falso con precondiciones inyectables para probar las carreras en la JVM antes de usar ningún servicio real.

## Puntos abiertos (a verificar antes de prometerlos)

- Qué servicios respetan de forma fiable una escritura condicional: Dropbox (modo `update` con un `rev`), OneDrive (`If-Match` con la etiqueta de entidad, que responde 412), Google Drive (no se encontró en la documentación) y cada proveedor compatible con S3 (Backblaze B2, Cloudflare R2, Amazon S3, MinIO) con `If-Match` en una subida. Lo confirmado y lo no confirmado está en `.agents/plans/cloud-backup/oauth-providers.RESEARCH.md`.
- ~~Cómo se elige el id de la bóveda para que dos dispositivos coincidan~~ Resuelto por el [ADR-0013](0013-device-sync.md) (2026-10-18): un id de sincronización aleatorio guardado en los datos personalizados de la propia bóveda (`KeyNexa.SyncId`), común a la sincronización por la nube y entre dispositivos. El cliente de escritorio ([ADR-0012](0012-windows-desktop-client.md)) usa este registro sin cambios.
- Si una sincronización en segundo plano compensa su coste de batería y con qué mecanismo de Android, una vez medida la versión en primer plano.
- Qué ocurre con dos dispositivos con contraseñas maestras distintas cuando uno la ha cambiado (reutilizar el flujo F1 o un mensaje propio).
