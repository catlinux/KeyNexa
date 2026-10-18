# ADR-0003: Plataforma y módulos

[English](../../decisions/0003-platform-and-modules.md) | [Español](0003-platform-and-modules.md)

- **Estado:** Aceptada
- **Fecha:** 2026-10-13

## Contexto

Android es la primera plataforma; Windows y Linux vendrán después. El motor KDBX (ADR-0002) es solo JVM. Las funciones de seguridad de Android (Keystore, BiometricPrompt, autorrelleno, proveedor de credenciales, `FLAG_SECURE`) deben ser de primera clase.

## Opciones

1. Núcleo Kotlin/JVM + Jetpack Compose (Android) + Compose Multiplatform Desktop más adelante.
2. Núcleo en Rust + UniFFI + IU nativas por plataforma.
3. Núcleo Kotlin Multiplatform (requiere un motor KDBX para KMP que hoy no existe).
4. Frameworks de IU multiplataforma (Flutter, React Native).

## Decisión

Opción 1. Proyecto Gradle multimódulo: `core/vault`, `core/kdbx`, `core/sync` (Kotlin/JVM puro, sin importaciones de Android), `platform/android`, `apps/android`. Detalles en [ARCHITECTURE](../ARCHITECTURE.md). minSdk 26, SDK de compilación y de destino 36.

## Consecuencias

- La lógica del núcleo se prueba con pruebas unitarias en la JVM, de forma rápida y sin dispositivos.
- Un cliente de escritorio reutiliza los módulos de núcleo en la JVM; solo son nuevos el almacenamiento, el almacenamiento de secretos y la IU.
- El núcleo podría pasar más adelante a KMP si aparece un motor KDBX para KMP; mantener los módulos de núcleo libres de API exclusivas de la JVM cuando resulte barato lo facilita.
- iOS no es un objetivo; esta elección no lo admite sin trabajo adicional.
