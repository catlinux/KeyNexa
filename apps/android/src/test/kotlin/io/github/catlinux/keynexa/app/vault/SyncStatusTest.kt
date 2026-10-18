package io.github.catlinux.keynexa.app.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncStatusTest {
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun neverWarnsWhenTheSettingIsOff() {
        assertNull(syncWarning(lastModifiedMillis = 0, nowMillis = 100 * day, warnDays = 0))
    }

    @Test
    fun neverWarnsWithoutAModificationTime() {
        assertNull(syncWarning(lastModifiedMillis = null, nowMillis = 100 * day, warnDays = 7))
    }

    @Test
    fun staysQuietBeforeTheLimit() {
        assertNull(syncWarning(lastModifiedMillis = 0, nowMillis = 7 * day - 1, warnDays = 7))
    }

    @Test
    fun warnsExactlyAtTheLimit() {
        assertEquals(7, syncWarning(lastModifiedMillis = 0, nowMillis = 7 * day, warnDays = 7))
    }

    @Test
    fun reportsTheDaysWithoutChanges() {
        assertEquals(10, syncWarning(lastModifiedMillis = 5 * day, nowMillis = 15 * day + 1, warnDays = 3))
    }
}
