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
import io.github.catlinux.keynexa.app.nav.Screen
import io.github.catlinux.keynexa.app.ui.home.CreateVaultScreen
import io.github.catlinux.keynexa.app.ui.home.HomeNoticeDialog
import io.github.catlinux.keynexa.app.ui.home.HomeScreen
import io.github.catlinux.keynexa.app.ui.home.PickVaultDialog
import io.github.catlinux.keynexa.app.ui.unlock.UnlockScreen
import io.github.catlinux.keynexa.app.vault.KnownVault
import io.github.catlinux.keynexa.app.vault.SessionState
import io.github.catlinux.keynexa.platform.SafVaultAccess
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.NewVault

private const val VAULT_EXTENSION = ".kdbx"

/**
 * What the user does with vault files: adding, creating, reconnecting and removing. Vaults are always reached
 * through a granted folder, because Android revokes the grant on a single file when a sync tool replaces it.
 * Holds the list of known vaults and, while the system folder picker is open, the vault being created (name and
 * credentials) or reconnected.
 */
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

    fun beginCreate(
        name: String,
        credentials: Credentials,
    ) {
        creating = name to credentials
    }

    fun beginReconnect(vault: KnownVault) {
        reconnecting = vault
    }

    fun dismissNotice() {
        notice = null
    }

    fun dismissChoices() {
        choices = null
        reconnecting = null
    }

    /** The user chose a folder to open a vault from (or to reconnect one when [beginReconnect] was called). */
    fun folderChosenToOpen(tree: Uri?) {
        if (tree == null) {
            reconnecting = null
            return
        }
        grant(tree)
        val files = container.access.listVaultFiles(tree)
        when {
            files.isEmpty() -> {
                reconnecting = null
                notice = R.string.home_no_vaults_in_folder
            }
            files.size == 1 -> open(files.first())
            else -> choices = files
        }
    }

    /** The user picked one of several vault files in the folder. */
    fun open(file: SafVaultAccess.VaultFile) {
        choices = null
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

/** Connects the screens to the session: navigation, the system folder pickers and the list of known vaults. */
@Composable
fun AppRoot(container: AppContainer) {
    val navigator = container.navigator
    val session = container.session
    val state by session.state.collectAsState()
    val context = LocalContext.current
    val actions = remember { VaultFileActions(container) }
    val openPicker = rememberLauncherForActivityResult(OpenVaultFolder(), actions::folderChosenToOpen)
    val createPicker = rememberLauncherForActivityResult(OpenVaultFolder(), actions::folderChosenToCreate)

    LaunchedEffect(state) {
        if (state is SessionState.Open && navigator.current is Screen.Unlock) navigator.push(Screen.Group(null))
    }
    BackHandler(enabled = navigator.current != Screen.Home) {
        ClipboardGuard.clearNow(context)
        if (state is SessionState.Open) session.lock() else session.dismissFailure()
        navigator.reset()
    }

    when (val screen = navigator.current) {
        Screen.Home ->
            HomeScreen(
                vaults = actions.vaults,
                onOpenExisting = { openPicker.launch(null) },
                onCreateNew = { navigator.push(Screen.CreateVault) },
                onSelect = { navigator.push(Screen.Unlock(it)) },
                onRemove = actions::remove,
            )
        Screen.CreateVault ->
            CreateVaultScreen(
                onCreate = { name, credentials ->
                    actions.beginCreate(name, credentials)
                    createPicker.launch(null)
                },
                onBack = { navigator.back() },
            )
        is Screen.Unlock ->
            UnlockScreen(
                vault = screen.vault,
                state = state,
                onUnlock = { session.unlock(screen.vault, it) },
                onReconnect = {
                    actions.beginReconnect(screen.vault)
                    openPicker.launch(null)
                },
                onBack = {
                    session.dismissFailure()
                    navigator.back()
                },
            )
        else -> (state as? SessionState.Open)?.let { OpenVaultScreens(container, it) }
    }
    actions.choices?.let { PickVaultDialog(it, onPick = actions::open, onDismiss = actions::dismissChoices) }
    actions.notice?.let { HomeNoticeDialog(it, onDismiss = actions::dismissNotice) }
}
