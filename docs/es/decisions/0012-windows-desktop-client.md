# ADR-0012: Cliente de escritorio para Windows

[English](../../decisions/0012-windows-desktop-client.md) | [Español](0012-windows-desktop-client.md)

- **Estado:** Aceptada
- **Fecha:** 2026-10-18
- **Amplía:** [ADR-0003](0003-platform-and-modules.md), [ADR-0011](0011-repository-layout.md)

## Contexto

El propietario quiere un cliente para Windows con las mismas funciones y el mismo aspecto que Android 1.0: bóvedas guardadas solo en el ordenador o también copiadas en la nube (Backblaze B2, Cloudflare R2), y sincronización con otros dispositivos sin Syncthing-Fork (ADR-0013). Todo debe ir en un solo paquete, y cada modo se activa por separado.

## Opciones

1. **Compose Multiplatform para escritorio sobre la JVM**, como preveía el ADR-0003: el núcleo, el cliente de la nube y la sesión de la bóveda funcionan sin cambios y el tema Material 3 se comparte con Android.
2. **Una interfaz nativa de Windows (WinUI, WPF) sobre el núcleo JVM**: un segundo lenguaje y un puente entre procesos, sin ganar nada en seguridad.
3. **Tecnologías web (Electron, Tauri)**: un motor de navegador dentro de un gestor de contraseñas y el núcleo en otro entorno de ejecución.

## Decisión

1. **Opción 1.** Kotlin, Compose Multiplatform para escritorio y Material 3 con el tema de `ui/shared`. Las pantallas se hacen para una ventana ancha (grupos, entradas y detalle uno al lado del otro, atajos de teclado) con la misma dirección visual que Android; el propietario aprueba la maqueta antes de construirlas.
2. **Sistemas soportados: Windows 10 22H2 y Windows 11, x64.** Windows 7 y 8.1 no se soportan: ya no reciben actualizaciones de seguridad y los JDK actuales no los admiten. Windows 10 se mantiene mientras el entorno de ejecución incluido y Compose funcionen en él (se comprueba en un Windows 10 real antes de cada publicación). Windows en ARM64 se estudiará más adelante.
3. **Empaquetado:** el entorno de ejecución del JDK va incluido (jpackage), así que no hace falta instalar nada antes. Dos formatos: un instalador MSI **por usuario** (sin derechos de administrador) y un ZIP portable. Sin actualizaciones automáticas en la primera versión. La firma de código se decide antes de la primera versión estable de escritorio; hasta entonces Windows SmartScreen avisará al instalar, y se publica el SHA-256 de cada archivo.
4. **Un paquete, tres modos**, que se eligen en los ajustes y se pueden combinar: **solo local** (por defecto; la app no abre ningún socket ni ninguna conexión), **copias en la nube** (ADR-0008, el mismo cliente S3 y el mismo texto de consentimiento que en Android) y **sincronización con otros dispositivos** (ADR-0013). A diferencia de Android, no hay una compilación sin conexión aparte: activar un modo es elección del usuario y se explica en pantalla.
5. **Almacenamiento:** las bóvedas son archivos normales en cualquier unidad local o de red. Se mantiene el proceso de guardado seguro de `core/sync`; además, el escritorio escribe los bytes nuevos **cifrados** en un archivo temporal de la misma carpeta y sustituye la bóveda con un movimiento atómico, y vuelve a la escritura en el sitio cuando el sistema de archivos no lo permite. Los datos privados (copias, diario, libro de copias fusionadas, ajustes) van a `%LOCALAPPDATA%\KeyNexa`, nunca a un perfil itinerante.
6. **Secretos:** las credenciales de la nube y la clave del dispositivo del ADR-0013 se cifran con DPAPI para el usuario actual, llamada mediante JNA. No hay desbloqueo rápido en la primera versión; Windows Hello tendrá su propio registro.
7. **Protecciones equivalentes a las de Android:** la ventana queda excluida de las capturas de pantalla (`SetWindowDisplayAffinity` con `WDA_EXCLUDEFROMCAPTURE`) salvo que el usuario las permita; los secretos copiados se marcan para que Windows no los guarde en el historial del portapapeles ni en el portapapeles en la nube, y se borran pasado el tiempo configurado, al bloquear y al salir; la bóveda se bloquea tras un tiempo sin actividad, al bloquear la sesión de Windows y al suspender; solo se ejecuta una instancia a la vez; nada de la bóveda se registra.
8. **Teclado de KeyNexa (ADR-0010):** disponible para las contraseñas maestras pero **desactivado por defecto** en el escritorio, donde lo normal es un teclado físico y un teclado en pantalla solo ayuda contra los keyloggers.
9. **Linux** vendrá en el mismo módulo `apps/desktop`, con sus propios paquetes y el Secret Service en `platform/desktop`, con su propio registro.

## Consecuencias

- El tamaño instalado incluye un entorno de ejecución de Java (decenas de megabytes), el precio de no necesitar nada instalado de antemano.
- Las dependencias nuevas (Compose Multiplatform, Skiko, JNA, el entorno de ejecución incluido) se registran con sus licencias antes de añadirlas.
- El modelo de amenazas incorpora el escritorio: otros programas que se ejecutan con el mismo usuario pueden leer la memoria del proceso y los archivos de `%LOCALAPPDATA%`, y DPAPI no protege frente a ellos; otro usuario de Windows no puede leerlos.
- Sin firma de código, los usuarios tienen que confiar en las sumas publicadas; es una barrera para la adopción hasta que se decida la firma.
