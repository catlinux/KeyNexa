# ADR-0008: Copias de la bóveda en la nube: asistente opcional, variante `cloud` y servicios

[English](../../decisions/0008-cloud-backup.md) | [Español](0008-cloud-backup.md)

- **Estado:** Aceptada (2026-10-15, por el propietario del proyecto)
- **Fecha:** 2026-10-15
- **Amplía:** el [ADR-0004](0004-storage-sync-and-safe-save.md); el paso posterior de «la nube como punto de encuentro siempre disponible» necesitará su propio registro

## Contexto

Hoy una bóveda vive en una carpeta que comparte Syncthing. Un móvil perdido o roto sin otra copia pierde todas las contraseñas, y dos dispositivos que nunca están encendidos a la vez no pueden encontrarse. El propietario quiere una copia en la nube opcional y muy recomendada, integrada en KeyNexa, gratuita, con un asistente que explique lo que necesita cada servicio y, más adelante, ese mismo servicio como punto de encuentro para sincronizar.

Restricciones:

- El archivo de la bóveda ya está cifrado (KDBX); la nube solo recibe texto cifrado.
- La app no declara hoy el permiso `INTERNET` y lo dice así. Android no permite activar y desactivar ese permiso en ejecución.
- Sin SDK propietarios, sin telemetría, sin dependencias nuevas sin motivo y licencia registrados ([AGENTS.md](../../../AGENTS.md)); sin criptografía propia.
- Iniciar sesión en Google, Dropbox o Microsoft exige que el proyecto registre KeyNexa una vez en cada uno; el resultado es un identificador de cliente público. Es un trámite manual del propietario del proyecto, que el asistente no puede hacer.

## Opciones

1. **Una carpeta que ofrece el selector del sistema (SAF).** Sencillo, sin código de red y sigue disponible; pero el soporte de cada proveedor es desigual y no hay nada que guíe al usuario.
2. **WebDAV** (Koofr, Nextcloud, servidor propio). Protocolo simple; quedan pocos proveedores gratuitos.
3. **Almacenamiento compatible con S3** (Backblaze B2, Cloudflare R2, MinIO en un VPS, AWS). No necesita registrar el proyecto y el servidor ofrece versiones y retención; el usuario debe crear cuenta, contenedor y claves, así que el asistente tiene que guiarlo.
4. **OAuth con la API REST del proveedor** (Google Drive, Dropbox, OneDrive). Lo más fácil para el usuario final (un solo inicio de sesión); necesita los registros de arriba y las reglas de cada proveedor deciden el esfuerzo.
5. **Syncthing como complemento opcional.** Ya está en evaluación (TODO); es independiente de este registro.

## Decisión

1. **Dos variantes de compilación de la misma app.** `offline` (la compilación actual, sin `INTERNET`; la de F-Droid y la de quien no quiera red) y `cloud` (declara `INTERNET` e incluye el código de la nube). La integración continua compila ambas y comprueba que el manifiesto de `offline` no tiene `INTERNET`. Cómo se publican las dos, y si una se puede instalar sobre la otra, se decide al construir las variantes.
2. **Un módulo nuevo para el código de red**, fuera de los módulos `core/*`, con sus dependencias registradas como siempre. Empieza con el cliente HTTP de la plataforma detrás de una interfaz pequeña; solo se añade una biblioteca si eso resulta insuficiente.
3. **Una interfaz para todos los servicios**, `CloudBackend`: subir, bajar, listar y borrar por nombre, devolviendo la etiqueta de entidad o revisión del proveedor, y una marca de capacidad para escrituras condicionales («solo si no ha cambiado»). El primer uso son **solo las copias de seguridad**: copias llamadas `<bóveda>-<fecha y hora UTC>.kdbx` dentro de una carpeta o prefijo propio de KeyNexa, conservadas hasta un número elegido (10 por defecto; solo se rota lo que KeyNexa ha creado), subidas en segundo plano tras un guardado correcto como mucho una vez al día, y un botón «Copiar ahora». Una subida fallida muestra un aviso discreto y nunca impide guardar.
4. **Un asistente.** Aparece solo en la variante `cloud` y solo cuando el usuario lo elige: (a) qué se envía, a dónde y por qué; (b) elegir el servicio en el orden Google Drive, Dropbox, OneDrive, S3; (c) qué necesita ese servicio, con enlaces para crear la cuenta o la clave; (d) conectar y probar; (e) primera copia; (f) recordatorio mientras no exista copia, nunca forzado. Los servicios aún no implementados no se muestran.
5. **Consentimiento y claridad.** Una pantalla dice, antes de configurar nada, que solo se envía el archivo cifrado de la bóveda, solo al servicio elegido y solo para copias y, más adelante, sincronización; que la contraseña maestra y las claves nunca salen del dispositivo; y cómo desconectarse. «Desconectar» borra las credenciales locales y, donde el proveedor lo permite, revoca la autorización.
6. **Orden de implementación.** Primero S3 (no necesita registro, valida la interfaz y el asistente), después Dropbox, OneDrive y por último Google Drive. El asistente los lista como en el punto 4.
7. **Los proveedores OAuth** usan el flujo de código de autorización con PKCE en el navegador del sistema, sin secreto de cliente en la app, y el permiso más estrecho que ofrezca el proveedor (una carpeta privada de la app, nunca todo el almacenamiento). Los identificadores de cliente son públicos y van en el código; cómo registrar cada uno se escribe en `docs/CLOUD_SETUP.md` cuando se añada el proveedor.
8. **Las credenciales** (claves S3, tokens OAuth) se guardan cifradas con una clave no exportable del Keystore de Android, en un almacenamiento excluido de la copia de seguridad, y nunca se registran en el log; tampoco las peticiones, porque llevan firmas o tokens. La validación de certificados TLS se queda en la predeterminada del sistema.
9. **La sincronización a través de la nube** (`StoragePort` remoto) es un paso posterior con su propio ADR. Solo se ofrece en los servicios cuyas escrituras condicionales se hayan probado; en los demás, la nube sigue siendo una copia de seguridad.

## Consecuencias

- La variante `cloud` tiene más superficie de ataque que `offline`. El modelo de amenazas gana el compromiso de la cuenta en la nube, el robo de credenciales y la exposición de metadatos (nombre, tamaño y fechas del archivo); las mitigaciones principales son la contraseña maestra fuerte que el usuario ya necesita, un archivo de clave y los permisos estrechos.
- Las copias antiguas pueden contener contraseñas antiguas (el historial de las entradas va dentro del archivo): el asistente lo dice y el número de copias queda limitado por defecto.
- El README y las fichas de las tiendas deben decir que solo la variante `offline` no tiene acceso a la red.
- Hace falta una declaración de privacidad (`docs/PRIVACY.md`, en los dos idiomas) antes de publicar la variante `cloud`, y algunos proveedores la piden para registrar la app.
- El propietario del proyecto debe registrar la app en cada proveedor OAuth antes de que salga su servicio, y mantener esos registros vivos.
- Los servicios con plan gratuito lo cambian de vez en cuando: la documentación los lista como información, nunca como promesa.

## Puntos abiertos (por verificar antes de prometerlos)

- Google ata un cliente Android al nombre del paquete y al certificado de firma. F-Droid firma con su propia clave, así que el inicio de sesión de Google puede no funcionar en esa compilación; y no se sabe si KeyNexa tendrá que pasar la verificación de Google.
- Si Microsoft exige una cuenta de Azure para registrar la app, y cuáles son los límites gratuitos de la carpeta de app de Dropbox.
- Qué proveedores respetan de forma fiable las escrituras condicionales (necesario para el punto 9).
- El espacio gratuito actual y sus condiciones, por proveedor.
