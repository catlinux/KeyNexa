package io.github.catlinux.keynexa.desktop

/** Outcome of asking Windows Hello to sign a challenge. */
sealed interface HelloResult {
    /** The signature. The caller owns the array and must wipe it. */
    class Signed(
        val bytes: ByteArray,
    ) : HelloResult

    /** The user dismissed the prompt or chose the password instead. */
    object Cancelled : HelloResult

    /** Hello is unavailable or reported an error; [reason] never carries secrets. */
    class Failed(
        val reason: String,
    ) : HelloResult
}

/**
 * Asks Windows Hello to sign a challenge, behind which the quick-unlock policy hides.
 *
 * The caller owns the returned signature and must wipe it. An implementation blocks until the user answers the Hello
 * prompt, so it must be called off the UI thread.
 */
fun interface HelloSigner {
    fun sign(challenge: ByteArray): HelloResult
}
