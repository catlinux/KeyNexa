package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * AC2 (differential part): what the engine writes back opens in KeePassXC and exports to the same XML as the
 * original. Skipped when keepassxc-cli is not installed.
 */
class KeePassXcDifferentialTest {
    private val engine = KotpassKdbxEngine()
    private val outputDir = File("build/differential")

    @Test
    fun rewrittenVaultsExportTheSameXmlFromKeepassXc() {
        val cli = keepassXcCli()
        assumeTrue("keepassxc-cli is not installed", cli != null)
        outputDir.mkdirs()

        try {
            for (fileName in differentialVaults) {
                assertSameExport(cli!!, fileName)
            }
        } finally {
            outputDir.deleteRecursively()
        }
    }

    private fun assertSameExport(
        cli: String,
        fileName: String,
    ) {
        val vault = TestVaults.standard.first { it.fileName == fileName }
        val password = requireNotNull(vault.password)
        val rewritten = File(outputDir, fileName)
        val credentials = Credentials.of(password = password.toCharArray())

        try {
            rewritten.writeBytes(engine.encode(engine.decode(vault.file.readBytes(), credentials), credentials))
        } finally {
            credentials.wipe()
        }

        val original = normalize(parse(export(cli, vault.file, password)))
        val produced = normalize(parse(export(cli, rewritten, password)))

        assertNull("$fileName: ${firstDifference(original.documentElement, produced.documentElement)}", null)
    }

    private fun export(
        cli: String,
        file: File,
        password: String,
    ): String {
        val process =
            ProcessBuilder(cli, "export", "-q", "-f", "xml", file.absolutePath)
                .redirectErrorStream(true)
                .start()
        process.outputStream.use { it.write("$password\n".toByteArray()) }
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        assertEquals("keepassxc-cli export failed for ${file.name}", 0, process.waitFor())
        return output.substring(output.indexOf("<?xml"))
    }

    private fun keepassXcCli(): String? = CLI_CANDIDATES.firstOrNull(::isRunnable)

    private fun isRunnable(candidate: String): Boolean =
        try {
            val process = ProcessBuilder(candidate, "--version").redirectErrorStream(true).start()
            process.inputStream.readBytes()
            process.waitFor() == 0
        } catch (ignored: IOException) {
            false
        }

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        return factory.newDocumentBuilder().parse(xml.byteInputStream())
    }

    private fun normalize(document: Document): Document {
        removeGenerator(document)
        sortCustomData(document)
        return document
    }

    private fun removeGenerator(document: Document) {
        val generators = document.getElementsByTagName(GENERATOR).let { list -> List(list.length) { list.item(it) } }
        generators.forEach { it.parentNode.removeChild(it) }
    }

    private fun sortCustomData(document: Document) {
        val blocks = document.getElementsByTagName("CustomData").let { list -> List(list.length) { list.item(it) } }
        for (block in blocks) {
            val items = elementChildren(block).filter { it.tagName == "Item" }
            val sorted = items.sortedBy { keyOf(it) }
            if (items == sorted) continue
            items.forEach { block.removeChild(it) }
            sorted.forEach { block.appendChild(it) }
        }
    }

    private fun keyOf(item: Element): String =
        elementChildren(item).firstOrNull { it.tagName == "Key" }?.textContent.orEmpty()

    private fun firstDifference(
        expected: Element,
        actual: Element,
        path: String = "/",
    ): String? {
        val here = "$path${expected.tagName}"
        return when {
            expected.tagName != actual.tagName -> "$here: expected <${expected.tagName}> but was <${actual.tagName}>"
            attributesOf(expected) != attributesOf(actual) ->
                "$here: attributes ${attributesOf(expected)} != ${attributesOf(actual)}"
            childrenOf(expected).size != childrenOf(actual).size ->
                "$here: children ${childrenOf(expected).size} != ${childrenOf(actual).size} " +
                    "(${childrenOf(expected).map { it.nodeName }} vs ${childrenOf(actual).map { it.nodeName }})"
            else -> childDifference(expected, actual, here)
        }
    }

    private fun childDifference(
        expected: Element,
        actual: Element,
        here: String,
    ): String? {
        val expectedChildren = childrenOf(expected)
        val actualChildren = childrenOf(actual)

        for (index in expectedChildren.indices) {
            val expectedChild = expectedChildren[index]
            val actualChild = actualChildren[index]
            val difference =
                when {
                    expectedChild is Element && actualChild is Element ->
                        firstDifference(expectedChild, actualChild, "$here/")
                    expectedChild.textContent != actualChild.textContent ->
                        "$here: text '${expectedChild.textContent}' != '${actualChild.textContent}'"
                    else -> null
                }
            if (difference != null) return difference
        }
        return null
    }

    private fun childrenOf(element: Element): List<Node> =
        element.childNodes.let { list ->
            List(list.length) { list.item(it) }.filterNot { it is org.w3c.dom.Text && it.textContent.isBlank() }
        }

    private fun elementChildren(parent: Node): List<Element> =
        parent.childNodes.let { list -> List(list.length) { list.item(it) }.filterIsInstance<Element>() }

    private fun attributesOf(element: Element): List<String> =
        element.attributes
            .let { attributes -> List(attributes.length) { attributes.item(it) } }
            .map { "${it.nodeName}=${it.nodeValue}" }
            .sorted()

    private companion object {
        val CLI_CANDIDATES =
            listOf(
                "keepassxc-cli",
                "keepassxc-cli.exe",
                "C:/Program Files/KeePassXC/keepassxc-cli.exe",
            )

        const val GENERATOR = "Generator"

        val differentialVaults =
            listOf(
                "rich-aes-argon2d.kdbx",
                "rich-chacha20-aeskdf.kdbx",
                "rich-kdbx31-aes-aeskdf.kdbx",
                "rich-aes-argon2d-tags.kdbx",
                "kpxc-aeskdf.kdbx",
                "kpxc-twofish-argon2d.kdbx",
                "kpxc-argon2d-tags-totp.kdbx",
            )
    }
}
