package io.github.catlinux.keynexa.app.updates

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import io.github.catlinux.keynexa.update.SigningCertificates
import io.github.catlinux.keynexa.update.UpdateCheck
import java.io.File
import java.io.IOException

/**
 * The Android side of an update (ADR-0015): checks that a downloaded APK is a newer build of this very app signed by
 * the same key, and hands it to the system installer, which asks the user and checks the signature again.
 */
internal object ApkInstaller {
    private const val SESSION_NAME = "keynexa-update.apk"

    /** True when the user allowed KeyNexa to install apps (a per-app switch in the system settings). */
    fun mayInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where that switch is. */
    fun openInstallPermissionSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, ("package:" + context.packageName).toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installedVersionName(context: Context): String = installedInfo(context)?.versionName.orEmpty()

    /**
     * True when [apk] is this app (same package name), carries the version the signed manifest promised and is signed
     * with the same certificates as the installed app. Anything unreadable is a no.
     */
    fun isOurUpdate(
        context: Context,
        apk: File,
        offer: UpdateCheck.Available,
    ): Boolean {
        val downloaded = archiveInfo(context, apk) ?: return false
        val installed = installedInfo(context) ?: return false
        return downloaded.packageName == context.packageName &&
            downloaded.versionName == offer.manifest.version.toString() &&
            SigningCertificates.sameSigners(signersOf(downloaded), signersOf(installed))
    }

    /** Gives [apk] to the system installer, which shows its own confirmation. False when it could not be handed over. */
    fun commit(
        context: Context,
        apk: File,
    ): Boolean =
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite(SESSION_NAME, 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(resultIntent(context, id).intentSender)
            }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }

    private fun resultIntent(
        context: Context,
        sessionId: Int,
    ): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
        // The installer fills in the result, so the intent has to be mutable.
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun signingFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun installedInfo(context: Context): PackageInfo? =
        try {
            val manager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(signingFlag().toLong()))
            } else {
                @Suppress("DEPRECATION")
                manager.getPackageInfo(context.packageName, signingFlag())
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    private fun archiveInfo(
        context: Context,
        apk: File,
    ): PackageInfo? {
        val manager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(signingFlag().toLong()))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageArchiveInfo(apk.path, signingFlag())
        }
    }

    /** The signing certificates of [info], as raw bytes; empty when the package does not carry them. */
    private fun signersOf(info: PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo
                ?.apkContentsSigners
                .orEmpty()
                .map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty().map { it.toByteArray() }
        }
}

/**
 * Receives the answer of the system installer. When it needs the user's confirmation it hands over the screen to show,
 * and a failure is reported back so the offer reappears; a success replaces this very process.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                confirmationOf(intent)?.let { confirmation ->
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmation)
                }
            PackageInstaller.STATUS_SUCCESS -> AndroidUpdates.installFinished(success = true)
            else -> AndroidUpdates.installFinished(success = false)
        }
    }

    private fun confirmationOf(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
}
