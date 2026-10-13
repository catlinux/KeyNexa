# kotpass (incluido)

[English](README.md) | [Español](README.es.md)

Copia de [kotpass](https://github.com/keemobile/kotpass), una biblioteca KDBX para Kotlin/JVM de Denis T., con licencia MIT (consulta [LICENSE.txt](LICENSE.txt)). KeyNexa la incluye y la mantiene según lo decidido en el [ADR-0005](../../docs/es/decisions/0005-reinforced-kotpass.md).

- **Upstream:** https://github.com/keemobile/kotpass
- **Commit upstream:** `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945` (versión 0.13.0, 2025-09-28)
- **Importado:** solo `kotpass/src/main`. No se importan los tests y sus bóvedas de ejemplo, ni la configuración de publicación, firma, documentación y comprobación de actualizaciones. El `build.gradle.kts` de aquí es propio de KeyNexa.
- **Dependencia:** Okio 3.15.0 (Apache-2.0), desde el catálogo de versiones.
- **Solo `core-kdbx` puede depender de este módulo.**

## Código de terceros incluido

Se conserva intacto con sus avisos:

| Ruta | Componente | Archivo de licencia |
| --- | --- | --- |
| `src/main/kotlin/org/redundent/kotlin/xml/` | Kotlin XML Builder | `LICENSE.txt` en ese directorio (Apache-2.0) |
| `src/main/java/org/apache/commons/lang3/` | Apache Commons Lang (partes) | `LICENSE.txt` y `NOTICE.txt` en ese directorio (Apache-2.0) |
| `src/main/kotlin/app/keemobile/kotpass/cryptography/` (ocho archivos) | Portado de Bouncy Castle | Cabecera Bouncy Castle License en cada archivo |

## Cambios locales

El código es idéntico al del commit upstream salvo por los cambios de abajo. Los cambios hechos en KeyNexa se anotan aquí, el más reciente primero, cada uno con su motivo; siguen bajo licencia MIT para poder ofrecerlos a upstream.

- **Distinguir archivos dañados de claves incorrectas; `throw` que faltaba.** En KDBX 4 solo la HMAC de la cabecera demuestra la clave; un fallo posterior del relleno AES al descifrar el contenido (`database/Decoder.kt`) lanza ahora `FormatError.InvalidContent` en lugar de `CryptoError.InvalidKey`, y una semilla AES-KDF no válida (`cryptography/format/AesKdf.kt`) lanza `FormatError.InvalidHeader`. Antes, un archivo truncado o manipulado se informaba como clave incorrecta, de modo que una transferencia parcial de sincronización parecía una contraseña errónea. En KDBX 3.x la comprobación de los bytes de arranque del flujo construía `FormatError.InvalidContent` pero nunca la lanzaba (error de upstream); ahora lanza `CryptoError.InvalidKey`. En 3.x una clave incorrecta y un archivo cortado siguen siendo indistinguibles, porque ese formato no tiene comprobación de integridad antes de descifrar.

- **Lector público de la cabecera, y Okio expuesto como `api`.** `database/Decoder.kt` incorpora `KeePassDatabase.readHeader`, una función pública que reutiliza el `DatabaseHeader.readFrom` interno y comprueba la firma, para que quien la use pueda inspeccionar el formato de un archivo sin derivar la clave. `build.gradle.kts` declara ahora Okio como `api` en vez de `implementation`: los modelos y la cabecera ya exponen `okio.ByteString` en su API pública, así que un consumidor no podría compilar contra ellos de otro modo. Lo necesita `KotpassKdbxEngine`, de KeyNexa.

- **Conservar los elementos XML desconocidos.** `xml/Entry.kt`, `xml/Group.kt`, `xml/Meta.kt` y `xml/DefaultXmlContentParser.kt` ignoraban en silencio los elementos que no reconocían dentro de `Meta`, `Group`, `Entry` (historial incluido) y el documento raíz, así que al reescribir una base de datos se perdía lo que hubieran añadido versiones más recientes de KeePass/KeePassXC. `Entry`, `Group`, `Meta` y `DatabaseContent` ahora los guardan en un campo nuevo `unknownElements` (`unknownRootElements` para los hijos de `Root`) y el parseador los vuelve a escribir. Limitación: un valor con `Protected="True"` dentro de un elemento desconocido no se descifra ni se vuelve a cifrar con el flujo interno, por lo que puede no sobrevivir a una ida y vuelta binaria; hoy ningún elemento de upstream anida valores protegidos dentro de desconocidos.

- **No escribir una fecha de modificación que no existía.** `xml/CustomIcons.kt` y `xml/CustomData.kt` escribían `LastModificationTime` (año 1, cero ticks) en cada icono personalizado y elemento de datos personalizados de un archivo KDBX 4.1 aunque el original no la tuviera. Ahora omiten el elemento cuando el valor es nulo. Se detectó comparando las exportaciones XML de KeePassXC del corpus antes y después de una ida y vuelta.

## Estilo de código

Este directorio está excluido de ktlint y detekt para poder compararlo con upstream.
