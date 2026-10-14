# Inclusión en F-Droid e IzzyOnDroid (E2.4, a mano por el usuario)

Requisitos leídos el 2026-10-14. Ninguna acción se hace desde la sesión (sin login).

## F-Droid (GitLab)
1. Primera contribución: abrir un issue en https://gitlab.com/fdroid/rfp/-/issues con su plantilla (no verificada: no se pudo leer).
2. O directamente: fork de https://gitlab.com/fdroid/fdroiddata, rama `io.github.catlinux.keynexa`, copiar `io.github.catlinux.keynexa.yml` (este directorio) a `metadata/`, comprobar que pasa el CI de GitLab y abrir el merge request (squash, rellenar su plantilla).
3. F-Droid compila desde el código: el APK firmado de Actions no se usa; la firma será la suya. Lo que no hay que dar por sabido: si compila con `gradle: yes` sin ajustes (hay que mirar el log del CI), si `testdata/*.kdbx` o la verificación de dependencias le dan problemas, y el JDK/SDK que usa. Es un borrador sin probar (`fdroid lint`/`build` no ejecutados).
4. `AutoUpdateMode: Version` + `UpdateCheckMode: Tags` toman los tags `vX.Y.Z`; `commit` debe ser el hash real del tag (v0.10.3 = dd78d06508ad259b223577ed5774cb3bb0d955e2).

## IzzyOnDroid (Codeberg: https://codeberg.org/IzzyOnDroid/repodata)
- Issue con la plantilla **«App Inclusion Request»** (otra forma → se cierra). Un issue por app.
- **Codeberg restringe el contenido generado por LLM y Izzy cierra solicitudes con más asistencia de IA que su umbral: redacta tú el texto.** Datos que puedes usar: id `io.github.catlinux.keynexa`, repo https://github.com/catlinux/KeyNexa, release v0.10.3 (APK `keynexa-0.10.3.apk` + `.sha256`, firmado por Actions, certificado SHA-256 `51:ca:da:3c:d0:8c:35:5f:13:67:90:b1:be:83:09:00:8b:b9:a3:1e:e9:44:b4:e6:e3:74:7e:6a:e9:d0:44:9b`), GPL-3.0-only, sin permiso de Internet, metadatos en `fastlane/metadata/android/`.
- Revisión: escaneo del APK (Cronos), metadatos fastlane, prueba en dispositivo, comprobación de reproducibilidad. Toma los APK de los releases de GitHub.
- Declarar con honestidad el uso de IA en el desarrollo (AGENTS.md ya lo documenta).
