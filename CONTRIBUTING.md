# Contributing

[English](CONTRIBUTING.md) | [Español](docs/es/CONTRIBUTING.md)

Thank you for your interest in KeyNexa. The project is in an early phase; please open an issue before starting large changes.

## Ground rules

- **No custom cryptography.** Use the KDBX engine and platform APIs.
- **Never commit secrets:** real vaults, passwords, key files, signing keys, `.env` files.
- **Never log secrets:** passwords, keys or decrypted content must not reach logs, crash output or temporary files.
- Keep security-sensitive code small and covered by tests.
- New dependencies need a justification and a license check ([process](docs/DEPENDENCIES_AND_LICENSES.md#process-for-adding-a-dependency)).
- Do not copy code, text, icons or visual identity from other password managers.

## License of contributions

KeyNexa is licensed under GPL-3.0-only with the additional terms in [NOTICE](NOTICE). By submitting a contribution you agree that it is your own work (or that you have the right to submit it) and that it is distributed under the same terms.

## Bilingual documentation

All documentation exists in **English** (primary) and **Spanish**. English files keep their usual place (`README.md`, `CHANGELOG.md`, `docs/ARCHITECTURE.md`…). Spanish translations mirror that structure under `docs/es/` (`docs/es/CHANGELOG.md`, `docs/es/ARCHITECTURE.md`, `docs/es/decisions/…`), except `README.es.md`, which stays in the root next to `README.md`.

1. Every document starts with the title and the language links, as relative paths to both versions, for example in `docs/ARCHITECTURE.md`: `[English](ARCHITECTURE.md) | [Español](es/ARCHITECTURE.md)`.
2. Any change to one language is applied to the other **in the same commit**. A pull request that changes only one language is incomplete.
3. Translations say the same thing; technical names, commands and identifiers stay in English.
4. Documentation describes the real state of the project and separates *implemented*, *planned* and *pending*.

## Versioning and changelog

- [Semantic Versioning](https://semver.org/); the current version is in [VERSION](VERSION). Before 1.0.0, incompatible changes bump the minor version.
- Every finished change bumps the version and adds an entry to [CHANGELOG](CHANGELOG.md) and [CHANGELOG.es](docs/es/CHANGELOG.md) ([Keep a Changelog](https://keepachangelog.com/) format) in the same commit.
- Pending work goes in [TODO](TODO.md); finished tasks are removed from it.

## Commits

- Small, coherent commits with descriptive messages (in Spanish, the maintainer's working language).
- CI must pass before merging (once CI exists).
