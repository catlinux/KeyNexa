package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.session.ConflictReport
import io.github.catlinux.keynexa.sync.CopyProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the conflict banner shows for a report. */
class DesktopVaultConflictsTest {
    @Test
    fun saysNothingWhenThereIsNothingToTell() {
        assertNull(conflictNotice(null))
        assertNull(conflictNotice(ConflictReport(mergedNow = 0, deletable = emptyList(), failed = emptyList())))
    }

    @Test
    fun countsTheCopiesMergedJustNow() {
        val notice = conflictNotice(ConflictReport(mergedNow = 2, deletable = emptyList(), failed = emptyList()))

        assertEquals(2, notice?.mergedNow)
        assertEquals(0, notice?.mergedEarlier)
        assertEquals(0, notice?.deletable)
        assertTrue(notice?.problems?.isEmpty() == true)
        assertFalse(notice?.rejected == true)
    }

    @Test
    fun fallsBackToTheCopiesMergedEarlier() {
        val report = ConflictReport(mergedNow = 0, deletable = emptyList(), failed = listOf(CopyProblem.Damaged))

        val notice = conflictNotice(report)

        assertEquals(0, notice?.mergedNow)
        assertEquals(0, notice?.mergedEarlier)
        assertEquals(listOf(CopyProblem.Damaged), notice?.problems)
    }

    @Test
    fun keepsEveryReasonACopyCouldNotBeMerged() {
        val notice =
            conflictNotice(
                ConflictReport(
                    mergedNow = 1,
                    deletable = emptyList(),
                    failed = listOf(CopyProblem.OtherPassword, CopyProblem.OtherVault, CopyProblem.Unreadable),
                ),
            )

        assertEquals(
            listOf(CopyProblem.OtherPassword, CopyProblem.OtherVault, CopyProblem.Unreadable),
            notice?.problems,
        )
    }

    @Test
    fun saysWhenTheLastPasswordDidNotOpenACopy() {
        val notice =
            conflictNotice(
                ConflictReport(
                    mergedNow = 1,
                    deletable = emptyList(),
                    failed = listOf(CopyProblem.OtherPassword),
                    otherPasswordRejected = true,
                ),
            )

        assertTrue(notice?.rejected == true)
    }
}
