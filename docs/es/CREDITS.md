# Créditos

[English](../../CREDITS.md) | [Español](CREDITS.md)

## Proyecto original

- **Autor y titular del copyright:** CatLinux
- **Proyecto:** KeyNexa
- **Repositorio:** https://github.com/catlinux/KeyNexa
- **Logotipo e icono:** CatLinux, hecho con ayuda de ChatGPT (OpenAI); original en `design/icon/`. Según la sección 3 de NOTICE, el nombre, el logotipo y el icono no se licencian a las versiones modificadas.
- **Licencia:** GPL-3.0-only con los términos adicionales de [NOTICE](../../NOTICE)

## Material de terceros

El esqueleto del proyecto incluye los componentes siguientes, cada uno bajo su propia licencia:

- **Biblioteca estándar de Kotlin y plugins de Gradle** — JetBrains, Apache-2.0.
- **AndroidX Core, AndroidX Activity, AndroidX Biometric (y sus dependencias AndroidX), Jetpack Compose (IU y Material 3)** — The Android Open Source Project, Apache-2.0.
- **JUnit 4** — Equipo de JUnit, EPL-1.0 (solo pruebas unitarias; no se distribuye con la aplicación).
- **kotpass 0.13.0** — Denis T. (copyright 2021), MIT. Origen: https://github.com/keemobile/kotpass, commit `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945`. Archivos: `third_party/kotpass/` (se conserva su `LICENSE.txt`; los cambios locales están en su README). Motor KDBX.
- **Okio 3.15.0** — Square, Inc., Apache-2.0. Origen: https://github.com/square/okio. Dependencia de ejecución de kotpass, no incluida en el código.
- **Kotlin XML Builder** — Jason Blackwell y colaboradores, Apache-2.0. Origen: https://github.com/redundent/kotlin-xml-builder. Incluido dentro de kotpass: `third_party/kotpass/src/main/kotlin/org/redundent/kotlin/xml/` (con su `LICENSE.txt`).
- **Apache Commons Lang (partes)** — The Apache Software Foundation, Apache-2.0. Origen: https://github.com/apache/commons-lang. Incluido dentro de kotpass: `third_party/kotpass/src/main/java/org/apache/commons/lang3/` (con su `LICENSE.txt` y `NOTICE.txt`).
- **Motores criptográficos portados de Bouncy Castle** — The Legion of the Bouncy Castle Inc. (copyright 2000-2021), Bouncy Castle License (texto MIT). Origen: https://github.com/bcgit/bc-java. Incluidos dentro de kotpass, cada archivo con su cabecera de licencia, en `third_party/kotpass/src/main/kotlin/app/keemobile/kotpass/cryptography/`: `ByteUtils.kt`, `engines/Argon2Engine.kt`, `engines/Blake2bDigest.kt`, `engines/ChaCha7539Engine.kt`, `engines/ChaChaEngine.kt`, `engines/Salsa20Engine.kt`, `engines/TwofishEngine.kt` y `padding/PKCS7Padding.kt`.

Componentes previstos y sus licencias verificadas: [Dependencias y licencias](DEPENDENCIES_AND_LICENSES.md).

## Inspiración

KeyNexa lee y escribe el formato KDBX de KeePass, creado por Dominik Reichl para [KeePass](https://keepass.info/). Otros clientes de KeePass (KeePassXC, KeePassDX, Keepass2Android) se estudiaron como referencia únicamente de comportamiento; no se copia de ellos ningún código, texto, icono ni identidad visual.
