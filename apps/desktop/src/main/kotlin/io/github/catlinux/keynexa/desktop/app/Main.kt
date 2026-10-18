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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.catlinux.keynexa.desktop.Theme
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.app_name
import io.github.catlinux.keynexa.desktop.app.generated.resources.dialog_ok
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_password
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_user_name
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_open_existing
import io.github.catlinux.keynexa.desktop.app.generated.resources.instance_already_open
import io.github.catlinux.keynexa.session.CloudBackupSource
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.session.KnownVaults
import io.github.catlinux.keynexa.session.SessionState
import io.github.catlinux.keynexa.session.VaultSession
import io.github.catlinux.keynexa.ui.theme.KeyNexaTheme
import io.github.catlinux.keynexa.vault.NewVault
import io.github.catlinux.keynexa.vault.VaultLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.Instant
import javax.swing.SwingUtilities

/** The file the first instance locks, so a second one knows KeyNexa is already running. */
private const val INSTANCE_LOCK = "instance.lock"

/** How often an open vault looks for a version written elsewhere, while the window has the focus. */
private const val REFRESH_MILLIS = 5_000L

/** How often the idle time is looked at while a vault is open. */
private const val IDLE_CHECK_MILLIS = 15_000L

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
@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun ApplicationScope.KeyNexaWindow(
    container: DesktopContainer,
    lock: InstanceLock,
) {
    val session = container.session
    val state by session.state.collectAsState()
    val settings = container.settings
    val idle = remember { IdleTimer() }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<KnownVault?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var cloudOpen by remember { mutableStateOf(false) }
    var cloudConfigured by remember { mutableStateOf(false) }
    var reminderState by remember { mutableStateOf<ReminderState?>(null) }
    var reminderNow by remember { mutableStateOf(Instant.now()) }
    val reminder: @Composable () -> Unit = {
        val shown = reminderState
        if (shown != null && reminderVisible(shown, reminderNow)) {
            DesktopCloudReminder(
                onConfigure = { cloudOpen = true },
                onLater = {
                    val until = snoozeUntil(Instant.now())
                    reminderState = shown.copy(snoozedUntil = until)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            container.cloud.stores.reminder
                                .snooze(until)
                        }
                    }
                },
            )
        }
    }
    val view = remember { DesktopVaultState(container.clipboard, session) }
    val openState = state as? SessionState.Open
    val openVault = openState?.vault
    val cloudSource =
        openState?.let { open ->
            CloudBackupSource(open.vault) { container.readVault(open.vault.location) }
        }
    // The window info only exists inside the Window content, so the focus is read there and kept here.
    var windowFocused by remember { mutableStateOf(true) }
    // The cloud is configured when its credentials load and its consent is accepted; refreshed when the dialog closes.
    LaunchedEffect(cloudOpen) {
        val read =
            withContext(Dispatchers.IO) {
                val stores = container.cloud.stores
                val configured = stores.consent.accepted() && stores.credentials.load() != null
                configured to reminderState(container.cloud)
            }
        cloudConfigured = read.first
        reminderNow = Instant.now()
        reminderState = read.second
    }
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
    val lockNow: () -> Unit = {
        selected = null
        settingsOpen = false
        container.clipboard.clearNow()
        session.lock()
    }
    Window(
        onCloseRequest = {
            container.systemEvents.close()
            container.clipboard.clearNow()
            container.clipboard.close()
            session.lock()
            lock.close()
            exitApplication()
        },
        onKeyEvent = { event ->
            idle.restart()
            val open = state as? SessionState.Open
            val shortcut = shortcutOf(event)
            if (open != null && shortcut != null) {
                runShortcut(shortcut, view, open.current, userNameLabel, passwordLabel, lockNow)
                true
            } else {
                false
            }
        },
        state = rememberWindowState(size = WindowSize),
        title = stringResource(Res.string.app_name),
    ) {
        window.minimumSize = Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT)
        val windowInfo = LocalWindowInfo.current
        LaunchedEffect(windowInfo) {
            snapshotFlow { windowInfo.isWindowFocused }.collect { windowFocused = it }
        }
        LaunchedEffect(settings.allowScreenshots) {
            container.captureGuard.apply(window, settings.allowScreenshots)
        }
        LaunchedEffect(Unit) {
            container.systemEvents.listen(
                onSessionLocked = { if (container.settings.lockOnWindowsLock) SwingUtilities.invokeLater(lockNow) },
                onSuspend = { if (container.settings.lockOnSuspend) SwingUtilities.invokeLater(lockNow) },
            )
        }
        LaunchedEffect(Unit) {
            Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
                lockNow()
                System.err.println("Uncaught in ${thread.name}: ${failure.javaClass.name}")
                failure.stackTrace.forEach { System.err.println("  at $it") }
            }
        }
        LaunchedEffect(openVault, settings.lockAfterIdleMinutes) {
            if (openVault == null || settings.lockAfterIdleMinutes <= 0) return@LaunchedEffect
            while (true) {
                delay(IDLE_CHECK_MILLIS)
                if (idle.isDue(settings.lockAfterIdleMinutes)) lockNow()
            }
        }
        KeyNexaTheme(
            darkTheme =
                when (settings.theme) {
                    Theme.System -> isSystemInDarkTheme()
                    Theme.Light -> false
                    Theme.Dark -> true
                },
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onPointerEvent(PointerEventType.Move) { idle.restart() }
                        .onPointerEvent(PointerEventType.Press) { idle.restart() }
                        .onPointerEvent(PointerEventType.Scroll) { idle.restart() }
                        .onPreviewKeyEvent {
                            idle.restart()
                            false
                        },
                color = MaterialTheme.colorScheme.background,
            ) {
                if (settingsOpen) {
                    DesktopSettingsScreen(
                        settings = settings,
                        onUpdate = container::updateSettings,
                        onBack = { settingsOpen = false },
                        cloudConfigured = cloudConfigured,
                        onOpenCloud = { cloudOpen = true },
                    )
                } else {
                    KeyNexaScreen(
                        session = session,
                        state = state,
                        knownVaults = container.knownVaults,
                        selected = selected,
                        onSelect = { selected = it },
                        onLock = lockNow,
                        onSettings = { settingsOpen = true },
                        reminder = reminder,
                        view = view,
                        useKeypad = settings.useKeypad,
                        parent = window,
                    )
                }
            }
            if (cloudOpen) {
                DesktopCloudDialog(
                    cloud = container.cloud,
                    source = cloudSource,
                    onDismiss = { cloudOpen = false },
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
    onSettings: () -> Unit,
    reminder: (@Composable () -> Unit)?,
    view: DesktopVaultState,
    useKeypad: Boolean,
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
                onSettings = onSettings,
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
                useKeypad = useKeypad,
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
        else ->
            KeyNexaHome(
                session = session,
                knownVaults = knownVaults,
                onSelect = onSelect,
                onSettings = onSettings,
                reminder = reminder,
                useKeypad = useKeypad,
                parent = parent,
            )
    }
}

/** The list of known vaults and the form for a new one: the two things the user can start from the home. */
@Suppress("LongParameterList")
@Composable
private fun KeyNexaHome(
    session: VaultSession,
    knownVaults: KnownVaults,
    onSelect: (KnownVault) -> Unit,
    onSettings: () -> Unit,
    reminder: (@Composable () -> Unit)?,
    useKeypad: Boolean,
    parent: Frame,
) {
    var vaults by remember { mutableStateOf(knownVaults.all()) }
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        DesktopCreateVaultScreen(
            parent = parent,
            useKeypad = useKeypad,
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
            onSettings = onSettings,
            reminder = reminder,
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
