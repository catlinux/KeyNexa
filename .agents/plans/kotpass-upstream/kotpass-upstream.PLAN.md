# Aportar los cambios de kotpass al proyecto original

Estado (2026-10-15): **las cuatro PR están abiertas** (abiertas por el usuario a mano el 2026-10-14): #43 `omit-absent-modification-time`, #44 `add-public-readheader`, #45 `fix-damaged-file-reported-as-wrong-key`, #46 `keep-unknown-xml-elements`, todas en https://github.com/keemobile/kotpass/pull/<número>. No hay `gh` ni token en el PC: se consultan por la API pública con `curl`.

## Qué ha pasado desde que se abrieron (2026-10-15)

- **Diagnóstico del CI en rojo (2026-10-15, reproducido en local con saltos de línea de Linux):**
  - **#43:** fallo propio, ya corregido en local (commit `1955d4a` en la rama `omit-absent-modification-time` de `%TEMP%\kp\kotpass`; **pendiente de push al fork**). La prueba `CustomIconsSpec > Serialize XML` leía `xml/custom_icons.xml`, que contenía un `<LastModificationTime/>` vacío (lo que el kotpass original escribía para una fecha ausente y KeePass lee como el año 1); al dejar de escribirlo, el viaje de ida y vuelta difería. La corrección es quitar ese elemento del fixture. Antes quedaba tapado por las 5 pruebas «Serialize XML» que fallan en Windows por saltos de línea.
  - **#44 y #46:** los tests pasan; falla solo el paso de Codecov («Token required because branch is protected»): las PR de forks no reciben el token del repositorio original. No es del código ni arreglable por nosotros.
  - **#45:** `gradle/actions/wrapper-validation` falló por un corte de red (ETIMEDOUT a `services.gradle.org`); transitorio, basta repetir el job.
  - Las cuatro ramas pasan `./gradlew check` en local con saltos de línea de Linux: `-I <script>` con `allprojects { tasks.withType(Test).configureEach { jvmArgs("-Dline.separator=
") } }` (en un archivo fuera del repo) y `git config core.autocrlf false` en la copia de trabajo. WSL (Debian) existe pero no tiene Java.
- **Elementos desconocidos (#46), comprobación con datos reales:** se exportaron a XML todas las bóvedas de `testdata/` (KeePass 2.x y KeePassXC) y se compararon sus elementos con los de `FormatXml.kt` de kotpass: **ninguno es desconocido**. No se puede mostrar un ejemplo real de pérdida hoy; es una salvaguarda de compatibilidad futura. Es la misma vara que ya se aplicó al cuarto cambio de la copia vendorizada (no proponer lo que no se puede demostrar).

### Borrador de respuesta para la #46 (en inglés; lo publica el usuario)

> Thanks for asking, and fair question. To be precise: I checked a corpus of databases saved by KeePass 2.x and KeePassXC (all KDBX 3.1/4.0/4.1 variants, with tags, TOTP, attachments, custom icons) and every element they write inside Meta, Group, Entry and Root is already known to kotpass, so I cannot point to a real element that is dropped today. The change is a forward-compatibility safeguard: if a newer KDBX revision, another client or a plugin writes an element kotpass does not model, opening and saving with kotpass would silently delete it. `UnknownElementsSpec` shows that with a synthetic file. If you consider that too speculative for the library, I am happy to close this PR, or to reduce it (for example only Entry and Group).

### Comentarios cortos para las otras PR (opcionales, en inglés)

- #43: `The CI failure was a test fixture that still carried the empty LastModificationTime element the old code wrote; fixed in the latest commit (all 197 tests pass locally with LF line endings).`
- #44 y #46: `The only failing step is the Codecov upload ("Token required because branch is protected"): pull requests from forks do not receive the repository's Codecov token, so it cannot pass from a fork. The build and all tests pass.`
- #45: `The check failed on a network timeout while validating the Gradle wrapper (ETIMEDOUT to services.gradle.org); re-running the job.`

## Pasos para dejarlo zanjado (el usuario, en GitHub; el push al fork lo hace Claude con su permiso)

1. **#43:** con permiso, `git push fork omit-absent-modification-time` desde `%TEMP%\kp\kotpass` (commit nuevo, sin reescribir historia). Vuelve a lanzar el CI solo.
2. **#45:** en la pestaña *Checks* de la PR, el trabajo en rojo → «Re-run failed jobs» (hace falta sesión de GitHub; no se puede repetir sin iniciar sesión).
3. **#44, #46:** no hay nada que arreglar; opcional, el comentario de arriba.
4. **#46:** publicar la respuesta de arriba; la decisión de mantenerla, reducirla o cerrarla es del usuario.
5. Cuando el CI de #43 y #45 acabe, mirar el estado con `curl` a la API pública (`/repos/keemobile/kotpass/pulls/<n>` y `/commits/<sha>/check-runs`). Lo único que seguirá en rojo en las cuatro es Codecov, por ser forks.

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
