package io.github.catlinux.keynexa.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_changed_during_save
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_credentials_changed
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_encode_failed
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_external_change
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_file_unreadable
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_storage_error
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_unmergeable
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_write_not_verified
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_damaged
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_invalid_key_file
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_not_a_vault
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_not_enough_memory
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_unreachable
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_unsupported_format
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_wrong_credentials
import io.github.catlinux.keynexa.session.UnlockError
import io.github.catlinux.keynexa.sync.SaveFailure
import io.github.catlinux.keynexa.ui.keypad.KeypadState
import io.github.catlinux.keynexa.ui.keypad.PasswordBuffer
import org.jetbrains.compose.resources.StringResource

/** The plain-language message of each way an unlock can fail. */
internal fun unlockErrorMessage(error: UnlockError): StringResource =
    when (error) {
        UnlockError.WrongCredentials -> Res.string.unlock_error_wrong_credentials
        UnlockError.InvalidKeyFile -> Res.string.unlock_error_invalid_key_file
        UnlockError.NotAVault -> Res.string.unlock_error_not_a_vault
        UnlockError.UnsupportedFormat -> Res.string.unlock_error_unsupported_format
        UnlockError.Damaged -> Res.string.unlock_error_damaged
        UnlockError.Unreachable -> Res.string.unlock_error_unreachable
        UnlockError.NotEnoughMemory -> Res.string.unlock_error_not_enough_memory
    }

/** The plain-language reason of each way a save can fail. */
internal fun saveProblemMessage(problem: SaveFailure): StringResource =
    when (problem) {
        SaveFailure.ExternalChangeNeedsMerge -> Res.string.save_problem_external_change
        SaveFailure.Unmergeable -> Res.string.save_problem_unmergeable
        SaveFailure.ChangedDuringSave -> Res.string.save_problem_changed_during_save
        SaveFailure.FileUnreadable -> Res.string.save_problem_file_unreadable
        SaveFailure.CredentialsChanged -> Res.string.save_problem_credentials_changed
        SaveFailure.EncodeFailed -> Res.string.save_problem_encode_failed
        SaveFailure.StorageError -> Res.string.save_problem_storage_error
        SaveFailure.WriteNotVerified -> Res.string.save_problem_write_not_verified
    }

/** Byte a wiped key file is filled with. */
private const val KEY_FILE_FILL: Byte = 0

/** The key file the user picked: its name and its bytes. [clear] wipes the bytes and leaves the holder empty. */
internal class KeyFileHolder {
    var name by mutableStateOf<String?>(null)
        private set
    var bytes by mutableStateOf<ByteArray?>(null)
        private set
    var failed by mutableStateOf(false)
        private set

    /** True when a key file is held. */
    val chosen: Boolean get() = bytes != null

    fun pick(
        name: String,
        bytes: ByteArray,
    ) {
        clear()
        this.name = name
        this.bytes = bytes
    }

    fun markFailed() {
        failed = true
    }

    /** The bytes as a new array the caller owns, or null; the holder keeps its own copy to wipe later. */
    fun copyOut(): ByteArray? = bytes?.copyOf()

    fun clear() {
        bytes?.fill(KEY_FILE_FILL)
        bytes = null
        name = null
        failed = false
    }
}

/** The master password while it is typed: the system keyboard's text or the keypad's wipeable buffer. */
internal class DesktopPassword(
    useKeypad: Boolean = false,
) {
    var useKeypad by mutableStateOf(useKeypad)
        private set
    var text by mutableStateOf("")
    var revealed by mutableStateOf(false)
    val buffer = PasswordBuffer()
    var bufferLength by mutableIntStateOf(0)
    val keypad = KeypadState()

    /** How many characters are typed. */
    val length: Int get() = if (useKeypad) bufferLength else text.length

    /** The typed password as a new array the caller owns, or null when nothing was typed; the screen forgets it. */
    fun take(): CharArray? {
        val typed = if (useKeypad) buffer.copyOut() else text.toCharArray()
        clear()
        return typed.takeIf { it.isNotEmpty() }
    }

    fun clear() {
        buffer.clear()
        bufferLength = 0
        text = ""
        revealed = false
    }

    fun switchInput() {
        clear()
        useKeypad = !useKeypad
    }

    /** Records a keypad press, so the screen recomposes. */
    fun keypadChanged() {
        bufferLength = buffer.length
    }
}
