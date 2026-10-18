package io.github.catlinux.keynexa.kdbx

import app.keemobile.kotpass.constants.MemoryProtectionFlag
import app.keemobile.kotpass.cryptography.format.BaseCiphers
import app.keemobile.kotpass.cryptography.format.TwofishCipher
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.header.DatabaseHeader
import app.keemobile.kotpass.database.header.KdfParameters
import app.keemobile.kotpass.database.header.VariantItem
import app.keemobile.kotpass.database.modifiers.binaries
import app.keemobile.kotpass.models.EntryValue
import io.github.catlinux.keynexa.vault.Argon2Variant
import io.github.catlinux.keynexa.vault.Attachment
import io.github.catlinux.keynexa.vault.AutoType
import io.github.catlinux.keynexa.vault.AutoTypeAssociation
import io.github.catlinux.keynexa.vault.AutoTypeObfuscation
import io.github.catlinux.keynexa.vault.Binary
import io.github.catlinux.keynexa.vault.BinaryId
import io.github.catlinux.keynexa.vault.Cipher
import io.github.catlinux.keynexa.vault.Compression
import io.github.catlinux.keynexa.vault.CustomDataValue
import io.github.catlinux.keynexa.vault.CustomIcon
import io.github.catlinux.keynexa.vault.DeletedObject
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import io.github.catlinux.keynexa.vault.FormatVersion
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Icon
import io.github.catlinux.keynexa.vault.Inheritance
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.ProtectableField
import io.github.catlinux.keynexa.vault.Times
import io.github.catlinux.keynexa.vault.VariantValue
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultFormat
import io.github.catlinux.keynexa.vault.VaultMeta
import okio.ByteString
import java.security.MessageDigest
import java.util.HexFormat
import app.keemobile.kotpass.constants.AutoTypeObfuscation as KotpassObfuscation
import app.keemobile.kotpass.constants.GroupOverride as KotpassOverride
import app.keemobile.kotpass.models.Entry as KotpassEntry
import app.keemobile.kotpass.models.Group as KotpassGroup
import app.keemobile.kotpass.models.Meta as KotpassMeta
import app.keemobile.kotpass.models.TimeData as KotpassTimeData

private const val MESSAGE_UNSUPPORTED_CIPHER = "This vault uses a cipher that cannot be written."
private const val MESSAGE_UNSUPPORTED_ARGON2 = "Argon2 secret key or associated data is not supported."

/** Lowercase hexadecimal SHA-256 of the content, the id the domain uses to reference an attachment. */
internal fun ByteArray.sha256Hex(): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(this))

/** Translates the whole database, as the library parses it, into the domain model. */
internal fun KeePassDatabase.toVault(): Vault {
    val attachmentIds = HashMap<ByteString, BinaryId>()
    val domainBinaries = LinkedHashMap<BinaryId, Binary>()

    for ((hash, data) in binaries) {
        val content = data.getContent()
        val id = BinaryId(content.sha256Hex())
        attachmentIds[hash] = id
        domainBinaries[id] = Binary(content, data.memoryProtection)
    }

    val databaseContent = content
    return Vault(
        format = header.toVaultFormat(),
        meta = databaseContent.meta.toVaultMeta(),
        root = databaseContent.group.toDomain(attachmentIds),
        deletedObjects = databaseContent.deletedObjects.map { DeletedObject(it.id, it.deletionTime) },
        binaries = domainBinaries,
        unknownElements = databaseContent.unknownElements.map { it.toXmlElement() },
        unknownRootElements = databaseContent.unknownRootElements.map { it.toXmlElement() },
    )
}

/** Reads the format from the header, without touching the encrypted content. */
internal fun DatabaseHeader.toVaultFormat(): VaultFormat =
    VaultFormat(
        version = FormatVersion(version.major.toInt(), version.minor.toInt()),
        cipher =
            when (cipherId) {
                BaseCiphers.Aes.uuid -> Cipher.Aes256
                BaseCiphers.ChaCha20.uuid -> Cipher.ChaCha20
                TwofishCipher.uuid -> Cipher.Twofish256
                else -> throw VaultException.UnsupportedFormat(MESSAGE_UNSUPPORTED_CIPHER)
            },
        kdf =
            when (this) {
                is DatabaseHeader.Ver3x -> Kdf.AesKdf(transformRounds.toLong())
                is DatabaseHeader.Ver4x -> kdfParameters.toDomain()
            },
        compression =
            when (compression) {
                DatabaseHeader.Compression.None -> Compression.None
                DatabaseHeader.Compression.GZip -> Compression.Gzip
            },
        publicCustomData =
            when (this) {
                is DatabaseHeader.Ver4x -> publicCustomData.mapValues { (_, item) -> item.toDomain() }
                is DatabaseHeader.Ver3x -> emptyMap()
            },
    )

private fun KdfParameters.toDomain(): Kdf =
    when (this) {
        is KdfParameters.Aes -> Kdf.AesKdf(rounds.toLong())
        is KdfParameters.Argon2 -> {
            if (secretKey != null || associatedData != null) {
                throw VaultException.UnsupportedFormat(MESSAGE_UNSUPPORTED_ARGON2)
            }
            Kdf.Argon2(
                variant =
                    when (variant) {
                        KdfParameters.Argon2.Variant.Argon2d -> Argon2Variant.Argon2d
                        KdfParameters.Argon2.Variant.Argon2id -> Argon2Variant.Argon2id
                    },
                memoryBytes = memory.toLong(),
                iterations = iterations.toLong(),
                parallelism = parallelism.toInt(),
                version = version.toInt(),
            )
        }
    }

private fun VariantItem.toDomain(): VariantValue =
    when (this) {
        is VariantItem.Bool -> VariantValue.Bool(value)
        is VariantItem.Int32 -> VariantValue.Int32(value)
        is VariantItem.Int64 -> VariantValue.Int64(value)
        is VariantItem.UInt32 -> VariantValue.UInt32(value.toLong())
        is VariantItem.UInt64 -> VariantValue.UInt64(value.toLong())
        is VariantItem.StringUtf8 -> VariantValue.Text(value)
        is VariantItem.Bytes -> VariantValue.Bytes(value.toByteArray())
    }

internal fun KotpassMeta.toVaultMeta(): VaultMeta =
    VaultMeta(
        generator = generator,
        name = name,
        description = description,
        defaultUser = defaultUser,
        color = color,
        settingsChanged = settingsChanged,
        nameChanged = nameChanged,
        descriptionChanged = descriptionChanged,
        defaultUserChanged = defaultUserChanged,
        maintenanceHistoryDays = maintenanceHistoryDays.toLong(),
        masterKeyChanged = masterKeyChanged,
        masterKeyChangeRecommendedDays = masterKeyChangeRec,
        masterKeyChangeForcedDays = masterKeyChangeForce,
        recycleBinEnabled = recycleBinEnabled,
        recycleBinGroup = recycleBinUuid,
        recycleBinChanged = recycleBinChanged,
        entryTemplatesGroup = entryTemplatesGroup,
        entryTemplatesGroupChanged = entryTemplatesGroupChanged,
        historyMaxItems = historyMaxItems,
        historyMaxSizeBytes = historyMaxSize,
        lastSelectedGroup = lastSelectedGroup,
        lastTopVisibleGroup = lastTopVisibleGroup,
        protectedFields =
            memoryProtection
                .map { flag ->
                    when (flag) {
                        MemoryProtectionFlag.Title -> ProtectableField.Title
                        MemoryProtectionFlag.UserName -> ProtectableField.UserName
                        MemoryProtectionFlag.Password -> ProtectableField.Password
                        MemoryProtectionFlag.Url -> ProtectableField.Url
                        MemoryProtectionFlag.Notes -> ProtectableField.Notes
                    }
                }.toSet(),
        customIcons = customIcons.mapValues { (_, icon) -> CustomIcon(icon.data, icon.name, icon.lastModified) },
        customData = customData.mapValues { (_, item) -> CustomDataValue(item.value, item.lastModified) },
        unknownElements = unknownElements.map { it.toXmlElement() },
    )

private fun KotpassTimeData.toDomain(): Times =
    Times(
        created = creationTime,
        lastAccessed = lastAccessTime,
        lastModified = lastModificationTime,
        locationChanged = locationChanged,
        expiry = expiryTime,
        expires = expires,
        usageCount = usageCount,
    )

internal fun KotpassGroup.toDomain(attachmentIds: Map<ByteString, BinaryId>): Group =
    Group(
        id = uuid,
        name = name,
        notes = notes,
        icon = Icon(icon.ordinal, customIconUuid),
        times = times?.toDomain() ?: Times(),
        expanded = expanded,
        defaultAutoTypeSequence = defaultAutoTypeSequence,
        autoType = enableAutoType.toDomain(),
        searching = enableSearching.toDomain(),
        lastTopVisibleEntry = lastTopVisibleEntry,
        previousParentGroup = previousParentGroup,
        tags = tags,
        groups = groups.map { it.toDomain(attachmentIds) },
        entries = entries.map { it.toDomain(attachmentIds) },
        customData = customData.mapValues { (_, item) -> CustomDataValue(item.value, item.lastModified) },
        unknownElements = unknownElements.map { it.toXmlElement() },
    )

internal fun KotpassEntry.toDomain(attachmentIds: Map<ByteString, BinaryId>): Entry {
    val domainFields = LinkedHashMap<String, FieldValue>(fields.size)
    for ((key, value) in fields) {
        domainFields[key] =
            when (value) {
                is EntryValue.Plain -> FieldValue(value.content, protected = false)
                is EntryValue.Encrypted -> FieldValue(value.content, protected = true)
            }
    }

    return Entry(
        id = uuid,
        icon = Icon(icon.ordinal, customIconUuid),
        foregroundColor = foregroundColor,
        backgroundColor = backgroundColor,
        overrideUrl = overrideUrl,
        times = times?.toDomain() ?: Times(),
        autoType =
            autoType?.let { data ->
                AutoType(
                    enabled = data.enabled,
                    obfuscation =
                        when (data.obfuscation) {
                            KotpassObfuscation.None -> AutoTypeObfuscation.None
                            KotpassObfuscation.UseClipboard -> AutoTypeObfuscation.UseClipboard
                        },
                    defaultSequence = data.defaultSequence,
                    associations = data.items.map { AutoTypeAssociation(it.window, it.keystrokeSequence) },
                )
            },
        fields = domainFields,
        tags = tags,
        attachments = binaries.map { Attachment(it.name, attachmentIds[it.hash] ?: BinaryId(it.hash.hex())) },
        history = history.map { it.toDomain(attachmentIds) },
        customData = customData.mapValues { (_, item) -> CustomDataValue(item.value, item.lastModified) },
        previousParentGroup = previousParentGroup,
        qualityCheck = qualityCheck,
        unknownElements = unknownElements.map { it.toXmlElement() },
    )
}

private fun KotpassOverride.toDomain(): Inheritance =
    when (this) {
        KotpassOverride.Inherit -> Inheritance.Inherit
        KotpassOverride.Enabled -> Inheritance.Enabled
        KotpassOverride.Disabled -> Inheritance.Disabled
    }
