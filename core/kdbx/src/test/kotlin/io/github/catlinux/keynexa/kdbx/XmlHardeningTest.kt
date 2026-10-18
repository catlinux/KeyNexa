package io.github.catlinux.keynexa.kdbx

import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.redundent.kotlin.xml.parse
import java.io.ByteArrayInputStream

/**
 * The XML parser kotpass uses for vault content and XML key files must never resolve external entities: a key file
 * is any file the user picks, so a hostile one could otherwise read a local file into the key or reach the network.
 */
class XmlHardeningTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun anExternalEntityIsNeverResolved() {
        val secret = folder.newFile("secret.txt").apply { writeText("TOP-SECRET-CONTENT") }
        val hostile =
            """
            <?xml version="1.0"?>
            <!DOCTYPE KeyFile [<!ENTITY leak SYSTEM "${secret.toURI()}">]>
            <KeyFile><Key><Data>&leak;</Data></Key></KeyFile>
            """.trimIndent()

        val text = runCatching { parse(ByteArrayInputStream(hostile.toByteArray())).toString() }.getOrDefault("")

        assertFalse(text.contains("TOP-SECRET-CONTENT"))
    }

    @Test
    fun anOrdinaryKeyFileStillParses() {
        val keyFile = "<KeyFile><Meta><Version>2.0</Version></Meta><Key><Data>AAAA</Data></Key></KeyFile>"

        val text = parse(ByteArrayInputStream(keyFile.toByteArray())).toString()

        assertFalse(text.isEmpty())
    }
}
