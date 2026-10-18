# ADR-0010: Teclado propio de la app para la contraseña maestra

[English](../../decisions/0010-in-app-password-keypad.md) | [Español](0010-in-app-password-keypad.md)

- **Estado:** Propuesta
- **Fecha:** 2026-10-18
- **Trata:** la amenaza T22 del [THREAT_MODEL](../THREAT_MODEL.md) (un keylogger o malware captura la contraseña maestra)

## Contexto

Hoy la contraseña maestra se escribe con el teclado del sistema. El teclado es otra app: ve cada tecla, puede aprender palabras, puede sincronizar con una cuenta en la nube, y un teclado malicioso o comprometido es un keylogger por definición. KeyNexa ya marca el campo como contraseña (sin sugerencias ni aprendizaje en los teclados que se portan bien), bloquea las capturas por defecto, ignora los toques bajo superposiciones e indica en voz baja cuándo un teclado o un servicio de accesibilidad no viene con el sistema (0.31.0). Nada de eso ayuda si el propio teclado registra lo que se escribe.

Un teclado dibujado por KeyNexa dentro de su propia ventana saca al teclado del camino: los caracteres nunca salen de la app. No lo para todo: un servicio de accesibilidad todavía puede leer lo que la pantalla expone, y un sistema comprometido puede leerlo todo (fuera del alcance, ver T22).

Restricciones: la contraseña maestra puede contener cualquier carácter (acentos, símbolos, otros alfabetos); la app debe seguir siendo usable con TalkBack y con texto grande; la contraseña no debe guardarse como un `String` inmutable más tiempo del necesario; nada puede registrarse.

## Opciones

1. **Mantener solo el teclado del sistema.** Sin trabajo, pero T22 sigue del todo abierta para los teclados.
2. **Un teclado propio, opcional, junto al teclado del sistema.** El usuario elige en cada dispositivo; el teclado del sistema sigue disponible para los caracteres que falten y para quien lo prefiera.
3. **Solo el teclado propio.** Lo más fuerte, pero una contraseña con un carácter que el teclado no tenga no se podría volver a escribir nunca, y hay usuarios que dependen de su teclado (accesibilidad, otros alfabetos).
4. **Un método de entrada (IME) completo de KeyNexa.** Serviría también a otras apps, pero necesita un servicio de todo el sistema, más permisos y mucha más superficie de ataque: lo contrario de lo que se busca.

## Decisión

Opción 2.

1. **Qué es:** un teclado dibujado con Compose dentro de la pantalla de desbloqueo. No es un método de entrada: no se registra en el sistema, no necesita permisos y ninguna otra app puede usarlo ni verlo.
2. **Cuándo se usa:** un ajuste «Escribir la contraseña maestra con el teclado de KeyNexa» (desactivado por defecto, para que nada cambie a quien ya usa la app) y un botón en la pantalla de desbloqueo para pasar del teclado propio al del sistema en ese intento. Cuando aparece el aviso discreto de un teclado o servicio de accesibilidad de otra app, gana un enlace «Usar el teclado de KeyNexa». El ajuste se aplica primero a la pantalla de desbloqueo; la de crear bóveda y el diálogo de «otra contraseña» de las copias de conflicto vienen en pasos posteriores.
3. **Distribución:** una página de letras QWERTY con mayúscula y bloqueo de mayúsculas; una página de cifras y símbolos con todos los caracteres ASCII imprimibles; una página de acentos con las letras latinas habituales (á é í ó ú à è ì ò ù ä ë ï ö ü â ê î ô û ñ ç, también en mayúscula). Los caracteres fuera de estas páginas se escriben con el teclado del sistema (el botón para cambiar sigue visible). La distribución es fija, no se baraja: barajar empeora el uso y solo ayuda contra la grabación de pantalla, que `FLAG_SECURE` ya bloquea por defecto.
4. **Sin eco visual de la tecla:** las teclas pulsadas no muestran la burbuja ampliada (enseñaría cada carácter a quien mire y a la grabación de pantalla si se permiten las capturas); la respuesta es una vibración corta y el estado pulsado de la tecla.
5. **Memoria:** los caracteres escritos viven en un búfer `CharArray` del formulario de desbloqueo (`PasswordBuffer`: añadir, borrar el último, borrar todo, copiar). Se borra al enviar la contraseña, al salir de la pantalla, al bloquearse la app o pasar a segundo plano, y al crecer (el array viejo se borra tras copiarlo). El campo muestra solo puntos; «mostrar la contraseña» pinta los caracteres solo mientras está activo, y ese texto pasajero se acepta igual que en el campo actual.
6. **Accesibilidad:** cada tecla es un botón real con una etiqueta hablada («a», «A mayúscula», «arroba»), de al menos 44 dp de alto y con separación, y el teclado sigue la escala de letra. Por tanto un lector de pantalla anuncia cada tecla: es el precio de la accesibilidad y la razón de que exista el aviso discreto. Las teclas nunca se ocultan a los servicios de accesibilidad.
7. **Nada más cambia:** los archivos de clave, el desbloqueo biométrico, el canal de guardado seguro y el formato de la bóveda no se tocan. El código vive solo en `app-android` (`ui/unlock/` y un `PasswordBuffer` y un modelo de distribución que se prueban en la JVM); sin dependencias nuevas.

## Consecuencias

- Un teclado malicioso o sincronizado con la nube deja de ver la contraseña maestra cuando se usa el teclado propio. Los servicios de accesibilidad y un sistema comprometido todavía pueden (riesgo residual documentado en T22).
- La contraseña deja de guardarse como `String` mientras se escribe con el teclado propio, lo que además mejora el campo actual.
- Escribir es más lento que con un teclado conocido; por eso es opcional y el cambio está a un toque.
- Las contraseñas con caracteres fuera de las páginas necesitan el teclado del sistema; la app nunca lo impide.
- Más código de interfaz que mantener y que probar en accesibilidad (TalkBack, texto grande, pantallas pequeñas).

## Plan de implementación

1. `PasswordBuffer` y el modelo de distribución con pruebas JVM (se borra por todos los caminos, al crecer se borra el array viejo, todos los caracteres ASCII imprimibles son alcanzables).
2. El teclado en Compose y su uso en la pantalla de desbloqueo: ajuste (desactivado por defecto), botón de cambio, enlace desde el aviso discreto, borrado al salir, al bloquear y en segundo plano. Textos en inglés y español.
3. Prueba en el dispositivo con TalkBack y texto grande; después la pantalla de crear bóveda y el diálogo de contraseña de las copias de conflicto.
4. Documentación en los dos idiomas: DESIGN (componente de teclado), THREAT_MODEL (T7 y T22), lista de funciones del README, TODO y CHANGELOG.
