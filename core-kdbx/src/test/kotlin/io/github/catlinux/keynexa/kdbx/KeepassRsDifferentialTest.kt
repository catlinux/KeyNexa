package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Vault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * AC2 (differential part, ADR-0005): a vault read and written by KeyNexa must look the same to keepass-rs.
 *
 * Skipped unless `KEEPASS_RS_DUMP` points at the `kdbx-dump` binary of `tools/kdbx-dump-rs`. For every corpus vault it
 * compares the canonical JSON of keepass-rs on the original file (a), on the file KeyNexa rewrote (c) and of the
 * KeyNexa model itself (d): (a) == (c) and (d) == (a). Any difference must be listed, with its reason, in
 * `keepass-rs-known-differences.txt`; a listed difference that no longer happens also fails the test.
 */
class KeepassRsDifferentialTest {
    private val engine = KotpassKdbxEngine()
    private val outputDir = File("build/tmp/differential")

    @Test
    fun keepassRsSeesTheSameVaultBeforeAndAfterKeyNexa() {
        val path = System.getenv("KEEPASS_RS_DUMP")
        assumeTrue("KEEPASS_RS_DUMP is not set", !path.isNullOrBlank())
        val binary = File(path!!)
        // Set but wrong (for example a path relative to another folder) must fail, never skip in silence.
        assertTrue("KEEPASS_RS_DUMP does not name a file: $path", binary.isFile)
        outputDir.mkdirs()
        val known = knownDifferences()
        val observed = mutableSetOf<String>()

        try {
            for (vault in TestVaults.standard) {
                compare(binary!!, vault, known, observed)
            }
        } finally {
            outputDir.deleteRecursively()
        }

        val stale = known.keys - observed
        assertEquals("known differences no longer happen: $stale", emptySet<String>(), stale)
    }

    private fun compare(
        binary: File,
        vault: TestVault,
        known: Map<String, String>,
        observed: MutableSet<String>,
    ) {
        val password = vault.password
        val keyFile = vault.keyFile
        val credentials = Credentials.of(password = password?.toCharArray(), keyFile = keyFile?.readBytes())
        val original = vault.file
        val rewritten = File(outputDir, vault.fileName)

        try {
            val decoded = engine.decode(original.readBytes(), credentials)
            rewritten.writeBytes(engine.encode(decoded, credentials))

            val keepassOriginal = dump(binary, original, password, keyFile)
            val keepassRewritten = dump(binary, rewritten, password, keyFile)
            val model = canonicalJson(decoded)

            check(vault.fileName, "a==c", keepassOriginal, keepassRewritten, known, observed)
            check(vault.fileName, "d==a", model, keepassOriginal, known, observed)
        } finally {
            credentials.wipe()
        }
    }

    @Suppress("LongParameterList")
    private fun check(
        fileName: String,
        comparison: String,
        expected: String,
        actual: String,
        known: Map<String, String>,
        observed: MutableSet<String>,
    ) {
        if (expected == actual) return
        val key = "$fileName|$comparison"
        val reason = known[key]
        if (reason != null) {
            observed += key
            return
        }
        assertEquals("$fileName ($comparison) differs: ${difference(expected, actual)}", expected, actual)
    }

    private fun dump(
        binary: File,
        vault: File,
        password: String?,
        keyFile: File?,
    ): String {
        val command = mutableListOf(binary.absolutePath, vault.absolutePath)
        if (keyFile != null) {
            command += "--keyfile"
            command += keyFile.absolutePath
        }
        val process = ProcessBuilder(command).start()
        process.outputStream.use { it.write("${password.orEmpty()}\n".toByteArray(Charsets.UTF_8)) }
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        val error = process.errorStream.readBytes().toString(Charsets.UTF_8)
        assertEquals("kdbx-dump failed for ${vault.name}: $error", 0, process.waitFor())
        return output.trimEnd('\n', '\r')
    }

    private fun knownDifferences(): Map<String, String> {
        val stream = javaClass.getResourceAsStream("/keepass-rs-known-differences.txt") ?: return emptyMap()
        return stream
            .bufferedReader()
            .readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associateBy { line -> line.substringBefore('|', "") + "|" + line.split('|').getOrNull(1).orEmpty() }
    }

    private fun difference(
        expected: String,
        actual: String,
    ): String {
        val index =
            expected
                .zip(actual)
                .indexOfFirst { (left, right) -> left != right }
                .takeIf { it >= 0 }
                ?: minOf(expected.length, actual.length)
        val from = (index - 40).coerceAtLeast(0)
        return "at $index expected ...${expected.substring(from, (index + 40).coerceAtMost(expected.length))}... " +
            "but was ...${actual.substring(from, (index + 40).coerceAtMost(actual.length))}..."
    }

    private fun canonicalJson(vault: Vault): String {
        val groups = mutableListOf<Pair<String, String>>()
        val entries = mutableListOf<Pair<String, String>>()

        fun visit(
            group: Group,
            parent: UUID?,
        ) {
            groups += group.id.toString() to groupJson(group, parent)
            group.entries.forEach { entry -> entries += entry.id.toString() to entryJson(entry, group.id) }
            group.groups.forEach { child -> visit(child, group.id) }
        }
        visit(vault.root, null)

        val groupText = groups.sortedBy { it.first }.joinToString(",") { it.second }
        val entryText = entries.sortedBy { it.first }.joinToString(",") { it.second }
        return "{\"entries\":[$entryText],\"groups\":[$groupText]}"
    }

    private fun groupJson(
        group: Group,
        parent: UUID?,
    ): String {
        val parentText = parent?.let { quote(it.toString()) } ?: "null"
        return "{\"name\":${quote(group.name)},\"notes\":${quote(group.notes)}," +
            "\"parent\":$parentText,\"uuid\":${quote(group.id.toString())}}"
    }

    private fun entryJson(
        entry: Entry,
        groupId: UUID,
    ): String {
        val fields =
            entry.fields.entries
                .sortedBy { it.key }
                .joinToString(",") { (name, field) ->
                    "${quote(name)}:{\"protected\":${field.protected},\"value\":${quote(field.value)}}"
                }
        val tags = entry.tags.sorted().joinToString(",") { quote(it) }
        val attachments =
            entry.attachments
                .sortedBy { it.name }
                .joinToString(",") { "${quote(it.name)}:${quote(it.id.hex)}" }
        return "{\"attachments\":{$attachments},\"fields\":{$fields}," +
            "\"group\":${quote(groupId.toString())},\"history\":${entry.history.size}," +
            "\"tags\":[$tags],\"uuid\":${quote(entry.id.toString())}}"
    }

    // The escaping serde_json uses, so both canonical dumps are byte for byte the same.
    private fun quote(text: String): String {
        val builder = StringBuilder("\"")
        for (character in text) {
            when (character) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                else ->
                    if (character < ' ') {
                        builder.append("\\u").append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        builder.append(character)
                    }
            }
        }
        return builder.append('"').toString()
    }
}
