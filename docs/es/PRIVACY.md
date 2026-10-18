# Privacidad

[English](../PRIVACY.md) | [Español](PRIVACY.md)

KeyNexa es local-first: tu bóveda es un archivo que te pertenece. El proyecto no tiene servidores propios ni recoge telemetría. Esta página describe exactamente qué hace cada variante con tus datos.

## La variante `offline`

- No solicita el permiso `INTERNET` y nunca accede a la red.
- Nada sale de tu dispositivo. No hay cuenta, ni analíticas, ni informes de fallos.

## La variante `cloud`

- Solo envía algo después de que leas y aceptes la pantalla de consentimiento.
- Qué envía: **solo una copia cifrada del archivo de tu bóveda** (`.kdbx`), tal cual está en disco. La contraseña maestra, el archivo de clave, las claves derivadas y el contenido descifrado nunca salen del dispositivo.
- A dónde va: **solo al servicio en la nube que tú elijas y configures** (por ejemplo un contenedor compatible con S3). KeyNexa no tiene servidores propios.
- Para qué: para guardar copias de seguridad de la bóveda y, más adelante, para sincronizar entre tus dispositivos.
- No se envían estadísticas, ni identificadores de publicidad, ni informes de fallos de terceros.

## Qué se guarda en tu dispositivo

- Las credenciales de la nube que configures, cifradas con una clave AES-256-GCM no exportable del Android Keystore, en almacenamiento privado de la aplicación excluido de la copia de seguridad de Android.
- Un número de versión que registra que aceptaste el texto del consentimiento. No se guarda nada más sobre ti.

## Cómo borrarlo

- **Desconectar y borrar**, en Ajustes, borra las credenciales de la nube y la marca del consentimiento de este dispositivo. Las copias ya subidas no las borra KeyNexa: bórralas desde el propio servicio en la nube.
- Desinstalar KeyNexa elimina todos sus datos privados, incluidas las credenciales cifradas y la marca del consentimiento.

## Contacto

Las dudas o peticiones sobre privacidad se pueden plantear en el repositorio del proyecto: <https://github.com/catlinux/KeyNexa>.
