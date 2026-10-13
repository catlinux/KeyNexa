package io.github.catlinux.keynexa.app

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.core.net.toUri
import io.github.catlinux.keynexa.app.nav.Screen
import io.github.catlinux.keynexa.app.ui.home.CreateVaultScreen
import io.github.catlinux.keynexa.app.ui.home.HomeScreen
import io.github.catlinux.keynexa.app.ui.unlock.UnlockScreen
import io.github.catlinux.keynexa.app.vault.KnownVault
import io.github.catlinux.keynexa.app.vault.SessionState
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.NewVault
import io.github.catlinux.keynexa.vault.VaultLocation

private const val ANY_FILE = "*/*"
private const val VAULT_EXTENSION = ".kdbx"

/**
 * What the user does with vault files: adding, creating, reconnecting and removing. Holds the list of known vaults
 * and, while the system file picker is open, the vault being created (name and credentials) or reconnected.
 */
private class VaultFileActions(
    private val container: AppContainer,
    private val resolver: ContentResolver,
) {
    var vaults by mutableStateOf(container.knownVaults.all())
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

    /** The user picked a file to open, or to reconnect when [beginReconnect] was called. */
    fun opened(uri: Uri?) {
        val previous = reconnecting
        reconnecting = null
        if (uri == null) return
        val location = grant(uri)
        if (previous != null) {
            container.knownVaults.remove(previous.location)
            container.navigator.back()
        }
        val vault = KnownVault(previous?.name ?: displayName(uri), location)
        add(vault)
        container.navigator.push(Screen.Unlock(vault))
    }

    /** The user chose where the new vault file goes; null when they cancelled. */
    fun created(uri: Uri?) {
        val (name, credentials) = creating ?: return
        creating = null
        if (uri == null) {
            credentials.wipe()
            return
        }
        val vault = KnownVault(name, grant(uri))
        add(vault)
        container.navigator.back()
        container.navigator.push(Screen.Unlock(vault))
        container.session.create(vault, NewVault().create(name), credentials)
    }

    fun remove(vault: KnownVault) {
        container.knownVaults.remove(vault.location)
        container.access.release(vault.location.value.toUri())
        vaults = container.knownVaults.all()
    }

    private fun add(vault: KnownVault) {
        container.knownVaults.add(vault)
        vaults = container.knownVaults.all()
    }

    // A provider that offers no lasting grant still opens the vault now; reconnecting is offered later.
    private fun grant(uri: Uri): VaultLocation =
        try {
            container.access.persistFile(uri)
        } catch (ignored: SecurityException) {
            VaultLocation(uri.toString())
        }

    private fun displayName(uri: Uri): String {
        val name =
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        return (name ?: uri.lastPathSegment ?: uri.toString()).removeSuffix(VAULT_EXTENSION)
    }
}

/** Connects the screens to the session: navigation, the system file pickers and the list of known vaults. */
@Composable
fun AppRoot(container: AppContainer) {
    val navigator = container.navigator
    val session = container.session
    val state by session.state.collectAsState()
    val resolver = LocalContext.current.contentResolver
    val actions = remember { VaultFileActions(container, resolver) }
    val openPicker = rememberLauncherForActivityResult(OpenVaultDocument(), actions::opened)
    val createPicker = rememberLauncherForActivityResult(CreateVaultDocument(), actions::created)

    LaunchedEffect(state) {
        if (state is SessionState.Open && navigator.current is Screen.Unlock) navigator.push(Screen.Group(null))
    }
    BackHandler(enabled = navigator.current != Screen.Home) {
        if (state is SessionState.Open) session.lock() else session.dismissFailure()
        navigator.reset()
    }

    when (val screen = navigator.current) {
        Screen.Home ->
            HomeScreen(
                vaults = actions.vaults,
                onOpenExisting = { openPicker.launch(arrayOf(ANY_FILE)) },
                onCreateNew = { navigator.push(Screen.CreateVault) },
                onSelect = { navigator.push(Screen.Unlock(it)) },
                onRemove = actions::remove,
            )
        Screen.CreateVault ->
            CreateVaultScreen(
                onCreate = { name, credentials ->
                    actions.beginCreate(name, credentials)
                    createPicker.launch(name + VAULT_EXTENSION)
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
                    openPicker.launch(arrayOf(ANY_FILE))
                },
                onBack = {
                    session.dismissFailure()
                    navigator.back()
                },
            )
        else -> (state as? SessionState.Open)?.let { OpenVaultScreens(container, it) }
    }
}
