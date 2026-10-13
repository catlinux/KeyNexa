# ADR-0001: License

[English](0001-license.md) | [Español](../es/decisions/0001-license.md)

- **Status:** Accepted
- **Date:** 2026-10-13
- **Decided by:** CatLinux (author and copyright holder)

## Context

KeyNexa's owner wants public, auditable code, mandatory credits, and protection against closed redistribution. A password manager also benefits from being reviewable by anyone. All planned dependencies are permissive (MIT, Apache-2.0, Bouncy Castle License, OFL).

## Options

1. **WNCL-TP-1.0 / WNCL-CU-1.0** (owner's custom licenses): restrict commercial use or sale; not Open Source; not accepted by F-Droid.
2. **PolyForm Noncommercial 1.0.0**: forbids commercial use; does not require forks to publish source.
3. **GPL-3.0**: Open Source copyleft; commercial use allowed, but every distributed version must stay GPL-3.0 with complete source; section 7 allows attribution and trademark terms.
4. **AGPL-3.0**: like GPL-3.0, plus source obligations for network services (not relevant for an offline app).

No Open Source license can forbid commercial use (Open Source Definition, criterion 6).

## Decision

**GPL-3.0-only**, with additional terms under section 7 written in [NOTICE](../../NOTICE): attribution to CatLinux and a link to the repository (7(b)), modified versions marked as such (7(c)), and no license to the KeyNexa name or logo (7(e)).

## Consequences

- Anyone may use, modify and redistribute KeyNexa, also commercially, but must keep it GPL-3.0, publish complete source and keep the attribution.
- All dependencies must be GPL-3.0-compatible (see [Dependencies and licenses](../DEPENDENCIES_AND_LICENSES.md)). Apache-2.0 is compatible with GPL-3.0 but not with GPL-2.0-only.
- GPL code from other KeePass clients could legally be reused, but KeyNexa still avoids copying it: the project keeps an original codebase and visual identity.
- F-Droid publication becomes possible.
- External contributions are accepted under the same license (inbound = outbound, see [CONTRIBUTING](../../CONTRIBUTING.md)).
