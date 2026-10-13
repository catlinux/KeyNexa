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

- **No escribir una fecha de modificación que no existía.** `xml/CustomIcons.kt` y `xml/CustomData.kt` escribían `LastModificationTime` (año 1, cero ticks) en cada icono personalizado y elemento de datos personalizados de un archivo KDBX 4.1 aunque el original no la tuviera. Ahora omiten el elemento cuando el valor es nulo. Se detectó comparando las exportaciones XML de KeePassXC del corpus antes y después de una ida y vuelta.

## Estilo de código

Este directorio está excluido de ktlint y detekt para poder compararlo con upstream.
