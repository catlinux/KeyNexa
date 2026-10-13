# Modelo de amenazas

[English](../THREAT_MODEL.md) | [Español](THREAT_MODEL.md)

> Estado: **borrador inicial** (v0.1.0), escrito antes de cualquier código sensible. Se revisará en cada fase. KeyNexa no ha sido auditado; nada de lo aquí expuesto es una garantía.

## Activos

| Activo | Dónde reside |
| --- | --- |
| Contenido descifrado de la bóveda (contraseñas, notas, adjuntos, secretos TOTP) | Memoria de la aplicación mientras está desbloqueada |
| Contraseña maestra y archivo de clave | Memoria del usuario / archivo elegido por el usuario; transitoriamente en la memoria de la aplicación |
| Claves derivadas (clave compuesta, clave transformada) | Memoria de la aplicación mientras está desbloqueada |
| Archivo cifrado de la bóveda (`.kdbx`) | Almacenamiento del usuario, carpetas sincronizadas, copias de seguridad privadas |
| Material de desbloqueo biométrico (clave maestra envuelta con una clave del Keystore) | Almacenamiento privado de la aplicación + Android Keystore |
| Clave de firma de las versiones publicadas | Solo el mantenedor; nunca en el repositorio ni en los logs de la CI |

## Atacantes dentro del alcance

1. Alguien con acceso físico a un dispositivo **bloqueado** (robo, pérdida).
2. Alguien con acceso breve a un dispositivo **desbloqueado**.
3. Una aplicación maliciosa en el mismo dispositivo (sin root): superposiciones, lectura del portapapeles, destinos de autorrelleno falsos, un teclado malicioso.
4. Alguien que obtiene el archivo `.kdbx` cifrado (de un dispositivo sincronizado, una copia de seguridad, una copia en la nube) y lo ataca sin conexión.
5. Un par de sincronización comprometido o malicioso que modifica o corrompe el archivo.
6. Una dependencia o una cadena de compilación comprometida (cadena de suministro).

**Fuera del alcance:** un sistema operativo con root o comprometido, ataques de hardware al Keystore/TEE, un usuario que revela su contraseña maestra y malware con control del dispositivo mediante un servicio de accesibilidad. Se documentan como riesgos residuales.

## Amenazas y mitigaciones

| N.º | Amenaza | Mitigación (planificada salvo que se indique lo contrario) |
| --- | --- | --- |
| T1 | Fuerza bruta sin conexión contra la contraseña maestra | KDBX 4 con Argon2id por defecto en las bóvedas nuevas, con parámetros calibrados por dispositivo (objetivo: unos 1 s para desbloquear) y mínimos razonables; indicación de la fortaleza de la contraseña al crear una bóveda; compatibilidad con archivos de clave. Las bóvedas existentes conservan su propia configuración de KDF salvo que el usuario la cambie. |
| T2 | Criptografía débil o propia | Sin criptografía propia. Todas las primitivas proceden del motor KDBX (kotpass, portado de Bouncy Castle) y de la plataforma (`javax.crypto`, Keystore). En KDBX 4, el HMAC de la cabecera y los HMAC de los bloques se verifican siempre. |
| T3 | Secretos en memoria | Conservar los datos descifrados solo mientras esté desbloqueada; campos protegidos descifrados bajo demanda; preferir `ByteArray`/`CharArray` y ponerlos a cero tras su uso; descartar las referencias al bloquear. **Limitación:** en la JVM/ART, los objetos `String` y las copias hechas por el recolector de basura o por el toolkit de IU no se pueden borrar de forma fiable. |
| T4 | Secretos en logs, informes de fallos, archivos temporales | Sin registrar secretos en los logs (regla de lint + lista de comprobación de revisión); sin informes de fallos de terceros; sin archivos temporales en texto claro; las compilaciones de publicación eliminan los logs de depuración. |
| T5 | Captura de pantalla, miniatura en recientes | `FLAG_SECURE` en todas las ventanas que muestran datos de la bóveda (el usuario no puede desactivarlo en el MVP). |
| T6 | Filtración por el portapapeles | Marcar los clips como sensibles (`ClipDescription.EXTRA_IS_SENSITIVE`, Android 13+); borrado automático tras un tiempo configurable, solo si el clip sigue siendo nuestro; preferir el autorrelleno a copiar. |
| T7 | Aprendizaje del teclado o keylogging | Los campos de contraseña usan `IME_FLAG_NO_PERSONALIZED_LEARNING` y tipos de entrada de contraseña. Un teclado de terceros sigue pudiendo ver lo que se escribe: se documenta y se recomienda el autorrelleno. |
| T8 | Aplicación desbloqueada y desatendida | Bloqueo automático por tiempo de inactividad, al apagar la pantalla y, opcionalmente, al salir de la aplicación; el bloqueo borra las claves y los datos descifrados. |
| T9 | Abuso del desbloqueo biométrico | Clave maestra envuelta con una clave AES del Android Keystore con `setUserAuthenticationRequired(true)`, autenticación en cada uso mediante `BiometricPrompt` + `CryptoObject`, solo `BIOMETRIC_STRONG`, invalidada cuando cambian los datos biométricos; StrongBox cuando esté disponible. Opcional (opt-in), con una solicitud periódica de la contraseña maestra. |
| T10 | Exfiltración mediante la copia de seguridad de Android | `allowBackup="false"` más `dataExtractionRules` y `fullBackupContent` explícitos que excluyen todos los datos de la aplicación; secretos en `noBackupFilesDir`. |
| T11 | `.kdbx` malformado o malicioso | HMAC de la cabecera y de los bloques de KDBX 4 verificados antes de analizar (KDBX 3.1 se apoya en sus bytes de inicio de flujo y bloques con hash); límites a los tamaños y a los parámetros de la KDF para evitar el agotamiento de memoria; entidades externas XML desactivadas; pruebas de fuzzing sobre el adaptador del motor. |
| T12 | Pérdida de datos al guardar (interrupción, truncamiento, escrituras parciales) | Flujo de guardado seguro: copia de seguridad privada, escritura con `"rwt"`, verificación por relectura, restauración si falla ([ADR-0004](decisions/0004-storage-sync-and-safe-save.md)). |
| T13 | Cambios externos y conflictos de sincronización sobrescritos | Comprobación de la huella antes de cada guardado y al reanudar; fusión automática por UUID y marcas de tiempo; los archivos `.sync-conflict-` de Syncthing se ofrecen para fusionar y nunca se eliminan automáticamente. |
| T14 | Phishing mediante autorrelleno (nombres de paquete falsos) | Credenciales asociadas a aplicaciones por certificado de firma verificado y Digital Asset Links, no solo por nombre de paquete; confirmación explícita cuando no está verificado. (Fase 2.) |
| T15 | Permisos excesivos | Sin permiso de internet en el MVP; sin `MANAGE_EXTERNAL_STORAGE`; solo SAF. |
| T16 | Cadena de suministro | Pocas dependencias, fijadas en un catálogo de versiones; verificación de dependencias de Gradle (sumas de verificación); comprobaciones automáticas de vulnerabilidades y licencias en la CI; estudio de compilaciones reproducibles antes de la 1.0. |
| T17 | Compromiso de la clave de firma | Clave mantenida sin conexión por el mantenedor; la CI firma solo mediante secretos cifrados del repositorio; nunca se sube al repositorio. |

## Riesgos residuales (aceptados, documentados para los usuarios)

- Un dispositivo con root o infectado por malware puede leer la memoria y la entrada.
- Los strings de la JVM y los campos de texto de la IU pueden conservar copias de secretos en memoria hasta la recolección de basura.
- Los teclados de terceros ven las contraseñas escritas.
- Syncthing en Android es una aplicación de terceros; su comportamiento (por ejemplo, cómo sustituye los archivos) puede cambiar, y los parches de seguridad recientes de Android (septiembre de 2026) revocan los permisos de URI a nivel de archivo cuando se sustituye un archivo sincronizado. KeyNexa lo mitiga con concesiones de carpeta y un flujo de «reconectar archivo», pero no puede controlarlo.
- La seguridad de KDBX depende de la fortaleza de la contraseña maestra.
