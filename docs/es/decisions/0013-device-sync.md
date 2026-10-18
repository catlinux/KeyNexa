# ADR-0013: Sincronización entre dispositivos sin Syncthing

[English](../../decisions/0013-device-sync.md) | [Español](0013-device-sync.md)

- **Estado:** Aceptada
- **Fecha:** 2026-10-18
- **Amplía:** [ADR-0004](0004-storage-sync-and-safe-save.md), [ADR-0007](0007-conflict-copies.md), [ADR-0009](0009-cloud-sync.md)

## Contexto

Hoy dos dispositivos comparten una bóveda mediante Syncthing-Fork, una app aparte que es fácil olvidarse de iniciar y que KeyNexa no puede ver. El propietario quiere que KeyNexa sincronice por sí misma, como un modo más del mismo paquete (ADR-0012), sin necesitar Syncthing.

Syncthing sincroniza carpetas cualesquiera bloque a bloque. KeyNexa solo necesita sincronizar bóvedas, que ocupan poco, y ya tiene lo que a Syncthing le falta: una fusión por UUID y fechas de modificación que conserva el historial y nunca sobrescribe lo que no sabe fusionar ([ADR-0004](0004-storage-sync-and-safe-save.md)).

## Opciones

1. **Incluir Syncthing** (MPL-2.0) y manejarlo con su API local. Probado y con relés por Internet, pero añade un binario grande por plataforma, un proceso aparte, un servicio en primer plano en Android y sus propias copias de conflicto.
2. **Un protocolo propio a nivel de bóveda (este registro)**: los dispositivos emparejados se intercambian el `.kdbx` cifrado entero y cada uno lo fusiona con el motor existente. Por la red local; a través de la nube (ADR-0009) para los dispositivos que nunca coinciden.
3. **Relés por Internet propios**: necesitan un servidor, descartado en el ADR-0008.

## Decisión

1. **Opción 2.** La sincronización intercambia archivos de bóveda cifrados enteros entre dispositivos emparejados; la fusión y el guardado son el `KeePassMerger` y el proceso de guardado seguro que ya existen. Los dispositivos que no están en la misma red se encuentran a través de la nube (ADR-0009). No hay relés por Internet.
2. **Nada de criptografía propia.** Cada dispositivo tiene un par de claves EC P-256 de larga duración y un certificado X.509 autofirmado; su id de dispositivo es el SHA-256 del certificado. Las conexiones usan **solo TLS 1.3, con autenticación mutua**, y aceptan solo certificados de dispositivos emparejados (fijados por su huella). La clave privada vive en el Android Keystore o, en Windows, cifrada con DPAPI.
3. **Emparejamiento** con un código QR que muestra el dispositivo que escucha (normalmente el escritorio): contiene la huella de ese dispositivo, su dirección y un testigo de emparejamiento aleatorio de un solo uso, válido dos minutos. El otro dispositivo lo escanea (ZXing, Apache-2.0; ninguna biblioteca de escaneo privativa), se conecta fijando la huella escaneada y presenta el testigo dentro del canal TLS; el dispositivo que escucha muestra entonces el nombre y la huella corta del nuevo dispositivo y el usuario lo confirma allí. Sin cámara, se pueden escribir la dirección y el testigo, y las dos pantallas muestran la misma huella corta para compararla. Quitar un dispositivo de la lista lo revoca.
4. **Qué bóvedas:** cada bóveda recibe un id de sincronización aleatorio guardado en sus propios datos personalizados (`KeyNexa.SyncId`), así que cada dispositivo la reconoce sea cual sea la ruta de su archivo; el mismo id sirve para el ADR-0009. El usuario elige qué bóvedas se comparten con qué dispositivo. Nunca se envía nada más.
5. **Una sesión** (versión 1 del protocolo; el formato de los mensajes y los límites se detallan en `core/peer`):
   1. Cada lado envía un saludo: versión del protocolo y, por cada bóveda compartida, su id de sincronización, la huella de su archivo actual y las huellas que envió y recibió por última vez de ese dispositivo.
   2. Si nada ha cambiado en ningún lado desde el último intercambio, no se envía nada.
   3. Si no, el lado cuyo archivo no ha visto el otro lo envía, junto con las copias pendientes que guarde (punto 6).
   4. Un receptor **desbloqueado** descifra el archivo recibido con las credenciales de la bóveda abierta y lo fusiona. Si la fusión no cambia nada de su bóveda, no escribe nada; si no, guarda por el proceso de guardado seguro y devuelve el resultado. Un archivo que no puede descifrar (otra contraseña maestra, dañado) se guarda como copia pendiente y se avisa, como se hace hoy con las copias de conflicto (ADR-0007, incluido el flujo de otra contraseña).
   5. Los dos lados anotan las huellas intercambiadas, así que el siguiente saludo no encuentra nada nuevo. Cada guardado crea una semilla maestra nueva, de modo que un mismo contenido puede tener huellas distintas; la regla «si la fusión no cambia nada, no se escribe nada» es lo que evita que los dispositivos se reenvíen archivos sin fin.
6. **Un dispositivo bloqueado nunca sustituye el archivo de su bóveda** (sin la clave no puede verificar un archivo recibido). Guarda lo que recibe como **copia pendiente** en sus datos privados, como mucho una por dispositivo y bóveda (el archivo más nuevo de un dispositivo contiene el anterior), y ofrece su propio archivo y esas copias pendientes al siguiente dispositivo que se conecte. Cualquier dispositivo desbloqueado las fusiona, y el bloqueado fusiona sus copias pendientes al desbloquearse, mediante el libro del ADR-0007. Así, un escritorio que se queda en la bandeja del sistema transmite los cambios entre móviles sin tener nunca la clave, y su propio archivo en disco se pone al día en el siguiente desbloqueo.
7. **Descubrimiento:** el dispositivo que escucha se anuncia en la red local con DNS-SD/mDNS (`_keynexa._tcp`, con un nombre de instancia opaco, nunca el nombre del dispositivo ni del usuario); también se pueden escribir a mano una dirección y un puerto. Solo escuchan los dispositivos con el modo de sincronización activado, en un puerto configurable; Android no escucha en la primera versión, sino que se conecta mientras KeyNexa está delante (al abrir, tras guardar y con «Sincronizar ahora»).
8. **Límites:** tamaño máximo de archivo, tiempos máximos en cada paso, una sesión por dispositivo a la vez y una espera tras saludos fallidos. Un fallo nunca bloquea el desbloqueo ni el guardado y deja la bóveda local intacta; el estado se muestra con discreción («sincronizado con Escritorio hace 2 min», «no se pudo sincronizar: …»).
9. **Android:** solo sincroniza la compilación `cloud`, porque Android exige el permiso de Internet para cualquier socket, también en la red local; la compilación `offline` sigue sin él. El permiso de cámara para el código QR también se pide solo en la compilación `cloud`.

## Consecuencias

- Los usuarios de KeyNexa ya no necesitan Syncthing. Sigue funcionando para quien lo use, porque las copias de conflicto se siguen fusionando.
- Los dispositivos en redes distintas solo se sincronizan a través de un servicio en la nube con escrituras condicionales probadas (ADR-0009); sin él, se sincronizan cuando coinciden en la misma red.
- En un escritorio bloqueado, el archivo de la bóveda va por detrás de los demás dispositivos hasta que se desbloquea; otro programa KeePass que abra ese archivo ve la versión anterior.
- Modelo de amenazas: un dispositivo de la red local puede ver que hay un dispositivo KeyNexa escuchando y en qué puerto, pero no puede conectarse sin un certificado emparejado. Un certificado de dispositivo robado sin la contraseña maestra puede enviar basura, que nunca se descifra: solo se guarda como copia pendiente y se avisa. Un dispositivo emparejado que tiene la contraseña maestra puede cambiar la bóveda como cualquier cliente; los borrados siguen viajando solo como objetos borrados de KDBX, el historial se conserva y quedan las copias privadas del proceso de guardado seguro.
- El protocolo es código nuevo que procesa entrada de la red: necesita pruebas en la JVM con dos dispositivos en memoria (carreras, dispositivos desconocidos, archivos de otra bóveda, entrada demasiado grande o truncada) y fuzzing del decodificador de mensajes antes de publicarse.
