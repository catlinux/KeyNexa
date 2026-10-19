# ADR-0014: Desbloqueo rápido con Windows Hello en el escritorio

[English](../../decisions/0014-windows-hello-quick-unlock.md) | [Español](0014-windows-hello-quick-unlock.md)

- **Estado:** Aceptada (prueba técnica y prueba a mano con el PIN real superadas el 2026-10-19; implementada en 1.9.2–1.10.0)
- **Fecha:** 2026-10-19
- **Amplía:** [ADR-0006](0006-biometric-unlock.md) (desbloqueo biométrico en Android), [ADR-0012](0012-windows-desktop-client.md) (cliente de escritorio para Windows)
- **Implementa:** la amenaza T28 del [modelo de amenazas](../THREAT_MODEL.md)

## Contexto

El escritorio se bloquea solo a menudo, y así debe ser: tras la inactividad (5 minutos por defecto), con Win+L y al suspender (T23). Cada vez, el usuario vuelve a escribir la contraseña maestra larga, y eso empuja a usar contraseñas cortas. El titular pidió un desbloqueo rápido con el PIN de Windows Hello, con estas condiciones (acordadas el 2026-10-19):

- La contraseña maestra nunca se escribe en disco, de ninguna forma.
- Solo sirve para volver a desbloquear mientras la app sigue abierta. El primer desbloqueo tras abrir la app siempre pide la contraseña maestra, y al cerrarla se olvida todo.
- Caduca a las 8 horas (opciones de 1, 4, 8 y 24 h).
- La opción se oculta si el equipo no tiene Windows Hello (PIN, cara o huella).

De qué protege: de alguien ante la sesión de Windows abierta cuando KeyNexa ya se ha bloqueado, por ejemplo un PC desatendido, o de un volcado de la memoria del proceso hecho con KeyNexa bloqueado. Ninguno de los dos debe abrir la bóveda sin el gesto de Hello. De qué no protege: de un programa malicioso que se ejecuta como el mismo usuario mientras la bóveda está abierta (T22, T23). Esa amenaza ya queda fuera del alcance.

Restricciones:

- No basta con guardar el secreto en memoria sin nada alrededor: con la app bloqueada, un volcado de memoria lo leería.
- Se descarta DPAPI a secas: cualquier proceso del mismo usuario lo abre sin el PIN.
- KeyNexa escribe una semilla maestra y una sal de KDF nuevas en cada guardado, así que solo sigue siendo válido lo que se toma antes de la derivación de la clave (como en el ADR-0006).
- Nada de criptografía propia, ni de binario nativo propio si basta con JNA (JNA ya es dependencia de `platform/windows`).

## Opciones

1. **Guardar en memoria la contraseña maestra o la clave derivada sin nada alrededor.** Es sencillo, pero un volcado del proceso bloqueado la lee, y el «PIN» sería una comprobación que ninguna clave obliga a cumplir. Descartada.
2. **Una clave del proveedor de almacén de claves Microsoft Passport mediante NCrypt** (`NCryptCreatePersistedKey` sobre `Microsoft Passport Key Storage Provider`, `NCryptDecrypt` para desenvolver). Es JNA puro. Sin embargo, una clave creada así no pide el PIN en cada uso salvo que se fije una propiedad **no documentada** (`NgcCacheType`). Lo único que se sabe de ella es que aparece como cadena dentro de `cryptngc.dll`, y un ingeniero de Microsoft respondió en Q&A que ahí no se ofrece la autenticación en cada uso. Si Windows ignora esa propiedad, el desenvolvido funciona **sin PIN** y nada nos avisa. Falla en abierto. Descartada.
3. **Una credencial de clave de Windows Hello (`Windows.Security.Credentials.KeyCredentialManager`)**: una API documentada, la misma que usa Windows Hello para las webs. Su clave se crea en el contenedor de Hello (respaldado por el TPM si lo hay) y no se puede exportar. Cada `RequestSignAsync` muestra la ventana de Hello. Las firmas RSA PKCS#1 v1.5 son deterministas, así que firmar un reto aleatorio da siempre los mismos bytes, y de ellos se puede derivar una clave de envoltura. Otros gestores de contraseñas de código abierto usan este método. Es una API WinRT, así que desde la JVM se llama mediante JNA sobre su ABI COM (`RoGetActivationFactory`, las tablas de las interfaces, la espera de `IAsyncOperation`). Supone más trabajo que la opción 2, pero si algo falla, no se abre nada. Esa es la forma segura de fallar.
4. **`UserConsentVerifier` sin más** (pide el PIN y responde sí o no). El secreto no dependería de la respuesta: basta con parchear una rama en memoria para saltárselo. Descartada.

## Decisión

Opción 3, a elección del usuario y con el secreto solo en memoria.

1. **Clave de Hello:** una credencial de clave por usuario de Windows, llamada `KeyNexa.QuickUnlock`. Se abre con `OpenAsync`, o se crea con `RequestCreateAsync` si no existe. KeyNexa nunca lee de ella nada que no sean firmas. `IsSupportedAsync` decide si la opción se muestra.
2. **Qué se protege:** los componentes de la clave compuesta de la bóveda (`KdbxEngine.hashedForm`: el SHA-256 de la contraseña y la clave procesada del archivo de clave), nunca la contraseña misma, como en el ADR-0006. La derivación de la clave sigue ejecutándose en cada desbloqueo.
3. **Activación** (solo tras un desbloqueo correcto con la contraseña maestra y con la opción activada):
   1. Generar un reto aleatorio de 32 bytes con `SecureRandom`.
   2. `RequestSignAsync(reto)`: Windows pide el PIN **una vez**, justo después del desbloqueo con contraseña. Si el usuario cancela, el desbloqueo rápido queda desactivado en esta sesión, sin volver a preguntar.
   3. Derivar la clave de envoltura: HKDF-SHA256 (RFC 5869, construido con el `HmacSHA256` de la JCA y comprobado con los vectores de prueba del RFC) sobre la firma, con el reto como sal y `KeyNexa quick unlock v1` como información. Borrar la firma.
   4. Cifrar los componentes con AES-256-GCM (JCA, IV aleatorio de 12 bytes). La ubicación de la bóveda y la hora de activación van como datos asociados. Borrar la clave de envoltura y los componentes en claro.
   5. Guardar **solo en memoria**: el reto, el IV, el texto cifrado, la ubicación de la bóveda y la hora de activación. Nada va al disco, a los registros ni al portapapeles.
4. **Desbloqueo** (pantalla de desbloqueo de la misma bóveda, con la activación vigente): ofrecer «Desbloquear con Windows Hello» y lanzarlo solo una vez al aparecer la pantalla. Firmar el reto guardado, derivar la clave, descifrar, borrar la clave, abrir por el camino normal con `Credentials.ofHashes` (con la derivación de la clave) y borrar los componentes. El campo de la contraseña maestra sigue disponible debajo.
5. **Desactivar** (borrar todo lo guardado en memoria) cuando: se cierra la app; pasa el límite de tiempo (se comprueba al mostrar la pantalla de desbloqueo y una vez por minuto); el usuario apaga la opción; se desbloquea otra bóveda; los componentes guardados dan `WrongCredentials` (la contraseña cambió en otro dispositivo); el descifrado falla; o Hello devuelve un error que no sea una cancelación (clave borrada, Hello desactivado, PIN restablecido). Una **cancelación** mantiene el desbloqueo rápido activado y deja el campo de la contraseña. Hello ya limita los intentos de PIN (bloqueo del TPM), así que KeyNexa no añade un contador propio.
6. **Ajustes:** «Desbloquear con Windows Hello» está desactivado por defecto y solo aparece si Hello está disponible. «Volver a pedir la contraseña maestra al cabo de» ofrece 1, 4, 8 y 24 h, con 8 por defecto. Cuenta la hora de activación, no el último uso: desbloquear con el PIN no lo alarga.
7. **Ventana delante:** la ventana de Hello debe salir por encima de la de KeyNexa. La prueba técnica comprueba si hace falta un identificador de ventana (`IKeyCredentialManagerInterop` o un traspaso del primer plano). Sin eso, Hello no se puede usar.
8. **Organización del código:** las llamadas WinRT/JNA van en `platform/windows` (`WindowsHello`: disponible, firmar reto). La política pura va en `platform/desktop`: activación, caducidad, HKDF y AES-GCM, y desactivación, detrás de una interfaz `HelloSigner`, para poder probarla con un firmante falso. La conexión con la interfaz va en `apps/desktop`.

## Consecuencias

- Con KeyNexa bloqueado y la sesión abierta, entrar requiere el PIN, la cara o la huella del usuario de Windows, o la contraseña maestra. Un volcado del proceso bloqueado solo contiene texto cifrado, un reto y un IV.
- Quien sepa el PIN de Windows puede abrir la bóveda hasta que pase el límite de tiempo. El ajuste lo dice en una línea. La opción está desactivada por defecto y su límite nunca pasa de 24 h.
- Un programa malicioso que se ejecute como el usuario también puede pedir una firma. Windows mostrará la ventana de Hello, y un usuario que meta el PIN por error le da el secreto. Esto entra en T22 y T23 (una sesión comprometida queda fuera del alcance).
- Para un desbloqueo rápido no hace falta el archivo de clave, porque su clave procesada forma parte de los componentes.
- El primer desbloqueo tras abrir la app, y el primero tras pasar el límite de tiempo, siempre piden la contraseña maestra, así que el usuario sigue practicándola.
- Llamar a WinRT mediante JNA es código frágil: lleva sus propias pruebas, y la prueba técnica puede demostrar que es demasiado frágil. En ese caso, la alternativa (un pequeño programa nativo firmado, o renunciar a la función) vuelve al titular. La opción 2 no se usa.
- Necesita una prueba a mano con el PIN real del titular (y sin Hello configurado, para ver que la opción se oculta). Las pruebas automáticas solo cubren la política, con un firmante falso.
