# Vault conventions

[English](VAULT_CONVENTIONS.md) | [Español](es/VAULT_CONVENTIONS.md)

How to organise a real vault so that KeePassXC (desktop) and KeyNexa (Android) read and write it the same way. Everything here is standard KDBX: custom fields are stored in the entry's string map, so any KeePass-compatible program keeps them.

## File name and location

- **Name:** one short file with the `.kdbx` extension, no spaces and no version in the name (for example `Claus.kdbx`). Never `vault-final2.kdbx`: versions are the job of the backups, not of the name.
- **Folder:** a dedicated folder that holds only the vault (and, if you use one, nothing else), for example `C:\Users\<you>\KeyNexa-vault\`. It is the folder you share with Syncthing and the one you pick in KeyNexa with *Open existing vault*.
- **Not** inside a project repository, **not** in Downloads or Desktop, and **not** in a folder that another cloud client also syncs (two synchronisers on one file create conflict copies).
- **Key file** (if you use one): outside the synchronised folder and never next to the vault.
- **Backups:** keep KeePassXC's automatic backup of the vault in a different folder, and make a periodic copy on another disk.

## Custom fields

Create them in KeePassXC under *Advanced → Additional attributes*. Names are lowercase with underscores and always the same, so a future "API / token" entry type in KeyNexa can use them without migrating anything. Tick **Protect** on the secret ones.

| Field | Protect | Meaning |
|---|---|---|
| `key_id` | no | Public identifier of the key (access key ID, client ID) |
| `secret_key` | **yes** | Secret that goes with `key_id` |
| `token` | **yes** | Bearer or API token when there is no `key_id` / `secret_key` pair |
| `endpoint` | no | Base URL of the API or service |
| `account_id` | no | Account, project or tenant the key belongs to |
| `scopes` | no | What the key may do (free text) |
| `expires` | no | Expiry date, `YYYY-MM-DD` |
| `sign_in_with` | no | Provider used to sign in (Google, Apple, GitHub…); leave the password empty |

Use only the fields an entry needs. Put the service name in the title and the user in the standard *Username* field. Long tokens, certificates and key files go in *Attachments*, not in a field.

## Groups

Suggested top-level groups: `Web`, `API`, `Finance`, `Devices`, `Documents`. Use tags for cross-cutting labels (`work`, `personal`). Do not nest more than two levels: the phone shows them one at a time.
