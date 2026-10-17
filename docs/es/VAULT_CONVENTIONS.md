# Convenciones de la bóveda

[English](../VAULT_CONVENTIONS.md) | [Español](VAULT_CONVENTIONS.md)

Cómo organizar una bóveda real para que KeePassXC (escritorio) y KeyNexa (Android) la lean y la escriban igual. Todo es KDBX estándar: los campos personalizados se guardan en el mapa de cadenas de la entrada, así que cualquier programa compatible con KeePass los conserva.

## Nombre y ubicación del archivo

- **Nombre:** un solo archivo corto con extensión `.kdbx`, sin espacios y sin versión en el nombre (por ejemplo `Claus.kdbx`). Nunca `boveda-final2.kdbx`: las versiones son cosa de las copias de seguridad, no del nombre.
- **Carpeta:** una carpeta dedicada que contenga solo la bóveda, por ejemplo `C:\Users\<usuario>\KeyNexa-boveda\`. Es la carpeta que compartes con Syncthing y la que eliges en KeyNexa con *Abrir bóveda existente*.
- **No** dentro del repositorio de un proyecto, **no** en Descargas ni en el Escritorio, y **no** en una carpeta que otro cliente de nube también sincronice (dos sincronizadores sobre un archivo crean copias en conflicto).
- **Archivo de clave** (si usas uno): fuera de la carpeta sincronizada y nunca junto a la bóveda.
- **Copias:** guarda la copia automática de KeePassXC en otra carpeta y haz una copia periódica en otro disco.

## Campos personalizados

Créalos en KeePassXC en *Avanzado → Atributos adicionales*. Los nombres van en minúsculas con guiones bajos y siempre iguales, para que un futuro tipo de entrada «API / token» de KeyNexa los use sin migrar nada. Marca **Proteger** en los secretos.

| Campo | Proteger | Significado |
|---|---|---|
| `key_id` | no | Identificador público de la clave (access key ID, client ID) |
| `secret_key` | **sí** | Secreto que acompaña a `key_id` |
| `token` | **sí** | Token bearer o de API cuando no hay pareja `key_id` / `secret_key` |
| `endpoint` | no | URL base de la API o del servicio |
| `account_id` | no | Cuenta, proyecto o tenant al que pertenece la clave |
| `scopes` | no | Qué permite hacer la clave (texto libre) |
| `expires` | no | Fecha de caducidad, `AAAA-MM-DD` |
| `sign_in_with` | no | Proveedor con el que se entra (Google, Apple, GitHub…); deja la contraseña vacía |

Usa solo los campos que necesite cada entrada. El nombre del servicio va en el título y el usuario en el campo estándar *Nombre de usuario*. Los tokens largos, certificados y archivos de clave van en *Adjuntos*, no en un campo.

## Grupos

Grupos de primer nivel sugeridos: `Web`, `API`, `Finanzas`, `Dispositivos`, `Documentos`. Usa etiquetas para lo transversal (`trabajo`, `personal`). No anides más de dos niveles: el móvil los muestra de uno en uno.
