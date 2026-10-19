package io.github.catlinux.keynexa.sync

import java.security.SecureRandom

/**
 * The synchronisation id shared by cloud and device synchronisation (ADR-0009 §5, ADR-0013).
 *
 * It is a 32-digit lowercase hexadecimal string (a random UUID without its dashes) stored in the vault's own custom
 * data under [KEY]. This object only works with strings and maps; it never touches the KDBX engine, and reading a
 * malformed value is told apart from a missing one so the caller can report [SyncError.InvalidSyncId].
 */
object SyncIds {
    /** The `meta.customData` key that holds a vault's synchronisation id. */
    const val KEY = "KeyNexa.SyncId"

    private const val ID_BYTES = 16
    private const val PREFIX = "KeyNexa/sync/"
    private val ID_PATTERN = Regex("^[0-9a-f]{32}$")
    private val random = SecureRandom()

    /** A fresh id: 32 lowercase hexadecimal digits from a secure random source. */
    fun newId(): String = ByteArray(ID_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    /** True when [value] is a well-formed synchronisation id. */
    fun isValid(value: String): Boolean = ID_PATTERN.matches(value)

    /** The well-formed id in [value], or null when it is absent or malformed (see [isMalformed]). */
    fun read(value: String?): String? = value?.takeIf(ID_PATTERN::matches)

    /** True when [value] is present but is not a well-formed id; a null value is simply missing, not malformed. */
    fun isMalformed(value: String?): Boolean = value != null && !ID_PATTERN.matches(value)

    /** The id in a vault's custom data, or null when it is absent or malformed (see [isMalformedCustomData]). */
    fun readCustomData(customData: Map<String, String>): String? = read(customData[KEY])

    /** True when the custom data carries a [KEY] value that is not a well-formed id. */
    fun isMalformedCustomData(customData: Map<String, String>): Boolean = isMalformed(customData[KEY])

    /** The name of the cloud object that holds the vault with [id] (ADR-0009 §1). */
    fun objectName(id: String): String = "$PREFIX$id.kdbx"
}
