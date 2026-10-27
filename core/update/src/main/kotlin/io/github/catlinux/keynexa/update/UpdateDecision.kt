package io.github.catlinux.keynexa.update

/** Decides whether a signed manifest is worth offering to the user. */
object UpdateDecision {
    /**
     * Returns [manifest] when its version is newer than [current], and null otherwise: an equal or older
     * version is never offered, whatever the server says (ADR-0015).
     */
    fun offer(
        current: SemVer,
        manifest: UpdateManifest,
    ): UpdateManifest? = manifest.takeIf { it.version > current }
}
