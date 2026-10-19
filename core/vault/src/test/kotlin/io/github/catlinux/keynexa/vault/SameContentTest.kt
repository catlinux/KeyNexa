package io.github.catlinux.keynexa.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SameContentTest {
    private val editor = VaultEditor()

    @Test
    fun theOrderOfEntriesDoesNotMatter() {
        val base = NewVault().create("Test")
        val first = editor.addEntry(editor.addEntry(base, base.root.id, "One"), base.root.id, "Two")
        val reversed = first.copy(root = first.root.copy(entries = first.root.entries.reversed()))

        assertTrue(first.sameContentAs(reversed))
        assertTrue(first != reversed)
    }

    @Test
    fun differentContentIsDifferent() {
        val base = NewVault().create("Test")
        val one = editor.addEntry(base, base.root.id, "One")
        val two = editor.addEntry(base, base.root.id, "Two")

        assertFalse(one.sameContentAs(two))
        assertFalse(one.sameContentAs(base))
    }
}
