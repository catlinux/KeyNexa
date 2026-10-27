package io.github.catlinux.keynexa.update

import io.github.catlinux.keynexa.platform.writeAtomically
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Properties

/**
 * What the updater remembers (ADR-0015): when it last checked for a release, so it does so at most once a day, and
 * the highest version it has ever seen in a signed manifest, so a validly signed but older manifest served again
 * is refused (a rollback to a release with known flaws).
 *
 * A plain UTF-8 properties file, written atomically. Nothing secret goes in it, and a missing, unreadable or damaged
 * file reads as "never checked" and "nothing seen", which only makes the next check run.
 */
class UpdateStateStore(
    private val file: Path,
) {
    /** The last recorded check, or null when there is none or it cannot be read. */
    @Suppress("SwallowedException")
    fun lastCheck(): Instant? {
        val text = load().getProperty(LAST_CHECK)?.trim() ?: return null
        return try {
            Instant.parse(text)
        } catch (failure: DateTimeParseException) {
            null
        }
    }

    /** Replaces the stored check with [at], keeping the rest. */
    fun recordCheck(at: Instant) {
        save(load().apply { setProperty(LAST_CHECK, at.toString()) })
    }

    /** The highest version recorded, or null when there is none or it cannot be read. */
    fun highestSeen(): SemVer? = load().getProperty(HIGHEST_SEEN)?.trim()?.let(SemVer::parse)

    /** Records [version] when it is higher than the one stored; a lower one never lowers the bar. */
    fun recordSeen(version: SemVer) {
        val stored = highestSeen()
        if (stored != null && version <= stored) return
        save(load().apply { setProperty(HIGHEST_SEEN, version.toString()) })
    }

    @Suppress("SwallowedException")
    private fun load(): Properties {
        val properties = Properties()
        try {
            Files.newInputStream(file).use { properties.load(InputStreamReader(it, Charsets.UTF_8)) }
        } catch (failure: IOException) {
            return Properties()
        } catch (failure: IllegalArgumentException) {
            return Properties()
        }
        return properties
    }

    private fun save(properties: Properties) {
        val text =
            properties
                .stringPropertyNames()
                .sorted()
                .joinToString("") { name -> name + "=" + properties.getProperty(name) + "\n" }
        writeAtomically(file.toFile(), text.toByteArray(Charsets.UTF_8))
    }

    private companion object {
        const val LAST_CHECK = "lastCheck"
        const val HIGHEST_SEEN = "highestSeen"
    }
}
