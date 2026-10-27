package io.github.catlinux.keynexa.update

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** One file listed by an [UpdateManifest]: its name, its size in bytes and the SHA-256 of its content. */
data class ManifestFile(
    val name: String,
    val size: Long,
    val sha256: String,
)

/**
 * The signed description of a release: its version, its release date and the files it ships.
 *
 * The text format is plain on purpose, so checking for updates needs no JSON parser and no GitHub API
 * (ADR-0015). [parse] describes it and rejects anything it does not allow, byte for byte.
 */
data class UpdateManifest(
    val version: SemVer,
    val released: LocalDate,
    val files: List<ManifestFile>,
) {
    /** The file named [name], or null when the manifest does not list one. */
    fun file(name: String): ManifestFile? = files.firstOrNull { it.name == name }

    companion object {
        private const val HEADER = "keynexa-update 1"
        private const val VERSION_KEY = "version"
        private const val RELEASED_KEY = "released"
        private const val FILE_KEY = "file"
        private const val LINE_SEPARATOR = "\n"
        private const val FIELD_SEPARATOR = " "
        private const val MAX_BYTES = 16_384
        private const val MIN_FILES = 1
        private const val MAX_FILES = 32
        private const val MAX_FILE_SIZE = 1_073_741_824L
        private const val MAX_SIZE_DIGITS = 10
        private const val MAX_NAME_LENGTH = 128
        private const val SHA256_LENGTH = 64
        private const val PREFIX_LINES = 3
        private const val SIMPLE_FIELDS = 2
        private const val FILE_FIELDS = 4
        private const val CR: Byte = 13

        private val NAME_PATTERN = Regex("""^[A-Za-z0-9][A-Za-z0-9._-]{0,${MAX_NAME_LENGTH - 1}}\z""")
        private val SHA256_PATTERN = Regex("""^[0-9a-f]{$SHA256_LENGTH}\z""")
        private val SIZE_PATTERN = Regex("""^[1-9][0-9]{0,${MAX_SIZE_DIGITS - 1}}\z""")

        /**
         * Parses a manifest, or returns null on any problem; it never throws.
         *
         * The format is UTF-8 with LF endings:
         * ```
         * keynexa-update 1
         * version <major>.<minor>.<patch>
         * released <YYYY-MM-DD>
         * file <name> <size> <sha256>
         * ```
         * The header, the version and the release date are required and come in that order, followed by 1 to
         * 32 file lines. Every field is separated by exactly one space, a single trailing newline is allowed,
         * and names are plain file names (no directory) of at most 128 characters.
         */
        @Suppress("ReturnCount")
        fun parse(bytes: ByteArray): UpdateManifest? {
            if (bytes.size > MAX_BYTES) return null
            if (bytes.any { it == CR }) return null
            val text = decodeStrict(bytes) ?: return null

            val lines = text.removeSuffix(LINE_SEPARATOR).split(LINE_SEPARATOR)
            if (lines.size < PREFIX_LINES + MIN_FILES || lines.size > PREFIX_LINES + MAX_FILES) return null
            if (lines[0] != HEADER) return null

            val version = parseVersion(lines[1]) ?: return null
            val released = parseReleased(lines[2]) ?: return null
            val files = lines.drop(PREFIX_LINES).map { parseFile(it) ?: return null }
            if (files.map(ManifestFile::name).toSet().size != files.size) return null

            return UpdateManifest(version, released, files)
        }

        @Suppress("SwallowedException")
        private fun decodeStrict(bytes: ByteArray): String? =
            try {
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (e: CharacterCodingException) {
                null
            }

        /**
         * Splits a line on single spaces, or returns null when the separators are not exactly one space:
         * `String.split` drops trailing empty fields, so they are checked before splitting.
         */
        private fun fields(line: String): List<String>? {
            if (line.isEmpty() || line.startsWith(FIELD_SEPARATOR) || line.endsWith(FIELD_SEPARATOR)) return null
            if (line.contains(FIELD_SEPARATOR + FIELD_SEPARATOR)) return null
            return line.split(FIELD_SEPARATOR)
        }

        /** The value of a `key value` line, or null when the key or the shape does not match. */
        private fun singleValue(
            line: String,
            key: String,
        ): String? {
            val fields = fields(line) ?: return null
            return if (fields.size == SIMPLE_FIELDS && fields[0] == key) fields[1] else null
        }

        private fun parseVersion(line: String): SemVer? = singleValue(line, VERSION_KEY)?.let(SemVer::parse)

        private fun parseReleased(line: String): LocalDate? {
            val text = singleValue(line, RELEASED_KEY) ?: return null
            return try {
                LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                null
            }
        }

        private fun parseFile(line: String): ManifestFile? {
            val fields = fields(line) ?: return null
            if (fields.size != FILE_FIELDS || fields[0] != FILE_KEY) return null

            val (name, sizeText, sha256) = fields.subList(1, FILE_FIELDS)
            val size = sizeText.takeIf { SIZE_PATTERN.matches(it) }?.toLongOrNull() ?: return null
            val nameIsValid = NAME_PATTERN.matches(name) && !name.contains("..")
            val contentIsValid = size <= MAX_FILE_SIZE && SHA256_PATTERN.matches(sha256)

            return if (nameIsValid && contentIsValid) ManifestFile(name, size, sha256) else null
        }
    }
}
