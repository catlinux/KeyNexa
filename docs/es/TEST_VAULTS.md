# Corpus de bóvedas de prueba

[English](../TEST_VAULTS.md) | [Español](TEST_VAULTS.md)

Instrucciones para crear las bóvedas ficticias de `testdata/` que usan las pruebas de compatibilidad (AC1, AC2). Se generan a propósito con programas KeePass reales: KeyNexa debe abrir archivos que no ha escrito él mismo.

**No pongas nunca datos reales.** Usa solo los valores inventados de abajo. Todas las contraseñas de este corpus son públicas y están documentadas.

## Herramientas

- **KeePassXC** 2.7.x (`winget install KeePassXCTeam.KeePassXC`).
- **KeePass 2.x** 2.61 (`winget install DominikReichl.KeePass`), para lo que KeePassXC no sabe escribir (Twofish, KDBX 3.x).

Guarda cada archivo en `testdata/`, en la raíz del repositorio, con el nombre exacto de la tabla. No hagas commit todavía: avisa al responsable de que los archivos están listos.

## Contenido de cada bóveda «rica»

Constrúyelo una vez en la primera bóveda, copia después ese archivo y cambia solo los ajustes de cifrado (*Base de datos → Configuración de la base de datos → Seguridad*), de modo que todas contengan los mismos datos. Si un programa no puede guardar algo, sáltalo y anótalo en `testdata/NOTES.txt`.

1. Grupos: `Root` con `Internet` (y dentro `Shopping`), `Work`, y la papelera con una entrada eliminada.
2. Entradas (al menos cinco) con título, usuario, contraseña, URL y notas. Una con texto no ASCII (`Ñandú café 日本語 😀`) en el título, las notas y la contraseña.
3. Un campo de texto personalizado y un segundo marcado como protegido.
4. Dos adjuntos en una entrada: un archivo de texto pequeño y uno binario de aproximadamente 1 MiB (vale cualquier archivo aleatorio).
5. Una entrada editada tres veces, para que tenga historial.
6. Iconos: varios iconos estándar elegidos en entradas y grupos, y un icono personalizado.
7. Una entrada con fecha de caducidad en el pasado y otra con etiquetas (las etiquetas requieren KeePassXC y dan KDBX 4.1).
8. Una entrada con un secreto TOTP configurado en KeePassXC (campo `otp`). Usa un secreto inventado como `JBSWY3DPEHPK3PXP`.
9. Datos personalizados: si el programa lo permite, deja los ajustes por defecto; KeePassXC guarda algunos automáticamente.

## Archivos

Contraseña maestra de todas las bóvedas: `test-password-1`, salvo que la tabla diga otra cosa. La memoria es la de Argon2 (MiB); usa 10 iteraciones y 2 hilos.

| Archivo | Programa | Cifrado | KDF | Notas |
| --- | --- | --- | --- | --- |
| `rich-aes-argon2d.kdbx` | KeePassXC | AES-256 | Argon2d, 64 MiB | La bóveda maestra con todo el contenido anterior |
| `rich-aes-argon2id.kdbx` | KeePassXC | AES-256 | Argon2id, 64 MiB | |
| `rich-chacha20-argon2id.kdbx` | KeePassXC | ChaCha20 | Argon2id, 64 MiB | |
| `rich-chacha20-argon2d.kdbx` | KeePassXC | ChaCha20 | Argon2d, 64 MiB | |
| `rich-aes-aeskdf.kdbx` | KeePassXC | AES-256 | AES-KDF, 600000 rondas | |
| `rich-twofish-argon2d.kdbx` | KeePass 2.x | Twofish | Argon2d, 64 MiB | Abre la bóveda maestra en KeePass 2.x con *Archivo → Abrir*, cambia los ajustes y usa *Guardar como* |
| `rich-twofish-aeskdf.kdbx` | KeePass 2.x | Twofish | AES-KDF, 600000 rondas | |
| `rich-kdbx3-aes-aeskdf.kdbx` | KeePass 2.x | AES-256 | AES-KDF, 600000 rondas | Formato de archivo **KDBX 3.x** (en *Configuración de la base de datos*, pestaña General o Compatibilidad, según la versión) |
| `rich-kdbx41-tags.kdbx` | KeePassXC | AES-256 | Argon2d, 64 MiB | Debe incluir etiquetas en entradas y grupos |
| `keyfile-password.kdbx` + `keyfile.keyx` | KeePassXC | AES-256 | Argon2id, 64 MiB | Contraseña `test-password-1` **más** un archivo de clave generado por KeePassXC (formato XML) |
| `keyfile-only.kdbx` + `keyfile-only.key` | KeePass 2.x | AES-256 | AES-KDF, 600000 rondas | Sin contraseña: solo archivo de clave (vale cualquier archivo aleatorio de 32 bytes) |
| `empty.kdbx` | KeePassXC | AES-256 | Argon2id, 64 MiB | Una bóveda nueva sin entradas |
| `argon2-1gib.kdbx` | KeePassXC | AES-256 | Argon2id, **1024 MiB**, 2 iteraciones | Basta una entrada pequeña; sirve para medir memoria y tiempo en Android |
| `unicode-password.kdbx` | KeePassXC | AES-256 | Argon2id, 64 MiB | Contraseña maestra `pässwörd-日本語-😀`, una entrada pequeña |

## Después

El asistente comprueba la cabecera de cada archivo con un pequeño script que imprime la versión de KDBX, el cifrado y el KDF, escribe las contraseñas y el contenido esperado junto a los archivos en `testdata/NOTES.txt`, y los confirma. Si una combinación resulta no poder crearse con los programas, se quita de la tabla y se anota allí.
