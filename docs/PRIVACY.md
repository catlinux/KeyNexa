# Privacy

[English](PRIVACY.md) | [Español](es/PRIVACY.md)

KeyNexa is local-first: your vault is a file you own. The project runs no servers and collects no telemetry. This page describes exactly what each build does with your data.

## The `offline` build

- It does not request the `INTERNET` permission and never accesses the network.
- Nothing leaves your device. There is no account, no analytics and no crash reporting.

## The `cloud` build

- It only sends anything after you read and accept the consent screen.
- What it sends: **only an encrypted copy of your vault file** (`.kdbx`), exactly as it is on disk. The master password, the key file, the derived keys and the decrypted content never leave the device.
- Where it goes: **only to the cloud service you choose and configure** (for example an S3-compatible bucket). KeyNexa has no servers of its own.
- Why: to keep backup copies of the vault and, later, to synchronise between your devices.
- No statistics, no advertising identifiers and no third-party crash reporting are sent.

## What is stored on your device

- The cloud credentials you configure, encrypted with a non-exportable AES-256-GCM key from the Android Keystore, in app-private storage excluded from Android backup.
- A version number that records that you accepted the consent text. Nothing else about you is kept.

## How to delete it

- **Disconnect and delete** in Settings deletes the cloud credentials and the consent mark from this device. Copies already uploaded are not deleted by KeyNexa: delete them from the cloud service itself.
- Uninstalling KeyNexa removes all of its app-private data, including the encrypted credentials and the consent mark.

## Contact

Questions or requests about privacy can be raised in the project repository: <https://github.com/catlinux/KeyNexa>.
