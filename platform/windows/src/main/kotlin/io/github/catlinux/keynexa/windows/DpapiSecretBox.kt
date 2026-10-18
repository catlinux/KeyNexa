package io.github.catlinux.keynexa.windows

import com.sun.jna.LastErrorException
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinCrypt
import io.github.catlinux.keynexa.platform.SecretBox

/**
 * [SecretBox] over Windows DPAPI, scoped to the current user: only that user, on that machine, can open a blob.
 *
 * [seal] prepends a version byte to the protected bytes; [open] checks it and answers null for a wrong version, a
 * damaged blob or any DPAPI failure. Only encrypted bytes pass through here, and nothing is logged.
 */
class DpapiSecretBox : SecretBox {
    override fun seal(plain: ByteArray): ByteArray {
        val protected = Crypt32Util.cryptProtectData(plain, ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, "", null)
        return ByteArray(protected.size + VERSION_BYTES).also { sealed ->
            sealed[0] = VERSION
            System.arraycopy(protected, 0, sealed, VERSION_BYTES, protected.size)
        }
    }

    @Suppress("SwallowedException")
    override fun open(sealed: ByteArray): ByteArray? {
        if (sealed.size <= VERSION_BYTES || sealed[0] != VERSION) return null
        val protected = sealed.copyOfRange(VERSION_BYTES, sealed.size)
        return try {
            Crypt32Util.cryptUnprotectData(protected, ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null)
        } catch (failure: Win32Exception) {
            null
        } catch (failure: LastErrorException) {
            null
        } catch (failure: IllegalArgumentException) {
            null
        } finally {
            protected.fill(0)
        }
    }

    private companion object {
        const val VERSION: Byte = 1
        const val VERSION_BYTES = 1

        /**
         * Mixed into DPAPI by both sides. It is not a secret: it only keeps KeyNexa's blobs apart from those of
         * other programs, so a blob from elsewhere is not opened as one of ours.
         */
        val ENTROPY = "io.github.catlinux.keynexa/secret-box/v1".toByteArray(Charsets.UTF_8)
    }
}
