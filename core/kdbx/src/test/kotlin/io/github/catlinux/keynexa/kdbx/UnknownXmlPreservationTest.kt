package io.github.catlinux.keynexa.kdbx

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decodeFromXml
import app.keemobile.kotpass.database.encodeAsXml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Checks that the vendored kotpass keeps XML elements it does not understand, in Meta, Group,
 * Entry, history entries and the document root, across a decode/encode round trip.
 */
class UnknownXmlPreservationTest {
    private val credentials = Credentials.from(EncryptedValue.fromString("test"))

    @Test
    fun keepsUnknownElementsAcrossARoundTrip() {
        val first = roundTrip(minimalDatabaseXml())

        for ((element, expected) in unknownElements) {
            assertTrue("$element was dropped", first.contains(expected))
        }
        val rootEnd = first.indexOf("</Root>")
        assertTrue("UnknownRoot left <Root>", first.indexOf("<UnknownRoot ") in 0 until rootEnd)
        assertTrue("UnknownKeePassFile left <KeePassFile>", first.indexOf("<UnknownKeePassFile ") > rootEnd)

        val second = roundTrip(first)

        assertEquals("round trip is not idempotent", first, second)
    }

    // The plain XML context lives behind internal visibility, so the test uses the public database API.
    private fun roundTrip(xml: String): String {
        val database =
            xml.byteInputStream().use {
                KeePassDatabase.decodeFromXml(it, credentials)
            }

        return database.encodeAsXml()
    }

    private fun minimalDatabaseXml(): String =
        """
        |<KeePassFile>
        |    <Meta>
        |        <Generator>KeyNexa test</Generator>
        |        <UnknownMeta note="m">meta-value</UnknownMeta>
        |    </Meta>
        |    <Root>
        |        <Group>
        |            <UUID>${uuid(1)}</UUID>
        |            <Name>Root</Name>
        |            <Group>
        |                <UUID>${uuid(2)}</UUID>
        |                <Name>Child</Name>
        |                <UnknownGroup note="g">group-value</UnknownGroup>
        |            </Group>
        |            <Entry>
        |                <UUID>${uuid(3)}</UUID>
        |                <UnknownEntry note="e">entry-value</UnknownEntry>
        |                <History>
        |                    <Entry>
        |                        <UUID>${uuid(4)}</UUID>
        |                        <UnknownHistory note="h">history-value</UnknownHistory>
        |                    </Entry>
        |                </History>
        |            </Entry>
        |        </Group>
        |        <DeletedObjects/>
        |        <UnknownRoot note="r">root-value</UnknownRoot>
        |    </Root>
        |    <UnknownKeePassFile note="k">file-value</UnknownKeePassFile>
        |</KeePassFile>
        """.trimMargin()

    private fun uuid(index: Int): String {
        val bytes = ByteArray(16) { (index * 16 + it).toByte() }
        return Base64.getEncoder().encodeToString(bytes)
    }

    private companion object {
        val unknownElements =
            listOf(
                "UnknownKeePassFile" to "<UnknownKeePassFile note=\"k\">file-value</UnknownKeePassFile>",
                "UnknownMeta" to "<UnknownMeta note=\"m\">meta-value</UnknownMeta>",
                "UnknownRoot" to "<UnknownRoot note=\"r\">root-value</UnknownRoot>",
                "UnknownGroup" to "<UnknownGroup note=\"g\">group-value</UnknownGroup>",
                "UnknownEntry" to "<UnknownEntry note=\"e\">entry-value</UnknownEntry>",
                "UnknownHistory" to "<UnknownHistory note=\"h\">history-value</UnknownHistory>",
            )
    }
}
