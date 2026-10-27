package io.github.catlinux.keynexa.update

/**
 * The public keys the app trusts to sign updates.
 *
 * [PRODUCTION] is the public half of the Ed25519 update key of KeyNexa (ADR-0015). The private half lives in
 * the `UPDATE_SIGNING_KEY` secret of the publication workflow and never leaves it.
 */
object UpdateKeys {
    /** SubjectPublicKeyInfo DER of the update key, Base64-encoded, as published with each release. */
    const val PRODUCTION = "MCowBQYDK2VwAyEAj7N0vtEMLww0R0qybVdJPcBXjETQDGqw+R4udkbu7uA="
}
