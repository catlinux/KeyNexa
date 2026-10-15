# Copias en la nube: guía de configuración

[English](../CLOUD_SETUP.md) | [Español](CLOUD_SETUP.md)

> **Todavía no está verificada con una cuenta real.** Estos pasos siguen la documentación de cada servicio y están escritos para coincidir con lo que pide el asistente; se comprobarán con una cuenta real en la prueba H6. Los planes gratuitos, los precios y los límites cambian con el tiempo y aquí no se prometen. Solo se enlazan las páginas oficiales.

KeyNexa solo envía **el archivo `.kdbx` cifrado** al servicio que tú elijas, y solo después de que aceptes la pantalla de consentimiento. La contraseña maestra, las claves y el contenido descifrado nunca salen del dispositivo. Consulta [Privacidad](PRIVACY.md).

Abre **Ajustes → Copias en la nube** y sigue el asistente. Tiene cinco pasos: consentimiento, elegir el servicio, qué necesitas, el formulario y el estado de las copias.

## Backblaze B2

1. Crea una cuenta gratuita en <https://www.backblaze.com/cloud-storage>.
2. Crea un cubo **privado** (que no sea público).
3. Crea una clave de aplicación **limitada a ese cubo**, con permiso de lectura y escritura.
4. Copia el `keyID` y la `applicationKey` (la clave de aplicación solo se muestra una vez) en el asistente.
5. Para el endpoint, usa `https://s3.<región>.backblazeb2.com`; la región es la que muestra el cubo (por ejemplo `us-west-004`). Deja activado «usar rutas con el cubo en la ruta».

## Cloudflare R2

1. Crea una cuenta de Cloudflare y abre R2 en <https://developers.cloudflare.com/r2/>.
2. Crea un cubo (privado por defecto).
3. Crea un token de API con permiso de lectura y escritura sobre ese cubo y copia el Access Key ID y el Secret Access Key.
4. Para el endpoint, usa `https://<ID de cuenta>.r2.cloudflarestorage.com`; en la región escribe `auto`. Deja activado «usar rutas con el cubo en la ruta».

## MinIO u otro servidor propio

1. Crea un cubo privado en tu servidor.
2. Crea un usuario o una clave con permiso de lectura y escritura sobre ese cubo.
3. Usa tu endpoint HTTPS, la región que espere tu servidor y el nombre del cubo. Mantén activado «usar rutas con el cubo en la ruta». Consulta <https://min.io/docs>.

## Probar la conexión

El botón **Probar conexión** lista la carpeta de copias, sube un objeto de prueba muy pequeño y lo borra. Te dirá qué ha pasado:

- **Conexión correcta**: las claves, el endpoint y el cubo están bien. Pulsa entonces **Guardar y activar**.
- **El servicio rechazó las claves**: revisa el ID de la clave de acceso y la clave secreta, y que la clave tenga permiso de lectura y escritura en este cubo.
- **No se encuentra el cubo o la dirección es incorrecta**: revisa el endpoint y el nombre del cubo.
- **No se pudo conectar**: revisa la red y la dirección, y vuelve a intentarlo.

No se guarda nada hasta que la prueba funcione.

## Copias y errores

- Tras un guardado correcto, KeyNexa sube una copia en segundo plano, como mucho una vez al día. Puedes forzar una con **Copiar ahora**.
- Solo se rotan las copias creadas por KeyNexa: se conservan las más nuevas y se borran las más antiguas. Tú eliges 5, 10 o 20.
- Si una copia falla, la pantalla de estado muestra un mensaje breve; una subida fallida nunca bloquea el guardado de la bóveda.
- Si las claves dejan de valer, vuelve a configurar el servicio con **Cambiar de servicio**.

## Borrar las copias

**Desconectar y borrar** quita las credenciales y la marca del consentimiento de este dispositivo, pero no borra las copias ya subidas: bórralas desde el propio servicio (en su consola web o su herramienta). La guía de cada proveedor explica dónde.
