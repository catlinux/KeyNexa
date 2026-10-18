package io.github.catlinux.keynexa.vault

import java.time.Instant
import java.util.UUID

/**
 * A whole KDBX database in memory.
 *
 * It holds everything needed to write the file back without losing data, which is what the safe-save and merge
 * code in `core/sync` operate on. Pure Kotlin: nothing here depends on a KDBX library or on Android.
 */
data class Vault(
    val format: VaultFormat,
    val meta: VaultMeta,
    val root: Group,
    val deletedObjects: List<DeletedObject> = emptyList(),
    /** Attachment contents, shared by every entry (and history item) that references them by [BinaryId]. */
    val binaries: Map<BinaryId, Binary> = emptyMap(),
    /** Elements this version of KeyNexa does not understand, kept so a newer client's data survives a save. */
    val unknownElements: List<XmlElement> = emptyList(),
    val unknownRootElements: List<XmlElement> = emptyList(),
)

/** How the file is encrypted and which KDBX version it uses; kept so a save does not change the file's format. */
data class VaultFormat(
    val version: FormatVersion,
    val cipher: Cipher,
    val kdf: Kdf,
    val compression: Compression = Compression.Gzip,
    /** Unencrypted header data some clients add (KDBX 4 only). */
    val publicCustomData: Map<String, VariantValue> = emptyMap(),
)

data class FormatVersion(
    val major: Int,
    val minor: Int,
) {
    override fun toString(): String = "$major.$minor"
}

enum class Cipher { Aes256, ChaCha20, Twofish256 }

enum class Compression { None, Gzip }

/** Key derivation function and its cost parameters. Salts and seeds are generated again on every save. */
sealed interface Kdf {
    data class AesKdf(
        val rounds: Long,
    ) : Kdf

    data class Argon2(
        val variant: Argon2Variant,
        val memoryBytes: Long,
        val iterations: Long,
        val parallelism: Int,
        val version: Int,
    ) : Kdf {
        val memoryMiB: Long get() = memoryBytes / BYTES_PER_MIB

        private companion object {
            const val BYTES_PER_MIB = 1024L * 1024L
        }
    }
}

enum class Argon2Variant { Argon2d, Argon2id }

/** A typed value of the KDBX 4 header's variant dictionary. Unsigned values are stored in their signed type. */
sealed interface VariantValue {
    data class Bool(
        val value: Boolean,
    ) : VariantValue

    data class Int32(
        val value: Int,
    ) : VariantValue

    data class Int64(
        val value: Long,
    ) : VariantValue

    data class UInt32(
        val value: Long,
    ) : VariantValue

    data class UInt64(
        val value: Long,
    ) : VariantValue

    data class Text(
        val value: String,
    ) : VariantValue

    class Bytes(
        val value: ByteArray,
    ) : VariantValue {
        override fun equals(other: Any?): Boolean = other is Bytes && value.contentEquals(other.value)

        override fun hashCode(): Int = value.contentHashCode()

        override fun toString(): String = "Bytes(${value.size})"
    }
}

data class VaultMeta(
    val generator: String = "",
    val name: String = "",
    val description: String = "",
    val defaultUser: String = "",
    val color: String? = null,
    val settingsChanged: Instant? = null,
    val nameChanged: Instant? = null,
    val descriptionChanged: Instant? = null,
    val defaultUserChanged: Instant? = null,
    val maintenanceHistoryDays: Long = 365,
    val masterKeyChanged: Instant? = null,
    val masterKeyChangeRecommendedDays: Int = -1,
    val masterKeyChangeForcedDays: Int = -1,
    val recycleBinEnabled: Boolean = false,
    val recycleBinGroup: UUID? = null,
    val recycleBinChanged: Instant? = null,
    val entryTemplatesGroup: UUID? = null,
    val entryTemplatesGroupChanged: Instant? = null,
    val historyMaxItems: Int = -1,
    val historyMaxSizeBytes: Int = -1,
    val lastSelectedGroup: UUID? = null,
    val lastTopVisibleGroup: UUID? = null,
    val protectedFields: Set<ProtectableField> = setOf(ProtectableField.Password),
    val customIcons: Map<UUID, CustomIcon> = emptyMap(),
    val customData: Map<String, CustomDataValue> = emptyMap(),
    val unknownElements: List<XmlElement> = emptyList(),
)

/** Standard entry fields a vault can ask clients to protect in memory. */
enum class ProtectableField { Title, UserName, Password, Url, Notes }

class CustomIcon(
    val data: ByteArray,
    val name: String? = null,
    val lastModified: Instant? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is CustomIcon &&
            data.contentEquals(other.data) &&
            name == other.name &&
            lastModified == other.lastModified

    override fun hashCode(): Int = 31 * data.contentHashCode() + (name?.hashCode() ?: 0)

    override fun toString(): String = "CustomIcon(name=$name, size=${data.size})"
}

data class CustomDataValue(
    val value: String,
    val lastModified: Instant? = null,
)

data class DeletedObject(
    val id: UUID,
    val deletedAt: Instant?,
)

/** Identifies an attachment by the lowercase hexadecimal SHA-256 of its content. */
@JvmInline
value class BinaryId(
    val hex: String,
)

class Binary(
    val content: ByteArray,
    val protectedInMemory: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        other is Binary && protectedInMemory == other.protectedInMemory && content.contentEquals(other.content)

    override fun hashCode(): Int = 31 * content.contentHashCode() + protectedInMemory.hashCode()

    /** Never prints the content. */
    override fun toString(): String = "Binary(size=${content.size}, protectedInMemory=$protectedInMemory)"
}

/** A minimal XML element, enough to carry unknown data through a load and a save. */
data class XmlElement(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val text: String? = null,
    val children: List<XmlElement> = emptyList(),
)
