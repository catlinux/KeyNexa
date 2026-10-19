package io.github.catlinux.keynexa.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import io.github.catlinux.keynexa.app.cloud.CloudSyncHook
import io.github.catlinux.keynexa.app.cloud.SyncTriggerGate
import io.github.catlinux.keynexa.app.nav.Screen
import io.github.catlinux.keynexa.app.security.BiometricProblem
import io.github.catlinux.keynexa.app.security.BiometricTexts
import io.github.catlinux.keynexa.app.security.ThirdPartyInput
import io.github.catlinux.keynexa.app.ui.home.CreateVaultScreen
import io.github.catlinux.keynexa.app.ui.home.HomeNoticeDialog
import io.github.catlinux.keynexa.app.ui.home.HomeScreen
import io.github.catlinux.keynexa.app.ui.home.PickVaultDialog
import io.github.catlinux.keynexa.app.ui.settings.SettingsScreen
import io.github.catlinux.keynexa.app.ui.unlock.BiometricOfferDialog
import io.github.catlinux.keynexa.app.ui.unlock.BiometricUi
import io.github.catlinux.keynexa.app.ui.unlock.UnlockScreen
import io.github.catlinux.keynexa.platform.SafVaultAccess
import io.github.catlinux.keynexa.session.CloudBackupSource
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.session.SessionState
import io.github.catlinux.keynexa.session.UnlockError
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.NewVault
import kotlinx.coroutines.delay

private const val VAULT_EXTENSION = ".kdbx"
private const val CONFLICT_MARKER = ".sync-conflict-"
private const val REFRESH_MILLIS = 5_000L
private const val SYNC_TRIGGER_MIN_MILLIS = 60_000L

/**
 * What the user does with vault files: adding, creating, reconnecting and removing. Vaults are always reached
 * through a granted folder, because Android revokes the grant on a single file when a sync tool replaces it.
 * Holds the list of known vaults and, while the system folder picker is open, the vault being created (name and
 * credentials) or reconnected.
 */
@Suppress("TooManyFunctions")
private class VaultFileActions(
    private val container: AppContainer,
) {
    var vaults by mutableStateOf(container.knownVaults.all())
        private set

    /** Vault files found in the chosen folder, when the user has to pick one. */
    var choices by mutableStateOf<List<SafVaultAccess.VaultFile>?>(null)
        private set

    /** A message for the user, as a string resource. */
    var notice by mutableStateOf<Int?>(null)
        private set

    private var creating: Pair<String, Credentials>? = null
    private var reconnecting: KnownVault? = null

    /** True while the folder picker is open to reconnect the vault that is already open, without closing it. */
    private var reconnectingOpenVault = false

    fun beginCreate(
        name: String,
        credentials: Credentials,
    ) {
        creating = name to credentials
    }

    fun beginReconnect(vault: KnownVault) {
        reconnecting = vault
    }

    fun beginReconnectOpenVault() {
        reconnectingOpenVault = true
    }

    fun dismissNotice() {
        notice = null
    }

    fun dismissChoices() {
        choices = null
        reconnecting = null
        reconnectingOpenVault = false
    }

    /** The user chose a folder to open a vault from (or to reconnect one when [beginReconnect] was called). */
    fun folderChosenToOpen(tree: Uri?) {
        if (tree == null) {
            reconnecting = null
            reconnectingOpenVault = false
            return
        }
        grant(tree)
        // A Syncthing conflict copy is not a vault of its own: it is merged into its vault when that is opened.
        val files = container.access.listVaultFiles(tree).filterNot { CONFLICT_MARKER in it.name }
        when {
            files.isEmpty() -> {
                reconnecting = null
                reconnectingOpenVault = false
                notice = R.string.home_no_vaults_in_folder
            }
            files.size == 1 -> open(files.first())
            else -> choices = files
        }
    }

    /** The user picked one of several vault files in the folder. */
    fun open(file: SafVaultAccess.VaultFile) {
        choices = null
        if (reconnectingOpenVault) {
            reconnectingOpenVault = false
            reconnectOpenVault(file)
            return
        }
        val previous = reconnecting
        reconnecting = null
        if (previous != null) {
            container.knownVaults.remove(previous.location)
            container.navigator.back()
        }
        val vault = KnownVault(previous?.name ?: file.name.removeSuffix(VAULT_EXTENSION), file.location)
        add(vault)
        container.navigator.push(Screen.Unlock(vault))
    }

    // The open vault keeps its session, edits and credentials: only where it is stored changes.
    private fun reconnectOpenVault(file: SafVaultAccess.VaultFile) {
        val current = (container.session.state.value as? SessionState.Open)?.vault ?: return
        container.knownVaults.remove(current.location)
        add(current.copy(location = file.location))
        container.session.relocate(file.location)
    }

    /** The user chose the folder where the new vault file goes; null when they cancelled. */
    fun folderChosenToCreate(tree: Uri?) {
        val (name, credentials) = creating ?: return
        creating = null
        if (tree != null) grant(tree)
        val location = tree?.let { container.access.createInFolder(it, name + VAULT_EXTENSION) }
        if (location == null) {
            credentials.wipe()
            if (tree != null) notice = R.string.home_cannot_create_in_folder
            return
        }
        val vault = KnownVault(name, location)
        add(vault)
        container.navigator.back()
        container.navigator.push(Screen.Unlock(vault))
        container.session.create(vault, NewVault().create(name), credentials)
    }

    fun remove(vault: KnownVault) {
        container.knownVaults.remove(vault.location)
        vaults = container.knownVaults.all()
    }

    private fun add(vault: KnownVault) {
        container.knownVaults.add(vault)
        vaults = container.knownVaults.all()
    }

    // A provider that offers no lasting grant still works for this session; the grant is asked for again later.
    private fun grant(tree: Uri) {
        try {
            container.access.persistFolder(tree)
        } catch (ignored: SecurityException) {
            // Not persistable: keep going with the temporary grant.
        }
    }
}

/**
 * Biometric unlock around the session (ADR-0006): the prompt, the offer after a password unlock, and the messages
 * when the biometric path ends. [viaBiometric] says how the current unlock attempt was started, so a stored blob
 * that no longer opens the vault is dropped instead of retried.
 */
private class BiometricActions(
    private val container: AppContainer,
) {
    /** Bumped when the stored state changes, so the screens ask again. */
    var version by mutableStateOf(0)
        private set

    var notice by mutableStateOf<Int?>(null)
        private set

    /** The vault for which the enable offer is showing. */
    var offer by mutableStateOf<KnownVault?>(null)
        private set

    var viaBiometric = false
        private set

    private var wasOpen = false

    private val bio get() = container.biometric

    fun uiFor(
        vault: KnownVault,
        activity: FragmentActivity?,
        context: android.content.Context,
    ): BiometricUi {
        if (version < 0) return BiometricUi()
        val location = vault.location.value
        val enrolled = bio.isEnrolled(location)
        val available = enrolled && activity != null && bio.canUnlock(location)
        return BiometricUi(
            available = available,
            enrolled = enrolled,
            notice = notice ?: R.string.biometric_notice_password_due.takeIf { enrolled && !available },
            onUnlock = { if (activity != null) unlock(activity, vault, context) },
            onForget = { forget(vault) },
        )
    }

    private fun texts(context: android.content.Context) =
        BiometricTexts(
            title = context.getString(R.string.biometric_prompt_title),
            negative = context.getString(R.string.biometric_prompt_negative),
        )

    private fun unlock(
        activity: FragmentActivity,
        vault: KnownVault,
        context: android.content.Context,
    ) {
        notice = null
        bio.unlock(activity, vault.location.value, texts(context)) { credentials, problem ->
            if (credentials != null) {
                viaBiometric = true
                container.session.unlock(vault, credentials)
            } else {
                notice =
                    when (problem) {
                        BiometricProblem.Invalidated -> R.string.biometric_notice_invalidated
                        BiometricProblem.Unusable -> R.string.biometric_notice_unusable
                        BiometricProblem.PasswordDue -> R.string.biometric_notice_password_due
                        BiometricProblem.Cancelled, null -> null
                    }
                version++
            }
        }
    }

    fun passwordUnlock(
        vault: KnownVault,
        credentials: io.github.catlinux.keynexa.vault.Credentials,
    ) {
        viaBiometric = false
        notice = null
        container.session.unlock(vault, credentials)
    }

    private fun forget(vault: KnownVault) {
        bio.remove(vault.location.value)
        notice = null
        version++
    }

    /** The session reached a new state: drop a stored blob that failed, or offer biometrics after a password unlock. */
    fun onState(state: SessionState) {
        val justOpened = state is SessionState.Open && !wasOpen
        wasOpen = state is SessionState.Open
        when {
            state is SessionState.Failed && viaBiometric && state.error == UnlockError.WrongCredentials -> {
                bio.remove(state.vault.location.value)
                notice = R.string.biometric_notice_changed
                viaBiometric = false
                version++
            }
            justOpened && state is SessionState.Open -> {
                val location = state.vault.location.value
                if (!viaBiometric) {
                    bio.passwordTyped(location)
                    if (bio.shouldOffer(location)) offer = state.vault
                }
                viaBiometric = false
            }
        }
    }

    fun enable(
        activity: FragmentActivity,
        context: android.content.Context,
    ) {
        val vault = offer ?: return
        offer = null
        val hashed = container.session.hashedCredentials() ?: return
        bio.enroll(
            activity,
            vault.location.value,
            BiometricTexts(
                title = context.getString(R.string.biometric_prompt_enable_title),
                negative = context.getString(R.string.biometric_offer_later),
            ),
            hashed,
        ) { done ->
            hashed.wipe()
            if (!done) notice = R.string.biometric_notice_enable_failed
            version++
        }
    }

    fun later() {
        offer = null
    }

    fun never() {
        offer?.let { bio.decline(it.location.value) }
        offer = null
    }
}

/** Connects the screens to the session: navigation, the system folder pickers and the list of known vaults. */
@Composable
fun AppRoot(container: AppContainer) {
    val navigator = container.navigator
    val session = container.session
    val state by session.state.collectAsState()
    val context = LocalContext.current
    val actions = remember { VaultFileActions(container) }
    val biometric = remember { BiometricActions(container) }
    // Returning to the foreground asks for a synchronisation at most once a minute; the gate holds that rule.
    val syncGate = remember { SyncTriggerGate(SYNC_TRIGGER_MIN_MILLIS) }
    val activity = context as? FragmentActivity
    val openPicker = rememberLauncherForActivityResult(OpenVaultFolder(), actions::folderChosenToOpen)
    val createPicker = rememberLauncherForActivityResult(OpenVaultFolder(), actions::folderChosenToCreate)

    LaunchedEffect(state) {
        val open = state
        if (open is SessionState.Open && navigator.current is Screen.Unlock) {
            navigator.push(Screen.Group(null))
            // Unlocking always asks for a synchronisation round, whatever the foreground rule says.
            syncGate.mark()
            CloudSyncHook.onTrigger(context, open.vault, container.syncLocal())
        }
    }
    LaunchedEffect(state) { biometric.onState(state) }
    val isOpen = state is SessionState.Open
    LaunchedEffect(isOpen, container.visible) {
        // Coming back to the front asks for one too, at most once a minute; the unlock above already asked.
        foregroundSyncVault(isOpen, container.visible, state, syncGate)?.let {
            CloudSyncHook.onTrigger(context, it, container.syncLocal())
        }
        // Another device or program may have changed the file: look every few seconds while the app is in front.
        while (isOpen && container.visible) {
            delay(REFRESH_MILLIS)
            session.refreshIfChanged()
        }
    }
    BackHandler(enabled = navigator.current == Screen.Settings) { navigator.back() }
    BackHandler(enabled = navigator.current != Screen.Home && navigator.current != Screen.Settings) {
        ClipboardGuard.clearNow(context)
        if (state is SessionState.Open) session.lock() else session.dismissFailure()
        navigator.reset()
    }

    AppScreens(container, actions, biometric, state, onOpenFolder = {
        openPicker.launch(null)
    }, onCreateFolder = { createPicker.launch(null) })
    actions.choices?.let { PickVaultDialog(it, onPick = actions::open, onDismiss = actions::dismissChoices) }
    if (biometric.offer != null && activity != null) {
        BiometricOfferDialog(
            onEnable = { biometric.enable(activity, context) },
            onLater = biometric::later,
            onNever = biometric::never,
        )
    }
    actions.notice?.let { HomeNoticeDialog(it, onDismiss = actions::dismissNotice) }
}

/** The open vault to synchronise when the app returns to the front, or null when the gate holds it back. */
private fun foregroundSyncVault(
    isOpen: Boolean,
    visible: Boolean,
    state: SessionState,
    gate: SyncTriggerGate,
): KnownVault? {
    if (!isOpen || !visible) return null
    val open = state as? SessionState.Open ?: return null
    return open.vault.takeIf { gate.due() }
}

/** The screen for the current navigation state, connected to the session and the file actions. */
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun AppScreens(
    container: AppContainer,
    actions: VaultFileActions,
    biometric: BiometricActions,
    state: SessionState,
    onOpenFolder: () -> Unit,
    onCreateFolder: () -> Unit,
) {
    val navigator = container.navigator
    val session = container.session
    val context = LocalContext.current
    when (val screen = navigator.current) {
        Screen.Home ->
            HomeScreen(
                vaults = actions.vaults,
                onOpenExisting = { onOpenFolder() },
                onCreateNew = { navigator.push(Screen.CreateVault) },
                onSelect = { navigator.push(Screen.Unlock(it)) },
                onRemove = actions::remove,
                onOpenSettings = { navigator.push(Screen.Settings) },
            )
        Screen.Settings ->
            SettingsScreen(
                values = container.settings.values,
                onChange = container.settings::update,
                onBack = { navigator.back() },
                cloudSource =
                    (state as? SessionState.Open)?.let { open ->
                        CloudBackupSource(open.vault) { container.storage.read(open.vault.location) }
                    },
                syncLocal = (state as? SessionState.Open)?.let { container.syncLocal() },
            )
        Screen.CreateVault ->
            CreateVaultScreen(
                onCreate = { name, credentials ->
                    actions.beginCreate(name, credentials)
                    onCreateFolder()
                },
                onBack = { navigator.back() },
            )
        is Screen.Unlock ->
            UnlockScreen(
                vault = screen.vault,
                state = state,
                onUnlock = { biometric.passwordUnlock(screen.vault, it) },
                biometric = biometric.uiFor(screen.vault, context as? FragmentActivity, context),
                thirdPartyInput = remember(screen.vault) { ThirdPartyInput.isActive(context) },
                onReconnect = {
                    actions.beginReconnect(screen.vault)
                    onOpenFolder()
                },
                onBack = {
                    session.dismissFailure()
                    navigator.back()
                },
            )
        else ->
            (state as? SessionState.Open)?.let {
                OpenVaultScreens(
                    container = container,
                    open = it,
                    onReconnect = {
                        actions.beginReconnectOpenVault()
                        onOpenFolder()
                    },
                )
            }
    }
}
