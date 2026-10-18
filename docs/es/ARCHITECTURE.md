# Arquitectura

[English](../ARCHITECTURE.md) | [Español](ARCHITECTURE.md)

> Estado: **propuesta** (v0.1.0). Todavía no existe código. Las decisiones se registran en [decisions/](decisions/README.md).

## Objetivos y restricciones

1. Seguridad e integridad de los datos ante todo; nunca perder en silencio los cambios del usuario.
2. Interoperabilidad KDBX completa con otros clientes de KeePass.
3. Sin criptografía propia: solo implementaciones consolidadas y probadas.
4. Android primero; Windows y Linux más adelante sin reescribir el núcleo.
5. Código sensible para la seguridad pequeño, explícito y comprobable con pruebas.
6. Toda dependencia compatible con GPL-3.0 y justificada (consulta [Dependencias y licencias](DEPENDENCIES_AND_LICENSES.md)).

## Elecciones tecnológicas

| Área | Elección | Por qué | Decisión |
| --- | --- | --- | --- |
| Lenguaje | Kotlin (destino JVM) | Lenguaje nativo de Android; el mismo código se ejecuta en la JVM de escritorio | [ADR-0003](decisions/0003-platform-and-modules.md) |
| Motor KDBX | [kotpass](https://github.com/keemobile/kotpass) (MIT), integrado y reforzado, envuelto tras nuestra propia interfaz | Única biblioteca JVM investigada que cubre KDBX 3.1/4.x con AES, ChaCha20, Twofish, AES-KDF, Argon2d y Argon2id; una sola dependencia en tiempo de ejecución (okio) | [ADR-0002](decisions/0002-kdbx-engine.md), [ADR-0005](decisions/0005-reinforced-kotpass.md) |
| IU de Android | Jetpack Compose + Material 3 (tema propio) | Estándar actual de Android; declarativo y comprobable con pruebas | [ADR-0003](decisions/0003-platform-and-modules.md) |
| IU de escritorio (futura) | Compose Multiplatform for Desktop (JVM) | Reutiliza sin cambios los módulos de núcleo en JVM | [ADR-0003](decisions/0003-platform-and-modules.md) |
| Acceso a archivos (Android) | Storage Access Framework (SAF), sin el permiso «Acceso a todos los archivos» | Mínimo privilegio; política de Google Play | [ADR-0004](decisions/0004-storage-sync-and-safe-save.md) |
| Desbloqueo biométrico | `androidx.biometric` + Android Keystore | Protección de claves respaldada por la plataforma | [THREAT_MODEL](THREAT_MODEL.md) |
| Compilación | Gradle (Kotlin DSL) con catálogo de versiones | Estándar; declaraciones de dependencias reproducibles | [DEVELOPMENT](DEVELOPMENT.md) |

## Estructura de módulos (planificada)

Las dependencias apuntan solo hacia abajo. Los módulos de núcleo son Kotlin/JVM puro, sin importaciones de Android, de modo que se pueden probar en la JVM y reutilizar en un cliente de escritorio.

```text
apps/android          Compose UI, navigation, Android services (autofill, biometric, clipboard)
  │
platform/android     SAF storage adapter, Keystore, lifecycle-based auto-lock
platform/cloud       S3-compatible cloud client (SigV4), JDK HTTP client only
  │
core/sync            change detection, merge, safe-save pipeline (uses StoragePort)
  │
core/vault           domain model, use cases, search, StoragePort / KdbxEngine interfaces
  │
core/kdbx            adapter implementing KdbxEngine on top of kotpass (the only module that sees kotpass)
```

- **`core/vault`** define el dominio (bóveda, grupo, entrada, campo, adjunto, historial) y los puertos: `KdbxEngine` (decodificar/codificar un flujo de bytes KDBX con credenciales) y `StoragePort` (leer, escribir, metadatos, listar los archivos hermanos de un archivo de bóveda).
- **`core/kdbx`** es el único módulo que depende de kotpass. Sustituir o bifurcar el motor solo afecta a este módulo.
- **`core/sync`** contiene la lógica que protege los datos: huellas de contenido, detección de cambios externos, la fusión y el flujo de guardado seguro. Es deliberadamente independiente de Android para poder probarlo exhaustivamente con un almacenamiento simulado.
- **`platform/android`** implementa `StoragePort` con SAF y envuelve el Android Keystore.
- **`platform/cloud`** es el cliente de red de la variante `cloud`: un backend compatible con S3 firmado con AWS SigV4, sobre el cliente HTTP del JDK y sin dependencias externas. La variante `offline` nunca lo incluye.
- **`apps/android`** es la IU y los servicios de Android.

**Próxima estructura (aceptada, [ADR-0011](decisions/0011-repository-layout.md)):** para el cliente de Windows ([ADR-0012](decisions/0012-windows-desktop-client.md)) y después Linux, los módulos pasan a `core/`, `platform/`, `ui/` y `apps/` (`apps/android`, `apps/desktop`); la sesión de la bóveda pasa a `core/session`, los almacenes privados de archivos a `platform/jvm`, el tema y el teclado a `ui/shared` (Compose Multiplatform), y la sincronización entre dispositivos ([ADR-0013](decisions/0013-device-sync.md)) tiene `core/peer`. Esta sección describe la estructura actual hasta que se haga ese traslado.

## Flujo de datos: apertura y guardado

1. El usuario elige una bóveda con SAF. KeyNexa conserva un permiso de URI persistente, prefiriendo la concesión de una **carpeta** (`ACTION_OPEN_DOCUMENT_TREE`) para que la bóveda sobreviva a que Syncthing la sustituya. Consulta [ADR-0004](decisions/0004-storage-sync-and-safe-save.md).
2. Los bytes se leen en memoria y se les calcula la huella (SHA-256 del archivo cifrado). Los bytes cifrados no se escriben nunca en los logs.
3. `KdbxEngine` descifra con la clave compuesta. Se rechaza un archivo cuya cabecera o bloques HMAC no se verifican (puede ser una transferencia parcial de Syncthing): KeyNexa reintenta tras una breve espera y nunca escribe encima.
4. Al guardar, se ejecuta el flujo de guardado seguro (más abajo).

**Buscar cambios externos con poco coste.** Mientras la app está delante, `VaultSession` pregunta a `VaultReader` cada pocos segundos si el archivo ha cambiado. Primero compara el tamaño y la fecha de modificación anotados justo antes de leer los bytes; cuando ambos coinciden, se salta la lectura del archivo. Ante cualquier duda (metadatos que faltan o difieren) y, como mínimo una vez por minuto, se hace igualmente la comprobación completa leyendo y calculando la huella, y el flujo de guardado seguro comprueba siempre la huella completa justo antes de escribir.

### Flujo de guardado seguro

Implementado en `core/sync` (`SafeSaver`, `VerifiedWriter`, `SaveRecovery`, `VaultReader`).

1. Serializar la bóveda en memoria como un nuevo array de bytes cifrado y descifrarlo una vez, para demostrar que los bytes nuevos son correctos.
2. Volver a leer el archivo actual y comparar su huella con la cargada. Si cambió, descifrarlo y **fusionarlo** con la bóveda en memoria y serializar de nuevo (como mucho unas pocas rondas). Sin fusión no se escribe nada.
3. Guardar dos copias cifradas en el almacenamiento privado de la app, excluido de la copia de seguridad de Android: el archivo anterior (`BeforeSave`, rotativas: las 10 más recientes por bóveda) y los bytes nuevos (`UnsavedChanges`, conservados hasta que se guardan). Anotar el guardado en un diario.
4. Comprobar la huella una vez más y escribir con el modo `"rwt"`; si el proveedor no lo admite, usar `"rw"` y truncar explícitamente.
5. Volver a leer el archivo. Solo hay éxito si su huella coincide con la de los bytes nuevos: bytes idénticos no necesitan un segundo descifrado, así que un guardado cuesta una sola derivación de clave.
6. Si no, averiguar qué contiene el archivo: sin cambios (no se escribió nada), sustituido por otro programa después de nuestra escritura (se deja como está) o roto por nuestra propia escritura. Un archivo roto se restaura byte a byte desde la copia `BeforeSave`. En cualquier fallo los cambios del usuario siguen en memoria y en la copia `UnsavedChanges`.

**Qué significa «nunca corrompe» aquí.** Un proveedor de almacenamiento no puede sustituir un archivo de forma atómica, así que un cierre inesperado a mitad de una escritura puede dejarlo roto; nada puede evitarlo. La garantía es que siempre existe una copia descifrable y se repone automáticamente: en el siguiente arranque, `SaveRecovery` encuentra la entrada del diario, compara el archivo con las dos copias y restaura la versión anterior si el archivo lo rompió nuestra escritura. Distinguir un archivo roto de uno escrito por otro programa no necesita la contraseña: cada guardado escribe en la cabecera una semilla maestra aleatoria nueva, así que solo un archivo que KeyNexa empezó a escribir comparte un prefijo largo con sus bytes nuevos.

**Lectura tolerante.** Un archivo que no supera sus comprobaciones de integridad puede ser una transferencia de sincronización en curso, así que `VaultReader` lo vuelve a leer tras 1, 2, 4 y 8 segundos antes de declararlo dañado. En KDBX 4 un error de clave incorrecta es fiable y se informa enseguida (la contraseña puede haber cambiado en otro dispositivo); en KDBX 3.x, donde un archivo dañado y una clave incorrecta parecen iguales, se reintenta si esas mismas credenciales ya abrieron la bóveda.

### Fusión

La fusión de KeyNexa sigue la semántica de la sincronización de KeePass 2.x, tal como también la implementa KeePassXC, reimplementada a partir del comportamiento documentado, no copiada:

- Los objetos se emparejan por UUID.
- Para una entrada modificada en ambos lados, la versión con la hora de última modificación más reciente pasa a ser la actual y la otra se conserva en su historial.
- Los historiales se fusionan sin eliminar elementos y luego se recortan a los límites de historial de la bóveda.
- Los movimientos siguen la hora de cambio de ubicación más reciente.
- Las eliminaciones solo se aplican a través de la lista de objetos eliminados de la bóveda, y solo si el objeto no se modificó después de la eliminación.

Implementado en `core/sync` como `KeePassMerger`, con estas reglas adicionales: los empates favorecen la versión del usuario en memoria; un objeto cuyo nuevo grupo padre no sobrevivió va al antepasado superviviente más cercano; los movimientos cruzados que formarían un bucle cuelgan el grupo de la raíz, de modo que no se pierde nada; la metadata se fusiona campo a campo por sus propios tiempos `*Changed`; los adjuntos que nadie referencia se descartan; un registro de borrado del grupo raíz se ignora. El código de edición debe actualizar la fecha de última modificación de un objeto (y la de cambio de ubicación al moverlo), porque la fusión decide por esas fechas. Probado con escenarios y con pares de bóvedas generados al azar comprobados contra invariantes (no se pierde nada, sin bucles, sin adjuntos colgantes, mismo resultado sea cual sea el lado «nuestro»).

Si los archivos no se pueden fusionar (por ejemplo, por tener contraseñas maestras distintas), KeyNexa no escribe: conserva ambas versiones y consulta al usuario.

### Copias de conflicto de Syncthing

Cuando dos dispositivos cambian la bóveda sin verse entre sí, Syncthing conserva una versión como archivo de la bóveda y renombra la otra a `<nombre>.sync-conflict-<fecha>-<hora>-<dispositivo>.kdbx`. KeyNexa pliega esas copias en la bóveda por su cuenta, para que sus cambios no se pierdan hasta que alguien abra la copia a mano ([ADR-0007](decisions/0007-conflict-copies.md)):

- Las copias se buscan entre los archivos hermanos del archivo de la bóveda, usando el nombre real del archivo en el almacenamiento (no el nombre que muestra la app).
- Un registro por bóveda en almacenamiento privado, excluido de las copias de seguridad, guarda las huellas SHA-256 de las copias ya fusionadas, de modo que una copia conocida no se descifra otra vez ni se fusiona dos veces.
- Cada copia restante se descifra con las credenciales de la bóveda abierta y se pliega con `KeePassMerger`; el resultado pasa por el canal de guardado normal, y las copias se registran solo cuando ese guardado se completa (si falla, se quedan fuera y la siguiente apertura lo vuelve a intentar).
- Las copias que no se pueden fusionar se informan y se dejan como están: escritas con otra contraseña, dañadas o aún en transferencia, de otra bóveda o ilegibles.
- **Nada se borra sin consentimiento.** Tras una fusión, la app dice cuántas copias se han fusionado y ofrece borrarlas, y solo borra las copias del registro cuyos bytes siguen coincidiendo con la huella grabada, releída justo antes de borrar. Si se rechaza, se conservan y la oferta vuelve en la siguiente apertura.

Vive en `core/sync` como `ConflictMerger` (con el puerto `MergedCopies` para el registro); la app de Android lo conecta en `VaultSession`, guarda el registro con `FileMergedCopies` y muestra el aviso en la pantalla de grupos. Como en el resto de la bóveda, solo se guardan huellas y motivos: nunca contenido ni secretos.

## Estrategia multiplataforma

| Opción | Veredicto |
| --- | --- |
| **Núcleo Kotlin/JVM + Compose (Android) + Compose Multiplatform Desktop** | **Elegida.** Un lenguaje, un motor KDBX, cadena de dependencias totalmente permisiva y verificada. |
| Núcleo en Rust (keepass-rs) + bindings UniFFI | Rechazada por ahora. keepass-rs (MIT) marca la escritura de KDBX como experimental, con problemas abiertos de pérdida de datos en adjuntos e historial (octubre de 2026); UniFFI añade una cadena de herramientas NDK por plataforma. Se volverá a evaluar antes de 1.0 ([ADR-0005](decisions/0005-reinforced-kotpass.md)). |
| Núcleo compartido con Kotlin Multiplatform (KMP) | No es posible hoy sin reescribir el motor: no se encontró ninguna biblioteca KDBX para KMP; kotpass es solo JVM. La JVM de escritorio lo hace innecesario. |
| Flutter / React Native | Rechazadas: peor acceso a las API de seguridad de Android (Keystore, autorrelleno, proveedor de credenciales) y ninguna biblioteca KDBX madura en esos ecosistemas con licencia verificada. |

Los clientes de escritorio añaden su propio almacenamiento de secretos: Windows usa DPAPI para el usuario actual ([ADR-0012](decisions/0012-windows-desktop-client.md)); Linux usará el Secret Service, con su propio registro.

## Internacionalización

- Recursos de cadenas de Android (`values/` para `en-US` como predeterminado, `values-es/` para español). Ningún texto visible para el usuario escrito a mano en Kotlin.
- La IU debe comprobarse con cadenas largas (el español suele ser más largo) y con las pseudolocalizaciones `en-XA`/`ar-XB`.
