package io.github.catlinux.keynexa.update

/**
 * A release version in the form `major.minor.patch`.
 *
 * Each part is a decimal number from 0 to 999,999,999 without leading zeros. There is no pre-release or build
 * metadata: the update channel only names releases, and the parts compare numerically, so `1.10.0` is newer
 * than `1.9.9`.
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemVer> {
    override fun toString(): String = "$major.$minor.$patch"

    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    companion object {
        private const val MAX_PART_DIGITS = 9

        private val PART = """(0|[1-9][0-9]{0,${MAX_PART_DIGITS - 1}})"""
        private val PATTERN = Regex("""^$PART\.$PART\.$PART\z""")

        /** Parses [text] as a version, or returns null when it is not exactly `major.minor.patch`. */
        fun parse(text: String): SemVer? {
            val match = PATTERN.matchEntire(text) ?: return null
            val (major, minor, patch) = match.destructured
            return SemVer(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
