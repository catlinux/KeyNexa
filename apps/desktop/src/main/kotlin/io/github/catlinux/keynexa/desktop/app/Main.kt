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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.app_name
import io.github.catlinux.keynexa.desktop.app.generated.resources.dialog_ok
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_password
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_user_name
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_open_existing
import io.github.catlinux.keynexa.desktop.app.generated.resources.instance_already_open
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.session.KnownVaults
import io.github.catlinux.keynexa.session.SessionState
import io.github.catlinux.keynexa.session.VaultSession
import io.github.catlinux.keynexa.ui.theme.KeyNexaTheme
import io.github.catlinux.keynexa.vault.NewVault
import io.github.catlinux.keynexa.vault.VaultLocation
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** The file the first instance locks, so a second one knows KeyNexa is already running. */
private const val INSTANCE_LOCK = "instance.lock"

/** How often an open vault looks for a version written elsewhere, while the window has the focus. */
private const val REFRESH_MILLIS = 5_000L

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

/** The main window: the home screen, the unlock screen of the vault the user picked, the open vault, and locking. */
@Composable
@Suppress("LongMethod")
private fun ApplicationScope.KeyNexaWindow(
    container: DesktopContainer,
    lock: InstanceLock,
) {
    val session = container.session
    val state by session.state.collectAsState()
    var selected by remember { mutableStateOf<KnownVault?>(null) }
    val view = remember { DesktopVaultState(container.clipboard, session) }
    val openState = state as? SessionState.Open
    val openVault = openState?.vault
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused, openVault) {
        if (!windowFocused || openState == null) return@LaunchedEffect
        session.refreshIfChanged()
        while (true) {
            delay(REFRESH_MILLIS)
            session.refreshIfChanged()
        }
    }
    LaunchedEffect(openState?.lastExternalChangeMillis) {
        if (openState?.lastExternalChangeMillis != null) view.updatedElsewhere = true
    }
    LaunchedEffect(openVault) {
        val content = (state as? SessionState.Open)?.current
        if (content != null) view.open(content) else view.reset()
    }
    val userNameLabel = stringResource(Res.string.entry_detail_user_name)
    val passwordLabel = stringResource(Res.string.entry_detail_password)
    val lockVault: () -> Unit = {
        selected = null
        container.clipboard.clearNow()
        session.lock()
    }
    Window(
        onCloseRequest = {
            container.clipboard.clearNow()
            container.clipboard.close()
            session.lock()
            lock.close()
            exitApplication()
        },
        onKeyEvent = { event ->
            val open = state as? SessionState.Open
            val shortcut = shortcutOf(event)
            if (open != null && shortcut != null) {
                runShortcut(shortcut, view, open.current, userNameLabel, passwordLabel, lockVault)
                true
            } else {
                false
            }
        },
        state = rememberWindowState(size = WindowSize),
        title = stringResource(Res.string.app_name),
    ) {
        window.minimumSize = Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT)
        KeyNexaTheme(darkTheme = isSystemInDarkTheme()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                KeyNexaScreen(
                    session = session,
                    state = state,
                    knownVaults = container.knownVaults,
                    selected = selected,
                    onSelect = { selected = it },
                    onLock = lockVault,
                    view = view,
                    parent = window,
                )
            }
        }
    }
}

/** The screen for the current state: the open vault, the unlock screen of the picked vault, or the home list. */
@Suppress("LongParameterList")
@Composable
private fun KeyNexaScreen(
    session: VaultSession,
    state: SessionState,
    knownVaults: KnownVaults,
    selected: KnownVault?,
    onSelect: (KnownVault?) -> Unit,
    onLock: () -> Unit,
    view: DesktopVaultState,
    parent: Frame,
) {
    val unlockVault =
        when (state) {
            is SessionState.Unlocking -> state.vault
            is SessionState.Failed -> state.vault
            else -> selected
        }
    when {
        state is SessionState.Open ->
            DesktopVaultScreen(
                state = state,
                view = view,
                onLock = onLock,
                onRetrySave = session::retrySave,
                onRestoreUnsaved = session::restoreUnsaved,
                onDiscardUnsaved = session::discardUnsaved,
                onDeleteConflictCopies = session::deleteConflictCopies,
                onMergeOtherPassword = session::mergeOtherPasswordCopies,
                onDismissConflicts = session::dismissConflicts,
                parent = parent,
            )
        unlockVault != null ->
            DesktopUnlockScreen(
                parent = parent,
                vault = unlockVault,
                state = state,
                onUnlock = { credentials ->
                    onSelect(unlockVault)
                    session.unlock(unlockVault, credentials)
                },
                onEdited = {
                    onSelect(unlockVault)
                    session.dismissFailure()
                },
                onBack = {
                    onSelect(null)
                    session.dismissFailure()
                },
            )
        else -> KeyNexaHome(session = session, knownVaults = knownVaults, onSelect = onSelect, parent = parent)
    }
}

/** The list of known vaults and the form for a new one: the two things the user can start from the home. */
@Composable
private fun KeyNexaHome(
    session: VaultSession,
    knownVaults: KnownVaults,
    onSelect: (KnownVault) -> Unit,
    parent: Frame,
) {
    var vaults by remember { mutableStateOf(knownVaults.all()) }
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        DesktopCreateVaultScreen(
            parent = parent,
            onBack = { creating = false },
            onCreate = { file, name, credentials ->
                val vault = KnownVault(name, VaultLocation(file.absolutePath))
                knownVaults.add(vault)
                vaults = knownVaults.all()
                onSelect(vault)
                session.create(vault, NewVault().create(name), credentials)
            },
        )
    } else {
        val openTitle = stringResource(Res.string.home_open_existing)
        DesktopHomeScreen(
            vaults = vaults,
            onOpenExisting = {
                chooseVaultFile(parent, openTitle)?.let { file ->
                    val vault = KnownVault(vaultNameOf(file.name), VaultLocation(file.absolutePath))
                    knownVaults.add(vault)
                    vaults = knownVaults.all()
                    onSelect(vault)
                }
            },
            onCreateNew = { creating = true },
            onRemove = { vault ->
                knownVaults.remove(vault.location)
                vaults = knownVaults.all()
            },
            onOpen = onSelect,
        )
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
