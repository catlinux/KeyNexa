package io.github.catlinux.keynexa.desktop.app

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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The texts of a failed unlock or save, and the wipeable holders behind the unlock screen. */
class UnlockSupportTest {
    @Test
    fun mapsEveryUnlockFailureToItsMessage() {
        val messages =
            mapOf(
                UnlockError.WrongCredentials to Res.string.unlock_error_wrong_credentials,
                UnlockError.InvalidKeyFile to Res.string.unlock_error_invalid_key_file,
                UnlockError.NotAVault to Res.string.unlock_error_not_a_vault,
                UnlockError.UnsupportedFormat to Res.string.unlock_error_unsupported_format,
                UnlockError.Damaged to Res.string.unlock_error_damaged,
                UnlockError.Unreachable to Res.string.unlock_error_unreachable,
                UnlockError.NotEnoughMemory to Res.string.unlock_error_not_enough_memory,
            )
        assertEquals(UnlockError.entries.size, messages.size)
        messages.forEach { (error, message) -> assertEquals(message, unlockErrorMessage(error)) }
    }

    @Test
    fun mapsEverySaveProblemToItsMessage() {
        val messages =
            mapOf(
                SaveFailure.ExternalChangeNeedsMerge to Res.string.save_problem_external_change,
                SaveFailure.Unmergeable to Res.string.save_problem_unmergeable,
                SaveFailure.ChangedDuringSave to Res.string.save_problem_changed_during_save,
                SaveFailure.FileUnreadable to Res.string.save_problem_file_unreadable,
                SaveFailure.CredentialsChanged to Res.string.save_problem_credentials_changed,
                SaveFailure.EncodeFailed to Res.string.save_problem_encode_failed,
                SaveFailure.StorageError to Res.string.save_problem_storage_error,
                SaveFailure.WriteNotVerified to Res.string.save_problem_write_not_verified,
            )
        assertEquals(SaveFailure.entries.size, messages.size)
        messages.forEach { (problem, message) -> assertEquals(message, saveProblemMessage(problem)) }
    }

    @Test
    fun wipesTheKeyFileBytesWhenTheyAreCleared() {
        val holder = KeyFileHolder()
        val bytes = byteArrayOf(1, 2, 3)
        holder.pick("personal.key", bytes)

        assertTrue(holder.chosen)
        assertEquals("personal.key", holder.name)

        holder.clear()

        assertFalse(holder.chosen)
        assertNull(holder.name)
        assertArrayEquals(ByteArray(3), bytes)
    }

    @Test
    fun handsOutACopyOfTheKeyFile() {
        val holder = KeyFileHolder()
        val bytes = byteArrayOf(4, 5, 6)
        holder.pick("personal.key", bytes)

        val copy = holder.copyOut()
        assertArrayEquals(byteArrayOf(4, 5, 6), copy)
        copy?.fill(0)

        assertArrayEquals(byteArrayOf(4, 5, 6), holder.copyOut())
    }

    @Test
    fun forgetsTheTypedPasswordWhenItIsTaken() {
        val password = DesktopPassword()
        password.setText(0, "master-password")

        assertEquals(15, password.length())
        assertArrayEquals("master-password".toCharArray(), password.take())
        assertEquals(0, password.length())
        assertEquals("", password.text())
    }

    @Test
    fun forgetsTheKeypadPasswordWhenItIsTaken() {
        val password = DesktopPassword(useKeypad = true)
        password.buffer().append('a')
        password.buffer().append('b')
        password.keypadChanged()

        assertEquals(2, password.length())
        assertArrayEquals(charArrayOf('a', 'b'), password.take())
        assertEquals(0, password.length())
        assertTrue(password.buffer().isEmpty())
    }

    @Test
    fun keepsTwoFieldsApartAndWipesBothWhenOneIsTaken() {
        val password = DesktopPassword(fields = 2)
        password.setText(0, "master-password")
        password.setText(1, "master-password")

        assertTrue(password.matches(0, 1))
        assertEquals("master-password", password.text(0))

        assertArrayEquals("master-password".toCharArray(), password.take(0))

        assertEquals(0, password.length(0))
        assertEquals(0, password.length(1))
    }

    @Test
    fun tellsTheTwoKeypadFieldsApart() {
        val password = DesktopPassword(useKeypad = true, fields = 2)
        password.buffer(0).append('a')
        password.buffer(1).append('a')

        assertTrue(password.matches(0, 1))

        password.buffer(1).append('b')

        assertFalse(password.matches(0, 1))
        assertFalse(password.buffer(1).isEmpty())
    }
}
