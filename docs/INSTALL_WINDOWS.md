# Installing KeyNexa on Windows

[English](INSTALL_WINDOWS.md) | [Español](es/INSTALL_WINDOWS.md)

The Windows client is a **preview**: it opens, creates and edits KDBX vaults like the Android app, but it is not signed and has not been through the final review yet. It needs **Windows 10 22H2 or Windows 11, 64-bit**. Nothing else has to be installed: the Java runtime is inside the package. Only Windows 11 has been tried so far.

## Download and check

Releases are on [GitHub Releases](https://github.com/catlinux/KeyNexa/releases). The Windows files are `KeyNexa-windows-x64-<version>.msi` (installer) and `KeyNexa-windows-x64-<version>.zip` (portable), each with a `.sha256` file. Check the download before running it:

```powershell
Get-FileHash -Algorithm SHA256 .\KeyNexa-windows-x64-<version>.msi
```

The hash printed must be the one in the `.sha256` file next to it.

The files are **not code-signed**, so Windows SmartScreen shows "Windows protected your PC" on the first run. Check the hash first and, if it matches, choose *More info* and then *Run anyway*.

## Installer (MSI)

Double-click the `.msi`, or run `msiexec /i KeyNexa-windows-x64-<version>.msi`. It installs **for your user only**: no administrator rights, no UAC prompt. The program goes to `%LOCALAPPDATA%\KeyNexa-app` and a *KeyNexa* shortcut appears in the Start menu.

To update, install the newer `.msi` over it: it replaces the installed version in the folder where it is, also when you chose another folder at install time. Installations made with 1.11.4 or earlier did not record their folder, so the first update from one of them asks for it once: choose the same folder.

To remove it, use *Settings > Apps* or `msiexec /x KeyNexa-windows-x64-<version>.msi`. Your vaults are never touched, and neither is the data folder described below.

## Portable ZIP

Extract the `.zip` anywhere and run `KeyNexa\KeyNexa.exe`. Nothing is installed and no shortcut is created; delete the folder to remove the program.

## Where KeyNexa keeps its data

Both forms keep their private data in `%LOCALAPPDATA%\KeyNexa`: the list of vaults, the settings, the encrypted safety copies and the save journal and, if you turn on cloud copies, the cloud credentials, sealed with Windows DPAPI for your user. Your vault files stay where you keep them. Uninstalling does not delete this folder: delete it by hand if you want no trace left.

## First steps

Choose *Open an existing vault* (a `.kdbx` file) or *Create a new vault*. KeyNexa does not open the network until you set up cloud copies in *Settings > Modes*; see [Cloud setup](CLOUD_SETUP.md) for the services it supports.
