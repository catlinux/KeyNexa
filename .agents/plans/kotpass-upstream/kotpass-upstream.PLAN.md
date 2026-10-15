# Aportar los cambios de kotpass al proyecto original

Estado (2026-10-15): **las cuatro PR están abiertas** (abiertas por el usuario a mano el 2026-10-14): #43 `omit-absent-modification-time`, #44 `add-public-readheader`, #45 `fix-damaged-file-reported-as-wrong-key`, #46 `keep-unknown-xml-elements`, todas en https://github.com/keemobile/kotpass/pull/<número>. No hay `gh` ni token en el PC: se consultan por la API pública con `curl`.

## Qué ha pasado desde que se abrieron (2026-10-15)

- **CI en rojo en las cuatro:** el flujo «Build, Test and Lint», trabajo `check`, falla en el paso **«Check all modules»** (los pasos de configuración pasan). Ejemplo: https://github.com/keemobile/kotpass/actions/runs/38490083373. La causa no se conoce: el registro del trabajo exige iniciar sesión en GitHub y no se puede leer por la API pública. Que falle también la PR #43 (la más pequeña) sugiere algo común: el estilo (ktlint o detekt de kotpass), una prueba que falla en Linux o que `develop` ya estuviera en rojo (no hay ejecuciones recientes en `develop` para comparar). Aquí, en Windows, fallan 5 pruebas «Serialize XML» con y sin los cambios.
- **El mantenedor (Anvell) ha preguntado en la #46:** qué se quiere decir con «elementos hijos que no reconocía… lo que versiones nuevas de KeePass o KeePassXC hubieran añadido»; qué se podría añadir ahí. Está **sin responder**.
- **Qué hacer, por orden:** (1) el usuario abre el registro del trabajo fallido (enlace de arriba, con sesión) y pega aquí el error; (2) reproducirlo en la copia de trabajo (`%TEMP%\kp\kotpass`, remoto `fork`) con `./gradlew check` (idealmente en Linux/Debian) y corregir en la rama de cada PR, con `git push` al fork (cada push vuelve a lanzar el CI); (3) responder en la #46 (borrador abajo).

### Borrador de respuesta para la #46 (en inglés, para que lo publique el usuario con su estilo; ajustarlo a lo que se compruebe)

> Thanks for asking. The concrete case is any XML element that kotpass's model does not know: elements added by newer KDBX revisions, by other password managers or by plugins. Today kotpass reads the database, ignores those elements and, when the database is saved again, they are gone, so opening and saving a file with kotpass silently deletes data written by another client. The PR keeps the elements it does not recognise inside `Meta`, `Group`, `Entry` and `Root` (in a new public `unknownElements` list) and writes them back in place. `UnknownElementsSpec` builds a file with such elements and fails on the current `develop`. I can reduce the scope (for example only `Entry` and `Group`) or split it if you prefer smaller changes.

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
