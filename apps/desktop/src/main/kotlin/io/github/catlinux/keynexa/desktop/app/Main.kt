package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.app_name
import io.github.catlinux.keynexa.desktop.app.generated.resources.dialog_ok
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_open_existing
import io.github.catlinux.keynexa.desktop.app.generated.resources.instance_already_open
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.ui.theme.KeyNexaTheme
import io.github.catlinux.keynexa.vault.VaultLocation
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** The file the first instance locks, so a second one knows KeyNexa is already running. */
private const val INSTANCE_LOCK = "instance.lock"

private val WindowSize = DpSize(1100.dp, 750.dp)
private val AlreadyOpenWindowSize = DpSize(380.dp, 190.dp)
private val PanelPadding = 24.dp
private val PanelGap = 16.dp
private const val MIN_WINDOW_WIDTH = 800
private const val MIN_WINDOW_HEIGHT = 560

fun main() =
    application {
        val container = remember { DesktopContainer() }
        val lock = remember { InstanceLock(container.dirs.data.resolve(INSTANCE_LOCK)) }
        if (lock.acquired) {
            KeyNexaWindow(container = container, lock = lock)
        } else {
            AlreadyOpenWindow()
        }
    }

/** The main window: the home screen, and locking the vault when the window is closed. */
@Composable
private fun ApplicationScope.KeyNexaWindow(
    container: DesktopContainer,
    lock: InstanceLock,
) {
    var vaults by remember { mutableStateOf(container.knownVaults.all()) }
    val openTitle = stringResource(Res.string.home_open_existing)
    Window(
        onCloseRequest = {
            container.session.lock()
            lock.close()
            exitApplication()
        },
        state = rememberWindowState(size = WindowSize),
        title = stringResource(Res.string.app_name),
    ) {
        window.minimumSize = Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT)
        KeyNexaTheme(darkTheme = isSystemInDarkTheme()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                DesktopHomeScreen(
                    vaults = vaults,
                    onOpenExisting = {
                        chooseVaultFile(window, openTitle)?.let { file ->
                            container.knownVaults.add(
                                KnownVault(vaultNameOf(file.name), VaultLocation(file.absolutePath)),
                            )
                            vaults = container.knownVaults.all()
                        }
                    },
                    onRemove = { vault ->
                        container.knownVaults.remove(vault.location)
                        vaults = container.knownVaults.all()
                    },
                    onOpen = { },
                )
            }
        }
    }
}

/** The small window a second instance shows before leaving. */
@Composable
private fun ApplicationScope.AlreadyOpenWindow() {
    Window(
        onCloseRequest = { exitApplication() },
        state = rememberWindowState(size = AlreadyOpenWindowSize),
        title = stringResource(Res.string.app_name),
    ) {
        KeyNexaTheme(darkTheme = isSystemInDarkTheme()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(PanelPadding),
                    verticalArrangement = Arrangement.spacedBy(PanelGap, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.instance_already_open),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = { exitApplication() }) {
                        Text(text = stringResource(Res.string.dialog_ok))
                    }
                }
            }
        }
    }
}

/** Asks for a `.kdbx` file with the native dialog; null when the user cancels. */
private fun chooseVaultFile(
    parent: Frame,
    title: String,
): File? {
    val dialog = FileDialog(parent, title, FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.endsWith(VAULT_EXTENSION, ignoreCase = true) }
    dialog.file = "*$VAULT_EXTENSION"
    dialog.isVisible = true
    val directory = dialog.directory
    val name = dialog.file
    return if (directory != null && name != null) File(directory, name) else null
}
