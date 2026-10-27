package io.github.catlinux.keynexa.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpdateManifestTest {
    private val firstSha = "abcdef0123456789".repeat(4)
    private val secondSha = "2".repeat(64)
    private val thirdSha = "3".repeat(64)

    private fun validLines(): List<String> =
        listOf(
            "keynexa-update 1",
            "version 1.10.0",
            "released 2026-10-27",
            "file KeyNexa-offline.apk 4194304 $firstSha",
            "file KeyNexa-cloud.apk 4194304 $secondSha",
            "file KeyNexa-1.9.0.msi 1048576 $thirdSha",
        )

    private fun parseLines(lines: List<String>): UpdateManifest? =
        UpdateManifest.parse(lines.joinToString("\n").toByteArray(Charsets.UTF_8))

    private fun withFileLine(line: String): List<String> = validLines().toMutableList().also { it[3] = line }

    @Test
    fun parsesAValidManifest() {
        val manifest = parseLines(validLines())

        assertNotNull(manifest)
        assertEquals(SemVer(1, 10, 0), manifest?.version)
        assertEquals(LocalDate.of(2026, 10, 27), manifest?.released)
        assertEquals(3, manifest?.files?.size)
        assertEquals(ManifestFile("KeyNexa-cloud.apk", 4194304L, secondSha), manifest?.file("KeyNexa-cloud.apk"))
        assertNull(manifest?.file("KeyNexa.exe"))
    }

    @Test
    fun allowsASingleTrailingNewline() {
        val text = validLines().joinToString("\n") + "\n"
        assertNotNull(UpdateManifest.parse(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun rejectsADifferentHeader() {
        assertNull(parseLines(validLines().toMutableList().also { it[0] = "keynexa-update 2" }))
        assertNull(parseLines(validLines().toMutableList().also { it[0] = "keynexa-updates 1" }))
    }

    @Test
    fun rejectsACarriageReturnAnywhere() {
        val text = validLines().joinToString("\r\n")
        assertNull(UpdateManifest.parse(text.toByteArray(Charsets.UTF_8)))
        assertNull(UpdateManifest.parse(validLines().joinToString("\n").plus("\r").toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun rejectsAnUnknownKey() {
        val lines = validLines().toMutableList().also { it.add(1, "comment hello") }
        assertNull(parseLines(lines))
    }

    @Test
    fun rejectsARepeatedVersion() {
        val lines = validLines().toMutableList().also { it.add(2, "version 1.10.0") }
        assertNull(parseLines(lines))
    }

    @Test
    fun rejectsARepeatedReleasedLine() {
        val lines = validLines().toMutableList().also { it.add(3, "released 2026-10-27") }
        assertNull(parseLines(lines))
    }

    @Test
    fun rejectsAMissingReleased() {
        val lines = validLines().toMutableList().also { it.removeAt(2) }
        assertNull(parseLines(lines))
    }

    @Test
    fun rejectsAManifestWithoutFiles() {
        assertNull(parseLines(validLines().take(3)))
    }

    @Test
    fun rejectsMoreThan32Files() {
        val lines = validLines().take(3).toMutableList()
        repeat(33) { index -> lines.add("file KeyNexa-$index.msi 1024 $firstSha") }
        assertNull(parseLines(lines))
    }

    @Test
    fun accepts32Files() {
        val lines = validLines().take(3).toMutableList()
        repeat(32) { index -> lines.add("file KeyNexa-$index.msi 1024 $firstSha") }
        assertEquals(32, parseLines(lines)?.files?.size)
    }

    @Test
    fun rejectsADotDotInAName() {
        assertNull(parseLines(withFileLine("file ..\\KeyNexa.msi 1024 $firstSha")))
        assertNull(parseLines(withFileLine("file KeyNexa..msi 1024 $firstSha")))
    }

    @Test
    fun rejectsASlashInAName() {
        assertNull(parseLines(withFileLine("file sub/KeyNexa.msi 1024 $firstSha")))
        assertNull(parseLines(withFileLine("file ..\\KeyNexa.msi 1024 $firstSha")))
    }

    @Test
    fun rejectsARepeatedFileName() {
        val lines = validLines().toMutableList().also { it[4] = "file KeyNexa-offline.apk 4194304 $secondSha" }
        assertNull(parseLines(lines))
    }

    @Test
    fun rejectsASizeOfZero() {
        assertNull(parseLines(withFileLine("file KeyNexa.msi 0 $firstSha")))
    }

    @Test
    fun rejectsASizeWithLeadingZeros() {
        assertNull(parseLines(withFileLine("file KeyNexa.msi 0123 $firstSha")))
    }

    @Test
    fun rejectsASizeAboveOneGiB() {
        assertNull(parseLines(withFileLine("file KeyNexa.msi 1073741825 $firstSha")))
    }

    @Test
    fun acceptsASizeOfOneGiB() {
        assertNotNull(parseLines(withFileLine("file KeyNexa.msi 1073741824 $firstSha")))
    }

    @Test
    fun rejectsANonDecimalSize() {
        assertNull(parseLines(withFileLine("file KeyNexa.msi +1 $firstSha")))
        assertNull(parseLines(withFileLine("file KeyNexa.msi 1_000 $firstSha")))
    }

    @Test
    fun rejectsAnUppercaseSha256() {
        assertNull(parseLines(withFileLine("file KeyNexa.msi 1024 ${firstSha.uppercase()}")))
    }

    @Test
    fun rejectsAShortSha256() {
        assertNull(parseLines(withFileLine("file KeyNexa.msi 1024 ${firstSha.drop(1)}")))
    }

    @Test
    fun rejectsMoreThan16384Bytes() {
        val text = validLines().joinToString("\n") + "\n" + "x".repeat(16400)
        assertNull(UpdateManifest.parse(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun rejectsInvalidUtf8() {
        val bytes = validLines().joinToString("\n").toByteArray(Charsets.UTF_8)
        bytes[bytes.size - 2] = 0xFF.toByte()
        assertNull(UpdateManifest.parse(bytes))
    }

    @Test
    fun rejectsABlankIntermediateLine() {
        val lines = validLines().toMutableList().also { it.add(3, "") }
        assertNull(parseLines(lines))
    }

    @Test
    fun rejectsSeparatorsThatAreNotExactlyOneSpace() {
        assertNull(parseLines(withFileLine("file  KeyNexa.msi 1024 $firstSha")))
        assertNull(parseLines(withFileLine("file KeyNexa.msi 1024 $firstSha ")))
        assertNull(parseLines(withFileLine(" file KeyNexa.msi 1024 $firstSha")))
        assertNull(parseLines(withFileLine("file KeyNexa.msi $firstSha")))
        assertNull(parseLines(withFileLine("file KeyNexa.msi 1024 $firstSha extra")))
    }

    @Test
    fun rejectsAnImpossibleReleaseDate() {
        val lines = validLines().toMutableList().also { it[2] = "released 2026-02-30" }
        assertNull(parseLines(lines))
        assertNull(parseLines(validLines().toMutableList().also { it[2] = "released 27-10-2026" }))
    }

    @Test
    fun parsesValidVersions() {
        assertEquals(SemVer(0, 0, 0), SemVer.parse("0.0.0"))
        assertEquals(SemVer(1, 2, 3), SemVer.parse("1.2.3"))
        assertEquals(SemVer(999999999, 0, 1), SemVer.parse("999999999.0.1"))
        assertEquals("1.2.3", SemVer.parse("1.2.3").toString())
    }

    @Test
    fun rejectsVersionsWithLeadingZeros() {
        assertNull(SemVer.parse("01.2.3"))
        assertNull(SemVer.parse("1.02.3"))
        assertNull(SemVer.parse("1.2.03"))
    }

    @Test
    fun rejectsMalformedVersions() {
        listOf("", "1", "1.2", "1.2.3.4", "v1.2.3", " 1.2.3", "1.2.3 ", "1.2.3\n", "1..3", "-1.2.3")
            .forEach { assertNull(it, SemVer.parse(it)) }
        assertNull(SemVer.parse("1.2.3-beta"))
        assertNull(SemVer.parse("1000000000.0.0"))
    }

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(SemVer.parse("1.10.0")!! > SemVer.parse("1.9.9")!!)
        assertTrue(SemVer.parse("2.0.0")!! > SemVer.parse("1.99.99")!!)
        assertTrue(SemVer.parse("1.2.3")!! < SemVer.parse("1.2.4")!!)
        assertEquals(0, SemVer.parse("1.2.3")!!.compareTo(SemVer.parse("1.2.3")!!))
    }
}
