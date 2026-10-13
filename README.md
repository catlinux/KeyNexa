# KeyNexa

[English](README.md) | [Español](README.es.md)

*Your keys. Your vault. Your control.*

A security-first, cross-platform password manager built around established cryptographic libraries, local vaults, and seamless Syncthing synchronization.

> **Project status: design phase (v0.1.0).** There is no application yet. This repository currently contains the research, architecture, threat model and roadmap. Nothing described as *planned* is implemented. Do not use KeyNexa to store real passwords until a release says otherwise.

## What KeyNexa will be

- An Android password manager first, with Windows and Linux clients later.
- Fully compatible with the KeePass **KDBX** format (3.1, 4.0 and 4.1), so your vault also opens in KeePassXC, KeePassDX and other KeePass clients.
- Local-first: your vault is a file you own. No accounts, no KeyNexa servers, no telemetry.
- Designed to live in a folder synchronized by [Syncthing](https://syncthing.net/), detecting external changes and merging conflicts instead of silently losing data.
- Bilingual interface: English (`en-US`) and Spanish (`es-ES`).

See the [roadmap](docs/ROADMAP.md) for what is planned in each phase.

## Documentation

| Document | Content |
| --- | --- |
| [Architecture](docs/ARCHITECTURE.md) | Modules, technology choices, multiplatform strategy |
| [Threat model](docs/THREAT_MODEL.md) | Assets, attackers, threats, mitigations, residual risks |
| [Dependencies and licenses](docs/DEPENDENCIES_AND_LICENSES.md) | Researched libraries, verified licenses, sources |
| [Development](docs/DEVELOPMENT.md) | Requirements and how to build (once code exists) |
| [Roadmap](docs/ROADMAP.md) | MVP, acceptance criteria and later phases |
| [Decisions](docs/decisions/README.md) | Architecture decision records (ADR) |
| [Contributing](CONTRIBUTING.md) | Workflow, bilingual documentation rules, versioning |
| [Security policy](SECURITY.md) | How to report vulnerabilities |
| [Changelog](CHANGELOG.md) | Changes per version |
| [To do](TODO.md) | Pending work |

## Requirements, installation and usage

Not available yet: there is no buildable application. [Development](docs/DEVELOPMENT.md) lists the planned toolchain and will contain verified build instructions once the project skeleton exists.

## Credits and license

**KeyNexa** is developed and maintained by **CatLinux**.
Copyright (C) 2026 CatLinux.

Licensed under the [GNU General Public License v3.0](LICENSE) (`GPL-3.0-only`): you may use, study, modify and share it, including commercially, but any distributed version, modified or not, must remain under GPL-3.0 and include its complete source code.

Under section 7 of the GPL, the [additional terms in NOTICE](NOTICE) also apply: copies and forks must keep this attribution and link to https://github.com/catlinux/KeyNexa, modified versions must be marked as such, and the KeyNexa name and logo are not licensed for forks.

### Third-party material

This repository does not yet contain third-party material. The libraries planned for the application, and their verified licenses, are listed in [Dependencies and licenses](docs/DEPENDENCIES_AND_LICENSES.md); they will be added to the table below when they are actually included.

| Component | Author | License | Link |
| --- | --- | --- | --- |

Full credits: [CREDITS.md](CREDITS.md).
