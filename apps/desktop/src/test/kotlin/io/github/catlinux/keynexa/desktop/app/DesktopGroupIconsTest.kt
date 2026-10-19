package io.github.catlinux.keynexa.desktop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BIN_ICON = 43
private const val LAST_STANDARD_ICON = 68
private const val UNKNOWN_ICON = 999
private const val MIN_ICONS = 40
private const val MAX_ICONS = 50

class DesktopGroupIconsTest {
    @Test
    fun theCatalogueOffersFortyToFiftyIcons() {
        assertTrue(GroupIconCatalog.size in MIN_ICONS..MAX_ICONS)
    }

    @Test
    fun everyIconIsAStandardNumberUsedOnce() {
        val ids = GroupIconCatalog.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it in 0..LAST_STANDARD_ICON })
    }

    @Test
    fun theBinNumberIsLeftToTheBin() {
        assertTrue(GroupIconCatalog.none { it.id == BIN_ICON })
        assertSame(TrashIcon, groupIconVector(BIN_ICON))
    }

    @Test
    fun everySectionHasIconsAndTheDefaultIsInTheCatalogue() {
        GroupIconCategory.entries.forEach { category ->
            assertTrue("$category is empty", GroupIconCatalog.any { it.category == category })
        }
        assertTrue(GroupIconCatalog.any { it.id == DEFAULT_GROUP_ICON })
    }

    @Test
    fun aNumberOutsideTheCatalogueIsDrawnAsTheDefaultFolder() {
        assertSame(groupIconVector(DEFAULT_GROUP_ICON), groupIconVector(UNKNOWN_ICON))
        assertEquals(GroupIconCategory.General, categoryOf(UNKNOWN_ICON))
    }

    @Test
    fun everyDrawingBuilds() {
        GroupIconCatalog.forEach { assertTrue(it.vector.root.size > 0) }
    }
}
