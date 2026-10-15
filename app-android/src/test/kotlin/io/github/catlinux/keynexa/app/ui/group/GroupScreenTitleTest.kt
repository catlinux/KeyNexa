package io.github.catlinux.keynexa.app.ui.group

import org.junit.Assert.assertEquals
import org.junit.Test

/** The group screen title: the vault name at the root, the group name elsewhere. */
class GroupScreenTitleTest {
    @Test
    fun theRootShowsTheVaultName() {
        assertEquals("My vault", groupScreenTitle(groupName = "Root", isRoot = true, rootTitle = "My vault"))
    }

    @Test
    fun theRootFallsBackToTheGroupNameWithoutAVaultName() {
        assertEquals("Root", groupScreenTitle(groupName = "Root", isRoot = true, rootTitle = null))
    }

    @Test
    fun aSubgroupShowsItsOwnName() {
        assertEquals("Internet", groupScreenTitle(groupName = "Internet", isRoot = false, rootTitle = "My vault"))
        assertEquals("Internet", groupScreenTitle(groupName = "Internet", isRoot = false, rootTitle = null))
    }
}
