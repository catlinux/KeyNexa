# Copias en la nube: guía de configuración

[English](../CLOUD_SETUP.md) | [Español](CLOUD_SETUP.md)

> **Verificada con una cuenta real para Backblaze B2 (2026-10-15) y Cloudflare R2 (2026-10-16).** Los pasos de Amazon S3 y MinIO siguen la documentación de cada servicio y aún no se han probado. Los planes gratuitos, los precios y los límites cambian con el tiempo y aquí no se prometen. Solo se enlazan las páginas oficiales.

KeyNexa solo envía **el archivo `.kdbx` cifrado** al servicio que tú elijas, y solo después de que aceptes la pantalla de consentimiento. La contraseña maestra, las claves y el contenido descifrado nunca salen del dispositivo. Consulta [Privacidad](PRIVACY.md).

Abre **Ajustes → Copias en la nube** y sigue el asistente. Tiene cinco pasos: consentimiento, elegir el servicio, qué necesitas, el formulario y el estado de las copias. Con Backblaze, Amazon S3 y Cloudflare R2 la región se rellena sola a partir del endpoint, así que puedes dejar vacío el campo de la región.

## Backblaze B2

1. Crea una cuenta gratuita en <https://www.backblaze.com/cloud-storage>. La página puede tardar un rato en cambiar de pantalla tras escribir el correo. Confirma el correo, elige una contraseña (vale el generador de tu gestor de contraseñas), elige la región donde se guardan tus datos (por ejemplo Europa o Estados Unidos), acepta los términos y elige *personal* u *organización*. Al entrar te pide un código enviado al correo (doble comprobación); puedes indicar que confíe en ese navegador.
2. Crea un contenedor **privado** (que no sea público) con un nombre único y sin datos personales, por ejemplo `keynexa-copias-4821`. Deja las opciones del servidor como vienen: la copia ya va cifrada por KDBX.
3. En los detalles del contenedor, apunta el **endpoint** (puede aparecer como «Endpoint», «Punto final», «S3 endpoint» o similar, por ejemplo `s3.eu-central-003.backblazeb2.com`). El panel lo muestra sin `https://`; el asistente lo necesita con `https://`.
4. Crea una clave de aplicación (*Application Keys → Add a New Application Key*) **limitada a ese contenedor**, con permiso de lectura y escritura.
5. Copia el `keyID` y la `applicationKey` (la clave de aplicación solo se muestra una vez) en el asistente. Escríbelos en el móvil desde tu gestor de contraseñas.
6. En el formulario: endpoint `https://s3.<región>.backblazeb2.com`, el nombre del contenedor, y deja activado «usar rutas con el contenedor en la ruta». La región es opcional: el asistente la rellena desde el endpoint (por ejemplo `eu-central-003`), así que puedes dejar el campo vacío.

## Cloudflare R2

Verificado con una cuenta real el 2026-10-16. El panel se puede pasar a español en los ajustes de la cuenta; los nombres de abajo son los ingleses (en español: «Almacenamiento y bases de datos → Almacenamiento de objetos»).

1. Crea una cuenta en <https://dash.cloudflare.com/sign-up> con un correo, una contraseña y la verificación humana, y confirma el correo.
2. Abre **Storage & databases → R2 object storage** y añade la suscripción de R2 a la cuenta. Pide una tarjeta aunque R2 incluye un uso mensual gratuito; consulta los precios actuales en las páginas de Cloudflare. Se aceptó una tarjeta prepago.
3. Crea un contenedor (privado por defecto). Vale la ubicación **Automático** y la clase de almacenamiento **Estándar**.
4. Crea el token: **R2 object storage → Overview → Account Details → API Tokens → Manage → Create Account API token**, con el permiso **Object Read & Write**. Si el formulario no deja limitarlo al contenedor, el token sirve para todos los contenedores de la cuenta: revócalo si más adelante creas otros contenedores. Copia el Access Key ID y el Secret Access Key (el secreto se muestra una sola vez) en tu gestor de contraseñas.
5. En el asistente, usa el endpoint `https://<ID de cuenta>.r2.cloudflarestorage.com` (el ID de cuenta está en la página de resumen de R2). La región es opcional: el asistente rellena `auto`. Deja activado «usar el contenedor en la ruta». Un contenedor creado en una jurisdicción (UE, EE. UU.) necesita su propio endpoint, como `https://<ID de cuenta>.eu.r2.cloudflarestorage.com`.

## Amazon S3

Todavía no probado con una cuenta real. Amazon pide una tarjeta de pago al registrarte y cobra por uso; una bóveda ocupa muy poco, pero consulta los precios y el nivel gratuito vigentes en las páginas de Amazon.

1. Crea una cuenta de AWS en <https://aws.amazon.com/s3/> y abre la consola de S3.
2. Crea un contenedor. Elige la región más cercana (por ejemplo `eu-west-1`) y deja activado **Block all public access**.
3. En IAM, crea un usuario para KeyNexa sin acceso a la consola y con una política limitada a ese contenedor: necesita `s3:ListBucket` sobre el contenedor y `s3:GetObject`, `s3:PutObject` y `s3:DeleteObject` sobre sus objetos.
4. Crea una clave de acceso para ese usuario y copia el Access Key ID y el Secret Access Key (el secreto solo se muestra una vez) en el asistente.
5. Para el endpoint, usa `https://s3.<región>.amazonaws.com` (por ejemplo `https://s3.eu-west-1.amazonaws.com`). La región se rellena sola a partir del endpoint, y puedes dejar activado «usar rutas con el contenedor en la ruta».

## MinIO u otro servidor propio

1. Crea un contenedor privado en tu servidor.
2. Crea un usuario o una clave con permiso de lectura y escritura sobre ese contenedor.
3. Usa tu endpoint HTTPS, la región que espere tu servidor y el nombre del contenedor. Mantén activado «usar rutas con el contenedor en la ruta». Consulta <https://min.io/docs>.

## Probar la conexión

El botón **Probar conexión** lista la carpeta de copias, sube un objeto de prueba muy pequeño y lo borra. Te dirá qué ha pasado:

- **Conexión correcta**: las claves, el endpoint y el contenedor están bien. Pulsa entonces **Guardar y activar**.
- **El servicio rechazó las claves**: revisa el ID de la clave de acceso y la clave secreta, y que la clave tenga permiso de lectura y escritura en este contenedor.
- **No se encuentra el contenedor o la dirección es incorrecta**: revisa el endpoint y el nombre del contenedor.
- **No se pudo conectar**: revisa la red y la dirección, y vuelve a intentarlo.

No se guarda nada hasta que la prueba funcione.

## Copias y errores

- Tras un guardado correcto, KeyNexa sube una copia en segundo plano, como mucho una vez al día. Puedes forzar una con **Copiar ahora**.
- Solo se rotan las copias creadas por KeyNexa: se conservan las más nuevas y se borran las más antiguas. Tú eliges 5, 10 o 20.
- Si una copia falla, la pantalla de estado muestra un mensaje breve; una subida fallida nunca bloquea el guardado de la bóveda.
- **Copiar ahora** necesita una bóveda abierta: abre la bóveda, luego **Ajustes** en su menú (los tres puntos) y **Copias en la nube**. Los Ajustes abiertos desde la lista de bóvedas no tienen ninguna bóveda que copiar.
- La primera copia no muestra ningún aviso: la línea de estado pasa a «Última copia: hace 0 minutos». Compruébala en el panel del propio servicio (en Backblaze: *Buckets → tu contenedor → Browse Files*, carpeta `KeyNexa/`).
- Si las claves dejan de valer, vuelve a configurar el servicio con **Cambiar de servicio**.
- Android no deja que una aplicación use la carpeta *Descargas* del sistema como ubicación de una bóveda; guarda las bóvedas en otra carpeta.

## Borrar las copias

**Desconectar y borrar** quita las credenciales y la marca del consentimiento de este dispositivo, pero no borra las copias ya subidas: bórralas desde el propio servicio (en su consola web o su herramienta). La guía de cada proveedor explica dónde.
