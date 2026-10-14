package io.github.catlinux.keynexa.kdbx

import app.keemobile.kotpass.constants.MemoryProtectionFlag
import app.keemobile.kotpass.constants.PredefinedIcon
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.modifiers.modifyBinaries
import app.keemobile.kotpass.models.AutoTypeItem
import app.keemobile.kotpass.models.DatabaseContent
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import io.github.catlinux.keynexa.vault.AutoTypeObfuscation
import io.github.catlinux.keynexa.vault.Binary
import io.github.catlinux.keynexa.vault.BinaryId
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Icon
import io.github.catlinux.keynexa.vault.Inheritance
import io.github.catlinux.keynexa.vault.ProtectableField
import io.github.catlinux.keynexa.vault.Times
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultMeta
import io.github.catlinux.keynexa.vault.XmlElement
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.CharBuffer
import java.security.SecureRandom
import java.time.Instant
import java.util.HexFormat
import app.keemobile.kotpass.constants.AutoTypeObfuscation as KotpassObfuscation
import app.keemobile.kotpass.constants.GroupOverride as KotpassOverride
import app.keemobile.kotpass.database.Credentials as KotpassCredentials
import app.keemobile.kotpass.models.AutoTypeData as KotpassAutoType
import app.keemobile.kotpass.models.BinaryData as KotpassBinaryData
import app.keemobile.kotpass.models.BinaryReference as KotpassBinaryReference
import app.keemobile.kotpass.models.CustomDataValue as KotpassCustomDataValue
import app.keemobile.kotpass.models.CustomIcon as KotpassCustomIcon
import app.keemobile.kotpass.models.DeletedObject as KotpassDeletedObject
import app.keemobile.kotpass.models.Entry as KotpassEntry
import app.keemobile.kotpass.models.FormatVersion as KotpassFormatVersion
import app.keemobile.kotpass.models.Group as KotpassGroup
import app.keemobile.kotpass.models.Meta as KotpassMeta
import app.keemobile.kotpass.models.TimeData as KotpassTimeData

private const val KEYNEXA_GENERATOR = "KeyNexa"
private const val KDBX_MAJOR_V3 = 3
private const val KDBX_MAJOR_V4 = 4
private const val WIPE_CHAR = '\u0000'
private const val MESSAGE_UNSUPPORTED_FORMAT = "This vault format cannot be written."

/** Builds the library's credentials without ever turning the password into a [String]. */
internal fun Credentials.toKotpass(): KotpassCredentials {
    if (isPreHashed) {
        val passwordHash = passwordHashCopy()
        val keyHash = keyHashCopy()
        try {
            return KotpassCredentials.fromHashes(passwordHash, keyHash)
        } finally {
            // fromHashes copies what it keeps.
            passwordHash?.fill(0)
            keyHash?.fill(0)
        }
    }
    val passwordChars = passwordCopy()
    val keyFile = keyFileCopy()
    var passwordBytes: ByteArray? = null

    try {
        val passphrase =
            passwordChars?.let { chars ->
                val buffer = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(chars))
                val bytes = ByteArray(buffer.remaining()).also(buffer::get)
                passwordBytes = bytes
                EncryptedValue.fromBinary(bytes)
            }

        return when {
            passphrase != null && keyFile != null -> KotpassCredentials.from(passphrase, keyFile)
            passphrase != null -> KotpassCredentials.from(passphrase)
            keyFile != null -> KotpassCredentials.from(keyFile)
            else -> error("Credentials without password or key file")
        }
    } finally {
        passwordChars?.fill(WIPE_CHAR)
        // The library keeps the key file array as its encrypted key, so it is handed over instead of wiped.
        passwordBytes?.fill(0)
    }
}

/** Builds a database with the domain's format, with fresh seeds, iv and salts. */
internal fun Vault.toKeePassDatabase(
    credentials: KotpassCredentials,
    random: SecureRandom = SecureRandom(),
): KeePassDatabase {
    val majorVersion = format.version.major
    if (majorVersion != KDBX_MAJOR_V3 && majorVersion != KDBX_MAJOR_V4) {
        throw VaultException.UnsupportedFormat(MESSAGE_UNSUPPORTED_FORMAT)
    }

    val cipher = format.cipher.toKotpassCipher()
    val compression = format.compression.toKotpassCompression()
    val version = KotpassFormatVersion(majorVersion.toShort(), format.version.minor.toShort())
    val publicCustomData = format.publicCustomData.mapValues { (_, value) -> value.toKotpassVariant() }
    val content =
        DatabaseContent(
            meta = meta.toKotpass(),
            group = root.toKotpass(),
            deletedObjects = deletedObjects.map { KotpassDeletedObject(it.id, it.deletedAt ?: Instant.EPOCH) },
            unknownElements = unknownElements.map(XmlElement::toNode),
            unknownRootElements = unknownRootElements.map(XmlElement::toNode),
        )
    val created =
        when (majorVersion) {
            KDBX_MAJOR_V3 -> KeePassDatabase.Ver3x.create(root.name, KotpassMeta(), credentials, random)
            else -> KeePassDatabase.Ver4x.create(root.name, KotpassMeta(), credentials, random)
        }
    val adjusted =
        when (created) {
            is KeePassDatabase.Ver3x ->
                created.copy(
                    header =
                        created.header.copy(
                            version = version,
                            cipherId = cipher.uuid,
                            compression = compression,
                            encryptionIV = randomByteString(cipher.ivLength.toInt(), random),
                            transformRounds = format.kdf.toAesRounds(),
                        ),
                    content = content,
                )
            is KeePassDatabase.Ver4x ->
                created.copy(
                    header =
                        created.header.copy(
                            version = version,
                            cipherId = cipher.uuid,
                            compression = compression,
                            encryptionIV = randomByteString(cipher.ivLength.toInt(), random),
                            kdfParameters = format.kdf.toKdfParameters(random),
                            publicCustomData = publicCustomData,
                        ),
                    content = content,
                )
        }

    return adjusted.modifyBinaries { binaries.toKotpass() }
}

internal fun VaultMeta.toKotpass(): KotpassMeta =
    KotpassMeta(
        generator = KEYNEXA_GENERATOR,
        settingsChanged = settingsChanged,
        name = name,
        nameChanged = nameChanged,
        description = description,
        descriptionChanged = descriptionChanged,
        defaultUser = defaultUser,
        defaultUserChanged = defaultUserChanged,
        maintenanceHistoryDays = maintenanceHistoryDays.toUInt(),
        color = color,
        masterKeyChanged = masterKeyChanged,
        masterKeyChangeRec = masterKeyChangeRecommendedDays,
        masterKeyChangeForce = masterKeyChangeForcedDays,
        recycleBinEnabled = recycleBinEnabled,
        recycleBinUuid = recycleBinGroup,
        recycleBinChanged = recycleBinChanged,
        entryTemplatesGroup = entryTemplatesGroup,
        entryTemplatesGroupChanged = entryTemplatesGroupChanged,
        historyMaxItems = historyMaxItems,
        historyMaxSize = historyMaxSizeBytes,
        lastSelectedGroup = lastSelectedGroup,
        lastTopVisibleGroup = lastTopVisibleGroup,
        memoryProtection =
            protectedFields
                .map { field ->
                    when (field) {
                        ProtectableField.Title -> MemoryProtectionFlag.Title
                        ProtectableField.UserName -> MemoryProtectionFlag.UserName
                        ProtectableField.Password -> MemoryProtectionFlag.Password
                        ProtectableField.Url -> MemoryProtectionFlag.Url
                        ProtectableField.Notes -> MemoryProtectionFlag.Notes
                    }
                }.toSet(),
        customIcons =
            customIcons.mapValues { (_, icon) ->
                KotpassCustomIcon(icon.data, icon.name, icon.lastModified)
            },
        customData = customData.mapValues { (_, item) -> KotpassCustomDataValue(item.value, item.lastModified) },
        unknownElements = unknownElements.map(XmlElement::toNode),
    )

internal fun Times.toKotpass(): KotpassTimeData =
    KotpassTimeData(
        creationTime = created,
        lastAccessTime = lastAccessed,
        lastModificationTime = lastModified,
        locationChanged = locationChanged,
        expiryTime = expiry,
        expires = expires,
        usageCount = usageCount,
    )

internal fun Group.toKotpass(): KotpassGroup =
    KotpassGroup(
        uuid = id,
        name = name,
        notes = notes,
        icon = icon.toPredefinedIcon(),
        customIconUuid = icon.custom,
        times = times.toKotpass(),
        expanded = expanded,
        defaultAutoTypeSequence = defaultAutoTypeSequence,
        enableAutoType = autoType.toKotpass(),
        enableSearching = searching.toKotpass(),
        lastTopVisibleEntry = lastTopVisibleEntry,
        previousParentGroup = previousParentGroup,
        tags = tags,
        groups = groups.map { it.toKotpass() },
        entries = entries.map { it.toKotpass() },
        customData = customData.mapValues { (_, item) -> KotpassCustomDataValue(item.value, item.lastModified) },
        unknownElements = unknownElements.map(XmlElement::toNode),
    )

internal fun Entry.toKotpass(): KotpassEntry =
    KotpassEntry(
        uuid = id,
        icon = icon.toPredefinedIcon(),
        customIconUuid = icon.custom,
        foregroundColor = foregroundColor,
        backgroundColor = backgroundColor,
        overrideUrl = overrideUrl,
        times = times.toKotpass(),
        autoType =
            autoType?.let { data ->
                KotpassAutoType(
                    enabled = data.enabled,
                    obfuscation =
                        when (data.obfuscation) {
                            AutoTypeObfuscation.None -> KotpassObfuscation.None
                            AutoTypeObfuscation.UseClipboard -> KotpassObfuscation.UseClipboard
                        },
                    defaultSequence = data.defaultSequence,
                    items = data.associations.map { AutoTypeItem(it.window, it.keystrokeSequence) },
                )
            },
        fields =
            EntryFields(
                fields.mapValues { (_, value) ->
                    if (value.protected) {
                        EntryValue.Encrypted(EncryptedValue.fromString(value.value))
                    } else {
                        EntryValue.Plain(value.value)
                    }
                },
            ),
        tags = tags,
        binaries = attachments.map { KotpassBinaryReference(it.id.toByteString(), it.name) },
        history = history.map { it.toKotpass() },
        customData = customData.mapValues { (_, item) -> KotpassCustomDataValue(item.value, item.lastModified) },
        previousParentGroup = previousParentGroup,
        qualityCheck = qualityCheck,
        unknownElements = unknownElements.map(XmlElement::toNode),
    )

private fun Icon.toPredefinedIcon(): PredefinedIcon = PredefinedIcon.entries.getOrNull(standardId) ?: PredefinedIcon.Key

private fun Inheritance.toKotpass(): KotpassOverride =
    when (this) {
        Inheritance.Inherit -> KotpassOverride.Inherit
        Inheritance.Enabled -> KotpassOverride.Enabled
        Inheritance.Disabled -> KotpassOverride.Disabled
    }

private fun BinaryId.toByteString(): ByteString = HexFormat.of().parseHex(hex).toByteString()

/** Keyed by the SHA-256 of the content, which is what the entry references look up. */
private fun Map<BinaryId, Binary>.toKotpass(): Map<ByteString, KotpassBinaryData> {
    val result = LinkedHashMap<ByteString, KotpassBinaryData>(size)
    for (binary in values) {
        val data = KotpassBinaryData.Uncompressed(binary.protectedInMemory, binary.content)
        result[data.hash] = data
    }
    return result
}
