# Sistema de diseño

[English](../DESIGN.md) | [Español](DESIGN.md)

> **Estado:** la marca es la del responsable (2026-10-13). La dirección visual «A, clara y sobria» (construida sobre esta paleta, con su tema oscuro a juego) y la fuente Manrope las eligió el responsable el 2026-10-16; las maquetas se guardan en `design/mockups/`. Los tokens de abajo están implementados en `apps/android`; cambiar un valor es editar una línea, y la prueba de contraste mantiene la paleta accesible. **La dirección A ya cubre todas las pantallas** (desbloqueo, lista de bóvedas, grupo, entrada, editor de entrada y ajustes); las capturas de `fastlane/` siguen pendientes de rehacer en el móvil.

## Principios

1. **Confianza antes que belleza.** Un gestor de contraseñas se juzga por si parece seguro. Pantallas sencillas, tranquilas y predecibles; sin decoración que compita con los datos.
2. **Decir qué ha pasado, sin dejar al usuario adivinando.** Cada fallo explica la causa en lenguaje llano y qué hacer a continuación. El silencio ante un guardado, una copia o un error es un defecto.
3. **Los secretos están ocultos por defecto** y solo se muestran con una acción deliberada, durante poco tiempo.
4. **A una mano en el móvil.** Las acciones principales están en la mitad inferior; las zonas táctiles miden al menos 48 dp.
5. **Accesible por construcción.** Contraste WCAG AA, texto escalable, foco visible, una descripción para cada icono, nada transmitido solo por el color.

## Identidad

- **Marca:** un hexágono partido en azul marino y azul celeste, con un pequeño hexágono «nodo» separado arriba a la derecha, un anillo hexagonal interior y un escudo con cerradura en el centro. Origen: `design/icon/KeyNexa.jpg`, aportado por el responsable (hecho con ChatGPT). Está trazado a vectores en `apps/android/src/main/res/drawable/ic_launcher_foreground.xml` (dos colores, `#03265D` y `#06A6F3`, sobre blanco) e `ic_launcher_monochrome.xml` (iconos temáticos, Android 13+), y escalado para que las esquinas del hexágono toquen la zona segura de 66 dp de los iconos adaptativos.
- **Nombre:** «KeyNexa», en la fuente de la interfaz con peso medio, nunca estilizado.
- **Idea de color:** el azul marino del icono como color de marca y su azul celeste como secundario, oscurecido donde hace falta para que el texto siga siendo legible; grises azulados neutros para las superficies. El rojo se reserva para errores y acciones destructivas.
- **Paleta fija, sin color dinámico:** la app no toma sus colores del fondo de pantalla, así que se ve igual, y mantiene su contraste comprobado, en todos los dispositivos.

## Color

Los tokens están en `apps/android/.../ui/theme/Palette.kt` y se corresponden uno a uno con los roles de Material 3. Los contrastes indicados son el más bajo de cada pareja de roles, comprobados por `PaletteContrastTest` (texto de al menos 4,5:1, bordes de la interfaz de al menos 3:1).

| Rol | Claro | Oscuro | Notas |
| --- | --- | --- | --- |
| `primary` / `onPrimary` | `#0A3D91` / `#FFFFFF` (10,1:1) | `#AAC7FF` / `#002F68` (7,7:1) | Acciones principales, enlaces, anillo de foco |
| `primaryContainer` / `onPrimaryContainer` | `#D6E3FF` / `#001A43` (13,3:1) | `#0A3D91` / `#D6E3FF` (7,8:1) | Elementos seleccionados, botones tonales |
| `secondary` / `onSecondary` | `#00658F` / `#FFFFFF` (6,5:1) | `#84CFFF` / `#00344C` (7,7:1) | Acciones secundarias, enlaces en el texto |
| `secondaryContainer` / `onSecondaryContainer` | `#C7E7FF` / `#001E2E` (13,3:1) | `#004C6C` / `#C7E7FF` (7,2:1) | Confirmaciones, chips |
| `error` / `onError` | `#B3261E` / `#FFFFFF` (6,5:1) | `#F2B8B5` / `#601410` (7,7:1) | Errores, acciones destructivas |
| `errorContainer` / `onErrorContainer` | `#FBDAD7` / `#410002` (13,2:1) | `#8C1D18` / `#FBDAD7` (7,0:1) | Avisos de error |
| `background` / `onBackground` | `#F7F8FC` / `#171B26` (16,2:1) | `#0F121A` / `#E6E9F2` (15,4:1) | Fondo de pantalla |
| `surface` / `onSurface` | `#FFFFFF` / `#171B26` (17,2:1) | `#161A24` / `#E6E9F2` (14,3:1) | Tarjetas, hojas, campos |
| `surfaceVariant` / `onSurfaceVariant` | `#E6E9F2` / `#434A5C` (7,3:1) | `#2A3040` / `#BCC3D6` (7,5:1) | Texto secundario, fondos de campo |
| `outline` | `#6B7285` (4,5:1 sobre el fondo) | `#8A91A6` (5,5:1 sobre la superficie) | Bordes de campo, divisores con significado |

## Tipografía

La interfaz usa la escala tipográfica de Material 3 con **Manrope**, incluida como una sola fuente variable (`res/font/manrope.ttf`, SIL OFL 1.1, ver [Créditos](CREDITS.md)); las contraseñas y los códigos mantienen la fuente monoespaciada de la plataforma.

| Uso | Estilo | Tamaño / peso |
| --- | --- | --- |
| Título de pantalla | `headlineSmall` | 24 sp / extra negrita |
| Título de sección | `titleMedium` | 16 sp / medio |
| Cuerpo, valores de campo | `bodyLarge` | 16 sp / regular |
| Texto secundario | `bodyMedium` | 14 sp / regular |
| Botones, etiquetas | `labelLarge` | 14 sp / medio |
| Contraseñas y códigos | `bodyLarge` con familia monoespaciada | 16 sp, caracteres agrupados al mostrarse |

El texto debe escalar con el tamaño de fuente del sistema hasta el 200 % sin recortarse.

## Espaciado, forma y elevación

- **Retícula:** 4 dp. Pasos habituales: 4, 8, 12, 16, 24, 32, 48. Margen lateral de pantalla de 16 dp (24 dp en tabletas).
- **Zonas táctiles:** al menos 48 x 48 dp, con al menos 8 dp entre ellas.
- **Forma:** tarjetas de 16–18 dp con borde de 1 dp, campos de 14 dp, botones principales de 54–56 dp con esquinas de 14 dp, la banda de cabecera 24 dp en las esquinas inferiores, chips totalmente redondeados, diálogos de 28 dp.
- **Elevación:** plana. El tono y los bordes separan las capas; las sombras solo en menús y diálogos.

## Componentes

| Componente | Comportamiento |
| --- | --- |
| **Tarjeta de bóveda** | Nombre, ubicación (carpeta o proveedor) y última apertura. Tocar para abrir; pulsación larga para opciones (quitar de la lista, nunca borrar el archivo). |
| **Distintivo hexagonal** | Un pequeño hexágono con punta arriba con las iniciales de una bóveda, un grupo o una entrada. Es decorativo: el texto de la fila ya lo nombra. |
| **Marca** | El hexágono de KeyNexa (dos azules, anillo interior, escudo con cerradura y un nodo pequeño) con el nombre debajo en la pantalla de desbloqueo. Es decorativa pero lleva etiqueta para los lectores de pantalla. |
| **Banda de cabecera** | La banda superior de las pantallas de bóveda: `primary` en claro y `primaryContainer` en oscuro (las pantallas leen `LocalDarkTheme`), con el título, el estado y las acciones principales. |
| **Rejilla de grupos** | Los subgrupos de un grupo como una rejilla de dos columnas de tarjetas: un distintivo hexagonal con la inicial del grupo (sus colores se eligen a partir del nombre), el nombre en negrita y *N entradas*. |
| **Tarjeta de entradas** | Las entradas de un grupo en una sola tarjeta con separadores: un distintivo hexagonal con hasta dos iniciales, el título en negrita, el usuario, una palabra cuando la entrada ha caducado y una etiqueta **2FA** cuando guarda un código de un solo uso. |
| **Búsqueda en la banda** | Un campo dentro de la banda de cabecera que no es un campo de texto: al tocarlo se abre la pantalla de búsqueda. Muestra *Buscar en <nombre>* y se lee como un botón. |
| **Formulario de desbloqueo** | Campo de contraseña con mostrar/ocultar, selector de archivo de clave, un botón *Desbloquear* desactivado mientras trabaja y un indicador de progreso para derivaciones de clave largas. Tras una contraseña incorrecta el campo conserva el foco y el mensaje aparece debajo. |
| **Teclado de contraseña** | Dibujado por KeyNexa bajo el campo de la contraseña maestra ([ADR-0010](decisions/0010-in-app-password-keypad.md)): una página QWERTY fija con ñ, una de cifras y símbolos y otra de acentos, mayúscula con bloqueo, borrar y *Listo*. Teclas de al menos 48 dp de alto y las especiales algo más anchas; sin vista ampliada de la tecla pulsada, solo una vibración corta. El campo de encima es de solo lectura y muestra puntos; un botón de texto debajo pasa al teclado del sistema. Activado por defecto; se puede desactivar en los ajustes. Con un lector de pantalla activo se usa el teclado del sistema, porque no lee las contraseñas en voz alta. |
| **Fila de entrada** | Título, usuario e icono; el favicon o el icono de la entrada al principio. Nunca muestra la contraseña. |
| **Fila de campo** | Una etiqueta pequeña en mayúsculas encima del valor y botones de icono de 44–48 dp al final: *copiar* y *mostrar* en un secreto (cada descripción nombra el campo, nunca el valor). Un secreto aparece como puntos; al mostrarlo se vuelve a ocultar solo o al apagar la pantalla. |
| **Tarjeta de campos** | Los campos de una entrada (usuario, contraseña, código de un solo uso, URL y cualquier campo personalizado) en una sola tarjeta, radio 18 dp, con un separador fino entre filas. Las notas, los adjuntos y las fechas de la entrada van en tarjetas propias. |
| **Barra de acciones** | La barra fija inferior de la entrada: un botón *Editar* a todo el ancho y un botón cuadrado y contorneado *Eliminar* en el color de error con su propia descripción. |
| **Tarjeta de sección** | Una tarjeta (superficie, borde de 1 dp, radio de 16 dp) con un título en mayúsculas sobre filas de la misma altura separadas por una línea fina: las secciones de la pantalla de ajustes y los grupos de campos del editor de entrada. |
| **Fila de bóveda** | Una bóveda en la lista de inicio: un avatar hexagonal con su inicial, el nombre en negrita, dónde vive y la acción para quitarla de la lista. |
| **Generador de contraseñas** | Control de longitud, clases de caracteres, barra de fortaleza con etiqueta de texto, *Usar* y *Regenerar*. |
| **Barra de búsqueda** | Busca en título, usuario, URL y etiquetas solo en la bóveda abierta; no envía nada a ningún sitio. |
| **Acción principal** | Un botón flotante *Nueva entrada*, abajo a la derecha. Las acciones destructivas usan el color de error y una confirmación. |
| **Diálogo** | Título, una frase llana con las consecuencias y dos botones, el seguro a la derecha. |
| **Snackbar** | Confirmaciones breves (*Copiado. El portapapeles se borra en 30 s*) con *Deshacer* cuando la acción se puede deshacer. |

## Estados

Toda pantalla que carga o cambia datos define todos estos:

- **Cargando:** un indicador de progreso y, si tarda más de un segundo, una línea que diga qué ocurre (*Abriendo la bóveda…*). La derivación de clave en un móvil puede tardar segundos.
- **Vacío:** un mensaje sin ilustraciones que diga qué falta y ofrezca el siguiente paso (*Aún no hay entradas. Añade la primera.*).
- **Error:** la causa, el efecto sobre los datos del usuario y un botón para el siguiente paso. Los errores nunca culpan al usuario y nunca incluyen secretos.
- **Bloqueado:** la bóveda se cierra tras el tiempo de autobloqueo o cuando la app pasa a segundo plano; solo se muestra el formulario de desbloqueo, sin datos visibles en la vista previa de apps recientes.
- **Conflicto o archivo dañado:** un aviso en la parte superior de la bóveda que explique qué ha encontrado KeyNexa y qué ha hecho (consulta los mensajes de abajo).

## Mensajes de seguridad

Son las situaciones exactas que la interfaz debe nombrar. Los textos finales viven en `strings.xml` (inglés y español).

| Situación | Idea del mensaje |
| --- | --- |
| Contraseña incorrecta | «Esa contraseña no abre esta bóveda. Compruébala e inténtalo de nuevo.» |
| Archivo dañado o incompleto | «Este archivo parece dañado o aún se está copiando. KeyNexa no lo ha modificado. Se reintentará en un momento.» |
| Guardado | «Guardado.» (con la hora, en la línea de estado de la bóveda) |
| Fallo al guardar | «No se pudo guardar. Tus cambios siguen aquí y el archivo no se modificó. [Reintentar] [Guardar una copia]» |
| Cambiado desde otro dispositivo | «Esta bóveda cambió en otro dispositivo. KeyNexa fusionó los cambios y guardó una copia de seguridad.» |
| Copia de conflicto encontrada | «Se encontró una copia de conflicto de esta bóveda. [Revisar] [Ignorar]» |
| Copiar al portapapeles | «Copiado. El portapapeles se borra en 30 segundos.» |
| Contraseña débil | Una barra de fortaleza y una palabra, nunca un aviso que bloquee. |
| Autobloqueo | «Bloqueada tras 5 minutos sin uso.» |

## Temas

Claro y oscuro se eligen en Ajustes (Automático sigue al sistema; Claro u Oscuro fuerzan uno de ellos). Las dos paletas se definen y se prueban juntas. Las superficies del tema oscuro son azul negruzco, no negro puro, para que se vean los bordes.

## Lista de accesibilidad

- Contraste garantizado por `PaletteContrastTest` para cada pareja de roles de arriba.
- Todo botón de icono tiene descripción de contenido; las imágenes decorativas no.
- El orden de foco sigue el orden de lectura; todos los controles se alcanzan y se manejan con TalkBack y teclado.
- Los errores se anuncian a los lectores de pantalla y no se muestran solo con color (junto al rojo van un icono y texto).
- Las animaciones son cortas (200-300 ms) y respetan el ajuste del sistema *quitar animaciones*.

## Decisiones abiertas para el responsable

1. Paleta y fuente: decididas el 2026-10-16 (dirección A, Manrope).
