# Cómo contribuir

[English](../../CONTRIBUTING.md) | [Español](CONTRIBUTING.md)

Gracias por tu interés en KeyNexa. El proyecto está en una fase temprana; por favor, abre una incidencia antes de empezar cambios grandes.

## Reglas básicas

- **Nada de criptografía propia.** Usa el motor KDBX y las API de la plataforma.
- **No subas nunca secretos:** bóvedas reales, contraseñas, archivos de clave, claves de firma, archivos `.env`.
- **No registres nunca secretos en los logs:** las contraseñas, las claves o el contenido descifrado no deben llegar a los logs, a la salida de fallos ni a archivos temporales.
- Mantén el código sensible para la seguridad pequeño y cubierto por pruebas.
- Las nuevas dependencias necesitan una justificación y una comprobación de licencia ([proceso](DEPENDENCIES_AND_LICENSES.md#proceso-para-añadir-una-dependencia)).
- No copies código, texto, iconos ni identidad visual de otros gestores de contraseñas.

## Licencia de las contribuciones

KeyNexa se distribuye bajo GPL-3.0-only con los términos adicionales de [NOTICE](../../NOTICE). Al enviar una contribución aceptas que es obra tuya (o que tienes derecho a enviarla) y que se distribuye bajo los mismos términos.

## Documentación bilingüe

Toda la documentación existe en **inglés** (principal) y en **español**. Los archivos en inglés están en su lugar habitual (`README.md`, `CHANGELOG.md`, `docs/ARCHITECTURE.md`…). Las traducciones al español reproducen esa estructura dentro de `docs/es/` (`docs/es/CHANGELOG.md`, `docs/es/ARCHITECTURE.md`, `docs/es/decisions/…`), salvo `README.es.md`, que se queda en la raíz junto a `README.md`.

1. Todo documento empieza con el título y los enlaces de idioma, con rutas relativas a las dos versiones; por ejemplo, en `docs/es/ARCHITECTURE.md`: `[English](../ARCHITECTURE.md) | [Español](ARCHITECTURE.md)`.
2. Cualquier cambio en un idioma se aplica al otro **en el mismo commit**. Un pull request que cambie solo un idioma está incompleto.
3. Las traducciones dicen lo mismo; los nombres técnicos, los comandos y los identificadores se mantienen en inglés.
4. La documentación describe el estado real del proyecto y separa lo *implementado*, lo *planificado* y lo *pendiente*.

## Versionado y registro de cambios

- [Versionado Semántico](https://semver.org/lang/es/); la versión actual está en [VERSION](../../VERSION). Antes de la 1.0.0, los cambios incompatibles suben la versión menor.
- Todo cambio terminado sube la versión y añade una entrada en [CHANGELOG](../../CHANGELOG.md) y en [CHANGELOG.es](CHANGELOG.md) (formato [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)) en el mismo commit.
- El trabajo pendiente va en [TODO](TODO.md); las tareas terminadas se eliminan de él.

## Commits

- Commits pequeños y coherentes con mensajes descriptivos (en español, el idioma de trabajo del mantenedor).
- La CI debe pasar antes de fusionar (cuando exista la CI).
