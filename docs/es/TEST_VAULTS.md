# Corpus de bóvedas de prueba

[English](../TEST_VAULTS.md) | [Español](TEST_VAULTS.md)

Las bóvedas ficticias de `testdata/` alimentan las pruebas de compatibilidad (AC1, AC2). Las producen programas KeePass reales a propósito: KeyNexa debe abrir archivos que no ha escrito él mismo. Solo contienen datos inventados; **todas las contraseñas de aquí son públicas**.

## Cómo se generan

| Script | Programa | Ejecución |
| --- | --- | --- |
| [`tools/testdata/New-KeePass2Vaults.ps1`](../../tools/testdata/New-KeePass2Vaults.ps1) | KeePass 2.61 (su propia KeePassLib, cargada desde PowerShell) | `powershell -File tools\testdata\New-KeePass2Vaults.ps1` |
| [`tools/testdata/new-keepassxc-vaults.sh`](../../tools/testdata/new-keepassxc-vaults.sh) | KeePassXC 2.7.12 (`keepassxc-cli`) | `bash tools/testdata/new-keepassxc-vaults.sh` |
| [`tools/testdata/inspect_kdbx.py`](../../tools/testdata/inspect_kdbx.py) | ninguno (lee cabeceras) | `python -I tools/testdata/inspect_kdbx.py testdata/*.kdbx` |

Requisitos: KeePass 2.x (`winget install DominikReichl.KeePass`) y KeePassXC (`winget install KeePassXCTeam.KeePassXC`). El contenido aleatorio (el adjunto de 64 KiB) usa una semilla fija, así que volver a ejecutar da archivos equivalentes pero no idénticos byte a byte (KeePass aleatoriza UUID, sales y marcas de tiempo). No regeneres el corpus sin motivo: las pruebas dependen de los archivos confirmados.

`keepassxc-cli` 2.7.12 no puede elegir el cifrado, el KDF ni la versión del formato, ni tampoco etiquetas, campos personalizados o TOTP. Por eso KeePassXC aporta solo las bóvedas de línea de órdenes de abajo y las tres guardadas desde su interfaz (última sección).

## Contenido de las bóvedas «ricas»

Grupos `Internet` (con `Shopping`), `Work` y una papelera con una entrada eliminada; entradas con usuario, contraseña, URL y notas; una entrada con texto no ASCII (`Ñandú café 日本語 😀`); campos personalizados (uno protegido) y dos adjuntos (un archivo de texto y 64 KiB de bytes aleatorios) en la entrada `Server`; una entrada con cuatro versiones en el historial (`Changing password`, contraseña actual `v4-password`); iconos estándar, un icono personalizado y una entrada caducada (bóvedas de KeePass 2); datos personalizados públicos (bóvedas de KeePass 2 salvo la KDBX 3.1). El archivo `*-tags` añade etiquetas en entradas y grupos, lo que lo convierte en KDBX 4.1.

## Archivos

Contraseña maestra de todas las bóvedas: `test-password-1`, salvo que se indique. Argon2: 64 MiB, 10 iteraciones, 2 hilos. AES-KDF: 600000 rondas (KeePassXC elige las suyas).

| Archivo | Escrito por | Formato | Cifrado | KDF |
| --- | --- | --- | --- | --- |
| `rich-aes-argon2d.kdbx` | KeePass 2 | 4.0 | AES-256 | Argon2d |
| `rich-aes-argon2id.kdbx` | KeePass 2 | 4.0 | AES-256 | Argon2id |
| `rich-chacha20-argon2d.kdbx` | KeePass 2 | 4.0 | ChaCha20 | Argon2d |
| `rich-chacha20-argon2id.kdbx` | KeePass 2 | 4.0 | ChaCha20 | Argon2id |
| `rich-aes-aeskdf.kdbx` | KeePass 2 | 4.0 | AES-256 | AES-KDF |
| `rich-chacha20-aeskdf.kdbx` | KeePass 2 | 4.0 | ChaCha20 | AES-KDF |
| `rich-kdbx31-aes-aeskdf.kdbx` | KeePass 2 | **3.1** | AES-256 | AES-KDF |
| `rich-aes-argon2d-tags.kdbx` | KeePass 2 | **4.1** (etiquetas) | AES-256 | Argon2d |
| `kpxc-aeskdf.kdbx` | KeePassXC | 4.1 | AES-256 | AES-KDF (unos 8,3 millones de rondas) |
| `kpxc-twofish-argon2d.kdbx` | KeePassXC (interfaz) | 4.1 | Twofish-256 | Argon2d |
| `kpxc-chacha20-argon2id.kdbx` | KeePassXC (interfaz) | 4.1 | ChaCha20 | Argon2id |
| `kpxc-argon2d-tags-totp.kdbx` | KeePassXC (interfaz) | 4.1 | AES-256 | Argon2d; etiqueta `personal` y TOTP (campo `otp`, secreto inventado `JBSWY3DPEHPK3PXP`) en `Internet/Example mail` |
| `kpxc-empty.kdbx` | KeePassXC | 3.1 | AES-256 | AES-KDF; sin entradas |
| `kpxc-keyfile-password.kdbx` + `kpxc-keyfile.keyx` | KeePassXC | 4.1 | AES-256 | AES-KDF; contraseña **y** archivo de clave |
| `keyfile-only.kdbx` + `keyfile-only.key` | KeePass 2 | 4.0 | AES-256 | AES-KDF; solo archivo de clave, sin contraseña |
| `keyfile-password-kp2.kdbx` (usa `keyfile-only.key`) | KeePass 2 | 4.0 | AES-256 | Argon2id; contraseña y archivo de clave |
| `unicode-password.kdbx` | KeePass 2 | 4.0 | AES-256 | Argon2id; contraseña `pässwörd-日本語-😀` |
| `argon2-1gib.kdbx` | KeePass 2 | 4.0 | AES-256 | Argon2id, **1024 MiB**, 2 iteraciones; para medir memoria y tiempo |

Cada archivo se abrió con `keepassxc-cli` como comprobación independiente y su cabecera se leyó con `inspect_kdbx.py`.

## Las tres bóvedas de KeePassXC guardadas desde su interfaz

`keepassxc-cli` no puede elegir Twofish, cambiar el KDF ni fijar etiquetas y TOTP, así que `kpxc-twofish-argon2d.kdbx`, `kpxc-chacha20-argon2id.kdbx` y `kpxc-argon2d-tags-totp.kdbx` se guardaron desde la ventana de KeePassXC, a partir de una copia de `kpxc-aeskdf.kdbx` y cambiando *Configuración de la base de datos → Seguridad → Opciones de cifrado* (Argon2: 64 MiB, 10 iteraciones, 2 hilos). Para rehacer una a mano, repite eso y, en la última, añade la etiqueta en el editor de la entrada y usa *Apuntes → TOTP → Configurar TOTP*. Estas tres no las produce ningún script.
