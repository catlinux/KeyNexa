# ADR-0009: Sincronización a través de la nube: primero local, escrituras condicionales

[English](../../decisions/0009-cloud-sync.md) | [Español](0009-cloud-sync.md)

- **Estado:** Propuesta
- **Fecha:** 2026-10-15
- **Amplía:** [ADR-0004](0004-storage-sync-and-safe-save.md) y [ADR-0008](0008-cloud-backup.md), cuya decisión 9 dejó este paso para su propio registro

## Contexto

El [ADR-0008](0008-cloud-backup.md) añadió **copias** opcionales en la nube: instantáneas de solo escritura, rotadas, que la app nunca vuelve a leer. El propietario quiere además que el mismo servicio sirva de punto de encuentro, de modo que dos dispositivos que nunca están encendidos a la vez (un móvil y un ordenador) acaben con la misma bóveda sin Syncthing.

Lo que el proyecto ya tiene:

- Un proceso de guardado seguro en `core/sync` (copia privada, nueva comprobación de la huella, fusión por UUID y fechas de modificación con historial, escritura y lectura de comprobación) y un motor de fusión que nunca sobrescribe lo que no sabe fusionar.
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
- El paso de reconciliación es lógica de `core/sync` sobre `CloudBackend`; necesita un servicio falso con precondiciones inyectables para probar las carreras en la JVM antes de usar ningún servicio real.

## Diseño de la implementación (2026-10-19)

Cómo se construye la decisión anterior para los servicios compatibles con S3 que KeyNexa ya admite. Precisa los puntos 3 y 5.

1. **La prueba del punto 3 se hace contra el bucket del propio usuario**, no una vez por proveedor: los servicios compatibles con S3 difieren, y el mismo endpoint puede cambiar. Cuando el usuario activa la sincronización, una sonda escribe un objeto temporal bajo `KeyNexa/sync/` y comprueba que una subida de solo creación (`If-None-Match: *`) sobre un objeto existente se rechaza, que una subida con la etiqueta de entidad actual (`If-Match`) se acepta, que una con una etiqueta caducada se rechaza y que cada subida rechazada dejó el contenido igual; después borra el objeto. Solo una sonda superada activa el interruptor; se guarda con la configuración y se anula al cambiar de servicio. Cloudflare R2 documenta `If-Match` e `If-None-Match` en `PutObject`, evaluados de forma atómica; Backblaze B2 no documenta ninguno, y se ofrece para sincronizar solo si la sonda la supera.
2. **El objeto remoto siempre se fusiona antes de subir nada.** No se sube nada encima del objeto remoto sin antes descifrarlo con las credenciales de la bóveda abierta y fusionarlo, aunque este dispositivo crea ser el único que escribe: otro programa puede haber sustituido el archivo local por uno más viejo, y solo la fusión (con su lista de objetos borrados) evita que eso haga retroceder a los demás. El único atajo es «nada que hacer»: si la etiqueta de entidad remota es la última que vio este dispositivo y el archivo local es el que sincronizó por última vez, no se lee ni se escribe nada.
3. **Subir solo lo que le falta al objeto remoto.** Tras la fusión, el resultado se guarda en local solo si difiere de la bóveda local, y se sube solo si difiere de la bóveda remota (la misma igualdad que ya usan las copias de conflicto). Es la regla de convergencia que evita que dos dispositivos se reboten archivos.
4. **La bóveda debe estar abierta y en reposo.** La fusión se ejecuta en el propio hilo de la sesión con las credenciales de la bóveda abierta; mientras corre un guardado, o con la bóveda bloqueada, la sincronización se aplaza (no es un error) y la ejecuta el siguiente disparador. Un guardado durante una sincronización pide una vuelta más al acabar.
5. **El id de sincronización** (`KeyNexa.SyncId`, 32 dígitos hexadecimales en minúscula de un generador aleatorio seguro) se añade la primera vez que se sincroniza una bóveda. Antes de crear uno nuevo, el dispositivo mira los objetos que ya hay bajo `KeyNexa/sync/` (10 como mucho) y adopta uno solo si se descifra con las credenciales de la bóveda abierta **y** tiene el mismo UUID de grupo raíz: así, una copia de la misma bóveda hecha antes (con Syncthing o a mano) se une al objeto existente en lugar de crear otro. Una bóveda con un id mal formado no se sincroniza. Un objeto cuyo id interno no coincide con su nombre nunca se fusiona.
6. **Otra contraseña maestra** (punto 5 y último punto abierto): en esta versión, un objeto remoto que no se descifra con las credenciales de la bóveda abierta se notifica («la copia de la nube usa otra contraseña maestra») y no se escribe nada en ningún lado. Fusionarlo con la otra contraseña queda para más adelante.
7. **Un dispositivo sin copia local** descarga un objeto de `KeyNexa/sync/` a un archivo que elige el usuario, lo añade a las bóvedas conocidas y lo desbloquea como siempre.
8. **Errores sin contenido:** el estado que se guarda por id de sincronización (última etiqueta de entidad vista, huella del archivo local sincronizado por última vez, último éxito, último error) no contiene ningún secreto ni contenido de la bóveda.

## Puntos abiertos (a verificar antes de prometerlos)

- Qué servicios respetan de forma fiable una escritura condicional: Dropbox (modo `update` con un `rev`), OneDrive (`If-Match` con la etiqueta de entidad, que responde 412), Google Drive (no se encontró en la documentación) y cada proveedor compatible con S3 con `If-Match` en una subida. Para los servicios compatibles con S3 lo responde la sonda por bucket del diseño de la implementación; Cloudflare R2 documenta las dos cabeceras ([compatibilidad de la API S3 de R2](https://developers.cloudflare.com/r2/api/s3/api/)), el [`PutObject`](https://www.backblaze.com/apidocs/s3-put-object) de Backblaze B2 no incluye ninguna y un tercero informa de que B2 rechaza `If-None-Match` ([parche de Proxmox, julio de 2025](https://lists.proxmox.com/pipermail/pbs-devel/2025-July/014234.html)). Los demás proveedores, en `.agents/plans/cloud-backup/oauth-providers.RESEARCH.md`.
- ~~Cómo se elige el id de la bóveda para que dos dispositivos coincidan~~ Resuelto por el [ADR-0013](0013-device-sync.md) (2026-10-18): un id de sincronización aleatorio guardado en los datos personalizados de la propia bóveda (`KeyNexa.SyncId`), común a la sincronización por la nube y entre dispositivos. El cliente de escritorio ([ADR-0012](0012-windows-desktop-client.md)) usa este registro sin cambios.
- Si una sincronización en segundo plano compensa su coste de batería y con qué mecanismo de Android, una vez medida la versión en primer plano.
- Qué ocurre con dos dispositivos con contraseñas maestras distintas cuando uno la ha cambiado: la primera versión solo lo notifica (diseño de la implementación, punto 6); fusionar con la otra contraseña por el flujo F1 sigue abierto.
