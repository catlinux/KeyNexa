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
- **Compose Multiplatform 1.8.2** — JetBrains s.r.o. y los colaboradores de Compose Multiplatform, Apache-2.0. Origen: https://github.com/JetBrains/compose-multiplatform. Lo usa el módulo de interfaz compartida `ui/shared` (tema, marca, teclado); en Android corresponde a las bibliotecas de Jetpack Compose de arriba, más su pequeño módulo `ui-backhandler`.
- **JNA 5.19.1 (Java Native Access)** — Timothy Wall y los colaboradores de JNA, doble licencia LGPL-2.1-or-later o Apache-2.0; se usa bajo Apache-2.0. Origen: https://github.com/java-native-access/jna. Lo usa `platform/windows` para llamar a DPAPI de Windows con los secretos del cliente de escritorio.
- **Runtime de Java (Eclipse Temurin JDK 17, OpenJDK) 17.0.18** — Eclipse Adoptium y los colaboradores de OpenJDK, GPL-2.0 con la excepción de Classpath. Origen: https://adoptium.net/. Incluido sin modificar en el MSI y el ZIP de Windows con sus avisos de `legal/` (también los del código de terceros del JDK).
- **Skiko 0.9.4.2** — JetBrains s.r.o., Apache-2.0. Origen: https://github.com/JetBrains/skiko. Capa gráfica del cliente de Windows; su biblioteca nativa se construye con Skia e incluye datos de ICU (licencias por verificar, véase [Dependencias y licencias](DEPENDENCIES_AND_LICENSES.md)).
- **JUnit 4** — Equipo de JUnit, EPL-1.0 (solo pruebas unitarias; no se distribuye con la aplicación).
- **Jazzer 0.30.0** — Code Intelligence GmbH y los colaboradores de Jazzer, Apache-2.0. Origen: https://github.com/CodeIntelligenceTesting/jazzer. Fuzzing guiado por cobertura del decodificador KDBX en el workflow de CI `fuzz`; se descarga como CLI con una suma SHA-256 fija, no es una dependencia de Gradle ni se distribuye con la aplicación.
- **keepass-rs 0.15.2 y la herramienta `tools/kdbx-dump-rs`** — Stefan Seemayer y los colaboradores de keepass-rs, MIT; la herramienta usa además serde, serde_json y sha2 (RustCrypto), MIT OR Apache-2.0. Orígenes: https://github.com/sseemayer/keepass-rs, https://github.com/serde-rs/serde, https://github.com/RustCrypto/hashes. Se usa en la prueba diferencial del trabajo `differential` de la CI; las dependencias Rust están fijadas en `tools/kdbx-dump-rs/Cargo.lock`, y ni la herramienta ni ellas se distribuyen con la aplicación.
- **kotpass 0.13.0** — Denis T. (copyright 2021), MIT. Origen: https://github.com/keemobile/kotpass, commit `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945`. Archivos: `third_party/kotpass/` (se conserva su `LICENSE.txt`; los cambios locales están en su README). Motor KDBX.
- **Okio 3.15.0** — Square, Inc., Apache-2.0. Origen: https://github.com/square/okio. Dependencia de ejecución de kotpass, no incluida en el código.
- **Kotlin XML Builder** — Jason Blackwell y colaboradores, Apache-2.0. Origen: https://github.com/redundent/kotlin-xml-builder. Incluido dentro de kotpass: `third_party/kotpass/src/main/kotlin/org/redundent/kotlin/xml/` (con su `LICENSE.txt`).
- **Apache Commons Lang (partes)** — The Apache Software Foundation, Apache-2.0. Origen: https://github.com/apache/commons-lang. Incluido dentro de kotpass: `third_party/kotpass/src/main/java/org/apache/commons/lang3/` (con su `LICENSE.txt` y `NOTICE.txt`).
- **Motores criptográficos portados de Bouncy Castle** — The Legion of the Bouncy Castle Inc. (copyright 2000-2021), Bouncy Castle License (texto MIT). Origen: https://github.com/bcgit/bc-java. Incluidos dentro de kotpass, cada archivo con su cabecera de licencia, en `third_party/kotpass/src/main/kotlin/app/keemobile/kotpass/cryptography/`: `ByteUtils.kt`, `engines/Argon2Engine.kt`, `engines/Blake2bDigest.kt`, `engines/ChaCha7539Engine.kt`, `engines/ChaChaEngine.kt`, `engines/Salsa20Engine.kt`, `engines/TwofishEngine.kt` y `padding/PKCS7Padding.kt`.
- **Manrope (fuente)** — The Manrope Project Authors (copyright 2018), diseño de Mikhail Sharanda, SIL Open Font License 1.1. Origen: https://github.com/googlefonts/manrope, tomada de https://github.com/google/fonts (`ofl/manrope/Manrope[wght].ttf`, commit `b31870aff700ab7a1d74fa0c6887d95beb9e0037`, SHA-256 `3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6`). Archivos: `ui/shared/src/commonRes/font/manrope.ttf` (fuente variable sin modificar; lleva dentro su aviso de copyright y de licencia) y `third_party/manrope/OFL.txt` (texto de la licencia). Fuente de la interfaz de la app.

Componentes previstos y sus licencias verificadas: [Dependencias y licencias](DEPENDENCIES_AND_LICENSES.md).

## Inspiración

KeyNexa lee y escribe el formato KDBX de KeePass, creado por Dominik Reichl para [KeePass](https://keepass.info/). Otros clientes de KeePass (KeePassXC, KeePassDX, Keepass2Android) se estudiaron como referencia únicamente de comportamiento; no se copia de ellos ningún código, texto, icono ni identidad visual.
