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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import io.github.catlinux.keynexa.desktop.OpenResult
import io.github.catlinux.keynexa.desktop.Theme
import io.github.catlinux.keynexa.desktop.UpdateCheck
import io.github.catlinux.keynexa.desktop.UpdateChoice
import io.github.catlinux.keynexa.desktop.UpdateDownload
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
import io.github.catlinux.keynexa.session.UnlockError
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
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/** The file the first instance locks, so a second one knows KeyNexa is already running. */
private const val INSTANCE_LOCK = "instance.lock"

/** How often an open vault looks for a version written elsewhere, while the window has the focus. */
private const val REFRESH_MILLIS = 5_000L

/** How often the idle time is looked at while a vault is open. */
private const val IDLE_CHECK_MILLIS = 15_000L

/** The least time between two synchronisations asked for by regaining the window's focus (ADR-0009 §4). */
private const val SYNC_FOCUS_MIN_MILLIS = 60_000L

/** How often the top bar's synchronisation state line is refreshed. */
private const val SYNC_TICK_MILLIS = 30_000L

/** How often the unlock screen checks whether the quick unlock has expired. */
private const val QUICK_UNLOCK_CHECK_MILLIS = 60_000L

private val WindowSize = DpSize(1360.dp, 780.dp)
private val AlreadyOpenWindowSize = DpSize(380.dp, 190.dp)
private val PanelPadding = 24.dp
private val PanelGap = 16.dp
private const val MIN_WINDOW_WIDTH = 800
private const val MIN_WINDOW_HEIGHT = 560
private const val WINDOW_ICON = "keynexa-icon.png"

/** The KeyNexa mark the title bar and the taskbar show, instead of the default Java one. */
private val windowIcon: BitmapPainter by lazy {
    val stream =
        checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(WINDOW_ICON)) { WINDOW_ICON }
    BitmapPainter(stream.use { ImageIO.read(it) }.toComposeImageBitmap())
}

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
    val syncStatus by container.cloudSync.status.collectAsState()
    val settings = container.settings
    val idle = remember { IdleTimer() }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<KnownVault?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var cloudOpen by remember { mutableStateOf(false) }
    var cloudPickerOpen by remember { mutableStateOf(false) }
    var cloudConfigured by remember { mutableStateOf(false) }
    var cloudSyncState by remember { mutableStateOf(CloudSyncCardState()) }
    var reminderState by remember { mutableStateOf<ReminderState?>(null) }
    var reminderNow by remember { mutableStateOf(Instant.now()) }
    var syncNowAt by remember { mutableStateOf(Instant.now()) }
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
    var quickUnlockSupported by remember { mutableStateOf(false) }
    var quickArmed by remember { mutableStateOf(false) }
    var quickBusy by remember { mutableStateOf(false) }
    var quickNotice by remember { mutableStateOf<QuickNotice?>(null) }
    var unlockSource by remember { mutableStateOf(UnlockSource.None) }
    val quickUnlock = container.quickUnlock
    val quickEnabled = quickUnlockSupported && settings.quickUnlock
    val openState = state as? SessionState.Open
    val openVault = openState?.vault
    val cloudSource =
        openState?.let { open ->
            CloudBackupSource(open.vault) { container.readVault(open.vault.location) }
        }
    val syncEnabled = cloudSyncState.enabled
    val canBringFromCloud = cloudConfigured && cloudSyncState.enabled
    val syncIndicator = syncIndicator(syncStatus, syncNowAt)
    val syncNow: () -> Unit = {
        val vault = openVault
        if (syncEnabled && vault != null) container.cloudSync.trigger(vault, container.syncLocal())
    }
    // While synchronisation is on and a vault is open, the state line keeps its "ago" up to date.
    LaunchedEffect(openVault, syncEnabled) {
        if (openVault == null || !syncEnabled) return@LaunchedEffect
        while (true) {
            delay(SYNC_TICK_MILLIS)
            syncNowAt = Instant.now()
        }
    }
    // The window info only exists inside the Window content, so the focus is read there and kept here.
    var windowFocused by remember { mutableStateOf(true) }
    // Regaining the focus asks for a synchronisation at most once a minute; the gate holds that rule.
    val syncGate = remember { SyncTriggerGate(SYNC_FOCUS_MIN_MILLIS) }
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
    // The synchronisation card reads the setup and the open vault; refreshed when the dialog closes or a round ends.
    LaunchedEffect(cloudOpen, openVault, syncStatus) {
        cloudSyncState =
            withContext(Dispatchers.IO) {
                cloudSyncCardState(container.cloud, container.cloudSync, openVault?.let { container.syncLocal() })
            }
    }
    LaunchedEffect(windowFocused, openVault) {
        if (!windowFocused || openState == null) return@LaunchedEffect
        session.refreshIfChanged()
        while (true) {
            delay(REFRESH_MILLIS)
            session.refreshIfChanged()
        }
    }
    // Unlocking always asks for a synchronisation round, whatever the focus rule says.
    LaunchedEffect(openVault) {
        if (openVault != null) {
            syncGate.mark()
            container.cloudSync.trigger(openVault, container.syncLocal())
        }
    }
    // Regaining the focus asks for one too, at most once a minute; the unlock above already asked for the first.
    LaunchedEffect(windowFocused) {
        val vault = openVault
        if (windowFocused && vault != null && syncGate.due()) {
            container.cloudSync.trigger(vault, container.syncLocal())
        }
    }
    LaunchedEffect(openState?.lastExternalChangeMillis) {
        if (openState?.lastExternalChangeMillis != null) view.updatedElsewhere = true
    }
    LaunchedEffect(openVault) {
        val content = (state as? SessionState.Open)?.current
        if (content != null) view.open(content) else view.reset()
    }
    val unlockTarget =
        when (val current = state) {
            is SessionState.Unlocking -> current.vault
            is SessionState.Failed -> current.vault
            else -> selected
        }
    // Hello is asked once, at start-up and off the interface thread; without it the option is never shown.
    LaunchedEffect(Unit) { quickUnlockSupported = withContext(Dispatchers.IO) { container.hello.isSupported() } }
    // Turning the option off, or losing Hello, forgets what was armed.
    LaunchedEffect(quickEnabled) {
        if (!quickEnabled) {
            quickArmed = false
            withContext(Dispatchers.IO) { quickUnlock.disarm() }
        }
    }
    // The unlock screen offers Hello only while the arming is valid; the check also catches the time limit.
    LaunchedEffect(unlockTarget, quickEnabled) {
        val target = unlockTarget
        while (true) {
            quickArmed =
                if (target != null && quickEnabled) {
                    withContext(Dispatchers.IO) { quickUnlock.isArmedFor(target.location.value) }
                } else {
                    false
                }
            delay(QUICK_UNLOCK_CHECK_MILLIS)
        }
    }
    // Only an unlock that came from the master password arms the quick unlock, so the time limit is never extended.
    LaunchedEffect(openVault) {
        val vault = openVault ?: return@LaunchedEffect
        val source = unlockSource
        unlockSource = UnlockSource.None
        if (source != UnlockSource.Password) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val components = if (quickEnabled) session.hashedCredentials() else null
            if (components == null) {
                quickUnlock.disarm()
            } else {
                try {
                    quickUnlock.arm(vault.location.value, components, settings.quickUnlockHours)
                } finally {
                    components.wipe()
                }
            }
        }
    }
    // Held components that no longer open the vault mean the password changed elsewhere.
    LaunchedEffect(state) {
        val failed = state as? SessionState.Failed ?: return@LaunchedEffect
        if (unlockSource != UnlockSource.Hello) return@LaunchedEffect
        unlockSource = UnlockSource.None
        if (failed.error == UnlockError.WrongCredentials) {
            quickArmed = false
            quickNotice = QuickNotice.PasswordChanged
            withContext(Dispatchers.IO) { quickUnlock.disarm() }
        }
    }
    val unlockWithHello: (KnownVault) -> Unit = { vault ->
        if (!quickBusy) {
            quickBusy = true
            quickNotice = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { quickUnlock.open(vault.location.value) }
                quickBusy = false
                when (result) {
                    is OpenResult.Opened -> {
                        unlockSource = UnlockSource.Hello
                        selected = vault
                        session.unlock(vault, result.credentials)
                    }
                    OpenResult.Cancelled -> Unit
                    is OpenResult.Failed -> {
                        quickArmed = false
                        quickNotice = QuickNotice.Unavailable
                    }
                }
            }
        }
    }
    val quickUnlockUi =
        QuickUnlockUi(
            available = quickArmed,
            notice = quickNotice,
            onQuickUnlock = unlockWithHello,
            onPasswordUnlock = {
                quickNotice = null
                unlockSource = UnlockSource.Password
            },
        )
    // Updates (ADR-0015): looked for in the background when the user said yes, offered only while no vault is open.
    var updatePhase by remember { mutableStateOf<UpdatePhase>(UpdatePhase.Idle) }
    var updateOfferHidden by remember { mutableStateOf(false) }
    val checkForUpdates: (Boolean) -> Unit = { asked ->
        scope.launch {
            if (asked) updatePhase = UpdatePhase.Checking
            val found = withContext(Dispatchers.IO) { container.updates.check(force = asked) }
            updatePhase =
                when (found) {
                    is UpdateCheck.Available -> UpdatePhase.Offered(found)
                    UpdateCheck.UpToDate -> if (asked) UpdatePhase.UpToDate else UpdatePhase.Idle
                    is UpdateCheck.Failed -> if (asked) UpdatePhase.Failed(found.reason) else UpdatePhase.Idle
                    UpdateCheck.NotDue -> UpdatePhase.Idle
                }
        }
    }
    LaunchedEffect(settings.updateChecks) {
        if (settings.updateChecks == UpdateChoice.On) checkForUpdates(false)
    }
    val closeApp: () -> Unit = {
        container.systemEvents.close()
        // Off the interface thread: it waits if a Hello prompt is open, and leaving wipes memory anyway.
        Thread { container.quickUnlock.disarm() }.apply { isDaemon = true }.start()
        container.clipboard.clearNow()
        container.clipboard.close()
        session.lock()
        lock.close()
        exitApplication()
    }
    val installUpdate: (UpdateCheck.Available) -> Unit = { offer ->
        scope.launch {
            updatePhase = UpdatePhase.Downloading
            val started =
                withContext(Dispatchers.IO) {
                    val downloaded = container.updates.download(offer)
                    downloaded is UpdateDownload.Ready &&
                        container.updates.verifyAgain(downloaded.path, offer.msi) &&
                        launchInstaller(downloaded.path)
                }
            if (started) {
                closeApp()
            } else {
                updatePhase = UpdatePhase.Offered(offer, failed = true)
            }
        }
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
        onCloseRequest = closeApp,
        onKeyEvent = { event ->
            idle.restart()
            val open = state as? SessionState.Open
            val shortcut = shortcutOf(event)
            if (open != null && shortcut != null) {
                runShortcut(shortcut, view, open.current, userNameLabel, passwordLabel, lockNow, syncNow)
                true
            } else {
                false
            }
        },
        state = rememberWindowState(size = WindowSize),
        title = stringResource(Res.string.app_name) + " " + AppVersion.current,
        icon = windowIcon,
    ) {
        window.minimumSize = Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT)
        LaunchedEffect(window) { container.windowHandle = container.hello.handleOf(window) }
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
            // Material's default dialog surface is lilac; the desktop dialogs use KeyNexa's own surface colour.
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(surfaceContainerHigh = MaterialTheme.colorScheme.surface),
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
                            cloudSyncState = cloudSyncState,
                            quickUnlockSupported = quickUnlockSupported,
                            updatePhase = updatePhase,
                            onCheckUpdates = { checkForUpdates(true) },
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
                            syncEnabled = syncEnabled,
                            syncIndicator = syncIndicator,
                            onSyncNow = syncNow,
                            onBringFromCloud = if (canBringFromCloud) ({ cloudPickerOpen = true }) else null,
                            quickUnlock = quickUnlockUi,
                        )
                    }
                }
                val noVaultOpen = state !is SessionState.Open && state !is SessionState.Unlocking
                if (noVaultOpen && !settingsOpen && settings.updateChecks == UpdateChoice.Ask) {
                    UpdateQuestionDialog(
                        onYes = { container.updateSettings { it.copy(updateChecks = UpdateChoice.On) } },
                        onNo = { container.updateSettings { it.copy(updateChecks = UpdateChoice.Off) } },
                    )
                }
                if (noVaultOpen && !updateOfferHidden && settings.updateChecks == UpdateChoice.On) {
                    UpdateOfferDialog(
                        phase = updatePhase,
                        onInstall = installUpdate,
                        onLater = { updateOfferHidden = true },
                    )
                }
                if (cloudOpen) {
                    DesktopCloudDialog(
                        cloud = container.cloud,
                        cloudSync = container.cloudSync,
                        source = cloudSource,
                        onDismiss = { cloudOpen = false },
                    )
                }
                if (cloudPickerOpen) {
                    CloudVaultPickerDialog(
                        cloud = container.cloud,
                        parent = window,
                        onDismiss = { cloudPickerOpen = false },
                        onPicked = { vault ->
                            container.knownVaults.add(vault)
                            selected = vault
                            cloudPickerOpen = false
                        },
                    )
                }
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
    syncEnabled: Boolean,
    syncIndicator: SyncIndicator,
    onSyncNow: () -> Unit,
    onBringFromCloud: (() -> Unit)?,
    quickUnlock: QuickUnlockUi,
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
                syncEnabled = syncEnabled,
                syncIndicator = syncIndicator,
                onSyncNow = onSyncNow,
                parent = parent,
            )
        unlockVault != null ->
            DesktopUnlockScreen(
                parent = parent,
                vault = unlockVault,
                state = state,
                useKeypad = useKeypad,
                quickUnlock = quickUnlock,
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
                onBringFromCloud = onBringFromCloud,
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
    onBringFromCloud: (() -> Unit)?,
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
            onBringFromCloud = onBringFromCloud,
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
        icon = windowIcon,
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
