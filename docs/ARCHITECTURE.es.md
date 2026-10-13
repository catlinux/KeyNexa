# Arquitectura

[English](ARCHITECTURE.md) | [Español](ARCHITECTURE.es.md)

> Estado: **propuesta** (v0.1.0). Todavía no existe código. Las decisiones se registran en [decisions/](decisions/README.es.md).

## Objetivos y restricciones

1. Seguridad e integridad de los datos ante todo; nunca perder en silencio los cambios del usuario.
2. Interoperabilidad KDBX completa con otros clientes de KeePass.
3. Sin criptografía propia: solo implementaciones consolidadas y probadas.
4. Android primero; Windows y Linux más adelante sin reescribir el núcleo.
5. Código sensible para la seguridad pequeño, explícito y comprobable con pruebas.
6. Toda dependencia compatible con GPL-3.0 y justificada (consulta [Dependencias y licencias](DEPENDENCIES_AND_LICENSES.es.md)).

## Elecciones tecnológicas

| Área | Elección | Por qué | Decisión |
| --- | --- | --- | --- |
| Lenguaje | Kotlin (destino JVM) | Lenguaje nativo de Android; el mismo código se ejecuta en la JVM de escritorio | [ADR-0003](decisions/0003-platform-and-modules.es.md) |
| Motor KDBX | [kotpass](https://github.com/keemobile/kotpass) (MIT), envuelto tras nuestra propia interfaz | Única biblioteca JVM investigada que cubre KDBX 3.1/4.x con AES, ChaCha20, Twofish, AES-KDF, Argon2d y Argon2id; una sola dependencia en tiempo de ejecución (okio) | [ADR-0002](decisions/0002-kdbx-engine.es.md) |
| IU de Android | Jetpack Compose + Material 3 (tema propio) | Estándar actual de Android; declarativo y comprobable con pruebas | [ADR-0003](decisions/0003-platform-and-modules.es.md) |
| IU de escritorio (futura) | Compose Multiplatform for Desktop (JVM) | Reutiliza sin cambios los módulos de núcleo en JVM | [ADR-0003](decisions/0003-platform-and-modules.es.md) |
| Acceso a archivos (Android) | Storage Access Framework (SAF), sin el permiso «Acceso a todos los archivos» | Mínimo privilegio; política de Google Play | [ADR-0004](decisions/0004-storage-sync-and-safe-save.es.md) |
| Desbloqueo biométrico | `androidx.biometric` + Android Keystore | Protección de claves respaldada por la plataforma | [THREAT_MODEL](THREAT_MODEL.es.md) |
| Compilación | Gradle (Kotlin DSL) con catálogo de versiones | Estándar; declaraciones de dependencias reproducibles | [DEVELOPMENT](DEVELOPMENT.es.md) |

## Estructura de módulos (planificada)

Las dependencias apuntan solo hacia abajo. Los módulos de núcleo son Kotlin/JVM puro, sin importaciones de Android, de modo que se pueden probar en la JVM y reutilizar en un cliente de escritorio.

```text
app-android          Compose UI, navigation, Android services (autofill, biometric, clipboard)
  │
platform-android     SAF storage adapter, Keystore, lifecycle-based auto-lock
  │
core-sync            change detection, merge, safe-save pipeline (uses StoragePort)
  │
core-vault           domain model, use cases, search, StoragePort / KdbxEngine interfaces
  │
core-kdbx            adapter implementing KdbxEngine on top of kotpass (the only module that sees kotpass)
```

- **`core-vault`** define el dominio (bóveda, grupo, entrada, campo, adjunto, historial) y los puertos: `KdbxEngine` (decodificar/codificar un flujo de bytes KDBX con credenciales) y `StoragePort` (leer, escribir, metadatos, listar los archivos hermanos de un archivo de bóveda).
- **`core-kdbx`** es el único módulo que depende de kotpass. Sustituir o bifurcar el motor solo afecta a este módulo.
- **`core-sync`** contiene la lógica que protege los datos: huellas de contenido, detección de cambios externos, la fusión y el flujo de guardado seguro. Es deliberadamente independiente de Android para poder probarlo exhaustivamente con un almacenamiento simulado.
- **`platform-android`** implementa `StoragePort` con SAF y envuelve el Android Keystore.
- **`app-android`** es la IU y los servicios de Android.

Un futuro `app-desktop` reutilizaría `core-*` y añadiría un `platform-desktop` con un `StoragePort` de archivos simples y un almacenamiento de secretos específico del sistema operativo.

## Flujo de datos: apertura y guardado

1. El usuario elige una bóveda con SAF. KeyNexa conserva un permiso de URI persistente, prefiriendo la concesión de una **carpeta** (`ACTION_OPEN_DOCUMENT_TREE`) para que la bóveda sobreviva a que Syncthing la sustituya. Consulta [ADR-0004](decisions/0004-storage-sync-and-safe-save.es.md).
2. Los bytes se leen en memoria y se les calcula la huella (SHA-256 del archivo cifrado). Los bytes cifrados no se escriben nunca en los logs.
3. `KdbxEngine` descifra con la clave compuesta. Se rechaza un archivo cuya cabecera o bloques HMAC no se verifican (puede ser una transferencia parcial de Syncthing): KeyNexa reintenta tras una breve espera y nunca escribe encima.
4. Al guardar, se ejecuta el flujo de guardado seguro (más abajo).

### Flujo de guardado seguro

1. Serializar la bóveda a un nuevo array de bytes cifrado en memoria.
2. Volver a leer el archivo actual y comparar su huella con la que se cargó. Si ha cambiado, descifrarlo y **fusionarlo** en la bóveda en memoria, y serializar de nuevo.
3. Conservar una copia del archivo cifrado anterior en el almacenamiento privado de la aplicación (copias rotativas, excluidas de la copia de seguridad de Android).
4. Escribir con el modo `"rwt"`; si el proveedor no lo admite, recurrir a `"rw"` y truncar explícitamente.
5. Volver a leer el archivo y verificar que su huella coincide con los bytes escritos y que se descifra.
6. Si falla algún paso, restaurar desde la copia privada cuando sea posible e informar claramente al usuario. No indicar nunca que ha tenido éxito sin el paso 5.

### Fusión

La fusión de KeyNexa sigue la semántica de la sincronización de KeePass 2.x, tal como también la implementa KeePassXC, reimplementada a partir del comportamiento documentado, no copiada:

- Los objetos se emparejan por UUID.
- Para una entrada modificada en ambos lados, la versión con la hora de última modificación más reciente pasa a ser la actual y la otra se conserva en su historial.
- Los historiales se fusionan sin eliminar elementos y luego se recortan a los límites de historial de la bóveda.
- Los movimientos siguen la hora de cambio de ubicación más reciente.
- Las eliminaciones solo se aplican a través de la lista de objetos eliminados de la bóveda, y solo si el objeto no se modificó después de la eliminación.

Si los archivos no se pueden fusionar (por ejemplo, por tener contraseñas maestras distintas), KeyNexa no escribe: conserva ambas versiones y consulta al usuario.

## Estrategia multiplataforma

| Opción | Veredicto |
| --- | --- |
| **Núcleo Kotlin/JVM + Compose (Android) + Compose Multiplatform Desktop** | **Elegida.** Un lenguaje, un motor KDBX, cadena de dependencias totalmente permisiva y verificada. |
| Núcleo en Rust (keepass-rs) + bindings UniFFI | Rechazada por ahora. keepass-rs (MIT) marca la escritura de KDBX como experimental, con problemas abiertos de pérdida de datos en adjuntos e historial (octubre de 2026); UniFFI es MPL-2.0 y añade una cadena de herramientas NDK por plataforma. |
| Núcleo compartido con Kotlin Multiplatform (KMP) | No es posible hoy sin reescribir el motor: no se encontró ninguna biblioteca KDBX para KMP; kotpass es solo JVM. La JVM de escritorio lo hace innecesario. |
| Flutter / React Native | Rechazadas: peor acceso a las API de seguridad de Android (Keystore, autorrelleno, proveedor de credenciales) y ninguna biblioteca KDBX madura en esos ecosistemas con licencia verificada. |

Los clientes de escritorio añadirán su propio almacenamiento de secretos (Windows DPAPI o Credential Manager, Linux Secret Service). Es un diseño de seguridad en sí mismo y tendrá un ADR aparte.

## Internacionalización

- Recursos de cadenas de Android (`values/` para `en-US` como predeterminado, `values-es/` para español). Ningún texto visible para el usuario escrito a mano en Kotlin.
- La IU debe comprobarse con cadenas largas (el español suele ser más largo) y con las pseudolocalizaciones `en-XA`/`ar-XB`.
