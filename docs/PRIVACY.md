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

## Looking for updates (Windows and the Android `cloud` build)

- It is off until you answer a one-time question (and it can be turned off in Settings). The `offline` Android build never does it: it has no network permission.
- What it does, at most once a day: it reads a small signed text file (`keynexa-update.txt` and its signature) from the project's repository on GitHub. If there is a newer version and you agree, it downloads the installer (the MSI on Windows, the APK on Android) from the project's GitHub releases. On Android the system installer always asks you to confirm, and the app also needs the system setting that lets it install apps (the `REQUEST_INSTALL_PACKAGES` permission, only in the `cloud` build).
- What GitHub sees: your IP address, the time and the app version (`KeyNexa/<version>` as the user agent). Nothing else is sent: no vault, no identifier, no statistics.
- What it checks before installing: the signature of the text file against the project's update key built into the app, then the size and SHA-256 of the installer. On Android it also checks that the downloaded APK is KeyNexa itself, with the version the signed file promised and the same signing certificate as the installed app. Anything that does not match is ignored, and it never installs an older version.

## What is stored on your device

- The cloud credentials you configure, encrypted with a non-exportable AES-256-GCM key from the Android Keystore, in app-private storage excluded from Android backup.
- A version number that records that you accepted the consent text. Nothing else about you is kept.

## How to delete it

- **Disconnect and delete** in Settings deletes the cloud credentials and the consent mark from this device. Copies already uploaded are not deleted by KeyNexa: delete them from the cloud service itself.
- Uninstalling KeyNexa removes all of its app-private data, including the encrypted credentials and the consent mark.

## Contact

Questions or requests about privacy can be raised in the project repository: <https://github.com/catlinux/KeyNexa>.
