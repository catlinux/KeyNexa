# Política de seguridad

[English](../../SECURITY.md) | [Español](SECURITY.md)

## Estado del proyecto

KeyNexa está en su fase de diseño y **no tiene ninguna aplicación publicada**. No ha sido auditado por un tercero. No guardes contraseñas reales en compilaciones previas a una versión publicada.

## Notificar una vulnerabilidad

Por favor, **no abras una incidencia pública** para problemas de seguridad.

Notifícalo de forma privada a través de GitHub: **Security → Report a vulnerability** en [catlinux/KeyNexa](https://github.com/catlinux/KeyNexa/security). Incluye la versión afectada, los pasos para reproducirlo y el impacto. No envíes nunca bóvedas ni contraseñas reales; usa una bóveda de pruebas.

Recibirás un acuse de recibo lo antes posible. Las correcciones se publican con una entrada en el CHANGELOG bajo *Seguridad*, citando a quien lo notificó si así lo desea.

## Versiones compatibles

Hasta la 1.0.0, solo la última versión publicada recibe correcciones de seguridad.

## Diseño de seguridad

Consulta el [modelo de amenazas](THREAT_MODEL.md) para ver los activos, las amenazas, las mitigaciones y los riesgos residuales, y la [arquitectura](ARCHITECTURE.md) para el diseño del guardado seguro y de la fusión.
