# ADR-0005: kotpass reforzado

[English](../../decisions/0005-reinforced-kotpass.md) | [Español](0005-reinforced-kotpass.md)

- **Estado:** Aceptada
- **Fecha:** 2026-10-13
- **Complementa:** [ADR-0002](0002-kdbx-engine.md)

## Contexto

La ADR-0002 eligió kotpass. Su principal debilidad es el mantenimiento: un solo contribuidor y ningún commit desde el 2025-09-28 (API de GitHub, consultada el 2026-10-13). Las alternativas se volvieron a evaluar en esa misma fecha:

| | kotpass | KeePassJava2 | keepass-rs | kdbxweb |
| --- | --- | --- | --- | --- |
| Contribuidores | 1 | 4 (un autor con 409 de unos 420 commits) | 34 (2 mantenedores principales) | 9 |
| Commits desde 2025-10-01 | 0 | 178 | 198 | 0 |
| Escritura | Estable (el autor afirma usarla en producción; no verificado) | Estable, con correcciones recientes de adjuntos | Experimental, con incidencias abiertas de pérdida de datos en adjuntos e historial y correcciones en revisión | — |
| Tamaño del código principal | ~10.000 líneas de Kotlin (~2.300 son portes de Bouncy Castle), 1 dependencia | ~14.400 líneas de Java, muchas dependencias | ~13.600 líneas de Rust | JavaScript |

kdbxweb está inactiva y necesitaría un motor JavaScript en Android. keepass-rs tiene la comunidad más sólida y está desarrollando la fusión de bases de datos, pero su escritura sigue siendo experimental y exige una cadena de herramientas Rust/UniFFI (la MPL-2.0 de UniFFI es compatible con la GPL-3.0, así que la licencia ya no es el obstáculo).

## Decisión

Mantener kotpass y **reforzarlo**, asumiendo su mantenimiento:

1. **Módulo integrado.** El código de kotpass se importa en `third_party/kotpass/` como módulo de Gradle, fijado al commit upstream `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945` (0.13.0), con su `LICENSE.txt` MIT, los avisos Apache-2.0 y Bouncy Castle que incluye, y un `README` que registra el commit upstream y cada cambio local. Los cambios locales se mantienen bajo MIT para poder ofrecerlos upstream.
2. **Pruebas diferenciales en la CI.** Cada bóveda escrita por las pruebas de KeyNexa se vuelve a leer con `keepassxc-cli` (KeePassXC) y con keepass-rs, y se comparan los contenidos. Estas herramientas solo se ejecutan en la CI y no se distribuyen con la aplicación.
3. **Fuzzing** del decodificador con archivos malformados y truncados (Jazzer, Apache-2.0, solo en pruebas).
4. **Correcciones de fidelidad:** conservar los elementos y atributos XML desconocidos al guardar; mantener la versión de KDBX al reescribir.
5. **Argon2 nativo opcional** mediante el `BaseKdfProvider` de kotpass si las mediciones en dispositivos de gama baja lo justifican (argon2kt, MIT).
6. **Primero upstream cuando sea posible:** las correcciones se proponen a kotpass; la copia integrada es la alternativa.

## Consecuencias

- KeyNexa mantiene unas 10.000 líneas de Kotlin de terceros; hay que vigilar las correcciones de seguridad del código portado de Bouncy Castle (versiones de bc-java).
- La interfaz `KdbxEngine` mantiene el motor sustituible; keepass-rs se vuelve a evaluar antes de 1.0 y en cuanto su escritura de KDBX deje de ser experimental.
- Créditos: kotpass, okio y los avisos incluidos en kotpass se añaden a CREDITS cuando se incorpore el módulo (fase C5).
