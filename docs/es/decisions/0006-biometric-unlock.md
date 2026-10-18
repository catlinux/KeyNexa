# ADR-0006: Desbloqueo biométrico

[English](../../decisions/0006-biometric-unlock.md) | [Español](0006-biometric-unlock.md)

- **Estado:** Aceptada
- **Fecha:** 2026-10-13
- **Implementa:** la amenaza T9 del [modelo de amenazas](../THREAT_MODEL.md); la fase D6 de la hoja de ruta

## Contexto

Escribir una contraseña maestra larga en el móvil cada vez es la razón principal por la que la gente elige contraseñas débiles. Por eso debe existir el desbloqueo biométrico, pero nunca puede convertirse en la vía más débil para entrar en la bóveda. Restricciones:

- KeyNexa escribe una semilla maestra y una sal de KDF nuevas en cada guardado, así que una clave derivada (posterior a la KDF) caduca tras cada guardado; solo lo que se toma antes de la derivación de clave sigue siendo válido.
- El Android Keystore puede ligar una clave a la autenticación biométrica, en cada uso, e invalidarla cuando se registran nuevos datos biométricos (`setInvalidatedByBiometricEnrollment`).
- El `minSdk` es 26: la API de vinculación biométrica por uso (`setUserAuthenticationParameters`) solo existe desde la API 30; en 26-29, una validez de `-1` segundos da el mismo comportamiento por uso.

## Opciones

1. **Guardar la contraseña maestra cifrada con una clave del Keystore.** Sencillo, pero la contraseña en claro vuelve a existir tras descifrarla, y puede estar reutilizada en otros servicios.
2. **Guardar los componentes de la clave compuesta** (el SHA-256 de la contraseña y los datos procesados del archivo de clave, que es lo que KDBX resume antes de la derivación de clave) cifrados con una clave del Keystore ligada a la biometría. La contraseña nunca se guarda. La derivación de clave sigue ejecutándose en cada desbloqueo.
3. **Guardar la clave posterior a la KDF.** Desbloqueo más rápido, pero no vale tras cada guardado (sal nueva) y se salta la protección que da la derivación de clave frente a un móvil robado y un Keystore comprometido.
4. **Credencial del dispositivo (PIN, patrón) como alternativa.** Más débil que la contraseña maestra en la mayoría de dispositivos.

## Decisión

La opción 2, sin alternativa por credencial del dispositivo.

1. **Una clave del Keystore por bóveda:** AES-256-GCM, sin relleno, alias derivado de un hash de la ubicación de la bóveda. Indicadores obligatorios: `setUserAuthenticationRequired(true)`; autenticación en cada uso (`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` en la API 30+, `setUserAuthenticationValidityDurationSeconds(-1)` antes); `setInvalidatedByBiometricEnrollment(true)`; `setUnlockedDeviceRequired(true)` en la API 28+; `setIsStrongBoxBacked(true)` cuando el dispositivo declara `FEATURE_STRONGBOX_KEYSTORE`, con alternativa al TEE si la generación de la clave falla.
2. **Qué se envuelve:** los componentes de la clave compuesta, nunca la contraseña. `core/vault` incorpora una forma de construir `Credentials` a partir de esos componentes, y kotpass un pequeño constructor público para credenciales ya resumidas (un cambio local anotado en su README).
3. **Alta** solo justo después de un desbloqueo con contraseña correcto, y por elección explícita: inicializar el cifrador para cifrar, autenticar con `BiometricPrompt` + `CryptoObject` (solo `BIOMETRIC_STRONG`), cifrar y guardar el texto cifrado y el IV en `noBackupFilesDir`.
4. **Desbloqueo:** inicializar el cifrador para descifrar con el IV guardado, autenticar con `BiometricPrompt` + `CryptoObject`, descifrar, abrir la bóveda por el camino normal (derivación de clave incluida) y borrar enseguida los componentes descifrados.
5. **Invalidación:** `KeyPermanentlyInvalidatedException` (nuevos datos biométricos, bloqueo de pantalla eliminado), un resultado `WrongCredentials` con los componentes guardados (la contraseña cambió en otro dispositivo) o un fallo al descifrar borran la clave y los datos guardados y vuelven a la contraseña, explicándole al usuario el motivo.
6. **Contraseña periódica:** la contraseña maestra se vuelve a pedir tras 7 días sin escribirla (configurable de 1 a 30 días) y siempre que falten o no sirvan los datos guardados. La marca de tiempo se guarda junto a ellos.
7. **Nada más usa los componentes descifrados:** ni registros, ni copias, ni autocompletado; solo existen mientras dura un desbloqueo.

## Consecuencias

- Un atacante con el móvil desbloqueado y el dedo del propietario puede abrir la bóveda, como con cualquier desbloqueo biométrico; uno sin la biometría necesita la contraseña maestra. Un móvil robado con el Keystore comprometido sigue enfrentándose a la derivación de clave completa, porque nunca se guarda la clave posterior a la KDF.
- Un desbloqueo biométrico tarda lo mismo que la derivación de clave (segundos con Argon2 en un móvil); la interfaz muestra el progreso.
- Cambiar la biometría, quitar el bloqueo de pantalla o cambiar la contraseña en otro dispositivo acaba siempre en una petición de contraseña; la interfaz debe explicarlo en lugar de fallar en silencio.
- El diseño necesita una matriz de pruebas en dispositivos reales (con y sin StrongBox, API 26-29 y 30+) antes de publicarse.
