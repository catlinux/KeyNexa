# Registro de cambios

[English](../../CHANGELOG.md) | [Español](CHANGELOG.md)

Todos los cambios notables de este proyecto se documentan en este archivo. El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto sigue el [Versionado Semántico](https://semver.org/lang/es/).

## [Sin publicar]

## [1.3.2] - 2026-10-18

La app de Android no cambia.

### Añadido

- Escritorio: editar y crear entradas en el propio panel de detalle (título, usuario, contraseña con el generador y su fortaleza, URL, notas, campos propios con su protección, adjuntos que se pueden añadir, quitar o guardar en disco tras avisar de que el archivo guardado no va cifrado, y una vista de solo lectura de cada versión anterior), guardado con el guardado seguro. Atajos Ctrl+N, Ctrl+E, Ctrl+S y Esc.

## [1.3.1] - 2026-10-18

La app de Android no cambia.

### Corregido

- Escritorio, tras una prueba a mano de los tres paneles: el botón Abrir solo aparece en direcciones web (http y https), la confirmación de copia dice «Copiado: <campo>», el botón Ajustes desactivado se lee bien en la barra superior, el detalle se vacía cuando la entrada seleccionada no está entre los resultados de la búsqueda y el campo de búsqueda ya no muestra una etiqueta cortada.

## [1.3.0] - 2026-10-18

La app de Android no cambia.

### Añadido

- Escritorio: la bóveda abierta en tres paneles que aprovechan el ancho de la pantalla de un PC: un árbol compacto de grupos con la papelera aparte, una lista densa de entradas en columnas ordenables (título, usuario, web, modificada) y el detalle de la entrada a la derecha (usuario, contraseña oculta, código de un solo uso con su cuenta atrás, dirección web que se abre en el navegador solo si es http o https, campos propios, notas y nombres de los adjuntos). Búsqueda en toda la bóveda y los atajos Ctrl+F, Ctrl+B, Ctrl+C, Ctrl+U y Ctrl+L, que se muestran en las pistas emergentes y en una barra de estado. Las copias pasan por el portapapeles seguro: contraseñas, campos protegidos, códigos de un solo uso y notas quedan fuera del historial y de la nube del portapapeles de Windows y se borran a los 30 segundos, al bloquear y al salir. Editar llega en el paso siguiente.

## [1.2.1] - 2026-10-18

La app de Android no cambia.

### Añadido

- Portapapeles seguro del escritorio (la app aún no lo usa): en Windows un secreto copiado lleva los formatos que documenta Microsoft para dejarlo fuera del historial del portapapeles (Win+V) y del portapapeles en la nube, y se borra a los 30 segundos, al bloquear y al salir, solo si el portapapeles sigue teniendo lo que puso KeyNexa.

## [1.2.0] - 2026-10-18

La app de Android no cambia. Con esta versión el esqueleto del escritorio está completo (paso 4 del plan de escritorio): abre, crea, desbloquea y bloquea bóvedas; navegar y editar llegan a continuación.

### Añadido

- Escritorio: crear una bóveda nueva (nombre, contraseña maestra con confirmación y fortaleza, archivo de clave opcional) guardada donde el usuario elige con el diálogo nativo, en KDBX 4.1 con Argon2id como en Android. Nunca se sobrescribe un archivo existente, y la contraseña solo se toma una vez elegido el destino.

## [1.1.2] - 2026-10-18

La app de Android no cambia.

### Añadido

- Escritorio: desbloquear una bóveda con la contraseña maestra y/o un archivo de clave (el teclado de KeyNexa está disponible y desactivado por defecto), los mismos mensajes de error que en Android, y una primera vista de la bóveda abierta con su estado de guardado, el aviso para restaurar o descartar cambios sin guardar de un guardado interrumpido y el bloqueo con un botón o con Ctrl+L. El campo de la contraseña se vacía en cuanto la contraseña pasa a la sesión, y la pantalla borra sus copias de la contraseña y del archivo de clave al cerrarse.

## [1.1.1] - 2026-10-18

La app de Android no cambia.

### Añadido

- Primera ventana de escritorio (`apps/desktop`, Compose for Desktop, Windows): el aspecto y los textos de KeyNexa en inglés y español, una sola instancia a la vez y la pantalla de inicio con la lista de bóvedas conocidas, abrir un `.kdbx` existente con el diálogo nativo de archivos y quitar una bóveda de la lista (el archivo nunca se borra). Se arranca con `.\gradlew.bat :apps:desktop:run`; desbloquear y crear bóvedas llegan a continuación.

## [1.1.0] - 2026-10-18

La app de Android no cambia. El cliente de escritorio recibe su capa de plataforma de Windows (la app llega en el paso siguiente).

### Añadido

- Módulo nuevo `platform/windows` (`:platform:windows`): la carpeta de datos por usuario `%LOCALAPPDATA%\KeyNexa` y `DpapiSecretBox`, que sella los secretos del cliente de escritorio con DPAPI de Windows para el usuario actual ([ADR-0012](decisions/0012-windows-desktop-client.md)). Sus pruebas de DPAPI se ejecutan en Windows y se saltan en los demás sistemas.
- JNA 5.19.1 (Apache-2.0, con doble licencia LGPL-2.1-or-later) para llamar a DPAPI, registrada en las dependencias y los créditos.

## [1.0.9] - 2026-10-18

Interno; la app de Android no cambia.

### Corregido

- CI: el informe de licencias fallaba con archivos POM de las dependencias de escritorio de `ui/shared` que no tenían suma verificada; se añaden sus SHA-256 (solo altas en `gradle/verification-metadata.xml`).

## [1.0.8] - 2026-10-18

Interno; la app de Android no cambia.

### Añadido

- Módulo nuevo `platform/desktop` (`:platform:desktop`), común a todos los sistemas de escritorio: `FileStoragePort` escribe la bóveda en un temporal en la misma carpeta y la mueve a su sitio de forma atómica (escribe directamente cuando el sistema u otro programa impiden el movimiento, igualmente protegido por las copias y la relectura del guardado seguro), los almacenes privados en una carpeta de datos por usuario y la lista de bóvedas conocidas en un archivo.

### Cambiado

- La interfaz `SecretBox` pasa de `platform/cloud` a `platform/jvm`, para que la usen el almacén de secretos del escritorio y la futura clave del dispositivo.

## [1.0.7] - 2026-10-18

Interno; la app de Android publicada no cambia.

### Cambiado

- Las compilaciones de depuración son una app aparte, *KeyNexa Debug* (`io.github.catlinux.keynexa.debug`), para que se instalen junto a un KeyNexa publicado en lugar de fallar por su firma, y el móvil conserve sus bóvedas y ajustes reales mientras se prueba una compilación.
- Comprobado en un móvil: la reorganización de 1.0.2–1.0.6 desbloquea con el teclado de KeyNexa, edita y guarda una bóveda de prueba, y las pantallas de desbloqueo, inicio, grupo, entrada y ajustes son idénticas píxel a píxel a la compilación anterior en claro y oscuro (solo cambia la entrada editada).

## [1.0.6] - 2026-10-18

Interno; la app de Android debería verse y comportarse igual (pendiente de confirmar en un móvil).

### Cambiado

- Módulo nuevo de Compose Multiplatform `ui/shared` (`:ui:shared`, objetivos Android y escritorio): la paleta, el tema, la tipografía Manrope, la marca, el hexágono y el teclado de KeyNexa salen de la app de Android con sus pruebas, para que el cliente de escritorio los reutilice. Sus textos pasan al módulo (los mismos en inglés y español) y el archivo de la fuente Manrope a `ui/shared/src/commonRes/font/`, compartido por los dos objetivos.
- Se añade Compose Multiplatform 1.8.2 (JetBrains, Apache-2.0) para ese módulo. Se eligió porque mantiene la app de Android en sus versiones actuales de Jetpack Compose 1.9.1 y Material 3 1.3.2; en el APK solo añade el pequeño módulo `ui-backhandler`. Registrado en las dependencias y los créditos.

## [1.0.5] - 2026-10-18

Interno; la app de Android no cambia.

### Cambiado

- Módulo nuevo `core/session` (`:core:session`, paquete `io.github.catlinux.keynexa.session`): la sesión de la bóveda (`VaultSession` y sus estados), los ayudantes del árbol de la bóveda, el aviso de sincronización y la lista de bóvedas conocidas salen de la app de Android con sus pruebas, para que el cliente de escritorio los reutilice. La lista de bóvedas conocidas queda detrás de una interfaz `KnownVaults`; Android conserva su implementación sobre las preferencias privadas. `kotlinx-coroutines-core` 1.9.0, que ya se distribuía, pasa a ser dependencia directa del módulo nuevo.

## [1.0.4] - 2026-10-18

Interno; la app de Android no cambia.

### Cambiado

- Módulo nuevo `platform/jvm` (`:platform:jvm`): los almacenes privados de archivos `FileBackupStore`, `FileSaveJournal` y `FileMergedCopies`, código JVM puro, salen de `platform/android` con sus pruebas para que el cliente de escritorio los reutilice. Los paquetes y el comportamiento no cambian.

## [1.0.3] - 2026-10-18

Solo documentación; la app de Android no cambia.

### Cambiado

- [ADR-0011](decisions/0011-repository-layout.md): la estructura queda preparada para Linux. El código de cada sistema operativo tiene un módulo propio (`platform/windows` con el cliente de Windows; `platform/linux` reservado y sin crear hasta que Windows esté terminado) detrás de las interfaces de `platform/desktop`, y el empaquetado va en `apps/desktop/packaging/<sistema>`, de modo que añadir Linux no mueve ningún directorio.

### Añadido

- README, hoja de ruta, arquitectura y ADR-0011: macOS e iOS no están previstos, porque el proyecto no dispone de hardware de Apple para probar y un gestor de contraseñas no se puede publicar solo con emuladores.

## [1.0.2] - 2026-10-18

Interno; la app de Android no cambia.

### Cambiado

- Los módulos pasan a carpetas por papel, como decide el [ADR-0011](decisions/0011-repository-layout.md): `core/{vault,kdbx,sync}`, `platform/{android,cloud}` y `apps/android` (rutas de Gradle `:core:vault`, `:apps:android`, …). Se conserva la historia (`git mv`). Los flujos de CI, publicación y fuzzing, la documentación y los créditos siguen las rutas nuevas. Los APK sin firmar se llaman ahora `keynexa-android-<variante>-release-unsigned.apk`; los nombres de los archivos publicados no cambian.


## [1.0.1] - 2026-10-18

Solo documentación; la app de Android no cambia.

### Añadido

- Decisiones para el cliente de escritorio y la sincronización propia de KeyNexa: [ADR-0011](decisions/0011-repository-layout.md) (repositorio agrupado por funciones, una sola versión del producto), [ADR-0012](decisions/0012-windows-desktop-client.md) (cliente para Windows 10/11 con Compose Multiplatform, modos solo local / copias en la nube / sincronización entre dispositivos, DPAPI, protección de capturas y del portapapeles) y [ADR-0013](decisions/0013-device-sync.md) (sincronización entre dispositivos emparejados por TLS mutuo, sin Syncthing).
- Modelo de amenazas: amenazas T23–T27 del escritorio y de la sincronización, y sus riesgos residuales.

### Cambiado

- [ADR-0009](decisions/0009-cloud-sync.md): queda resuelto el id común de la bóveda (`KeyNexa.SyncId` en los datos personalizados de la bóveda). Arquitectura, hoja de ruta (nueva fase F) y lista de pendientes al día; se descarta incluir Syncthing como plugin.

## [1.0.0] - 2026-10-18

Primera versión estable de la app de Android, en dos compilaciones firmadas con la misma clave: `keynexa-1.0.0.apk` (offline, sin permiso de Internet) y `keynexa-cloud-1.0.0.apk` (añade las copias cifradas opcionales en la nube). Instalar una sobre la otra conserva las bóvedas y los ajustes. Desde la última versión preliminar publicada (0.10.3):

### Añadido

- Copias cifradas opcionales de la bóveda en almacenamiento compatible con S3 (verificado con Backblaze B2 y Cloudflare R2), con asistente, rotación de las copias antiguas y recordatorio, solo en la compilación `cloud` ([ADR-0008](decisions/0008-cloud-backup.md), [guía](CLOUD_SETUP.md), [privacidad](PRIVACY.md)).
- El teclado de KeyNexa para las contraseñas maestras, activado por defecto, para que el teclado del sistema nunca las vea; el teclado del sistema está a un toque y se usa automáticamente con un lector de pantalla ([ADR-0010](decisions/0010-in-app-password-keypad.md)).
- Un aspecto nuevo (dirección A, claro y oscuro, fuente Manrope) en todas las pantallas, y un ajuste de tema claro/oscuro/automático.
- Códigos de un solo uso (TOTP) en solo lectura, estado de sincronización del archivo con aviso opcional, adjuntos, borrado del historial de una entrada, fusión de copias de conflicto escritas con otra contraseña, recuperación de los cambios que un guardado fallido no pudo escribir.
- Refuerzo: toques ignorados bajo ventanas de otras apps, aviso discreto de teclados o servicios de accesibilidad de terceros, rechazo de copias de conflicto con un coste de derivación absurdo, análisis XML sin entidades externas.

### Cambiado

- Flujo de publicación: compilación y firma en trabajos separados, acciones fijadas a commits, control de permisos en los dos APK.

### Corregido

- CI: los pasos de compilación y de licencias pasaban Gradle por `tee` sin `pipefail`, así que una compilación fallida salía en verde. Encontrado al preparar la 1.0: desde la actualización del plugin de Android (0.23.5) la compilación del CI fallaba por dos sumas que solo descarga Linux (`aapt2` para Linux y los POM de `okio` y `okio-jvm` 3.15.0). Las dos están ahora en `gradle/verification-metadata.xml`, comprobadas contra el SHA-256 y el SHA-1 publicados por Google Maven y Maven Central. El aviso `OldTargetApi` de lint (objetivo 36 mientras el SDK del CI conoce la 37) es informativo hasta la migración de herramientas; `ExpiredTargetSdkVersion` sigue bloqueando.

### Seguridad

- Revisión de seguridad final documentada en [SECURITY_REVIEW](SECURITY_REVIEW.md): nueve hallazgos corregidos (0.31.0 a 0.33.11), riesgos aceptados listados, ningún hallazgo de gravedad alta abierto. Es una revisión hecha por IA, no una auditoría independiente.

## [0.33.15] - 2026-10-18

### Eliminado

- Bóvedas y carpetas de prueba borradas del móvil y del PC del titular antes de la 1.0 (las bóvedas ficticias de `testdata/` se quedan: las necesitan las pruebas); las entradas obsoletas de la lista de bóvedas del móvil ya no están, así que se quita la tarea del TODO. Solo documentación (parche).

## [0.33.14] - 2026-10-18

### Cambiado

- Las publicaciones incluyen ahora las dos compilaciones, firmadas con la misma clave: `keynexa-<versión>.apk` (offline, sin permiso de Internet) y `keynexa-cloud-<versión>.apk` (con las copias opcionales en la nube), cada una con su archivo SHA-256; el flujo falla salvo que el APK offline no tenga el permiso de Internet y el cloud sí (ADR-0008, decisión del titular). La compilación cloud de publicación se comprobó en un móvil (desbloqueo y una copia a la nube). README y DEVELOPMENT al día. Parche: herramientas de publicación y documentación.

## [0.33.13] - 2026-10-18

### Cambiado

- El diálogo de «otra contraseña» de las copias de conflicto se comprobó en el móvil, así que están hechas todas las comprobaciones en el móvil de la revisión de seguridad salvo las dos que aplazó el titular. La hoja de ruta marca las fases D y E como hechas y la 1.0 de Android como lista, pendiente de su etiqueta. Solo documentación (parche).

## [0.33.12] - 2026-10-18

### Cambiado

- Revisión de seguridad: resultados de las comprobaciones en el móvil (matar la app durante un guardado, una variante sobre la otra, borrar historial, lector de pantalla) y las que siguen pendientes. Solo documentación (parche).

## [0.33.11] - 2026-10-18

### Corregido

- En Android cualquier bóveda se leía como dañada desde la 0.33.7: el refuerzo del XML desactivaba también la expansión de entidades, lo que allí rompe las entidades predefinidas (`&amp;` y similares) que contiene toda bóveda. Ese ajuste se quita; las entidades externas siguen rechazadas. Encontrado en el móvil, no por las pruebas en la JVM. Parche.

## [0.33.10] - 2026-10-18

### Añadido

- `docs/es/SECURITY_REVIEW.md` (y su original en inglés): la revisión final antes de la 1.0, con alcance, método, los nueve hallazgos corregidos de la 0.31.0 a la 0.33.9, los riesgos aceptados y las comprobaciones pendientes en un móvil. `THREAT_MODEL` al día en T11, T16, T17 y T20. Solo documentación (parche).

## [0.33.9] - 2026-10-18

### Corregido

- CI: el fallo intermitente del trabajo diferencial con keepass-rs no era una diferencia de interoperabilidad, sino la verificación de dependencias de Gradle: ese trabajo resuelve a veces el POM de `kotlinx-coroutines-bom` 1.8.0, que no tenía suma. Su SHA-256, que coincide con el SHA-256 y el SHA-1 publicados por Maven Central, está ahora en `gradle/verification-metadata.xml`. Parche: solo compilación.

## [0.33.8] - 2026-10-18

### Corregido

- La pantalla de entrada mostraba *Abrir URL* para cualquier dirección, pero solo se abren las http y https: con `ssh://` y similares el botón no hacía nada. Ahora solo aparece en direcciones web (la dirección siempre se puede copiar), y un móvil sin navegador ya no se cierra al pulsarlo. Encontrado en la auditoría final. Parche.

## [0.33.7] - 2026-10-18

### Seguridad

- El analizador XML incluido con kotpass resolvía entidades externas en la JVM (XXE): un archivo de clave XML hostil, que es cualquier archivo que elige el usuario, podía meter un archivo local en la clave o conectarse a la red. Ahora rechaza las entidades externas y las DTD en todas las plataformas (el analizador de Android no las cargaba, pero los clientes de escritorio funcionarán en la JVM). Encontrado en la auditoría final, con una prueba que fallaba antes del arreglo. Parche.

### Cambiado

- Al README en español del kotpass incluido le faltaba la entrada de las credenciales a partir de componentes ya cifrados; añadida.

## [0.33.6] - 2026-10-18

### Seguridad

- Flujo de publicación: compilar y firmar son ahora trabajos separados, así que nada de lo que ejecuta la compilación (Gradle, sus plugins, las dependencias, acciones de terceros) comparte máquina con la clave de firma; todas las acciones están fijadas a un commit; y la publicación falla si el APK offline pide el permiso de Internet, un control que el plan daba por hecho y el flujo no tenía. Encontrado en la auditoría final. Parche: solo herramientas de publicación (aún sin ejecutar; la próxima etiqueta lo comprueba).

## [0.33.5] - 2026-10-18

### Seguridad

- Una copia de conflicto de Syncthing cuya cabecera sin cifrar pide más de 16 veces el trabajo de derivación de clave de la bóveda abierta ya no se descifra (se indica como dañada), así que una copia hostil o rota no puede tener ocupado durante horas, en cada apertura, el hilo que también guarda la bóveda. Los límites absolutos del motor siguen aplicándose a todo. Encontrado en la auditoría final. Parche.

## [0.33.4] - 2026-10-18

### Corregido

- Las copias en la nube de dos bóvedas con el mismo nombre compartían carpeta y número de «copias a conservar», así que las subidas de una podían borrar por rotación todas las copias de la otra. Cada bóveda tiene ahora su carpeta, `KeyNexa/<nombre>-<8 caracteres de su id>/`. Las copias anteriores se quedan donde están y la app nunca las rota ni las borra (bórralas a mano cuando ya no las quieras). Encontrado en la auditoría final. Parche.

## [0.33.3] - 2026-10-18

### Seguridad

- Mientras haya un lector de pantalla activo, las contraseñas maestras se escriben con el teclado del sistema y no con el de KeyNexa: el teclado propio leería cada tecla en voz alta, y el del sistema sigue el ajuste de Android que evita leer las contraseñas (adenda del ADR-0010). Parche.

## [0.33.2] - 2026-10-18

### Cambiado

- CI: cuando falla la prueba diferencial con keepass-rs, el mensaje de la prueba se publica como anotación legible sin iniciar sesión, para diagnosticar un fallo intermitente que solo se ve en el CI. Parche: solo CI.

## [0.33.1] - 2026-10-18

### Corregido

- Con texto grande, los botones de altura fija cortaban su etiqueta (por ejemplo *Desbloquear con biometría* mostraba solo *Desbloquear con*): los botones de desbloqueo, inicio, entrada y editor crecen ahora con la letra. Parche.

## [0.33.0] - 2026-10-18

### Añadido

- El teclado de KeyNexa escribe también la contraseña maestra nueva y su confirmación al crear una bóveda (*Listo* pasa a la confirmación), y la contraseña de una copia de conflicto escrita con otra contraseña (ADR-0010).

### Cambiado

- Las tres entradas de contraseña maestra comparten un componente: lo escrito se borra al salir de la pantalla o del diálogo y al pasar la app a segundo plano, la confirmación se compara sin hacer copias en texto, y la contraseña de una copia de conflicto llega a la sesión como un array y no como un `String`.

Menor: comportamiento nuevo y compatible.

## [0.32.0] - 2026-10-18

### Añadido

- El teclado de KeyNexa en la pantalla de desbloqueo (ADR-0010): la contraseña maestra se escribe con teclas dibujadas por la app, así que el teclado del sistema nunca la ve. Activado por defecto; un botón bajo el campo pasa al teclado del sistema en ese intento, y un ajuste lo desactiva. Sin burbuja en la tecla pulsada, solo una vibración corta; cada tecla tiene una etiqueta hablada.

### Seguridad

- En la pantalla de desbloqueo la contraseña escrita se borra al enviarla, al salir de la pantalla y al pasar la app a segundo plano; con el teclado propio nunca se guarda como `String`.

Menor: función nueva y compatible.

## [0.31.3] - 2026-10-18

### Añadido

- Base del teclado de KeyNexa (ADR-0010, paso E1): un búfer de contraseña que se puede borrar y nunca pasa a `String`, y la distribución fija del teclado (letras, cifras y símbolos, acentos), con pruebas. Aún no es visible (parche: interno).

## [0.31.2] - 2026-10-18

### Cambiado

- ADR-0010 aceptado: el teclado de KeyNexa está activado por defecto, se usa solo en los campos de contraseña maestra y con distribución fija. Solo documentación (parche).

## [0.31.1] - 2026-10-18

### Añadido

- ADR-0010 (propuesta): un teclado opcional dibujado por KeyNexa para la contraseña maestra, para que el teclado del sistema no la vea nunca (amenaza T22). Solo documentación (parche).

## [0.31.0] - 2026-10-18

### Añadido

- La pantalla de desbloqueo indica en voz baja, con un texto gris pequeño, cuando el teclado en uso o un servicio de accesibilidad activo viene de una app instalada y no del sistema (amenaza T22). No nombra ninguna app ni bloquea nada.

### Corregido

- Los cambios sin guardar ya no quedan perdidos tras un guardado que falla limpiamente y después se cierra la app: al abrir de nuevo se ofrece la copia privada (Restaurar / Descartar) si todavía aporta algo, y se borra sin avisar si no aporta nada. Restaurar la fusiona y solo la borra una vez guardada.
- La copia privada de un guardado fallido se borra cuando un guardado posterior sale bien, en lugar de quedarse para siempre en el almacenamiento de la app.

Menor: comportamiento nuevo y compatible.

## [0.30.6] - 2026-10-18

### Cambiado

- La pantalla de crear bóveda explica para qué sirve el archivo de clave opcional y avisa de que perderlo supone perder la bóveda. Parche: solo texto.

## [0.30.5] - 2026-10-18

### Seguridad

- Se ignoran los toques mientras otra app dibuja una ventana encima de KeyNexa (`filterTouchesWhenObscured`), contra el tapjacking y las superposiciones falsas (amenaza T22). Parche: refuerzo sin cambios en el formato ni en el uso normal.

## [0.30.4] - 2026-10-18

### Corregido

- La pantalla de desbloqueo mostraba el archivo de la bóveda como una ruta codificada como URL (`primary%3Asyncthing-proves%2F…`); ahora muestra el nombre de archivo normal. Parche: solo visualización.

### Cambiado

- Redacción en español: «cubo» (bucket) pasa a «contenedor» en el asistente de la nube y en la guía de la nube, el aviso de privacidad y el ADR-0008 en español.

## [0.30.3] - 2026-10-17

### Añadido

- `docs/es/VAULT_CONVENTIONS.md`: consejos de nombre y ubicación para una bóveda real y los nombres de campos personalizados (`key_id`, `secret_key`, `token`, `endpoint`…) para credenciales de API, que coincidirán con el futuro tipo de entrada «API / token». Solo documentación (parche).

## [0.30.2] - 2026-10-16

### Cambiado

- Modelo de amenazas: nueva amenaza T22 (keylogger o malware que captura la contraseña maestra) con sus mitigaciones y una nota de riesgo residual; el refuerzo previsto está en el TODO.

## [0.30.1] - 2026-10-16

### Cambiado

- La guía de configuración de la nube cubre ahora Cloudflare R2 paso a paso, verificada con una cuenta real (prueba de conexión y copia subida).

## [0.30.0] - 2026-10-16

### Cambiado

- Alineación con la dirección visual A elegida de las pantallas que faltaban: el inicio (una cabecera con la marca y la acción de ajustes, las bóvedas conocidas en una tarjeta de filas con avatar hexagonal y dónde vive cada una, la etiqueta de sección «Tus bóvedas», un estado vacío con la marca y los botones «Abrir una bóveda existente» y «Crear una bóveda nueva» en la zona del pulgar), los ajustes (una banda de cabecera, una tarjeta por sección con título en mayúsculas y filas de la misma altura separadas por una línea, y la fila de copias en la nube encajada en su propia tarjeta en la compilación cloud) y el editor de entrada (los campos en tarjetas con esquinas de 14 dp y una barra inferior fija con «Cancelar» y «Guardar»). El comportamiento no cambia.

## [0.29.0] - 2026-10-16

### Cambiado

- Rediseño de la pantalla de entrada con la dirección visual A elegida: una barra superior compacta con la vuelta y la ruta del grupo, una cabecera con el avatar hexagonal de la entrada, el título y la línea de última modificación, una tarjeta para los campos (una etiqueta pequeña en mayúsculas encima de cada valor, con botones de icono de copiar/mostrar/abrir de 44–48 dp y el código de un solo uso en una cifra monoespaciada grande con su barra de segundos), las notas, los adjuntos y las fechas en tarjetas propias, y una barra inferior fija con un botón «Editar» a todo el ancho y un botón cuadrado y contorneado «Eliminar». El comportamiento no cambia: los secretos siguen ocultos hasta mostrarlos y ningún secreto llega a una descripción, un registro ni un aviso.

## [0.28.0] - 2026-10-16

### Cambiado

- Rediseño de la pantalla de grupo con la dirección visual A elegida: una banda de cabecera redondeada (el nombre de la bóveda o del grupo, una línea de guardado/guardando/sin guardar, el botón de bloquear y el menú), un campo de búsqueda dentro de la banda que abre la pantalla de búsqueda, los subgrupos como una rejilla de dos columnas de tarjetas con un distintivo hexagonal y el número de entradas, las entradas en una sola tarjeta con separadores, avatar hexagonal, la palabra de caducada y una etiqueta «2FA» cuando la entrada tiene un código de un solo uso, y un botón flotante «Nueva entrada». El comportamiento no cambia.

## [0.27.0] - 2026-10-16

### Cambiado

- Rediseño de la pantalla de desbloqueo con la dirección visual A elegida: la marca de KeyNexa y su nombre arriba, y en la mitad inferior la tarjeta de la bóveda (con su avatar hexagonal, su nombre y la acción «Cambiar»), el campo de contraseña maestra con mostrar/ocultar, el selector de archivo de clave, el botón «Desbloquear» y el botón secundario «Usar huella». El comportamiento no cambia. Nuevas piezas compartidas: `HexagonShape`/`HexBadge`, `BrandMark` y `LocalDarkTheme`.

## [0.26.0] - 2026-10-16

### Añadido

- Dirección visual «A, clara y sobria», elegida por el responsable, con su versión oscura; las maquetas están en `design/mockups/`.
- La fuente Manrope (SIL OFL 1.1) como fuente de la interfaz, incluida como una sola fuente variable, con su licencia en `third_party/manrope/` y en los créditos.

## [0.25.2] - 2026-10-16

### Añadido

- Prueba diferencial contra keepass-rs (ADR-0005): una pequeña herramienta en Rust en `tools/kdbx-dump-rs` (`keepass = "=0.15.2"`) vuelca una bóveda a JSON canónico, y `KeepassRsDifferentialTest` compara, para cada bóveda del corpus, lo que ve keepass-rs en el archivo original y en el archivo reescrito por KeyNexa, y el propio modelo de KeyNexa. La prueba se omite sin `KEEPASS_RS_DUMP` y se ejecuta en un trabajo nuevo `differential` de la CI con Rust 1.99.0; una lista de diferencias conocidas falla si aparece una diferencia no listada o si una listada deja de ocurrir (está vacía: las dos bibliotecas coinciden).

## [0.25.1] - 2026-10-16

### Cambiado

- Buscar cambios externos es más barato: `VaultReader` compara el tamaño y la fecha de modificación del archivo (anotados justo antes de leer los bytes) y solo lee y calcula la huella del archivo entero cuando difieren, ante cualquier duda o en una comprobación forzada como mínimo una vez por minuto. El flujo de guardado seguro sigue comprobando la huella completa justo antes de escribir, así que nunca se pierde un cambio.

## [0.25.0] - 2026-10-16

### Añadido

- La pantalla de detalle de una entrada muestra ahora el código de un solo uso (TOTP) cuando la entrada tiene una configuración válida: una fila «Código de un solo uso», oculta por defecto y revelada con el Mostrar/Ocultar habitual, un Copiar sensible y una barra de progreso con los segundos que le quedan (en el color de error cuando quedan cinco segundos o menos). El código se refresca cada segundo sin recomponer la pantalla entera, y nunca se escribe en una descripción, un log ni un aviso.

## [0.24.4] - 2026-10-16

### Añadido

- El núcleo de TOTP en `core-vault` (todavía sin interfaz): `Entry.totp()` lee el campo `otp` de KeePassXC (una URI `otpauth://totp/...`) o los campos `TOTP Seed`/`TOTP Settings` de KeeTrayTOTP, y `TotpConfig.codeAt(instant)` deriva el código de 6-8 dígitos según el RFC 6238 (SHA1/SHA256/SHA512, HMAC de `javax.crypto` y truncado dinámico del RFC 4226, con un decodificador Base32 mínimo del RFC 4648). Las configuraciones inválidas devuelven null y nunca lanzan, y no se imprime nunca ningún secreto ni código.

## [0.24.3] - 2026-10-16

### Cambiado

- TODO: las actualizaciones automáticas quedan anotadas como idea en evaluación.

## [0.24.2] - 2026-10-16

### Añadido

- Fuzzing guiado por cobertura del decodificador KDBX con la CLI de Jazzer (0.30.0), sin añadir ninguna dependencia de Gradle: un punto de entrada `DecoderFuzzTarget` y una prueba JUnit que lo ejecuta con las bóvedas rápidas del corpus dentro de `check`, una tarea Gradle `writeFuzzClasspath` y un workflow de CI `fuzz` semanal y manual que comprueba la suma SHA-256 de la descarga de Jazzer antes de ejecutarla. La licencia de Jazzer queda registrada en los documentos de dependencias, créditos y desarrollo.

## [0.24.1] - 2026-10-16

### Corregido

- Abrir o guardar una bóveda cuya derivación de clave Argon2 pide más memoria de la que el dispositivo puede dar ya no cae con un `OutOfMemoryError`: el motor KDBX lo convierte en `VaultException.NotEnoughMemory`, y la pantalla de desbloqueo lo explica y sugiere reducir la memoria de Argon2 de la bóveda desde otro programa.

## [0.24.0] - 2026-10-16

### Añadido

- Un ajuste de tema (Ajustes → Aspecto) con tres opciones: Automático (el del sistema, por defecto), Claro y Oscuro. La elección se guarda con el resto de ajustes, se aplica al momento (se recrea la actividad, sin bloquear la bóveda) y se mantiene tras reiniciar; un valor guardado desconocido vuelve a Automático.

## [0.23.6] - 2026-10-16

### Cambiado

- El asistente de S3 rellena el campo de la región mientras se escribe el endpoint: al cambiar el endpoint, la región muestra lo que se puede deducir de él (Backblaze, Amazon, Cloudflare R2) salvo que la haya escrito el usuario, en cuyo caso nunca se pisa. La decisión vive en la función pura `regionAfterEndpointChange` de `platform-cloud`.

## [0.23.5] - 2026-10-15

### Cambiado

- Herramientas de compilación: Gradle 8.14.6 y plugin de Android para Gradle 8.13.2 (actualizaciones de parche dentro de las líneas actuales), con los metadatos de verificación de dependencias regenerados y revisados.

## [0.23.4] - 2026-10-15

### Añadido

- ADR-0009 (propuesta): sincronización a través de la nube, primero local, con escrituras condicionales y una prueba por servicio antes de ofrecerla.

## [0.23.3] - 2026-10-15

### Añadido

- Sección de Amazon S3 en la guía de configuración de la nube (todavía no probada con una cuenta real).

## [0.23.2] - 2026-10-15

### Cambiado

- La pantalla del grupo raíz muestra ahora como título el nombre de la bóveda (el de la lista de bóvedas), en vez del nombre del grupo raíz del archivo («Root» por defecto en KeePassXC). Los subgrupos siguen mostrando su propio nombre, y nada dentro del KDBX cambia.

## [0.23.1] - 2026-10-15

### Cambiado

- TODO: la petición a IzzyOnDroid fue rechazada por no seguir su plantilla; aparcada.

## [0.23.0] - 2026-10-15

### Añadido

- El asistente de S3 deduce la región a partir del endpoint con Backblaze B2, Amazon S3 y Cloudflare R2 (`regionFromEndpoint` en `platform-cloud`), así que el campo de la región es opcional con ellos y solo es un error cuando no se puede deducir. El texto de ayuda lo indica.
- Tras «Copiar ahora», la pantalla de estado muestra un mensaje breve (copia subida o el error) y lo mantiene hasta salir de la pantalla.

### Cambiado

- «Desconectar y borrar» borra también el estado de las copias (`FileBackupState.clearAll()` y `CloudBackupService.reset()`), así que una conexión nueva no muestra la «última copia» anterior.
- La guía de configuración de la nube explica que la región se rellena sola con Backblaze, Amazon y Cloudflare, y el TODO ya no lista estos puntos terminados.

## [0.22.1] - 2026-10-15

### Cambiado

- Guía de configuración de la nube verificada con una cuenta real de Backblaze B2: alta, cubo, nombres del endpoint, clave, primera copia y el límite de la carpeta Descargas.

## [0.22.0] - 2026-10-15

### Añadido

- Un elemento «Ajustes» en el menú de una bóveda abierta. Los Ajustes solo se abrían desde la lista de bóvedas y al volver atrás desde una bóveda esta se bloquea, así que «Copiar ahora» (variante cloud) no podía activarse nunca.

## [0.21.4] - 2026-10-15

### Cambiado

- Variante cloud: cuando se abren los Ajustes sin ninguna bóveda abierta, el botón «Copiar ahora» desactivado explica ahora por qué y qué hacer.

## [0.21.3] - 2026-10-15

### Corregido

- Variante cloud: «Guardar y activar» cerraba la app en un móvil real, porque el Keystore de Android rechaza un vector de inicialización elegido por quien llama. Ahora lo genera el propio cifrador.

## [0.21.2] - 2026-10-15

### Corregido

- Variante cloud: «Probar conexión» decía siempre que no podía conectar en un móvil real, porque el lector de XML de Android rechaza las opciones de endurecimiento de Apache con las que se leían las respuestas de S3. Ahora se aplican una a una y una respuesta con DOCTYPE o entidades se rechaza antes de leerla.

## [0.21.1] - 2026-10-15

### Cambiado

- Pulido del asistente de copias en la nube (solo variante cloud): el resultado de «Probar conexión» aparece ahora justo encima del botón, de modo que se ve sin desplazarse; la fila «Copias en la nube» de Ajustes parece una fila pulsable, con una flecha al final y una segunda línea con el estado actual; la descripción de S3 ya no dice «sin tener cuenta con KeyNexa» y en su lugar indica que KeyNexa no tiene cuentas ni servidores propios; y la pantalla de consentimiento agrupa sus frases bajo los subtítulos en negrita «Qué se envía», «A dónde», «Para qué» y «Cómo dejar de usarlo».

## [0.21.0] - 2026-10-15

### Añadido

- El asistente de copias en la nube (camino S3) en la variante `cloud` (ADR-0008 §4): una pantalla paso a paso con el consentimiento, la elección del servicio, lo que necesita cada proveedor (con botones que abren el navegador), el formulario de endpoint/región/cubo/claves (la clave secreta como campo de contraseña, nunca en el estado guardado), una prueba de conexión con mensajes claros por caso, el guardado de la configuración y una pantalla de estado con la última copia, el último error, el número de copias (5/10/20), «Copiar ahora», «Cambiar de servicio» y «Desconectar y borrar». En la pantalla de inicio aparece un recordatorio discreto, nunca modal, mientras no haya servicio configurado ni copia, que se puede aplazar una semana. Nueva [guía de configuración de las copias en la nube](CLOUD_SETUP.md) bilingüe.

### Cambiado

- `CloudSettingsEntry` recibe ahora la bóveda abierta (`CloudBackupSource`) para que «Copiar ahora» pueda subirla; la variante `offline` mantiene huecos vacíos con la misma firma.

## [0.20.0] - 2026-10-15

### Añadido

- El motor de las copias en la nube en la variante `cloud` (ADR-0008), todavía sin pantalla de configuración: `S3Setup` (configuración guardable con `toString` redactado), `FileBackupState` (último éxito y último error por bóveda, con clave = SHA-256 de su ubicación, atómico y tolerante con un archivo corrupto), `testConnection` (lista, sube un objeto de prueba y lo borra) y `CloudBackupRunner` en `platform-cloud`; un gancho `VaultSession.afterSave` que se ejecuta una vez tras cada guardado correcto y nunca deja que el gancho rompa el guardado; y `CloudBackupService` en el sabor cloud, una cola de un solo hilo que sube el archivo cifrado tras un guardado (`backupIfDue`), se puede forzar, coalesce por bóveda y expone un estado `StateFlow`. La variante `offline` mantiene huecos vacíos con la misma firma.

## [0.19.0] - 2026-10-15

### Añadido

- Un almacén cifrado de las credenciales de la nube y la pantalla de consentimiento, solo en la variante `cloud` (ADR-0008): `SecretBox`/`AesGcmSecretBox` y `CloudCredentialStore` en `platform-cloud` (AES-256-GCM, escrituras atómicas, nada legible en disco), un `KeystoreSecretBox` cuya clave no exportable vive en el Android Keystore, una marca de consentimiento en `noBackupFilesDir` y una fila «Copias en la nube» en Ajustes con el flujo de consentimiento y una acción «Desconectar y borrar». La variante `offline` mantiene una entrada vacía y no lleva código de nube. Nueva [declaración de privacidad](PRIVACY.md) y amenazas de nube en el [modelo de amenazas](THREAT_MODEL.md).

## [0.18.0] - 2026-10-15

### Añadido

- Un cliente de nube compatible con S3 en el nuevo módulo `platform-cloud`, todavía sin usar desde la aplicación (ADR-0008): `HttpTransport` (con un `UrlConnectionTransport` sobre `HttpsURLConnection` del JDK), `S3Signer` (AWS Signature Version 4 por la cabecera `Authorization`, puro y determinista, comprobado con los cuatro vectores oficiales de AWS) y `S3Backend` (put, get, delete y `ListObjectsV2` paginado sobre un endpoint configurable, path-style o virtual-hosted). No añade ninguna dependencia, nunca registra ni incluye en los mensajes URLs, cabeceras, claves ni cuerpos, y solo permite HTTPS fuera de las pruebas.

## [0.17.0] - 2026-10-15

### Añadido

- La base interna de las copias en la nube (ADR-0008 §3), todavía sin código de red ni interfaz de usuario: `CloudBackend` en `core-sync` (put, get, list y delete de copias cifradas opacas, con la capacidad `conditionalWrites` y una `CloudAuthException` para los fallos de credenciales) y `BackupUploader`, que nombra la copia `<prefijo><bóveda>/<bóveda>-<UTC>.kdbx`, sube como mucho una vez al día (`backupNow` ignora el límite), rota solo las copias que él mismo creó hasta el número elegido e informa de un fallo como valor de resultado, de modo que nunca puede impedir un guardado.

## [0.16.0] - 2026-10-15

### Añadido

- Dos sabores de compilación, `offline` y `cloud`, en la dimensión `distribution`. `offline` es el predeterminado y se comporta como antes: se comprueba que su manifiesto fusionado no lleva el permiso `INTERNET`, aunque lo añada una biblioteca. `cloud` declara `INTERNET` (para las copias en la nube que aún están por llegar) y muestra «KeyNexa (cloud)» como nombre de la aplicación; por ahora no cambia nada más. Ambos comparten `applicationId`, `versionCode`, `versionName` y firma.

### Cambiado

- Las rutas de los APK ahora llevan el sabor: `app-android/build/outputs/apk/<sabor>/<tipo>/app-android-<sabor>-<tipo>.apk`. El workflow de publicación compila y comprueba ambos sabores, pero solo publica el `offline`.
- El bloqueo de dependencias cubre las configuraciones de runtime de los sabores (`offlineReleaseRuntimeClasspath`, `cloudReleaseRuntimeClasspath`), por lo que se regeneraron los lockfiles.

## [0.15.0] - 2026-10-14

### Añadido

- Borrar las versiones anteriores de una entrada con `VaultEditor.clearHistory`, que vacía el historial de la entrada (sin crear versión nueva ni cambiar su fecha de modificación) y recolecta los binarios de adjuntos que solo referenciaba el historial. La pantalla de detalle ofrece «Borrar versiones anteriores» junto a «Versiones anteriores: N» con un diálogo de confirmación; al quitar un adjunto, su contenido queda en el historial hasta usar esta acción.

## [0.14.0] - 2026-10-14

### Añadido

- Añadir, quitar y guardar en un archivo los adjuntos de una entrada: `VaultEditor.addAttachment`/`removeAttachment` se ejecutan mediante `editEntry` (así se guarda versión), reemplazando el adjunto de mismo nombre, recolectando los binarios que ya no referencia nadie en el árbol ni su historial y rechazando adjuntos de más de 10 MB o con nombre en blanco. La pantalla de detalle muestra nombre y tamaño legible de cada adjunto con la acción «Guardar en…» que escribe el binario en el archivo elegido (con el aviso claro de que el archivo guardado no está cifrado), y la pantalla de edición permite añadir archivos (`*/*`) y quitar adjuntos, aplicándolos junto con el resto de los campos al guardar.

## [0.13.1] - 2026-10-14

### Corregido

- La app ya no se cierra cuando el archivo de la bóveda desaparece o deja de ser accesible con la bóveda abierta: la fecha de modificación del archivo (0.12.0) se leía en el hilo de la pantalla y su fallo no se capturaba; ahora se lee en segundo plano.
- La lista «¿Qué bóveda?» ya no ofrece las copias de conflicto de Syncthing como si fueran bóvedas.

## [0.13.0] - 2026-10-14

### Añadido

- Reconectar la carpeta sin cerrar la bóveda cuando un guardado falla por haber perdido el permiso, reintentando el guardado pendiente por el flujo normal (no escribe si el archivo elegido es otra bóveda).
- Restaurar los cambios sin guardar de una sesión anterior interrumpida tras un reinicio: el aviso ofrece fusionar la copia privada con la bóveda abierta o descartarla, borrando la copia solo cuando el guardado tiene éxito.

## [0.12.0] - 2026-10-14

### Añadido

- Estado de sincronización del archivo de la bóveda (modificación del archivo, último guardado de KeyNexa, último cambio recibido de otro dispositivo) y aviso opcional tras varios días sin cambios. KeyNexa no sabe qué ha subido Syncthing; el aviso es una heurística.

### Cambiado

- `StoragePort` gana `lastModified(location)`.

## [0.11.0] - 2026-10-14

### Añadido

- Una copia de conflicto de Syncthing escrita con otra contraseña ya no solo se informa: el aviso ofrece escribir esa contraseña y la copia se fusiona en la bóveda cuando el guardado va bien. La contraseña se usa una vez y se borra, también cuando no abre la copia; una contraseña rechazada deja la copia como estaba y el aviso lo indica.

## [0.10.3] - 2026-10-14

### Corregido

- La bóveda se bloquea tras el tiempo elegido en segundo plano aunque la app siga allí; al volver, se cancela el temporizador y se comprueba el tiempo transcurrido.
- Antes de borrar un secreto copiado, el portapapeles comprueba la marca de KeyNexa y conserva el contenido que haya sustituido otra app.
- La protección de pantalla se aplica antes del primer fotograma según el ajuste guardado de capturas.

### Cambiado

- La documentación ya describe la versión preliminar v0.10.2 publicada, las mitigaciones implementadas y las dependencias de ejecución del APK.
- Añadidos metadatos de tienda y capturas de pantalla (bóveda ficticia) en `fastlane/metadata/android/` (inglés y español) para IzzyOnDroid y F-Droid; el APK ya no incluye el bloque de información de dependencias.
- La compilación exige que los componentes menor y parche de la versión sean inferiores a 100 para que `versionCode` siga aumentando.

## [0.10.2] - 2026-10-14

### Corregido

- El análisis informativo de las herramientas de compilación ya no pinta una ✗ roja en cada commit: `continue-on-error` pasa del job a su paso, de modo que el job termina en verde y los avisos siguen visibles en el log. Sigue sin bloquear nada.

## [0.10.1] - 2026-10-14

### Corregido

- «Abrir una bóveda existente» cerraba la app con `IllegalArgumentException: Can only use lower 16 bits for requestCode`: `androidx.biometric` 1.1.0 arrastraba `androidx.fragment` 1.2.5, que rechaza los códigos de petición de la API actual de Activity Result. Ahora `androidx.fragment` 1.8.5 (Apache-2.0) es una dependencia directa. Fallo introducido en 0.9.0.

### Nota

- La fusión de copias de conflicto (0.10.0) se ha comprobado en un móvil real con Syncthing: se fusionaron las dos ediciones, llegaron al PC, la app ofreció borrar la copia y desapareció de la carpeta de Windows tras aceptar.

## [0.10.0] - 2026-10-14

### Añadido

- Las copias de conflicto de Syncthing se fusionan en la bóveda automáticamente (fase D7, ADR-0007): al abrir una bóveda y al cargar una versión escrita en otro sitio, las copias `*.sync-conflict-*` que hay junto al archivo de la bóveda se descifran con las credenciales de la bóveda abierta y se pliegan con la semántica de fusión, y el resultado pasa por el canal de guardado seguro. Un registro de huellas por bóveda en almacenamiento privado (excluido de las copias de seguridad) evita descifrar dos veces la misma copia, y una copia solo se registra cuando el guardado que la fusionó se completa. La pantalla de la bóveda dice entonces cuántas copias se han fusionado y ofrece borrarlas, y solo se borran las copias cuya huella grabada sigue coincidiendo: nada se borra sin consentimiento, y si se rechaza las copias se conservan y la oferta vuelve en la siguiente apertura. Una copia escrita con otra contraseña, dañada o aún en transferencia, de otra bóveda o ilegible se informa con su motivo y se deja intacta.

### Cambiado

- `StoragePort` incorpora `name(location)` (el nombre real del archivo en el almacenamiento) y `delete(location)`; la implementación de Android usa el nombre visible del documento y `DocumentsContract.deleteDocument`.

### Nota

- Aún no comprobado en un móvil real: la fusión al abrir, la oferta de borrar las copias y el caso de una copia escrita con otra contraseña necesitan la prueba en dispositivo con Syncthing (D7.5).

## [0.9.0] - 2026-10-14

### Añadido

- Desbloqueo biométrico (fase D6, ADR-0006). Tras un desbloqueo con contraseña, la app ofrece activarlo para esa bóveda (activar, ahora no o no volver a preguntar). Guarda, cifrados con una clave del Keystore de Android por bóveda que exige una biometría fuerte en cada uso (StrongBox si existe, invalidada al registrar nuevos datos biométricos o quitar el bloqueo de pantalla), el hash de la contraseña y del archivo de clave, nunca la contraseña; la derivación de clave se sigue ejecutando en cada desbloqueo. La contraseña se pide de nuevo tras 7 días sin escribirla (de 1 a 30 días en los ajustes) y siempre que la clave se invalide o los datos guardados ya no abran la bóveda, con un mensaje que explica el motivo. Un botón en la pantalla de desbloqueo deja de usarlo para esa bóveda.
- Kotpass incorpora `Credentials.fromHashes` (cambio local anotado en su README) y el motor `KdbxEngine.hashedForm`, de modo que una bóveda se abre desde los componentes ya hasheados. Probado con todo el corpus de pruebas, incluidas las bóvedas con archivo de clave.

### Cambiado

- `MainActivity` extiende `FragmentActivity` (lo necesita `BiometricPrompt`) y la app depende de `androidx.biometric` 1.1.0 (Apache-2.0).

### Nota

- Aún no comprobado en un móvil real: las pruebas unitarias cubren las reglas de almacenamiento y el formato de los componentes, pero el Keystore y el diálogo necesitan la matriz de pruebas en dispositivo (en el TODO).

## [0.8.1] - 2026-10-14

### Añadido

- Pantalla de ajustes (engranaje en el inicio): tiempo de bloqueo automático, bloquear al apagar la pantalla, permitir capturas de pantalla y cuánto tiempo queda un secreto copiado en el portapapeles. Comprobado en un Pixel 10 Pro: la bóveda se bloquea tras el tiempo elegido en segundo plano, la ventana no se puede capturar con las capturas desactivadas, un cambio externo aparece en segundos, y ni la contraseña ni ningún texto de entradas aparecen en el registro del sistema ni en los archivos de la app (AC6).

## [0.8.0] - 2026-10-14

### Añadido

- Protecciones (fase D5): la bóveda abierta se bloquea sola tras un tiempo en segundo plano (60 segundos por defecto; opciones desde «inmediatamente» hasta «nunca»), al apagarse la pantalla y a mano; al bloquear se borran las credenciales, se descarta la bóveda de la memoria, se limpia el portapapeles y se vuelve a la pantalla de inicio. La ventana se marca como segura (sin capturas de pantalla y en blanco en la lista de apps recientes) salvo que el ajuste permita las capturas. El tiempo que un secreto copiado queda en el portapapeles es un ajuste (30 segundos por defecto). Los ajustes se guardan en privado y por ahora solo se cambian desde el código: la pantalla de ajustes llega a continuación.
- Los cambios que otro programa (Syncthing, KeePassXC) haga en el archivo de la bóveda se detectan en unos 5 segundos mientras la app está a la vista, y se cargan solos cuando la bóveda abierta no tiene ediciones sin guardar; si las tiene, se fusionan en el siguiente guardado.

### Cambiado

- Un formulario de edición abierto conserva sus campos cuando la bóveda se recarga desde el disco, y se cierra si su entrada se eliminó en otro sitio.

## [0.7.5] - 2026-10-14

### Cambiado

- kotpass vendorizado: `readHeader` comprueba ahora la firma KDBX antes de analizar la cabecera, de modo que lo que no es KDBX se rechaza como tal.
- TODO: se registran las ideas en evaluación (Syncthing integrado, configurar un dispositivo nuevo con una frase semilla, copias de seguridad en la nube, mantener kotpass fuerte). Hay cuatro correcciones de kotpass, con pruebas, preparadas como ramas del fork del proyecto para el mantenedor original.

## [0.7.4] - 2026-10-14

### Cambiado

- El README y la hoja de ruta describen ahora el estado real del proyecto (qué funciona en Android, qué falta, cómo probar una compilación de depuración con Syncthing).

## [0.7.3] - 2026-10-14

### Corregido

- El guardado fallaba después de que Syncthing sustituyera el archivo de la bóveda: Android revoca el permiso sobre un archivo suelto en ese caso (`SecurityException`, comprobado en un Pixel 10 Pro). Las bóvedas se añaden y crean ahora con un permiso de carpeta (`ACTION_OPEN_DOCUMENT_TREE`), que sobrevive a la sustitución, como preveía el ADR-0004. La pantalla de inicio pide la carpeta que contiene la bóveda, lista los archivos `.kdbx` que hay en ella y crea las bóvedas nuevas dentro.
- La fusión de una bóveda editada en otro sitio mientras está abierta se ha comprobado en un dispositivo real: una edición del móvil y otra hecha con KeePassXC entre medias acaban las dos en el archivo (criterio de aceptación AC4).

## [0.7.2] - 2026-10-14

### Corregido

- El formulario de entrada nueva ya no se abre con el error «una entrada necesita un título» a la vista; solo aparece después de escribir el título y vaciarlo.

## [0.7.1] - 2026-10-13

### Corregido

- El contenido ya no queda oculto bajo la barra de estado, la de navegación ni el teclado (el menú con la papelera quedaba fuera de alcance).
- El portapapeles se borra al volver KeyNexa al primer plano pasados 30 segundos y al bloquear la bóveda: Android no deja que una app en segundo plano modifique el portapapeles, así que un temporizador no bastaba.

## [0.7.0] - 2026-10-13

### Añadido

- Pantallas de contenido (fase D4.4): explorar grupos y entradas, búsqueda (sin distinguir mayúsculas ni acentos, nunca en contraseñas), detalle de entrada (contraseña oculta y visible 30 segundos como máximo, copia con marca de portapapeles sensible que se borra a los 30 segundos, abrir direcciones web), crear y editar entradas y campos personalizados, generador de contraseñas integrado (`PasswordGenerator`, aleatorio seguro), papelera con restaurar, borrado definitivo y vaciado, y una barra de estado de guardado que explica cada fallo y ofrece reintentar. Cada cambio se guarda al instante con el guardado seguro.
- Pantallas de acceso (fase D4.3): inicio con las bóvedas conocidas (añadir, crear, quitar de la lista sin tocar el archivo), formulario de bóveda nueva (nombre, contraseña maestra dos veces, archivo de clave opcional, aviso de contraseña irrecuperable) y pantalla de desbloqueo (mensajes claros para cada fallo, progreso mientras se deriva la clave, opción de reconectar el archivo), conectadas en `MainActivity` con los selectores de archivo del sistema y permisos de lectura y escritura duraderos.
- Base de la app (fase D4.2): `AppContainer` que conecta almacenamiento, motor, guardado seguro y almacenes privados; `VaultSession` (desbloquear, crear, editar con guardado seguro automático, reintentar, bloquear borrando las credenciales y recuperación tras un corte al abrir); la lista de bóvedas conocidas; y la navegación por estado.
- Casos de uso de edición en `core-vault` (fase D4.1): `VaultEditor` (alta, edición con historial, mover, borrar a la papelera, restaurar, vaciarla y borrado definitivo con registro de objetos eliminados), `VaultSearch` (sin distinguir mayúsculas ni acentos, nunca lee contraseñas ni valores protegidos, deja fuera la papelera) y `NewVault` (bóveda KDBX 4.1 vacía con ChaCha20 y Argon2id).
- ADR-0006: diseño del desbloqueo biométrico (fase D6): componentes de la clave compuesta envueltos por una clave del Android Keystore por bóveda, ligada a biometría fuerte en cada uso, invalidada al cambiar la biometría y con petición periódica de la contraseña maestra.

## [0.6.1] - 2026-10-13

### Añadido

- Almacenamiento Android (fase D3) en `platform-android`: `SafStoragePort` (Storage Access Framework; escribe con `"rwt"`, recurre a `"rw"` y siempre trunca de forma explícita, convierte cualquier fallo del proveedor o permiso revocado en `IOException`), `SafVaultAccess` (concesiones persistentes de carpeta y archivo, localizar la bóveda por nombre dentro de una carpeta concedida, `isReachable` para el flujo «reconectar archivo»), y `FileBackupStore` y `FileSaveJournal` (escritura atómica en `noBackupFilesDir`; un diario ilegible es un error, nunca «nada pendiente»).
- `conflictCopiesOf` en `core-sync`: reconoce las copias `*.sync-conflict-*` de Syncthing junto a la bóveda, las más nuevas primero, para que la interfaz las ofrezca para fusionar.

### Eliminado

- El `PlatformModule` de relleno.

## [0.6.0] - 2026-10-13

### Añadido

- Fusión (fase D2): `KeePassMerger` en `core-sync`, una fusión a dos bandas con la semántica de sincronización de KeePass escrita a partir de su comportamiento documentado: emparejamiento por UUID, gana la modificación más reciente y la otra versión queda en el historial, historiales unidos y recortados a los límites de la bóveda, ubicación según el cambio de ubicación más reciente, borrados solo mediante registros de objetos eliminados y nunca sobre cambios posteriores, metadata campo a campo. Los bucles por movimientos cruzados y los registros de borrado del grupo raíz se resuelven sin perder datos. `SafeSaver` la usa para incorporar los cambios externos antes de escribir.
- 44 pruebas de fusión: escenarios, invariantes comprobadas sobre pares de bóvedas generados al azar (no se pierde ningún objeto, sin bucles, sin adjuntos colgantes, resultados simétricos) y guardados completos a través de `SafeSaver` (AC4, parte unitaria).

## [0.5.0] - 2026-10-13

### Añadido

- Canal de guardado seguro y detección de cambios en `core-sync` (fase D1): `SafeSaver` solo escribe tras copiar el archivo anterior y los bytes nuevos al almacenamiento privado y anotar el guardado en un diario, y solo informa de éxito tras volver a leer el archivo con la huella SHA-256 esperada; un archivo roto por su propia escritura se restaura byte a byte, y uno sustituido por otro programa se deja como está. `SaveRecovery` termina o deshace un guardado interrumpido por un cierre inesperado, sin necesitar la contraseña. `VaultReader` reintenta los archivos que pueden estar aún en transferencia. Los cambios externos o no fusionables nunca sobrescriben el archivo: la versión del usuario se conserva como copia privada (AC5).
- Los puertos `BackupStore` y `SaveJournal`, una operación `write` en `StoragePort` y la interfaz `VaultMerger` que implementa la fase D2.
- Pruebas de inyección de fallos (53) con almacenamiento falso: escrituras cortadas, escrituras que no truncan, proveedores que mienten, cambios externos antes, durante y después de la escritura, y cierres inesperados en cada punto (AC3 y AC5).

### Cambiado

- detekt admite hasta cuatro `return` (sin contar las cláusulas de guarda) y trata como gestionadas las excepciones de E/S y de bóveda que se traducen a propósito.

## [0.4.1] - 2026-10-13

### Cambiado

- El icono de la app es ahora la marca del responsable (hexágono, escudo y cerradura; original en `design/icon/`), trazada a vectores y con variante monocroma para iconos temáticos. Los colores primario y secundario siguen el azul marino y el celeste del icono, manteniendo WCAG AA o mejor.

## [0.4.0] - 2026-10-13

### Añadido

- Propuesta del sistema de diseño (fase C6): `docs/DESIGN.md` con principios, identidad, una paleta de colores accesible para los temas claro y oscuro, tipografía, espaciado, componentes, estados y los mensajes de seguridad. La identidad visual espera la aprobación del responsable.
- La paleta y el tema Material 3 en `app-android`, con una prueba unitaria que rompe la compilación si algún par de texto baja de WCAG AA (4,5:1) o algún borde de la interfaz de 3:1.
- Un icono de lanzador adaptativo original (una llave con una red de tres nodos) con una variante monocroma para iconos temáticos.

### Cambiado

- Se elimina la excepción de lint de «falta el icono del lanzador», ahora que la app tiene icono.

## [0.3.1] - 2026-10-13

### Añadido

- Fuzzing por mutación del decodificador (fase C5b, primera parte): copias dañadas al azar de las bóvedas del corpus solo pueden fallar con una `VaultException`, dentro de un límite de tiempo. Nueve mil mutaciones se ejecutaron sin fallos.
- La CI instala KeePassXC para que la prueba diferencial se ejecute allí.

### Seguridad

- El motor rechaza ajustes de derivación de clave fuera de límites razonables (memoria de Argon2 por encima de 2 GiB, más de 10000 iteraciones o 256 carriles, más de 500 millones de rondas de AES-KDF) antes de hacer ningún trabajo: los parámetros están en la cabecera sin cifrar, así que un archivo dañado o malicioso podría bloquear o hacer caer la app.

## [0.3.0] - 2026-10-13

### Añadido

- Motor KDBX (fase C5). `core-vault` define el modelo de dominio (bóveda, grupos, entradas, historial, adjuntos, datos personalizados, XML desconocido), las interfaces `KdbxEngine` y `StoragePort`, `Credentials` (secretos que se pueden borrar) y `VaultException`. `core-kdbx` implementa el motor sobre kotpass: `KotpassKdbxEngine`, con un mapeo completo en los dos sentidos para que cargar y guardar no pierda datos, y con los fallos traducidos a `VaultException` sin secretos en los mensajes.
- kotpass 0.13.0 (commit upstream `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945`) importado como módulo Gradle `:kotpass` en `third_party/kotpass/`, con Okio 3.15.0 como única dependencia; créditos de kotpass, Okio y los avisos que incluye.
- Pruebas de aceptación AC1 y AC2 sobre el corpus de 18 bóvedas: apertura, contenido esperado, errores, ida y vuelta para cada cifrado y derivación de clave, y comparación diferencial con `keepassxc-cli`.
- `docs/BENCHMARKS.md`: tiempos de Argon2 de la implementación en Kotlin puro en una JVM de escritorio, y la medición pendiente en móvil.

### Cambiado

- Correcciones locales en el kotpass incluido, listadas en su README: los elementos XML desconocidos se conservan y se reescriben; un icono personalizado o dato personalizado sin fecha de modificación ya no recibe una falsa; un archivo KDBX 4 truncado o manipulado se informa como dañado en lugar de como clave incorrecta; la comprobación de arranque del flujo de KDBX 3.x ahora sí lanza el error (fallo de upstream); un lector público de la cabecera.
- ktlint y detekt comparten un límite de 120 caracteres por línea.

## [0.2.9] - 2026-10-13

### Corregido

- La verificación de dependencias cubre ahora también las variantes de Linux y macOS de `aapt2`, que necesita el ejecutor Linux de la CI; el metadata solo tenía la de Windows.

### Añadido

- `scripts/ci-gradle-error.sh`: la CI publica el bloque «What went wrong» de una ejecución fallida de Gradle como anotación, legible sin iniciar sesión.

## [0.2.8] - 2026-10-13

### Añadido

- Tres bóvedas de prueba más guardadas desde la interfaz de KeePassXC: Twofish con Argon2d, ChaCha20 con Argon2id y Argon2d con una etiqueta y un campo TOTP. El corpus de `testdata/` queda completo (18 bóvedas).

## [0.2.7] - 2026-10-13

### Corregido

- Los workflows de CI y de publicación localizan `sdkmanager` dentro de `ANDROID_HOME` en lugar de depender del `PATH`, donde el ejecutor no lo ofrece (código de salida 127).

## [0.2.6] - 2026-10-13

### Añadido

- Corpus de bóvedas de prueba ficticias en `testdata/` (15 bóvedas y 3 archivos de clave) escritas por KeePass 2.61 y KeePassXC 2.7.12: AES-256 y ChaCha20, AES-KDF, Argon2d y Argon2id, KDBX 3.1, 4.0 y 4.1, archivos de clave, contraseña Unicode, historial, adjuntos, iconos, etiquetas y una bóveda Argon2 de 1 GiB.
- Scripts generadores e inspector de cabeceras en `tools/testdata/`, documentados en `docs/TEST_VAULTS.md` junto con las tres bóvedas que aún requieren la interfaz gráfica de KeePassXC.

## [0.2.5] - 2026-10-13

### Cambiado

- El análisis de vulnerabilidades de la CI pasa a cubrir solo las dependencias que se distribuyen con la aplicación, mediante el bloqueo de dependencias de Gradle (los archivos `gradle.lockfile` de las configuraciones de runtime). Las vulnerabilidades de las herramientas de build las informa un job nuevo que no bloquea.

## [0.2.4] - 2026-10-13

### Añadido

- `docs/TEST_VAULTS.md`: lista de bóvedas ficticias que hay que crear con KeePassXC y KeePass 2.x para el corpus de compatibilidad.

### Corregido

- El paso de licencias del SDK de Android en los workflows de CI y de publicación ya no falla con SIGPIPE bajo `pipefail`.

## [0.2.3] - 2026-10-13

### Añadido

- Flujo de publicación (`.github/workflows/release.yml`): con una etiqueta `vX.Y.Z` comprueba la etiqueta contra `VERSION` y `CHANGELOG.md`, compila el APK release, lo firma con los secretos del repositorio y publica una release de GitHub (preliminar antes de la 1.0) con el APK y su archivo SHA-256.
- Documentación de cómo crear el keystore de firma y los cuatro secretos del repositorio.

## [0.2.2] - 2026-10-13

### Añadido

- Integración continua con GitHub Actions (`.github/workflows/ci.yml`): compilación y comprobaciones de calidad, comprobación de la documentación bilingüe, informe de licencias de dependencias y análisis de vulnerabilidades con OSV.
- `scripts/check-docs-i18n.sh`, que falla si un documento en inglés no tiene su traducción al castellano o al revés, o si falta la línea de idiomas.
- El plugin Gradle License Report, conectado a `generateLicenseReport` para todos los subproyectos.

## [0.2.1] - 2026-10-13

### Añadido

- Comprobaciones de calidad integradas en `./gradlew check`: ktlint (con `ktlintFormat` para autocorregir), detekt y Android Lint endurecido en ambos módulos Android, donde `HardcodedText`, `MissingTranslation`, `ExtraTranslation` y las comprobaciones de seguridad son errores.
- Verificación de dependencias de Gradle: todas las dependencias, plugins y herramientas se comprueban con las sumas SHA-256 de `gradle/verification-metadata.xml`.

## [0.2.0] - 2026-10-13

### Añadido

- Esqueleto Gradle multimódulo (fase C1): los módulos Kotlin/JVM 17 `core-vault`, `core-kdbx` y `core-sync`, la librería Android `platform-android` y la aplicación Compose + Material 3 `app-android`, con catálogo de versiones y una prueba JUnit trivial por módulo.
- `app-android` toma su `versionName` de `VERSION` y deriva de él el `versionCode`; el manifest desactiva las copias de seguridad y no declara ningún permiso, y los textos de la interfaz se ofrecen en `en-US` y castellano solo mediante recursos de cadena.
- Instrucciones de compilación, pruebas y empaquetado verificadas en `docs/es/DEVELOPMENT.md`.

## [0.1.2] - 2026-10-13

### Añadido

- ADR-0005: se mantiene kotpass, integrado como módulo y reforzado (pruebas diferenciales contra KeePassXC y keepass-rs, fuzzing, correcciones para conservar el XML, Argon2 nativo opcional), tras reevaluar kotpass, KeePassJava2, keepass-rs y kdbxweb.

## [0.1.1] - 2026-10-13

### Cambiado

- Las traducciones al español pasan a `docs/es/`, con la misma estructura que la versión inglesa; solo `README.es.md` se queda en la raíz. Actualizados los enlaces de idioma y las reglas bilingües de CONTRIBUTING.

## [0.1.0] - 2026-10-13

### Añadido

- Fase de investigación y arquitectura (fases A y B): arquitectura, modelo de amenazas, investigación de dependencias y licencias, hoja de ruta con criterios de aceptación del MVP, plan de desarrollo y registros de decisiones de arquitectura 0001–0004.
- Licencia: GPL-3.0-only con términos adicionales de atribución y marca (sección 7) en `NOTICE`; `CREDITS.md`.
- Documentación del proyecto en inglés y español: README, CONTRIBUTING, SECURITY, CHANGELOG, TODO.
- Archivo `VERSION`.
