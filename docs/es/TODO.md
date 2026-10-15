# Pendientes

[English](../../TODO.md) | [Español](TODO.md)

Pasos detallados: consulta la [hoja de ruta](ROADMAP.md).

## Prioridad alta (fase C — base del proyecto)

- [ ] Desbloqueo biométrico: la matriz del ADR-0006 está comprobada en un Pixel 10 Pro (activar, desbloquear, una huella nueva lo invalida, contraseña exigida tras los días elegidos, contraseña cambiada en otro dispositivo). Sigue abierto: móviles sin StrongBox y API 26-29.

- [ ] Prueba diferencial de las bóvedas escritas por KeyNexa contra keepass-rs, y fuzzing con Jazzer (ADR-0005; el fuzzing por mutación y la comparación con keepassxc-cli ya se ejecutan).
- [ ] Medir Argon2 de kotpass en un móvil real cuando la app abra una bóveda; decidir sobre argon2kt (consulta `docs/es/BENCHMARKS.md`).
- [ ] Aprobar el resto de la identidad propuesta en `docs/DESIGN.md` (paleta, fuente, interruptor manual claro/oscuro); la marca ya está decidida.

- [ ] Aportar las correcciones de kotpass al proyecto original: hay cuatro parches preparados en `.agents/plans/kotpass-upstream/`; publicarlos necesita la aprobación del mantenedor y pruebas dentro de kotpass.

## Prioridad media (fase D — MVP seguro)

- [ ] La nube como punto de encuentro siempre disponible: permitir que una bóveda viva en WebDAV o almacenamiento compatible con S3 mediante un `StoragePort` remoto con escrituras condicionales (ETag / If-Match), de modo que el mismo flujo leer-fusionar-escribir sirva para dispositivos que nunca están encendidos a la vez. Necesita un ADR.
- [ ] Mirar los cambios externos con menos coste: comparar primero tamaño y fecha de modificación y leer el archivo entero solo si difieren; mirar con menos frecuencia tras minutos sin actividad. Medir el consumo de batería en un móvil real antes y después.
- [ ] Borrado del portapapeles totalmente fiable (Android solo deja cambiar el portapapeles a la app que está delante).
- [ ] Pedir la inclusión en F-Droid e IzzyOnDroid (E2.4; aparcado el 2026-10-15: la primera petición a IzzyOnDroid en GitLab fue rechazada por no seguir la plantilla de la petición, así que hay que rehacerla a mano con la plantilla): los metadatos en borrador y los pasos están en `.agents/plans/store-inclusion/`; las peticiones se hacen a mano (el formulario de Izzy pregunta por el uso de IA y exige respuestas sinceras).

## Seguimiento y comprobaciones abiertas (2026-10-15)

- [ ] **Probar en el móvil lo que solo cubren las pruebas:** el diálogo de estado de sincronización y el aviso de «el archivo no cambia» (0.12.0), restaurar los cambios sin guardar tras matar la app en pleno guardado (0.13.0), el bloqueo en segundo plano (0.10.3), borrar el historial de una entrada (0.15.0) y el asistente de la nube con una cuenta S3 real (H6).
- [ ] **Copias en la nube** ([ADR-0008](decisions/0008-cloud-backup.md), aceptada el 2026-10-15): el flujo S3 funciona con una cuenta real de Backblaze B2 (2026-10-15). Siguiente: facilitar el formulario (ofrecer la lista de cubos en vez de escribirlo, si el servicio permite listar con una clave limitada a un cubo), guías y prueba real de Cloudflare R2 y Amazon S3, y una forma de desconectar un servicio y elegir otro; y después Dropbox, OneDrive y Google Drive cuando la app esté registrada en cada uno; la guía `docs/CLOUD_SETUP.md` solo está verificada para Backblaze B2. Plan: `.agents/plans/cloud-backup/`.
- [ ] **Solicitudes de cambio a kotpass #43 a #46** ([keemobile/kotpass](https://github.com/keemobile/kotpass/pulls)): el CI en rojo se diagnosticó y se gestionó el 2026-10-15 (#43: fixture de prueba corregido y subido como `1955d4a`; #44 y #46: solo el paso del token de Codecov, que un fork no puede pasar, explicado en un comentario; #45: corte de red, PR reabierta) y la pregunta del mantenedor en la #46 está respondida. Solo se espera al mantenedor: su respuesta en la #46 y, si lo pide, la aprobación para ejecutar el workflow en la #43. Notas: `.agents/plans/kotpass-upstream/`.
- [ ] **Comprobar el workflow de release con las variantes:** `release.yml` firma ahora el APK `offline` desde su ruta nueva y no se ha ejecutado desde entonces; el primer tag posterior a 0.10.3 lo demostrará. Nada posterior a la 0.10.3 se ha publicado.
- [ ] Quitar las entradas de bóvedas obsoletas de la lista del móvil (dos «demo» y los «prova-ac4» duplicados).

## Prioridad baja (más adelante)

- [ ] Actualizar el plugin de Android para Gradle, Gradle y Kotlin (solo versiones estables) para eliminar los avisos del análisis informativo de herramientas de compilación del CI (104 el 2026-10-14, todos en herramientas de compilación, ninguno en el APK).
- [ ] Clientes de Windows y Linux, cuando la app de Android sea funcional (reutilizan los módulos `core-*`).
- [ ] Servicio de autorrelleno y proveedor de credenciales (passkeys).
- [ ] TOTP.
- [ ] Clientes de escritorio (Windows, Linux).

## En evaluación (decisión pendiente)

- Mostrar los cambios aún sin subir requiere el complemento de Syncthing (KeyNexa no usa su API REST).
- Sincronización propia que no dependa de Syncthing (por ejemplo, un cliente de escritorio que haga de enlace), dejando Syncthing como opción.
- Integrar del todo en el proyecto las primitivas criptográficas (ya están vendorizadas con kotpass) en lugar de depender de binarios externos. No se prevé escribir algoritmos de cifrado nuevos: solo implementaciones auditadas, con versión fija y probadas contra los vectores oficiales.
- Syncthing como plugin opcional en todas las plataformas, nunca obligatorio: un solo dispositivo no necesita sincronización (la bóveda es local; las copias de seguridad en la nube son una opción aparte y recomendada). Motivo: cuando la sincronización es una app aparte es fácil olvidarse de arrancarla y entonces no se sincroniza nada; con el plugin, abrir KeyNexa la arranca, muestra el estado («sincronizado hace unos segundos») y evita errores de configuración como compartir la carpeta equivocada. Se ofrece al añadir un segundo dispositivo, emparejado con un código QR. Modos: integrado (recomendado) / usar un Syncthing externo / ninguno. En Android necesita un servicio en primer plano que siga vivo hasta terminar la subida pendiente al salir de la app. Llevar Syncthing (MPL-2.0, compatible con GPL-3.0) gestionado por su API REST (solo local, con clave aleatoria). Necesita un ADR (tamaño por arquitectura, reglas de procesos en segundo plano, política de actualizaciones, convivencia con Syncthing-Fork). Paso intermedio barato que hay que comprobar: pedir a un Syncthing-Fork instalado que arranque cuando se abre KeyNexa.
- Configurar un dispositivo nuevo con una frase semilla: un paquete cifrado con los ajustes y el emparejamiento de la sincronización (nunca la contraseña maestra), protegido con una clave derivada de la frase, guardado en la carpeta sincronizada o mostrado como código QR. Necesita un ADR.
- Copias de seguridad en la nube: primero a cualquier carpeta que el sistema exponga (Drive, Dropbox, Mega y otros a través de sus propias apps, sin SDK extra), después WebDAV y almacenamiento compatible con S3; sin SDK propietarios con telemetría. La app debe recomendarlo encarecidamente (aviso en el primer uso y recordatorio persistente hasta que exista una copia, sin imponerlo): perder o romper el móvil sin copia es perder todas las contraseñas. Necesita un ADR. Ahora propuesto en el [ADR-0008](decisions/0008-cloud-backup.md); el plan por pasos está en `.agents/plans/cloud-backup/`.
- Mantener kotpass fuerte: ofrecerse a ayudar a mantenerlo en el proyecto original, añadir fuzzing con Jazzer y la comparación con keepass-rs (ya listados arriba) y reevaluar las alternativas cada año (ADR-0005).

## Descartado

- Núcleo en Rust con UniFFI: la escritura de KDBX en keepass-rs es experimental y UniFFI añade una cadena de herramientas NDK; se reevaluará antes de 1.0 ([ARCHITECTURE](ARCHITECTURE.md#estrategia-multiplataforma)).
- Permiso `MANAGE_EXTERNAL_STORAGE`: no es necesario con SAF y no está permitido en Google Play para este uso.
- Depender de la API REST de Syncthing: requiere una clave de API y es posible que Syncthing no esté instalado.
