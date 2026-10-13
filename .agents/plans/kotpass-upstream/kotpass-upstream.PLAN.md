# Aportar los cambios de kotpass al proyecto original

Estado: **parches preparados, nada enviado.** Publicar es una acción externa y necesita la aprobación explícita del usuario.

## Qué hay

Cuatro parches independientes sobre el commit original que vendorizamos (`4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945`, versión 0.13.0 de https://github.com/keemobile/kotpass, licencia MIT). Se aplican limpios y por separado (`git am`). Autor de los commits: `catlinux <marc.catlinux@gmail.com>`.

| Parche | Qué corrige o añade |
| --- | --- |
| `fix-damaged-file-reported-as-wrong-key.patch` | Un contenido KDBX 4 dañado se informaba como clave incorrecta (el HMAC de la cabecera ya prueba la clave). Ahora es `FormatError.InvalidContent`; una semilla AES-KDF inválida es `FormatError.InvalidHeader`; en KDBX 3.x faltaba un `throw` (error del original). |
| `add-public-readheader.patch` | `KeePassDatabase.readHeader`: lee la cabecera sin derivar la clave. |
| `keep-unknown-xml-elements.patch` | Conserva los elementos XML desconocidos de `Meta`, `Group`, `Entry` (con historial) y `Root`, para no perder datos de versiones nuevas de KeePass/KeePassXC al reescribir. Es el más grande y el que cambia modelos públicos (campo `unknownElements`). |
| `omit-absent-modification-time.patch` | No escribir una `LastModificationTime` del año 1 en iconos y datos personalizados que no la tenían. |

## Comprobaciones hechas

- Los cuatro aplicados juntos compilan y pasan las pruebas del propio kotpass igual que el original: en este PC (Windows) fallan las mismas 5 pruebas «Serialize XML» (de 195) **con y sin** los parches, así que no las causan los cambios.
- Las pruebas que cubren estos cambios están en `core-kdbx` de KeyNexa (corpus propio de bóvedas, ida y vuelta, fuzzing). **Faltan pruebas dentro de kotpass:** conviene añadirlas antes de abrir cada solicitud (una por parche), porque un mantenedor las pedirá.

## Antes de publicar

1. El usuario decide si quiere publicar y con qué cuenta (`catlinux`). Hace falta una bifurcación (fork) de `keemobile/kotpass` en esa cuenta; yo no la creo.
2. El proyecto tiene un solo mantenedor y no recibe commits desde 2025-09-28: puede tardar o no responder. Mientras tanto seguimos con la copia vendorizada (ADR-0005).
3. Abrir una solicitud de cambio por parche, en inglés, con el texto del mensaje del commit como descripción, y enlazar el caso de uso (KeyNexa) solo como contexto.
4. Orden sugerido (de menor a mayor riesgo): `omit-absent-modification-time`, `add-public-readheader`, `fix-damaged-file-reported-as-wrong-key`, `keep-unknown-xml-elements`.

## Cómo regenerar los parches

La copia de trabajo está fuera del repo (`%TEMP%\kp\kotpass`, clon por SSH de upstream). Si se pierde: clonar `keemobile/kotpass`, `git reset --hard 4e0bd6b`, y aplicar los parches con `git am`.
