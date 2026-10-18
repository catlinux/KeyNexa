# Revisión de seguridad

[English](../SECURITY_REVIEW.md) | [Español](SECURITY_REVIEW.md)

Revisión final antes de la 1.0 (paso B3 de la hoja de ruta y la parte de código del paso Z). Hecha el 2026-10-18 sobre la versión 0.33.3; los arreglos que provocó están en la 0.33.4 a la 0.33.11.

## Alcance y método

- **Código:** todos los módulos del repositorio: `core-vault`, `core-kdbx`, `core-sync`, `platform-android`, `platform-cloud`, `app-android` (fuentes `main`, `offline` y `cloud`), el `third_party/kotpass` incluido (solo donde toca entradas no fiables y los cambios locales), y los flujos de CI y de publicación. Unas 15.000 líneas de código de KeyNexa y 10.000 de código incluido.
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

## Comprobaciones pendientes en un móvil

Pendientes porque se cortó la conexión adb, o porque necesitan condiciones que la revisión no pudo crear. Todas están cubiertas por pruebas unitarias.

- Matar la app en mitad de un guardado y volver a abrir (recuperación del guardado interrumpido).
- El teclado propio en el diálogo de «otra contraseña» de una copia de conflicto.
- Instalar una variante sobre la otra.
- Que un lector de pantalla pase los campos de contraseña maestra al teclado del sistema.
- Desbloqueo biométrico en un móvil sin StrongBox y con API 26–29; Argon2 en un móvil modesto.
- Borrar el historial de una entrada.

## Veredicto

No queda abierto ningún hallazgo de gravedad alta. Antes de la 1.0: hacer las comprobaciones en el móvil de arriba, subir la primera etiqueta para comprobar el nuevo flujo de publicación y conseguir la revisión independiente que pide la hoja de ruta.
