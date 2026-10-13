# ADR-0005: Reinforced kotpass

[English](0005-reinforced-kotpass.md) | [Español](../es/decisions/0005-reinforced-kotpass.md)

- **Status:** Accepted
- **Date:** 2026-10-13
- **Complements:** [ADR-0002](0002-kdbx-engine.md)

## Context

ADR-0002 chose kotpass. Its main weakness is maintenance: one contributor and no commits since 2025-09-28 (GitHub API, checked 2026-10-13). The alternatives were re-evaluated on the same date:

| | kotpass | KeePassJava2 | keepass-rs | kdbxweb |
| --- | --- | --- | --- | --- |
| Contributors | 1 | 4 (one author with 409 of ~420 commits) | 34 (2 main maintainers) | 9 |
| Commits since 2025-10-01 | 0 | 178 | 198 | 0 |
| Writing | Stable (author reports production use; not verified) | Stable, recent attachment fixes | Experimental, open attachment/history data-loss issues with fixes under review | — |
| Main code size | ~10,000 lines Kotlin (~2,300 are Bouncy Castle ports), 1 dependency | ~14,400 lines Java, many dependencies | ~13,600 lines Rust | JavaScript |

kdbxweb is inactive and would need a JavaScript engine on Android. keepass-rs has the strongest community and is developing database merge, but its writing is still experimental and it needs a Rust/UniFFI toolchain (UniFFI's MPL-2.0 is compatible with GPL-3.0, so licensing is no longer the obstacle).

## Decision

Keep kotpass and **reinforce it**, taking ownership of its maintenance:

1. **Vendored module.** kotpass source is imported into `third_party/kotpass/` as a Gradle module, pinned to upstream commit `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945` (0.13.0), with its MIT `LICENSE.txt`, the bundled Apache-2.0 and Bouncy Castle notices, and a `README` recording the upstream commit and every local change. Local changes stay under MIT so they can be offered upstream.
2. **Differential testing in CI.** Every vault written by KeyNexa's tests is re-read by `keepassxc-cli` (KeePassXC) and by keepass-rs, and the contents compared. These tools run only in CI and are not distributed with the app.
3. **Fuzzing** of the decoder with malformed and truncated files (Jazzer, Apache-2.0, in tests only).
4. **Fidelity fixes:** preserve unknown XML elements and attributes on save; keep the KDBX version on rewrite.
5. **Optional native Argon2** through kotpass's `BaseKdfProvider` if benchmarks on low-end devices justify it (argon2kt, MIT).
6. **Upstream first when possible:** fixes are proposed to kotpass; the vendored copy is the fallback.

## Consequences

- KeyNexa maintains about 10,000 lines of third-party Kotlin; security fixes in the ported Bouncy Castle code must be watched (bc-java releases).
- The `KdbxEngine` interface keeps the engine replaceable; keepass-rs is re-evaluated before 1.0 and whenever its KDBX writing stops being experimental.
- Credits: kotpass, okio and the notices bundled in kotpass are listed in CREDITS when the module is added (phase C5).
