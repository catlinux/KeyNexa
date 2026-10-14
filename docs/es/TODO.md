# Pendientes

[English](../../TODO.md) | [Español](TODO.md)

Pasos detallados: consulta la [hoja de ruta](ROADMAP.md).

## Prioridad alta (fase C — base del proyecto)

- [ ] Primera publicación firmada de prueba: subir una etiqueta (con aprobación del responsable) y comprobar en GitHub el workflow, la firma y la suma de verificación.
- [ ] Prueba diferencial de las bóvedas escritas por KeyNexa contra keepass-rs, y fuzzing con Jazzer (ADR-0005; el fuzzing por mutación y la comparación con keepassxc-cli ya se ejecutan).
- [ ] Medir Argon2 de kotpass en un móvil real cuando la app abra una bóveda; decidir sobre argon2kt (consulta `docs/es/BENCHMARKS.md`).
- [ ] Aprobar el resto de la identidad propuesta en `docs/DESIGN.md` (paleta, fuente, interruptor manual claro/oscuro); la marca ya está decidida.

- [ ] Aportar las correcciones de kotpass al proyecto original: hay cuatro parches preparados en `.agents/plans/kotpass-upstream/`; publicarlos necesita la aprobación del mantenedor y pruebas dentro de kotpass.
- [ ] Primera versión preliminar en GitHub cuando estén hechas las fases D5, D6 y la revisión (E1); el flujo de publicación nunca se ha ejecutado.

## Prioridad media (fase D — MVP seguro)

- [ ] **Fusionar automáticamente las copias `*.sync-conflict-*`** (prioridad: hace falta siempre que dos dispositivos editan sin estar conectados entre sí). Al abrir una bóveda, fusionar cada copia de conflicto en el archivo principal con el fusionador existente (las entradas tienen UUID aleatorios, así que las altas independientes nunca chocan), guardar y preguntar antes de borrar las copias; recordar lo ya fusionado para no repetirlo. Nada se borra solo.
- [ ] Estado de sincronización en la app: cuándo se sincronizó la bóveda por última vez, cambios aún sin subir y un aviso tras varios días sin sincronizar.
- [ ] La nube como punto de encuentro siempre disponible: permitir que una bóveda viva en WebDAV o almacenamiento compatible con S3 mediante un `StoragePort` remoto con escrituras condicionales (ETag / If-Match), de modo que el mismo flujo leer-fusionar-escribir sirva para dispositivos que nunca están encendidos a la vez. Necesita un ADR.
- [ ] Mirar los cambios externos con menos coste: comparar primero tamaño y fecha de modificación y leer el archivo entero solo si difieren; mirar con menos frecuencia tras minutos sin actividad. Medir el consumo de batería en un móvil real antes y después.
- [ ] Cuando un guardado falle por haber perdido el permiso, ofrecer reconectar la carpeta sin cerrar la bóveda; ofrecer restaurar la copia privada sin guardar tras un reinicio.
- [ ] Borrado del portapapeles totalmente fiable (Android solo deja cambiar el portapapeles a la app que está delante).
- [ ] Desbloqueo biométrico.

## Prioridad baja (más adelante)

- [ ] Clientes de Windows y Linux, cuando la app de Android sea funcional (reutilizan los módulos `core-*`).
- [ ] Servicio de autorrelleno y proveedor de credenciales (passkeys).
- [ ] TOTP.
- [ ] Clientes de escritorio (Windows, Linux).

## En evaluación (decisión pendiente)

- Sincronización propia que no dependa de Syncthing (por ejemplo, un cliente de escritorio que haga de enlace), dejando Syncthing como opción.
- Integrar del todo en el proyecto las primitivas criptográficas (ya están vendorizadas con kotpass) en lugar de depender de binarios externos. No se prevé escribir algoritmos de cifrado nuevos: solo implementaciones auditadas, con versión fija y probadas contra los vectores oficiales.
- Syncthing como plugin opcional en todas las plataformas, nunca obligatorio: un solo dispositivo no necesita sincronización (la bóveda es local; las copias de seguridad en la nube son una opción aparte y recomendada). Motivo: cuando la sincronización es una app aparte es fácil olvidarse de arrancarla y entonces no se sincroniza nada; con el plugin, abrir KeyNexa la arranca, muestra el estado («sincronizado hace unos segundos») y evita errores de configuración como compartir la carpeta equivocada. Se ofrece al añadir un segundo dispositivo, emparejado con un código QR. Modos: integrado (recomendado) / usar un Syncthing externo / ninguno. En Android necesita un servicio en primer plano que siga vivo hasta terminar la subida pendiente al salir de la app. Llevar Syncthing (MPL-2.0, compatible con GPL-3.0) gestionado por su API REST (solo local, con clave aleatoria). Necesita un ADR (tamaño por arquitectura, reglas de procesos en segundo plano, política de actualizaciones, convivencia con Syncthing-Fork). Paso intermedio barato que hay que comprobar: pedir a un Syncthing-Fork instalado que arranque cuando se abre KeyNexa.
- Configurar un dispositivo nuevo con una frase semilla: un paquete cifrado con los ajustes y el emparejamiento de la sincronización (nunca la contraseña maestra), protegido con una clave derivada de la frase, guardado en la carpeta sincronizada o mostrado como código QR. Necesita un ADR.
- Copias de seguridad en la nube: primero a cualquier carpeta que el sistema exponga (Drive, Dropbox, Mega y otros a través de sus propias apps, sin SDK extra), después WebDAV y almacenamiento compatible con S3; sin SDK propietarios con telemetría. Necesita un ADR.
- Mantener kotpass fuerte: ofrecerse a ayudar a mantenerlo en el proyecto original, añadir fuzzing con Jazzer y la comparación con keepass-rs (ya listados arriba) y reevaluar las alternativas cada año (ADR-0005).

## Descartado

- Núcleo en Rust con UniFFI: la escritura de KDBX en keepass-rs es experimental y UniFFI añade una cadena de herramientas NDK; se reevaluará antes de 1.0 ([ARCHITECTURE](ARCHITECTURE.md#estrategia-multiplataforma)).
- Permiso `MANAGE_EXTERNAL_STORAGE`: no es necesario con SAF y no está permitido en Google Play para este uso.
- Depender de la API REST de Syncthing: requiere una clave de API y es posible que Syncthing no esté instalado.
