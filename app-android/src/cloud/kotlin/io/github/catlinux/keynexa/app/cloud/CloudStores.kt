package io.github.catlinux.keynexa.app.cloud

import android.content.Context
import io.github.catlinux.keynexa.cloud.CloudCredentialStore
import java.io.File
import java.io.IOException

/** The Keystore alias of the key that protects the cloud credentials. */
internal const val CLOUD_KEY_ALIAS = "keynexa-cloud-credentials"

/** The consent text version; a new text raises it and the user is asked again. */
const val CONSENT_VERSION = 1

private const val CLOUD_DIRECTORY = "cloud"
private const val CREDENTIALS_FILE = "credentials.bin"
private const val CONSENT_FILE = "consent"

/** Where the cloud credentials and the consent mark live: app-private storage excluded from backup. */
object CloudStores {
    fun credentials(context: Context): CloudCredentialStore =
        CloudCredentialStore(File(directory(context), CREDENTIALS_FILE), KeystoreSecretBox())

    internal fun directory(context: Context): File = File(context.noBackupFilesDir, CLOUD_DIRECTORY)
}

/** Remembers, as a version number, that the user accepted the consent text. */
class CloudConsentStore(
    context: Context,
) {
    private val file = File(CloudStores.directory(context), CONSENT_FILE)

    fun accepted(): Boolean =
        try {
            file.isFile && (file.readText().trim().toIntOrNull() ?: 0) >= CONSENT_VERSION
        } catch (failure: IOException) {
            false
        }

    fun accept() {
        file.parentFile?.mkdirs()
        file.writeText(CONSENT_VERSION.toString())
    }

    fun revoke() {
        if (file.exists()) file.delete()
    }
}
