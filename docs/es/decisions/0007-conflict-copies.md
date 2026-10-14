# ADR-0007: Fusión de las copias de conflicto de Syncthing

[English](../../decisions/0007-conflict-copies.md) | [Español](0007-conflict-copies.md)

- **Estado:** Aceptada
- **Fecha:** 2026-10-14
- **Implementa:** la fase D7 de la hoja de ruta; amplía el [ADR-0004](0004-storage-sync-and-safe-save.md)

## Contexto

Cuando dos dispositivos cambian la bóveda sin verse, Syncthing conserva una versión como archivo de la bóveda y renombra la otra a `<nombre>.sync-conflict-<fecha>-<hora>-<dispositivo>.kdbx`. Nada la fusiona después: los cambios de la copia quedan invisibles hasta que alguien la abre a mano y, con el tiempo, se olvidan. El guardado seguro ya fusiona cambios concurrentes con la semántica de KeePass (`KeePassMerger`), y las entradas tienen UUID aleatorios, así que las altas independientes nunca chocan.

Restricciones: abrir una copia cuesta una derivación de clave completa (segundos con Argon2 en un móvil); una copia puede estar escrita con otra contraseña (cambiada en un solo dispositivo), estar dañada, seguir en transferencia o pertenecer a otra bóveda; no se puede borrar nada sin el consentimiento del usuario.

## Opciones

1. **Mostrar las copias y que el usuario fusione cada una.** Seguro, pero fácil de ignorar, que es justo el problema actual.
2. **Fusionar automáticamente al abrir y preguntar antes de borrar.** La fusión solo añade información (o aplica borrados que el otro dispositivo registró), las versiones anteriores siguen en las copias privadas y las copias de conflicto se quedan en el disco hasta que el usuario acepte.
3. **Fusionar y borrar automáticamente.** Lo más cómodo, pero una fusión errónea no dejaría rastro en la carpeta sincronizada.

## Decisión

Opción 2.

1. **Cuándo:** tras abrir una bóveda y tras cargar un cambio externo, mientras la bóveda abierta no tenga nada sin guardar ni un guardado en curso. Las copias se buscan con `conflictCopiesOf` entre los archivos vecinos del de la bóveda, con el nombre real del archivo según el almacenamiento (no el nombre que muestra la app).
2. **Qué se salta:** una copia cuya huella (SHA-256 de sus bytes) está en el registro de copias fusionadas de esa bóveda. El registro vive en almacenamiento privado excluido de las copias de seguridad y solo guarda huellas; evita pagar una derivación de clave en cada apertura.
3. **Cómo:** cada copia restante se descifra con las credenciales de la bóveda abierta y se integra en la bóveda actual con `KeePassMerger` (los empates favorecen a la bóveda abierta). El resultado pasa por el guardado seguro normal. Las copias que no aportan nada se registran como fusionadas sin guardar.
4. **Se registra solo tras el éxito:** las huellas se añaden al registro solo cuando el guardado devuelve `Saved` (o cuando no hacía falta guardar). Un guardado fallido no las registra, así que la próxima apertura lo intenta otra vez.
5. **Las copias que no se pueden fusionar** se indican y se dejan como están, nunca se borran: otra contraseña (`WrongCredentials`; una versión posterior podrá pedirla), dañada o aún en transferencia (`Corrupted`, se reintenta la próxima vez), otra bóveda (`Unmergeable`) o ilegible.
6. **Borrado solo con consentimiento:** tras una fusión, la app dice cuántas copias se fusionaron y ofrece borrarlas. Solo borra copias del registro cuyos bytes siguen teniendo la huella registrada, releídos justo antes de borrar; todo lo demás se conserva. Si el usuario no acepta, las copias se quedan y la oferta vuelve en la próxima apertura.
7. **Nada secreto sale de la memoria:** las copias se descifran en memoria, como la bóveda; solo se guardan huellas.

## Consecuencias

- Dos dispositivos que editan sin verse acaban con la unión de sus cambios en cuanto uno de ellos abre la bóveda, sin que el usuario haga nada salvo confirmar la limpieza.
- Cada copia nueva cuesta una derivación de clave la primera vez que se encuentra; las ya conocidas solo cuestan una lectura y un hash.
- Una copia escrita con otra contraseña queda sin fusionar hasta que se pida esa contraseña; la interfaz lo indica.
- `StoragePort` incorpora `name(location)` y `delete(location)`; la implementación de Android usa el nombre visible del documento y `DocumentsContract.deleteDocument`.
