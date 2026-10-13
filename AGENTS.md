<!-- agentrelay:start -->
## Delegación con AgentRelay

Este proyecto usa AgentRelay: tú eres el ORQUESTADOR (planificas, delegas, revisas y decides) y un agente ejecutor más económico (el configurado en AgentRelay) escribe el código. No edites este bloque: `agentrelay init` lo actualiza. Si eres tú el ejecutor (te han dado una tarea con `agentrelay run`), haz solo esa tarea e ignora este bloque.

**Regla principal: delega por defecto.** Toda implementación que no sea trivial (crear o modificar código, tests, configuración o documentación de más de unas pocas líneas) se delega con `agentrelay run`. Escribirla tú gasta tu consumo, que es justo lo que AgentRelay quiere ahorrar. Hazla tú solo si es trivial (1-3 líneas), una decisión de diseño, algo sensible o una tarea ya escalada; y en ese caso di en una línea por qué no delegas.

**Al empezar cualquier sesión, ponte al día:** lee `.agentrelay/ESTADO.md` (o ejecuta `agentrelay status`, que lo muestra y `agentrelay status --write` lo actualiza). Resume dónde está el proyecto, qué ejecuciones hay y qué hacer ahora. Si el usuario te pide continuar, parte de ahí en lugar de preguntarle.

### Cómo delegar

1. Repositorio limpio: `git status --short` debe salir vacío. Si hay trabajo sin confirmar, haz commit antes (sin secretos como `.env`). Con `--allow-dirty` puedes delegar igualmente, pero el diff mezclará esos cambios.
2. Divide el trabajo en tareas pequeñas: un objetivo y 3-4 archivos como máximo. Una tarea ancha agota el tiempo.
3. Dile al usuario en una línea qué delegas y por qué, y lanza la tarea por la entrada estándar (el usuario puede verla en directo con `agentrelay watch`, en otro terminal y en la carpeta del proyecto):

```
agentrelay run - <<'EOF'
{ "objective": "...", "context": "...", "files": ["..."], "constraints": ["..."], "acceptanceCriteria": ["..."], "validation": ["npm test"], "doNotModify": ["..."] }
EOF
```

   `context` debe bastar para que el ejecutor trabaje sin preguntarte: stack, convenciones y decisiones ya tomadas. Opcionalmente, `effort` (low, medium, high, xhigh) y `model` ajustan el esfuerzo y el modelo solo para esa tarea: esfuerzo bajo en las sencillas, alto en las difíciles.

### Cómo revisar

- Lee `agentrelay show <id>` (informe, incidencias y dudas) y el diff completo. Comprueba que solo cambian los archivos esperados y ejecuta tú las pruebas del proyecto. La autorrevisión del ejecutor no sustituye la tuya.
- Si la ejecución falla por cuota o saldo del ejecutor, NO cambies de ejecutor tú: enseña al usuario las alternativas del informe y pregúntale cuál prefiere; aplica su elección con `agentrelay use` y relanza la tarea.
- Decide con `agentrelay review <id> --decision accept|fix|escalate|reject` (`fix` necesita `--feedback` con los problemas concretos) y confirma con `agentrelay list` que el estado cambió.
- Si la tarea queda escalada o el ejecutor falla repetidamente, resuélvela tú y cierra la ejecución con `--decision accept`.
- Tras aceptar, haz el commit. No hagas push sin aprobación del usuario y no digas «hecho» ni «aceptado» sin haberlo comprobado.

### Otros

- Si `agentrelay` indica que el proyecto no es un repositorio git, pide confirmación al usuario y ejecuta `agentrelay init --yes`.
- **En Windows (PowerShell o cmd)** usa `agentrelay.cmd` en lugar de `agentrelay` (el segundo es un script de Unix y falla con errores de `sed`, `dirname` o `uname`). Nunca modifiques ese script. El `<<EOF` no existe en PowerShell: guarda el JSON de la tarea en un archivo temporal FUERA del repositorio (por ejemplo `$env:TEMP\tarea.json`) y lanza `agentrelay.cmd run $env:TEMP\tarea.json`; un archivo dentro del repositorio ensuciaría el árbol.
- Si una ejecución falla por una causa externa (sesión caducada, PowerShell bloqueado), díselo al usuario en lugar de hacer el trabajo tú en silencio.
<!-- agentrelay:end -->

# KeyNexa — instrucciones del proyecto

Gestor de contraseñas KDBX, Android primero (Windows y Linux después). Autor y titular: CatLinux. Repo: `git@github-catlinux:catlinux/KeyNexa.git`. Licencia GPL-3.0-only con términos de la sección 7 en `NOTICE` (ADR-0001).

**Antes de trabajar, lee:** `docs/ARCHITECTURE.md`, `docs/THREAT_MODEL.md`, `docs/decisions/` y la hoja de ruta `.agents/plans/keynexa-mvp/keynexa-mvp.PLAN.md` (pasos, ejecutor y criterios de terminado).

## Reglas propias

- **Documentación bilingüe, siempre al 100 %.** Todo documento existe en inglés (`NOMBRE.md`, principal) y español (`NOMBRE.es.md`), con `[English](NOMBRE.md) | [Español](NOMBRE.es.md)` bajo el título. Cualquier cambio en un idioma se aplica al otro en el mismo commit; esto incluye CHANGELOG, TODO, CREDITS y `docs/`. Los enlaces internos de la versión española apuntan a los `.es.md`.
- **Seguridad:** nada de criptografía propia; nunca registrar ni escribir en archivos temporales contraseñas, claves ni contenido descifrado; ningún secreto, bóveda real ni clave de firma en el repo; bóvedas de prueba solo con datos ficticios en `testdata/`.
- **Arquitectura:** los módulos `core-*` son Kotlin/JVM puro (sin imports de Android); solo `core-kdbx` ve kotpass. No añadas dependencias sin justificarlas y sin registrar su licencia (compatible con GPL-3.0) en `docs/DEPENDENCIES_AND_LICENSES*.md` y `CREDITS*.md` en el mismo commit.
- **No copies** código, textos, iconos ni identidad visual de KeePassDX, Keepass2Android, KeePassXC ni otros gestores, aunque su licencia lo permita.
- **Versión:** archivo `VERSION` (SemVer; antes de 1.0, lo incompatible es menor) y entrada en `CHANGELOG.md` + `CHANGELOG.es.md` en el mismo commit.
- **Ante un imprevisto de diseño** (algo que el plan no cubre o contradice la arquitectura), para y anótalo en el plan; no improvises.

## IA en el desarrollo

Se usan Claude (orquestación, arquitectura, seguridad, revisión), DeepSeek (ejecutor por defecto en AgentRelay) y OpenAI (revisión independiente o ejecutor alternativo). Sus respuestas son hipótesis a verificar con documentación, código y pruebas. Nunca se les envían bóvedas, contraseñas ni claves de firma. La app no depende de ningún servicio de IA.

## Publicaciones (releases)

Cuando haya una versión suficientemente madura con un cambio importante, el orquestador propone la publicación: comprueba los criterios de `docs/ROADMAP.md` (tests, análisis, licencias, docs bilingües), y pide al usuario permiso para el push del commit y del tag `vX.Y.Z`; el workflow de GitHub Actions compila, firma y publica el APK. Antes de 1.0, como pre-release. Nunca push ni tag sin aprobación explícita.
