# ADR-0015: Actualizaciones automáticas de las apps de Android y Windows

[English](../../decisions/0015-automatic-updates.md) | [Español](0015-automatic-updates.md)

- **Estado:** Aceptada (el titular eligió la respuesta recomendada en los tres puntos abiertos el 2026-10-27)
- **Fecha:** 2026-10-27
- **Amplía:** [ADR-0012](0012-windows-desktop-client.md) (cliente de escritorio para Windows); el flujo de publicación de [DEVELOPMENT](../DEVELOPMENT.md)
- **Implementa:** la amenaza T29 del [modelo de amenazas](../THREAT_MODEL.md)

## Contexto

KeyNexa solo se publica en las publicaciones de GitHub: dos APK de Android (`offline` y `cloud`), firmados con la clave del proyecto, y un MSI y un ZIP de Windows sin firma de código (la decisión D9 sigue abierta). Los usuarios actualizan a mano, así que la mayoría usará versiones viejas, también sin los arreglos de seguridad. El titular pidió actualizaciones automáticas en las dos plataformas (2026-10-20).

El canal de actualizaciones es el objetivo más atractivo de un gestor de contraseñas: quien pueda publicar una actualización ejecuta código junto a cada bóveda. Por eso el diseño parte de lo que podría hacer un atacante, no de la comodidad.

Restricciones:

- La compilación `offline` no tiene permiso de `INTERNET` y lo sigue sin tener (T15).
- Sin servidores de KeyNexa, sin cuentas y sin telemetría: solo se contacta con GitHub, que ya aloja los archivos.
- Android nunca permite una instalación silenciosa fuera de una tienda: el instalador del sistema siempre pregunta al usuario.
- El MSI de Windows es por usuario y, desde la 1.11.5, se actualiza en la carpeta donde está.
- Una actualización nunca toca las bóvedas ni la carpeta de datos.

## Amenazas

1. **Una publicación maliciosa** hecha con una cuenta de GitHub robada, un flujo de publicación comprometido o un secreto de firma robado.
2. **Manipulación en el camino** o una red hostil.
3. **Vuelta atrás**: ofrecer una versión más antigua con un fallo conocido.
4. **Cambiar el archivo descargado** entre la comprobación y la instalación.
5. **Privacidad**: quién sabe que este equipo usa KeyNexa, y qué versión.

## Opciones

1. **Solo avisar:** consultar las publicaciones y mostrar «Hay una versión X» con un enlace. Es segura y sencilla, pero el usuario sigue descargando e instalando a mano, y no comprueba nada.
2. **Descargar y comprobar el SHA-256 publicado.** El hash sale de la misma publicación que el archivo, así que solo demuestra la transferencia, que ya protege HTTPS. Frente a la amenaza 1 no aporta nada.
3. **Descargar y comprobar una firma hecha con una clave de actualizaciones de KeyNexa cuya parte pública va dentro de la app.** El flujo de publicación firma con Ed25519 un pequeño manifiesto (la versión y el SHA-256 de cada archivo); la app comprueba la firma, luego el hash, y entonces instala. Una cuenta de GitHub robada ya no basta para publicar una actualización: también hace falta la clave privada. Ed25519 está en la JCA desde Java 15, así que el escritorio no necesita ninguna dependencia nueva.
4. **Apoyarse en la firma de la plataforma.** En Android el sistema ya rechaza una actualización que no esté firmada con la clave de la app instalada, que es la misma clave que usa la publicación. En Windows no hay nada equivalente hasta que el MSI tenga firma de código (D9).

## Decisión

La opción 3 en Windows, la opción 4 en Android y la opción 1 para la compilación `offline`, todas a elección del usuario.

1. **La consulta:** una sola petición `GET` anónima por HTTPS a `api.github.com/repos/catlinux/KeyNexa/releases`, con un `User-Agent` `KeyNexa/<versión>` y nada más, al arrancar y como mucho una vez al día. Se incluyen las preversiones mientras todas las publicaciones lo sean (hoy lo son todas). Solo se ofrecen versiones más nuevas que la que se ejecuta: nunca se propone volver atrás, diga lo que diga el servidor.
2. **Preguntar:** la app muestra la versión nueva y su entrada del registro de cambios, e instala solo si el usuario acepta, cada vez. No hay instalación silenciosa.
3. **Windows:**
   1. El trabajo de publicación del flujo escribe `keynexa-update.json` (la versión, y el nombre, el tamaño y el SHA-256 de cada archivo) y lo firma con la clave Ed25519 de actualizaciones en `keynexa-update.json.sig`. La clave privada está en un secreto del repositorio que solo ve ese trabajo, que ya no ejecuta Gradle ni código de terceros (la misma regla que la clave del APK).
   2. La app descarga el manifiesto y su firma, comprueba la firma con la clave pública que lleva, comprueba que la versión es más nueva, descarga el MSI en `%LOCALAPPDATA%\KeyNexa\updates`, comprueba el tamaño y el SHA-256 con el manifiesto, bloquea la bóveda, lanza `msiexec /i <archivo>` con la interfaz normal del instalador y se cierra. Si falla cualquier comprobación, borra el archivo y ofrece la página de la publicación.
   3. El hash se vuelve a comprobar justo antes de lanzar `msiexec`, y la carpeta es del usuario, lo que reduce la amenaza 4 a código que ya se ejecuta como ese usuario (T23, fuera del alcance).
4. **Android (solo la compilación `cloud`):** la app descarga el APK de su propia compilación en su caché privada, lee el certificado de firma del archivo descargado y el de la app instalada, y solo pasa el archivo al instalador del sistema si son iguales. El sistema vuelve a comprobar la firma. Necesita el permiso `REQUEST_INSTALL_PACKAGES`, que se añade solo a la compilación `cloud`. Una compilación firmada con otra clave (la de F-Droid, por ejemplo) no encuentra ninguna actualización que ofrecer, porque los APK de GitHub no podrían instalarse encima.
5. **La compilación `offline`:** sin red y sin consulta. Su pantalla *Acerca de* explica cómo actualizar (la página de publicaciones, o una app que siga las publicaciones de GitHub), y las tiendas la actualizarán cuando KeyNexa esté en F-Droid o IzzyOnDroid.
6. **Ajustes:** «Buscar actualizaciones» está **desactivado** hasta que el usuario responde una pregunta única en el primer arranque tras la actualización que trae esta función. Se puede desactivar en cualquier momento.
7. **Privacidad:** [PRIVACY](../PRIVACY.md) explica lo que ve GitHub (la dirección IP, la hora y la versión de la app en el `User-Agent`) y que no se envía nada más.

### Decidido por el titular (2026-10-27)

1. La clave de actualizaciones es una clave Ed25519 que genera el titular y se guarda como el secreto del repositorio `UPDATE_SIGNING_KEY`, así las publicaciones se firman sin un paso manual.
2. La búsqueda de actualizaciones empieza desactivada y la app pregunta una vez.
3. En Windows la app descarga, comprueba y lanza el instalador cuando el usuario acepta.

## Consecuencias

- Una cuenta de GitHub robada no basta para hacer llegar una actualización a los usuarios de Windows: también hace falta la clave de actualizaciones. Si la clave se filtra, el flujo de publicación y la app necesitan una clave nueva; una lista de claves en la app que permita rotarlas queda para más adelante.
- En Android la confianza sigue donde está hoy: la clave de firma del APK.
- La compilación `cloud` gana `REQUEST_INSTALL_PACKAGES`, y la app contacta con GitHub, algo que el usuario ha aceptado; la compilación `offline` no gana nada.
- El flujo de publicación gana un manifiesto y su firma; las versiones anteriores los ignoran.
- El código nuevo que descarga, comprueba y lanza un instalador es crítico para la seguridad: cada comprobación tiene una prueba con una firma incorrecta, un hash incorrecto, una versión más antigua y un archivo cortado, y el camino completo se prueba a mano en las dos plataformas antes de publicarlo.
