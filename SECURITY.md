# Security policy

[English](SECURITY.md) | [Español](docs/es/SECURITY.md)

## Project status

KeyNexa is in its design phase and has **no released application**. It has not been audited by a third party. Do not store real passwords in pre-release builds.

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Report privately through GitHub: **Security → Report a vulnerability** in [catlinux/KeyNexa](https://github.com/catlinux/KeyNexa/security). Include affected version, steps to reproduce and impact. Never send real vaults or passwords; use a test vault.

You will receive an acknowledgement as soon as possible. Fixes are released with a CHANGELOG entry under *Security*, crediting the reporter if they wish.

## Supported versions

Only the latest published version receives security fixes until 1.0.0.

## Security design

See the [threat model](docs/THREAT_MODEL.md) for assets, threats, mitigations and residual risks, and the [architecture](docs/ARCHITECTURE.md) for the safe-save and merge design.
