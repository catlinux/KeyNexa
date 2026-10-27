# Revisión de seguridad

[English](../SECURITY_REVIEW.md) | [Español](SECURITY_REVIEW.md)

Aquí se guardan dos revisiones. La primera es la revisión final antes de la 1.0 de Android (paso B3 de la hoja de ruta y la parte de código del paso Z), hecha el 2026-10-18 sobre la versión 0.33.3; los arreglos que provocó están en la 0.33.4 a la 0.33.11. La segunda, la [revisión del cliente de escritorio](#revisión-del-escritorio-2026-10-27), cubre lo que vino después.

## Alcance y método

- **Código:** todos los módulos del repositorio: `core/vault`, `core/kdbx`, `core/sync`, `platform/android`, `platform/cloud`, `apps/android` (fuentes `main`, `offline` y `cloud`), el `third_party/kotpass` incluido (solo donde toca entradas no fiables y los cambios locales), y los flujos de CI y de publicación. Unas 15.000 líneas de código de KeyNexa y 10.000 de código incluido.
- **Cómo:** una lectura manual por riesgo (secretos en memoria y en disco, guardado seguro y recuperación, nube, superficie de Android, analizador incluido, cadena de suministro) y búsquedas automáticas en todo el código de registros, `http://`, aleatoriedad insegura, componentes exportados, archivos temporales o legibles por todos y contraseñas guardadas como `String`. Cada arreglo vino con una prueba, y con una prueba que fallaba antes del arreglo cuando se pudo.
- **Dispositivo:** un Pixel 10 Pro (Android 17) por adb con la bóveda ficticia `prova-nube`: desbloqueo con el teclado propio y con el del sistema, borrado al pasar a segundo plano, bloqueo en segundo plano, copia a la nube en Backblaze B2, recuperación de una copia sin guardar que había quedado perdida, texto grande. La conexión se cortó durante la revisión; las comprobaciones pendientes están más abajo.
- **Quién:** un modelo de IA (Claude Opus) trabajando para el titular. Es una revisión documentada, **no una auditoría externa independiente**; la hoja de ruta pide una de las dos antes de la 1.0, y se recomienda una segunda opinión independiente (otro modelo o una persona).

## Hallazgos corregidos

| # | Gravedad | Área | Hallazgo | Corregido en |
| --- | --- | --- | --- | --- |
| R1 | Media | Nube | Dos bóvedas con el mismo nombre compartían carpeta en la nube y número de «copias a conservar», así que las subidas de una podían borrar por rotación todas las copias de la otra. | 0.33.4 |
| R2 | Media (JVM), baja (Android) | Analizador incluido | El analizador XML incluido con kotpass resolvía entidades externas en la JVM (XXE). Un archivo de clave XML hostil podía meter un archivo local en la clave o conectarse a la red. `THREAT_MODEL` T11 ya decía que estaba desactivado; no lo estaba. El analizador de Android no las cargaba, pero los clientes de escritorio funcionarán en la JVM. | 0.33.7 |
| R3 | Media | Publicación | El trabajo de publicación ejecutaba la compilación (Gradle, plugins, dependencias, acciones de terceros fijadas por etiqueta) en la misma máquina donde luego decodificaba la clave de firma, y no comprobaba que el APK offline no pidiera permiso de Internet, un control que el plan daba por hecho. | 0.33.6 (lo comprueba la próxima etiqueta) |
| R4 | Baja a media | Sincronización | La cabecera sin cifrar de una copia de conflicto podía pedir una derivación de clave enorme dentro de los límites absolutos del motor; se descifraba en cada apertura en el hilo que también guarda. | 0.33.5 |
| R5 | Baja | Teclado | Con un lector de pantalla activo, el teclado de KeyNexa leería cada tecla en voz alta, mientras que el del sistema sigue el ajuste de Android de no leer las contraseñas. | 0.33.3 |
| R6 | Baja | Guardado | Un guardado que fallaba limpiamente dejaba su copia privada sin guardar sin ofrecer tras cerrar la app, y las copias de intentos fallidos nunca se borraban tras un guardado posterior correcto. | 0.31.0 |
| R7 | Baja | Entrada | *Abrir URL* se ofrecía para cualquier dirección pero solo se abren las http y https; un móvil sin navegador se cerraba al pulsarlo. | 0.33.8 |
| R8 | Baja | Accesibilidad | Los botones de altura fija cortaban su etiqueta con texto grande (*Desbloquear con biometría*). | 0.33.1 |
| R9 | Informativo | CI | El fallo intermitente del trabajo de keepass-rs era la verificación de dependencias de Gradle de un POM sin suma, no una diferencia de interoperabilidad. | 0.33.9 |

**Regresión causada por esta revisión:** la primera versión del arreglo de R2 (0.33.7) también ponía `isExpandEntityReferences = false`, lo que en Android rompe las entidades XML predefinidas que contiene toda bóveda, así que cualquier bóveda se leía como dañada. Las pruebas unitarias se ejecutan en la JVM y no lo detectaron; el móvil sí. Corregido en la 0.33.11, manteniendo la protección contra entidades externas.

Además, en los mismos días y antes de esta revisión: se ignoran los toques bajo ventanas de otras apps (0.30.5), la pantalla de desbloqueo menciona en voz baja un teclado o servicio de accesibilidad que no viene con el sistema (0.31.0), y las contraseñas maestras se escriben con el teclado de KeyNexa (0.32.0 a 0.33.0; [ADR-0010](decisions/0010-in-app-password-keypad.md)).

## Riesgos aceptados y puntos abiertos

| # | Gravedad | Punto | Decisión |
| --- | --- | --- | --- |
| A1 | Baja | La clave secreta de la nube se guarda como `String` mientras se usa, y las credenciales serializadas no se borran tras cifrarlas. | Aceptado: en disco están cifradas con una clave no exportable del Keystore; de todos modos, las cadenas de la JVM no se pueden borrar de forma fiable. |
| A2 | Baja | La comprobación de `<!DOCTYPE` en las respuestas S3 lee los bytes como Latin-1, así que un documento en UTF-16 la pasaría. | Aceptado: el servidor es el que configuró el usuario, por HTTPS, y el analizador de Android no carga entidades externas; el refuerzo del analizador sigue aplicándose. |
| A3 | Baja | El tamaño de una respuesta S3 no está limitado. | Aceptado: la misma confianza que en A2. |
| A4 | Informativo | Los nombres de los objetos en la nube muestran el nombre saneado de la bóveda (T20). | Documentado; el usuario puede elegir un nombre neutro. |
| A5 | Informativo | `ci.yml` y `fuzz.yml` usan acciones fijadas por etiqueta. | Aceptado por ahora: esos trabajos no tienen secretos. Fijarlas cuando se actualicen las herramientas. |
| A6 | Baja | `BiometricStore.save` no es atómico (de la revisión de la 0.10.2). | Aceptado: un blob roto acaba en la petición de contraseña y se borra. |
| A7 | Residual | Un servicio de accesibilidad o un sistema comprometido todavía pueden leer lo que se escribe o se muestra (T22). | Fuera del alcance de una app; mitigado con el aviso discreto, la opción de archivo de clave y la 2FA. |

## Comprobaciones en el móvil

Hechas el 2026-10-18 al volver la conexión (versión 0.33.11):

- **Matar la app durante un guardado:** 13 cierres forzados entre 0,4 y 4,4 segundos después de *Guardar*. Antes de la escritura se pierde la edición en curso y el archivo no se toca; después, la edición queda guardada. El archivo nunca quedó dañado. La escritura en sí dura pocos milisegundos y no se pudo acertar desde adb; la recuperación tras una escritura cortada sigue cubierta por las pruebas de inyección de fallos (AC3).
- **Una variante sobre la otra:** la `offline` instalada sobre la `cloud` conserva bóvedas y ajustes y no muestra la opción de nube; la `cloud` instalada de nuevo conserva la configuración de la nube, y una copia se sube a Backblaze B2 con la carpeta nueva de R1.
- **Borrar el historial de una entrada:** de 10 versiones anteriores a 0, y siguen en 0 tras bloquear y volver a abrir.
- **Lector de pantalla:** con TalkBack activo, la pantalla de desbloqueo ofrece el teclado del sistema y no el teclado propio. TalkBack se desactivó de nuevo y se restauraron los ajustes de accesibilidad.

- **El diálogo de «otra contraseña» de una copia de conflicto:** una copia de `prova-nube` recifrada con otra contraseña y con una entrada más (hecha con `keepassxc-cli`). El diálogo se abre con el teclado de KeyNexa y sin el del sistema; una contraseña incorrecta se rechaza con un mensaje claro; la correcta fusiona la copia (aparece la entrada nueva y la bóveda se guarda), y *Borrar las copias* elimina el archivo.

Aplazado por el titular, sin bloquear la 1.0: desbloqueo biométrico en un móvil sin StrongBox y con API 26–29, y Argon2 en un móvil modesto.

## Revisión del escritorio (2026-10-27)

Hecha sobre la versión 1.11.2; los arreglos están en la 1.11.2 y la 1.11.3.

- **Alcance:** todo lo añadido desde la 0.33.11, unas 15.300 líneas de Kotlin de producción: `platform/windows` (DPAPI, portapapeles, protección de capturas, eventos de sesión y de energía, Windows Hello sobre WinRT), `platform/desktop` (almacenamiento de archivos, ajustes, temporizador del portapapeles, almacenes de la nube, la política del desbloqueo rápido), los almacenes de `platform/jvm`, la sincronización por la nube en `core/sync`, `core/session` y `platform/cloud`, el cableado del desbloqueo, el portapapeles y el desbloqueo rápido en `apps/desktop`, las sugerencias de usuario y el trabajo `windows` del flujo de publicación.
- **Cómo:** el mismo método que arriba. Las pantallas que nunca tocan un secreto solo se miraron por encima, no línea a línea.
- **Quién:** el mismo modelo de IA (Claude Opus) para el titular; de nuevo, **no es una revisión independiente**.

### Hallazgos corregidos

| # | Gravedad | Área | Hallazgo | Corregido en |
| --- | --- | --- | --- | --- |
| R10 | Media | Windows Hello | Mientras esperaba el PIN, el código que trae el prompt de Hello al frente se ejecutaba cada 150 ms durante hasta cinco minutos, con una pulsación de Alt simulada cada vez. Un usuario que cambiaba a otra ventana volvía arrastrado, y lo que escribiera para esa ventana podía llegar al campo del PIN, donde los intentos fallidos cuentan para el bloqueo de Hello. Ahora para en cuanto el prompt queda delante, o a los cinco segundos. | 1.11.2 |
| R11 | Baja a media | Sincronización | Una copia bajada de la nube se descifraba sin el límite de coste de la derivación de clave que R4 puso a las copias de conflicto, así que quien pudiera escribir en el bucket podía tener ocupado mucho tiempo el hilo que también guarda. Los dos caminos comparten ahora un mismo límite, con una prueba que falla sin él. | 1.11.3 |

### Riesgos aceptados y puntos abiertos

| # | Gravedad | Punto | Decisión |
| --- | --- | --- | --- |
| A8 | Baja a media | En el escritorio el teclado del sistema es el predeterminado, y con él la contraseña maestra vive en `String` de Compose, uno por pulsación, que no se pueden borrar y siguen en memoria hasta que el recolector la reutiliza. Lo mismo pasa con la contraseña del diálogo de las copias de conflicto. Un volcado del proceso, un volcado por fallo, el archivo de paginación o el de hibernación pueden contenerlas. El ADR-0014 decía que un volcado del proceso bloqueado solo contenía texto cifrado; eso vale para lo que guarda el desbloqueo rápido, no para estos restos, y el ADR ya lo dice. | Aceptado por ahora (las cadenas de la JVM no se pueden borrar de forma fiable, y en Android el teclado propio es el predeterminado). Pendiente en la [lista de pendientes](TODO.md): un campo de contraseña maestra borrable en el escritorio. |
| A9 | Informativo | La carpeta de instalación por usuario se puede escribir con ese usuario, así que un código que se ejecute como él podría sustituir los archivos del programa. | Igual que T23: una sesión comprometida queda fuera del alcance. |
| A10 | Baja | El MSI y el ZIP no están firmados, y su SHA-256 sale de la misma publicación, así que demuestra la descarga, no quién lo compiló. | Abierto: decisión D9 y el ADR de las actualizaciones automáticas. |
| A11 | Informativo | El trabajo `windows` de la publicación instala WiX desde Chocolatey, sin versión fija, cuando la máquina no lo trae. | Aceptado: ese trabajo no tiene secretos y los archivos se compilan desde el código de la etiqueta; fijarlo si la máquina deja de traer WiX. |
| A12 | Informativo | Mientras trae el prompt de Hello al frente, KeyNexa permite a cualquier proceso tomar el primer plano (`ASFW_ANY`) y envía una pulsación de Alt simulada. | Aceptado: solo dura hasta que el prompt queda delante (cinco segundos como mucho) y solo cambia el foco. |

### Comprobado sin hallazgos

- **Datos en reposo:** al archivo de la bóveda, a las copias privadas y a la nube solo llegan bytes KDBX cifrados; las escrituras van a un archivo temporal en la misma carpeta, forzado a disco y movido a su sitio; la carpeta de datos es `%LOCALAPPDATA%\KeyNexa`, que solo puede leer el usuario. El diario de guardado guarda huellas y referencias, nunca contenido.
- **Credenciales de la nube:** selladas con DPAPI para el usuario actual, con entropía propia de KeyNexa y un byte de versión; un blob dañado se abre como nada.
- **Portapapeles:** las marcas que dejan un secreto fuera del historial y del portapapeles en la nube se ponen antes del texto; el borrado solo vacía el portapapeles si aún tiene la copia de KeyNexa; bloquear y salir lo vacían al momento; el array copiado se borra.
- **Bloqueo:** Win+L y la suspensión llegan a la app por una ventana oculta en su propio hilo; una devolución de llamada que falla no detiene la vigilancia.
- **Red:** solo HTTPS (HTTP sin cifrar solo en las pruebas), nunca se siguen redirecciones, el analizador XML de las respuestas S3 rechaza DTD y entidades externas, y los errores de sincronización se guardan como tipos cerrados, nunca como mensajes.
- **Desbloqueo rápido:** HKDF-SHA256 sobre el HMAC de la JCA, comprobado con el RFC 5869; AES-256-GCM con la ubicación de la bóveda y la hora de activación como datos asociados; firma, clave y texto en claro borrados en todos los caminos; nada en los registros; un error de Hello, un descifrado fallido, otra bóveda, el límite de tiempo o unas credenciales cambiadas lo desactivan.
- **Registros:** la única salida es el manejador de excepciones no capturadas, que imprime la clase de la excepción y su pila, nunca su mensaje.
- **Publicación:** acciones fijadas por commit, el trabajo `windows` no tiene secretos, y el MSI se instala y se desinstala en la máquina antes de publicarse.

### Pendiente de comprobar a mano

El bloqueo con Win+L y al suspender, el desbloqueo rápido en un equipo sin Windows Hello (la opción debe quedar oculta) y un MSI instalado sobre otro anterior.

## Veredicto

No queda abierto ningún hallazgo de gravedad alta en ninguna de las dos revisiones. La 1.0 de Android salió tras la primera. Para que el escritorio deje de ser una versión preliminar: las comprobaciones a mano de arriba, la decisión D9, lo que queda de A8 y la revisión independiente que pide la hoja de ruta (otro modelo o una persona), que ninguna de las dos es.
