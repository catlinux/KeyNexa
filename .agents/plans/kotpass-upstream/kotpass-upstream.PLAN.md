# Aportar los cambios de kotpass al proyecto original

Estado (2026-10-14): **cuatro ramas subidas al fork `catlinux/kotpass`; solicitudes de cambio (PR) sin abrir.** No hay `gh` ni token en el PC: las PR se abren desde la web con los enlaces de abajo (un clic cada una, con el texto ya preparado).

## Qué hay

Cuatro cambios independientes sobre el commit original que vendorizamos (`4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945`, versión 0.13.0 de https://github.com/keemobile/kotpass, licencia MIT, rama `develop`). Cada rama lleva su cambio **y sus pruebas** dentro de kotpass. Los `.patch` de esta carpeta son los mismos commits (`git am`).

| Rama del fork | Qué hace | Prueba |
| --- | --- | --- |
| `omit-absent-modification-time` | No escribe una `LastModificationTime` del año 1 en iconos y datos personalizados que no la tenían. | `AbsentModificationTimeSpec` (falla en el original) |
| `add-public-readheader` | `KeePassDatabase.readHeader`: lee la cabecera sin derivar la clave y rechaza como `UnknownFormat` lo que no empieza con la firma KDBX. | `ReadHeaderSpec` |
| `fix-damaged-file-reported-as-wrong-key` | Una semilla AES-KDF de longitud inválida es `FormatError.InvalidHeader` (antes, clave incorrecta); en KDBX 3.x faltaba un `throw` en la comprobación de los bytes iniciales (error del original). | `DecodeErrorsSpec` (la de la semilla falla en el original) |
| `keep-unknown-xml-elements` | Conserva los elementos XML desconocidos de `Meta`, `Group`, `Entry` y `Root` al reescribir. Es el más grande y añade un campo público (`unknownElements`). | `UnknownElementsSpec` (falla en el original) |

La copia vendorizada de KeyNexa conserva además un cuarto cambio que **no** se propone: convertir un fallo de relleno AES en KDBX 4 en «contenido dañado». Con las mutaciones de prueba no se pudo demostrar que cambie nada, y no se propone lo que no se puede demostrar.

## Comprobaciones hechas

- Los cuatro juntos: 203 pruebas (195 originales + 8 nuevas); las 8 nuevas pasan. Fallan las mismas 5 pruebas «Serialize XML» **con y sin** los cambios (en Windows, por saltos de línea de los archivos de referencia), así que no las causan los cambios.
- En KeyNexa los mismos cambios además se ejercitan con el corpus de bóvedas, ida y vuelta, fuzzing por mutación y comparación con `keepassxc-cli`.

## Enlaces para abrir cada PR (base: `keemobile/kotpass`, rama `develop`)

- https://github.com/keemobile/kotpass/compare/develop...catlinux:kotpass:omit-absent-modification-time?expand=1
- https://github.com/keemobile/kotpass/compare/develop...catlinux:kotpass:add-public-readheader?expand=1
- https://github.com/keemobile/kotpass/compare/develop...catlinux:kotpass:fix-damaged-file-reported-as-wrong-key?expand=1
- https://github.com/keemobile/kotpass/compare/develop...catlinux:kotpass:keep-unknown-xml-elements?expand=1

Título y descripción: el primer renglón del mensaje del commit y el resto (ya van en cada rama). Añadir al final de cada descripción: «Found while building KeyNexa (GPL-3.0 password manager) on top of kotpass; the change is offered under kotpass's MIT license. Tests included.»

## Notas

- Orden sugerido (de menor a mayor riesgo): el de la tabla, de arriba abajo.
- El proyecto tiene un solo mantenedor, no recibe commits desde 2025-09-28 y hay una incidencia abierta (#42) preguntando si sigue vivo. Si no responde, seguimos con la copia vendorizada (ADR-0005). Idea: ofrecerse a ayudar a mantenerlo si el autor lo pide.
- Copia de trabajo fuera del repo: `%TEMP%\kp\kotpass` (clon por SSH, remoto `fork`). Si se pierde, clonar el fork y trabajar desde sus ramas.
- Los parches se generan desde `third_party/kotpass` (guion en el directorio de trabajo temporal); si cambia la copia vendorizada, regenerar.
