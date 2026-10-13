# ADR-0002: Motor KDBX

[English](../../decisions/0002-kdbx-engine.md) | [Español](0002-kdbx-engine.md)

- **Estado:** Aceptada
- **Fecha:** 2026-10-13

## Contexto

KeyNexa debe leer y escribir KDBX 3.1, 4.0 y 4.1 sin implementar criptografía propia, con interoperabilidad con KeePassXC y otros clientes. La comparación de candidatas está en [Dependencias y licencias](../DEPENDENCIES_AND_LICENSES.md).

## Opciones

1. **kotpass** (MIT, JVM): cobertura completa de cifrados/KDF, una sola dependencia en tiempo de ejecución, pruebas con archivos de ejemplo; un único mantenedor, sin versiones desde 2025-09-28.
2. **KeePassJava2** (Apache-2.0): activa, pero sin Twofish, solo Argon2d, nueva API 3.x con cambios incompatibles y errores recientes de integridad de adjuntos.
3. **keepass-rs** (MIT, Rust): activa, pero la escritura es experimental y requeriría una cadena de herramientas de Rust/UniFFI.
4. **Implementación KDBX propia** sobre Bouncy Castle: control total, pero grande, arriesgada y contraria al principio de «sin código criptográfico propio».

## Decisión

Usar **kotpass**, aislada en el módulo `core-kdbx` tras la interfaz `KdbxEngine` de KeyNexa.

## Consecuencias

- Solo `core-kdbx` depende de kotpass; sustituirla o bifurcarla no afecta al resto.
- La fase C/D debe demostrar, con pruebas automatizadas sobre un corpus de archivos de prueba creado por KeePassXC y KeePass 2.x:
  - la conservación en ida y vuelta de grupos, entradas, campos personalizados, adjuntos, historial, iconos, datos personalizados y objetos eliminados;
  - el comportamiento con elementos XML desconocidos (probablemente se descartan; en ese caso, documentarlo o aportar una corrección);
  - el rendimiento de Argon2 en un dispositivo de gama baja con parámetros de memoria grandes; si es inaceptable, incorporar una KDF nativa a través de `BaseKdfProvider` de kotpass (argon2kt, MIT).
- Si el mantenedor sigue inactivo y hace falta una corrección, KeyNexa bifurca kotpass (MIT lo permite) conservando sus avisos.
- KeePassJava2 sigue siendo la candidata de reserva.
