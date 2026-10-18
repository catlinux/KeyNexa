package io.github.catlinux.keynexa.vault

import java.time.Instant
import java.util.UUID

data class Group(
    val id: UUID,
    val name: String,
    val notes: String = "",
    val icon: Icon = Icon(FOLDER_ICON),
    val times: Times = Times(),
    val expanded: Boolean = true,
    val defaultAutoTypeSequence: String? = null,
    val autoType: Inheritance = Inheritance.Inherit,
    val searching: Inheritance = Inheritance.Inherit,
    val lastTopVisibleEntry: UUID? = null,
    val previousParentGroup: UUID? = null,
    val tags: List<String> = emptyList(),
    val groups: List<Group> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val customData: Map<String, CustomDataValue> = emptyMap(),
    val unknownElements: List<XmlElement> = emptyList(),
) {
    private companion object {
        const val FOLDER_ICON = 48
    }
}

data class Entry(
    val id: UUID,
    val icon: Icon = Icon(KEY_ICON),
    val foregroundColor: String? = null,
    val backgroundColor: String? = null,
    val overrideUrl: String = "",
    val times: Times = Times(),
    val autoType: AutoType? = null,
    /** Standard fields use the names `Title`, `UserName`, `Password`, `URL` and `Notes`; the rest are custom. */
    val fields: Map<String, FieldValue> = emptyMap(),
    val tags: List<String> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    /** Previous versions of the entry, oldest first. Each one is an [Entry] with its own empty history. */
    val history: List<Entry> = emptyList(),
    val customData: Map<String, CustomDataValue> = emptyMap(),
    val previousParentGroup: UUID? = null,
    val qualityCheck: Boolean = true,
    val unknownElements: List<XmlElement> = emptyList(),
) {
    val title: String get() = fields[TITLE]?.value.orEmpty()
    val userName: String get() = fields[USER_NAME]?.value.orEmpty()
    val url: String get() = fields[URL]?.value.orEmpty()

    companion object {
        const val TITLE = "Title"
        const val USER_NAME = "UserName"
        const val PASSWORD = "Password"
        const val URL = "URL"
        const val NOTES = "Notes"
        private const val KEY_ICON = 0
    }
}

/**
 * The text of an entry field. Secrets live here as plain strings in memory, so it never prints its value:
 * logging or asserting on a [FieldValue] must not leak a password.
 */
class FieldValue(
    val value: String,
    val protected: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        other is FieldValue && value == other.value && protected == other.protected

    override fun hashCode(): Int = 31 * value.hashCode() + protected.hashCode()

    override fun toString(): String = "FieldValue(length=${value.length}, protected=$protected)"
}

/** A standard icon number and, optionally, a custom icon of the vault that replaces it. */
data class Icon(
    val standardId: Int,
    val custom: UUID? = null,
)

data class Attachment(
    val name: String,
    val id: BinaryId,
)

data class AutoType(
    val enabled: Boolean,
    val obfuscation: AutoTypeObfuscation = AutoTypeObfuscation.None,
    val defaultSequence: String? = null,
    val associations: List<AutoTypeAssociation> = emptyList(),
)

data class AutoTypeAssociation(
    val window: String,
    val keystrokeSequence: String,
)

enum class AutoTypeObfuscation { None, UseClipboard }

enum class Inheritance { Inherit, Enabled, Disabled }

data class Times(
    val created: Instant? = null,
    val lastAccessed: Instant? = null,
    val lastModified: Instant? = null,
    val locationChanged: Instant? = null,
    val expiry: Instant? = null,
    val expires: Boolean = false,
    val usageCount: Int = 0,
)
