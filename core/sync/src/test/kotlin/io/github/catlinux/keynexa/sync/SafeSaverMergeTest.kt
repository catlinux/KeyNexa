package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge as the save pipeline uses it: the file changed on another device while the user was editing, and a file
 * that belongs to another vault.
 */
class SafeSaverMergeTest {
    private val location = VaultLocation("vault.kdbx")
    private val credentials = Credentials.of(password = "correct horse".toCharArray())
    private val internet = uuid("$ROOT/Internet")
    private val github = uuid("$ROOT/Internet/github")

    private fun base(): Vault =
        vault {
            group("Internet", at(1)) {
                entry("github", at(2)) {
                    userName = "ana"
                    password = "v1"
                }
            }
        }

    @Test
    fun anEditHereAndAChangeFromAnotherDeviceAreBothSaved() {
        val world = SyncWorld(merger = KeePassMerger())
        val base = base()
        world.withFile(location, world.engine.encode(base, credentials))
        val loaded = world.loaded(location, base)
        val edited = base.editEntry(github, at(10)) { it.withField(PASSWORD, "v2") }
        val fromAnotherDevice = base.addEntry(internet, newEntry("shared", at(11)))
        world.withFile(location, world.engine.encode(fromAnotherDevice, credentials))

        val saved = world.saver.save(loaded, edited, credentials) as SaveOutcome.Saved

        assertTrue(saved.merged)
        val merged = world.decodeFile(location, credentials)
        assertEquals("v2", requireNotNull(merged.findEntry(github)).fields[PASSWORD]?.value)
        assertNotNull(merged.findEntry(uuid("shared")))
    }

    @Test
    fun anEntryDeletedOnTheOtherDeviceButEditedHereAfterwardsSurvivesTheSave() {
        val world = SyncWorld(merger = KeePassMerger())
        val base = base()
        world.withFile(location, world.engine.encode(base, credentials))
        val loaded = world.loaded(location, base)
        val edited = base.editEntry(github, at(12)) { it.withField(PASSWORD, "v2") }
        world.withFile(location, world.engine.encode(base.deleteEntry(github, at(10)), credentials))

        val saved = world.saver.save(loaded, edited, credentials) as SaveOutcome.Saved

        assertTrue(saved.merged)
        val merged = world.decodeFile(location, credentials)
        assertEquals("v2", requireNotNull(merged.findEntry(github)).fields[PASSWORD]?.value)
        assertTrue(merged.deletedObjects.isEmpty())
    }

    @Test
    fun aFileFromADifferentVaultIsNotSavedAndIsLeftAsItIs() {
        val world = SyncWorld(merger = KeePassMerger())
        val base = base()
        world.withFile(location, world.engine.encode(base, credentials))
        val loaded = world.loaded(location, base)
        val external = world.engine.encode(vault(root = "Other") { group("Internet", at(1)) }, credentials)
        world.withFile(location, external)

        val outcome = world.saver.save(loaded, base, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.Unmergeable, outcome.reason)
        assertEquals(FileState.Untouched, outcome.file)
        assertArrayEquals(external, world.storage.read(location))
    }
}
