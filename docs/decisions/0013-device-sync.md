# ADR-0013: Synchronisation between devices without Syncthing

[English](0013-device-sync.md) | [Español](../es/decisions/0013-device-sync.md)

- **Status:** Accepted
- **Date:** 2026-10-18
- **Extends:** [ADR-0004](0004-storage-sync-and-safe-save.md), [ADR-0007](0007-conflict-copies.md), [ADR-0009](0009-cloud-sync.md)

## Context

Today two devices share a vault through Syncthing-Fork, a separate app that is easy to forget to start and that KeyNexa cannot see. The owner wants KeyNexa to synchronise by itself, as one more mode of the same package (ADR-0012), with Syncthing no longer needed.

Syncthing synchronises arbitrary folders block by block. KeyNexa only needs to synchronise vaults, which are small, and it already has what Syncthing lacks: a merge by UUID and modification times that keeps history and never overwrites what it cannot merge ([ADR-0004](0004-storage-sync-and-safe-save.md)).

## Options

1. **Bundle Syncthing** (MPL-2.0) and drive it through its local API. Proven and with relays over the Internet, but it adds a large binary per platform, a separate process, a foreground service on Android and its own conflict files.
2. **An own protocol at vault level (this record)**: paired devices exchange the whole encrypted `.kdbx` and each one merges it with the existing engine. Over the local network; through the cloud (ADR-0009) for devices that never meet.
3. **Relays over the Internet of our own**: needs a server, rejected in ADR-0008.

## Decision

1. **Option 2.** Synchronisation exchanges whole encrypted vault files between paired devices; merging and saving are the existing `KeePassMerger` and safe-save pipeline. Devices that are not on the same network meet through the cloud (ADR-0009). There are no Internet relays.
2. **No cryptography of our own.** Each device has a long-term EC P-256 key pair and a self-signed X.509 certificate; its device id is the SHA-256 of the certificate. Connections use **TLS 1.3 only, with mutual authentication**, accepting only certificates of paired devices (pinned by fingerprint). The private key lives in the Android Keystore or, on Windows, encrypted with DPAPI.
3. **Pairing** with a QR code shown by the device that listens (normally the desktop): it holds that device's fingerprint, its address and a random single-use pairing token, valid for two minutes. The other device scans it (ZXing, Apache-2.0; no proprietary scanning library), connects pinning the scanned fingerprint and presents the token inside the TLS channel; the listening device then shows the new device's name and short fingerprint and the user confirms it there. Without a camera, the address and token can be typed and both screens show the same short fingerprint to compare. Removing a device from the list revokes it.
4. **Which vaults:** each vault gets a random synchronisation id stored in its own custom data (`KeyNexa.SyncId`), so every device recognises it whatever its file path; the same id serves ADR-0009. The user chooses which vaults are shared with which device. Nothing else is ever sent.
5. **One session** (version 1 of the protocol; framing and limits detailed in `core/peer`):
   1. Each side sends a hello: protocol version, and per shared vault its sync id, the fingerprint of its current file and the fingerprints it last sent to and received from that peer.
   2. Where nothing changed on either side since the last exchange, nothing is sent.
   3. Otherwise the side whose file the peer has not seen sends it, plus any pending copies it holds (point 6).
   4. An **unlocked** receiver decrypts the incoming file with the open vault's credentials and merges it. If the merge changes nothing in its vault, it writes nothing; otherwise it saves through the safe-save pipeline and sends the result back. A file it cannot decrypt (another master password, damaged) is kept as a pending copy and reported, as conflict copies are today (ADR-0007, including the flow for another password).
   5. Both sides record the fingerprints exchanged, so the next hello finds nothing new. Saving creates a fresh master seed, so equal content can have different fingerprints; the "merge changed nothing, write nothing" rule is what stops the devices from bouncing files forever.
6. **A locked device never replaces its vault file** (it cannot verify an incoming file without the key). It stores what it receives as a **pending copy** in its private data, at most one per peer and vault (a peer's newer file contains its older one), and offers its own file and those pending copies to the next peer that connects. Any unlocked device merges them, and the locked device merges its pending copies when it is unlocked, through the ledger of ADR-0007. So a desktop left running in the system tray relays changes between phones without ever holding the key, and its own file on disk catches up at the next unlock.
7. **Discovery:** the listening device announces itself on the local network with DNS-SD/mDNS (`_keynexa._tcp`, with an opaque instance name, never the device or user name); an address and port can also be entered by hand. Only devices with the synchronisation mode on listen, on a configurable port; Android does not listen in the first version, it connects while KeyNexa is in front (on open, after saving and with "Sync now").
8. **Limits:** maximum file size, time limits on every step, one session per peer at a time, and a delay after failed handshakes. A failure never blocks unlocking or saving and leaves the local vault untouched; the state is shown quietly ("synchronised with Desktop 2 min ago", "could not synchronise: …").
9. **Android:** only the `cloud` build synchronises, because Android requires the Internet permission for any socket, including on the local network; the `offline` build stays without it. The camera permission for the QR code is also requested only in the `cloud` build.

## Consequences

- Syncthing is no longer needed by KeyNexa users. It keeps working for those who still use it, because conflict copies are still merged.
- Devices on different networks synchronise only through a cloud service with tested conditional writes (ADR-0009); without one, they synchronise when they meet on the same network.
- On a locked desktop the vault file lags behind the other devices until it is unlocked; another KeePass program opening that file sees the older version.
- Threat model: a device on the local network can see that a KeyNexa device is listening and on which port, but cannot connect without a paired certificate. A stolen device certificate without the master password can send garbage, which is never decrypted, only stored as a pending copy and reported. A paired device that holds the master password can change the vault like any client; deletions still travel only as KDBX deleted objects, history is kept, and the private backups of the safe-save pipeline remain.
- The protocol is new code that handles network input: it needs JVM tests with two peers in memory (races, unknown peers, files of another vault, oversized or truncated input) and fuzzing of the message decoder before it is released.
