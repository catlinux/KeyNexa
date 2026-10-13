# Hoja de ruta

[English](../ROADMAP.md) | [Español](ROADMAP.md)

Leyenda: ✅ hecho · 🔄 en curso · ⏳ planificado. Nada está «hecho» hasta que se cumplen sus criterios de aceptación y pasan sus pruebas.

## Fases

| Fase | Contenido | Versión objetivo | Estado |
| --- | --- | --- | --- |
| A — Investigación | Entorno, bibliotecas KDBX, licencias, restricciones de Android | 0.1.0 | ✅ |
| B — Arquitectura y seguridad | Arquitectura, modelo de amenazas, MVP, decisión de licencia | 0.1.0 | ✅ |
| C — Base del proyecto | Esqueleto multimódulo de Gradle, CI, lint/formato, i18n, sistema de diseño, flujo de publicación | 0.2.0 – 0.4.0 | ✅ |
| D — MVP seguro | Abrir/bloquear/editar bóvedas, guardado seguro, cambios externos y fusión. Hecho: guardado seguro, fusión, almacenamiento Android, pantallas, bloqueo automático y protección de pantalla (0.5.0 – 0.8.x). Pendiente: pantalla de ajustes, borrado del portapapeles totalmente fiable y desbloqueo biométrico | 0.5.0 – 0.8.0 | 🔄 |
| E — Revisión | Revisión de dependencias y de seguridad, pruebas de interoperabilidad, documentación, primera versión preliminar | 0.9.0 | ⏳ |
| Más adelante | Autorrelleno, proveedor de credenciales, TOTP, clientes de Windows y Linux (cuando la app de Android sea funcional) | 1.0.0+ | ⏳ |

## MVP (Android)

**Dentro del alcance**

1. Abrir una bóveda KDBX 3.1/4.0/4.1 existente elegida con el selector de archivos del sistema (concesión de archivo o de carpeta), con contraseña maestra y/o archivo de clave.
2. Crear una nueva bóveda KDBX 4 (Argon2id, AES-256 o ChaCha20).
3. Explorar grupos, buscar entradas, ver una entrada; copiar usuario/contraseña con el indicador de portapapeles sensible y borrado automático.
4. Crear, editar, mover y eliminar entradas y grupos (a la papelera de reciclaje cuando esté activada), conservando el historial.
5. Guardar con el flujo de guardado seguro; detectar cambios externos; fusión automática; archivos de conflicto ofrecidos para fusionar.
6. Bloquear manualmente, por tiempo de inactividad y al apagar la pantalla; `FLAG_SECURE`; sin copia de seguridad de los datos de la aplicación.
7. Desbloqueo biométrico opcional (clave maestra envuelta con el Keystore).
8. Interfaz en inglés y español, temas claro y oscuro, accesible (etiquetas de TalkBack, texto escalable).

**Fuera del alcance del MVP:** autorrelleno y passkeys, generación de TOTP, edición de adjuntos (los adjuntos se conservan, solo lectura), proveedores en la nube, clientes de escritorio, archivos KDB (KeePass 1.x).

## Criterios de aceptación del MVP

| N.º | Criterio | Verificado mediante |
| --- | --- | --- |
| AC1 | Las bóvedas creadas por KeePassXC y KeePass 2.x (archivos de prueba, sin datos reales) se abren correctamente con todas las combinaciones de cifrado/KDF admitidas | Pruebas automatizadas de compatibilidad con un corpus de archivos de prueba |
| AC2 | Una bóveda guardada por KeyNexa se abre en KeePassXC con todos los grupos, entradas, campos personalizados, adjuntos, historial, iconos y datos personalizados intactos | Pruebas de ida y vuelta + comprobación manual registrada en las notas de la versión |
| AC3 | Guardar nunca deja un archivo truncado o imposible de descifrar, ni siquiera si el proceso se mata durante la escritura | Pruebas de inyección de fallos en el flujo de guardado seguro con almacenamiento simulado |
| AC4 | Un cambio externo hecho mientras la bóveda está abierta se fusiona al guardar sin perder los cambios de ninguno de los lados | Pruebas unitarias de fusión + prueba en dispositivo con Syncthing |
| AC5 | Los conflictos que no se pueden fusionar nunca sobrescriben el archivo; se conservan ambas versiones | Pruebas unitarias |
| AC6 | Ningún secreto aparece en logcat, en la salida de fallos ni en archivos de la aplicación fuera de la bóveda y las copias de seguridad | Aserciones sobre los logs en las pruebas + revisión manual |
| AC7 | El bloqueo borra los datos descifrados y las claves; el bloqueo automático funciona por tiempo de inactividad y al apagar la pantalla | Pruebas instrumentadas |
| AC8 | Todo el texto visible para el usuario existe en inglés y español; ningún diseño se rompe con cadenas largas | Lint (`HardcodedText`, traducciones que faltan) + pruebas de capturas de pantalla |
| AC9 | Las comprobaciones de licencias y de vulnerabilidades de las dependencias pasan en la CI | Informe de la CI |

## Estrategia de versionado

- [Versionado Semántico](https://semver.org/lang/es/): `MAJOR.MINOR.PATCH`, almacenado en el archivo `VERSION` (la compilación de Android lo leerá).
- Durante `0.y.z` el formato, los ajustes y las API pueden cambiar entre versiones menores; una versión `0.y` **no** está lista para producción, aunque compile y pase las pruebas.
- La `1.0.0` requiere: criterios de aceptación del MVP cumplidos, una revisión de seguridad externa o al menos una revisión independiente documentada, y ningún error conocido de pérdida de datos.
- Todo cambio terminado sube la versión y añade una entrada en el [CHANGELOG](CHANGELOG.md) en el mismo commit.

## Criterios de publicación (toda compilación publicada)

1. Todas las pruebas automatizadas y el análisis estático pasan en la CI.
2. Análisis de vulnerabilidades de dependencias e informe de licencias revisados.
3. CHANGELOG y documentación actualizados en ambos idiomas.
4. APK de publicación compilado y firmado por GitHub Actions a partir de una etiqueta `vX.Y.Z`; sumas de verificación publicadas.
5. Las publicaciones previas a la 1.0 se marcan como *pre-release* en GitHub.
