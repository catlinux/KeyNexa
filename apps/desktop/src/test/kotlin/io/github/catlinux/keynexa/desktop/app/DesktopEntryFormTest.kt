package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.vault.Attachment
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import io.github.catlinux.keynexa.vault.NewVault
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultEditor
import io.github.catlinux.keynexa.vault.binaryIdOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** The entry form: what it saves, when it is dirty, which names it refuses and where a new entry goes. */
class DesktopEntryFormTest {
    private companion object {
        const val WORK = "Work"
    }

    @Test
    fun keepsTheFieldsItWasGiven() {
        val initial = fieldsOf()
        val form = EntryForm(initial)

        assertEquals(initial, form.toFields())
        assertTrue(form.unchanged)
        assertFalse(form.blocked)
    }

    @Test
    fun isDirtyOnceSomethingWasTyped() {
        val form = EntryForm(fieldsOf())
        form.notes = "another note"

        assertFalse(form.unchanged)
    }

    @Test
    fun aNewEntryStartsEmptyAndBlocked() {
        val form = EntryForm(null)

        assertTrue(form.toFields().isEmpty())
        assertTrue(form.unchanged)
        assertTrue(form.blocked)
    }

    @Test
    fun blocksWhileTheTitleIsMissingOrACustomNameIsWrong() {
        val form = EntryForm(null)
        form.title = "GitHub"
        assertFalse(form.blocked)

        form.addCustom()
        form.custom[0].name = Entry.TITLE
        assertTrue(form.blocked)
        assertEquals(FieldNameProblem.Standard, form.nameProblem(0))

        form.custom[0].name = "Seed"
        assertFalse(form.blocked)

        form.addCustom()
        form.custom[1].name = "Seed"
        assertTrue(form.blocked)
        assertEquals(FieldNameProblem.Repeated, form.nameProblem(1))
        assertEquals(FieldNameProblem.Repeated, form.nameProblem(0))

        form.custom[1].name = "Other"
        assertFalse(form.blocked)
        assertNull(form.nameProblem(0))
    }

    @Test
    fun refusesTheStandardNamesAndTheRepeatedOnes() {
        assertEquals(FieldNameProblem.Standard, fieldNameProblem("Title", emptyList()))
        assertEquals(FieldNameProblem.Standard, fieldNameProblem(" Password ", emptyList()))
        assertEquals(FieldNameProblem.Repeated, fieldNameProblem("Seed", listOf(" Seed ")))
        assertNull(fieldNameProblem("Seed", listOf("Other")))
        assertNull(fieldNameProblem("  ", listOf("Seed")))
    }

    @Test
    fun keepsTheProtectionAFieldAlreadyHad() {
        val form = EntryForm(fieldsOf())

        assertEquals(true, form.toFields()[Entry.PASSWORD]?.protected)
        assertEquals(false, form.toFields()[Entry.USER_NAME]?.protected)
    }

    @Test
    fun protectsThePasswordOfANewEntry() {
        val form = EntryForm(null)
        form.title = "GitHub"
        form.password = "s3cret"

        assertEquals(true, form.toFields()[Entry.PASSWORD]?.protected)
    }

    @Test
    fun keepsAnEmptyStandardFieldOnlyWhenTheEntryHadIt() {
        val form = EntryForm(fieldsOf())
        form.userName = ""

        assertTrue(form.toFields().containsKey(Entry.USER_NAME))

        val fresh = EntryForm(null)
        fresh.title = "GitHub"
        assertFalse(fresh.toFields().containsKey(Entry.USER_NAME))
    }

    @Test
    fun clearsEverythingWhenItIsLeft() {
        val form = EntryForm(fieldsOf())
        form.addCustom()

        form.clear()

        assertEquals("", form.title)
        assertEquals("", form.userName)
        assertEquals("", form.password)
        assertEquals("", form.notes)
        assertTrue(form.custom.isEmpty())
    }

    @Test
    fun aNewEntryGoesToTheSelectedGroupOrToTheRoot() {
        val vault = vaultWithWork()
        val work = vault.root.groups.first { it.name == WORK }
        val bin = UUID.randomUUID()
        val withBin = vault.copy(meta = vault.meta.copy(recycleBinGroup = bin))

        assertEquals(work.id, newEntryGroup(withBin, work.id, searching = false))
        assertEquals(vault.root.id, newEntryGroup(withBin, null, searching = false))
        assertEquals(vault.root.id, newEntryGroup(withBin, bin, searching = false))
        assertEquals(vault.root.id, newEntryGroup(withBin, work.id, searching = true))
    }

    @Test
    fun addsANewEntryWithItsFieldsAndAttachmentsInPlace() {
        val vault = vaultWithWork()
        val work = vault.root.groups.first { it.name == WORK }
        val content = "hello".toByteArray()
        val fields = fieldsOf()
        val attachments = listOf(Attachment("notes.txt", binaryIdOf(content)))

        val added = addNewEntry(vault, work.id, fields, attachments, listOf(content))

        val addedWork = added.root.groups.first { it.name == WORK }
        val entry = addedWork.entries.single()
        assertEquals("GitHub", entry.title)
        assertEquals(fields, entry.fields)
        assertEquals(listOf("notes.txt"), entry.attachments.map { it.name })
        assertTrue(added.binaries.containsKey(binaryIdOf(content)))
        assertTrue(entry.history.isEmpty())
    }

    private fun vaultWithWork(): Vault {
        val created = NewVault().create("Personal")
        return VaultEditor().addGroup(created, created.root.id, WORK)
    }

    private fun fieldsOf(): Map<String, FieldValue> =
        mapOf(
            Entry.TITLE to FieldValue("GitHub"),
            Entry.USER_NAME to FieldValue("ana"),
            Entry.PASSWORD to FieldValue("s3cret", protected = true),
            Entry.URL to FieldValue("https://github.com"),
            Entry.NOTES to FieldValue("work login"),
            "Seed" to FieldValue("abc"),
        )
}
