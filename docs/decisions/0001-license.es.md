# ADR-0001: Licencia

[English](0001-license.md) | [Español](0001-license.es.md)

- **Estado:** Aceptada
- **Fecha:** 2026-10-13
- **Decidido por:** CatLinux (autor y titular del copyright)

## Contexto

El propietario de KeyNexa quiere un código público y auditable, créditos obligatorios y protección frente a la redistribución cerrada. Un gestor de contraseñas también se beneficia de poder ser revisado por cualquiera. Todas las dependencias previstas son permisivas (MIT, Apache-2.0, Bouncy Castle License, OFL).

## Opciones

1. **WNCL-TP-1.0 / WNCL-CU-1.0** (licencias personalizadas del propietario): restringen el uso comercial o la venta; no son Open Source; no las acepta F-Droid.
2. **PolyForm Noncommercial 1.0.0**: prohíbe el uso comercial; no obliga a los forks a publicar el código fuente.
3. **GPL-3.0**: copyleft Open Source; se permite el uso comercial, pero toda versión distribuida debe seguir siendo GPL-3.0 con el código fuente completo; la sección 7 permite términos de atribución y de marca.
4. **AGPL-3.0**: como GPL-3.0, más obligaciones sobre el código fuente para servicios en red (no relevante para una aplicación sin conexión).

Ninguna licencia Open Source puede prohibir el uso comercial (Open Source Definition, criterio 6).

## Decisión

**GPL-3.0-only**, con términos adicionales conforme a la sección 7 escritos en [NOTICE](../../NOTICE): atribución a CatLinux y enlace al repositorio (7(b)), versiones modificadas marcadas como tales (7(c)) y ninguna licencia sobre el nombre ni el logotipo de KeyNexa (7(e)).

## Consecuencias

- Cualquiera puede usar, modificar y redistribuir KeyNexa, también con fines comerciales, pero debe mantenerlo bajo GPL-3.0, publicar el código fuente completo y conservar la atribución.
- Todas las dependencias deben ser compatibles con GPL-3.0 (consulta [Dependencias y licencias](../DEPENDENCIES_AND_LICENSES.es.md)). Apache-2.0 es compatible con GPL-3.0 pero no con GPL-2.0-only.
- El código GPL de otros clientes de KeePass podría reutilizarse legalmente, pero KeyNexa sigue evitando copiarlo: el proyecto mantiene una base de código y una identidad visual originales.
- La publicación en F-Droid pasa a ser posible.
- Las contribuciones externas se aceptan bajo la misma licencia (entrada = salida, consulta [CONTRIBUTING](../../CONTRIBUTING.es.md)).
