# ADR-0011: Estructura del repositorio para varios clientes y una sola versión del producto

[English](../../decisions/0011-repository-layout.md) | [Español](0011-repository-layout.md)

- **Estado:** Aceptada
- **Fecha:** 2026-10-18
- **Amplía:** [ADR-0003](0003-platform-and-modules.md)

## Contexto

La app de Android llegó a la 1.0.0 con todos los módulos en la raíz del repositorio (`core-vault`, `core-kdbx`, `core-sync`, `platform-android`, `platform-cloud`, `app-android`). Ahora viene un cliente para Windows y después uno para Linux, los dos sobre la JVM (ADR-0003). Con todos los módulos en la raíz se mezclan los clientes, y el código que el escritorio necesita de la app de Android (la sesión de la bóveda, los almacenes privados de archivos, el tema) vive hoy en módulos de Android.

## Opciones

1. **Un repositorio por cliente**, con el núcleo publicado como biblioteca. Cada cambio del núcleo exige una publicación y subir la versión en cada cliente; se duplican el CI, la documentación y los registros de licencias.
2. **Un repositorio y una compilación Gradle, con los módulos en la raíz** (como hoy). Sencillo, pero la lista crece mezclada y nada indica de qué cliente es cada módulo.
3. **Un repositorio y una compilación Gradle, con los módulos agrupados por función (este registro).** La estructura habitual de los proyectos Gradle grandes con varios módulos: el núcleo cambia para todos los clientes en un solo commit, con un solo CI y un solo sitio para la documentación y las licencias.
4. **Carpetas por sistema operativo** (`windows/`, `linux/`). Descartada: Windows y Linux ejecutan la misma aplicación JVM; separarlos duplicaría la interfaz.

## Decisión

1. Opción 3, con esta estructura y rutas de proyecto Gradle que siguen las carpetas:

   ```text
   core/vault       :core:vault       modelo del dominio y puertos
   core/kdbx        :core:kdbx        el único módulo que ve kotpass
   core/sync        :core:sync        guardado seguro, fusión, copias de conflicto, reconciliación con la nube
   core/session     :core:session     sesión de la bóveda (abrir, editar, guardar, cambios externos), sacada de la app de Android
   core/peer        :core:peer        sincronización entre dispositivos (ADR-0013)
   platform/jvm     :platform:jvm     almacenes privados de archivos comunes a todo cliente JVM (copias, diario, libro de copias fusionadas)
   platform/android :platform:android SAF, Keystore
   platform/cloud   :platform:cloud   cliente compatible con S3
   platform/desktop :platform:desktop almacenamiento en archivos normales, secretos del sistema (Windows ahora, Linux después)
   ui/shared        :ui:shared        tema, marca, teclado y componentes comunes en Compose Multiplatform (Android + escritorio)
   apps/android     :apps:android     la app de Android
   apps/desktop     :apps:desktop     la app de escritorio (Windows ahora, Linux después)
   third_party/kotpass :kotpass       motor integrado (sin cambios)
   ```

2. Todo lo que no es un módulo se queda en la raíz: `README`, `LICENSE`, `NOTICE`, `CHANGELOG.md`, `TODO.md`, `VERSION`, `docs/`, `testdata/`, `scripts/`, `config/`, `gradle/` y `fastlane/` (F-Droid lo busca allí).
3. Los módulos se mueven con `git mv`, así que conservan su historia. El traslado no cambia el comportamiento de la app de Android; el identificador de la aplicación, la firma y los nombres de las APK siguen igual.
4. **Una sola versión del producto.** Un archivo `VERSION` y un `CHANGELOG.md` para todos los clientes. Una etiqueta de publicación `vX.Y.Z` publica todos los clientes compilados desde ese commit (las dos APK y los paquetes de Windows, cada uno con su SHA-256). Mientras el cliente de escritorio vaya por detrás de la app de Android, sus archivos llevan `preview` en el nombre y las notas de la publicación lo dicen; la publicación conserva el estado de la app de Android.
5. Las diferencias entre sistemas operativos van en `platform/desktop` (un conjunto de fuentes o paquete por sistema), nunca en una copia de la app.

## Consecuencias

- Los cambios del núcleo llegan a todos los clientes en un solo commit, probados juntos por un solo CI.
- El traslado toca los scripts de compilación, los flujos de trabajo (rutas de las APK y de los informes), los archivos de bloqueo y de verificación y cada ruta de la documentación; se hace en un paso propio y se comprueba compilando las dos APK y desbloqueando en el móvil.
- Un arreglo solo de escritorio también sube la versión compartida con Android, así que una publicación de Android puede no llevar ningún cambio de Android. El registro de cambios dice a qué cliente corresponde cada entrada.
- La receta de compilación de F-Droid, aún no presentada, debe apuntar a `apps/android`.
