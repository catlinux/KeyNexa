package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

private const val URL = "https://example.org"
private const val TOTP = "otpauth://totp/KeyNexa:ana?secret=JBSWY3DPEHPK3PXP&period=30&digits=6"

/** What the read-only detail works out without drawing anything: its rows and its subtitle. */
class DesktopEntryDetailTest {
    private fun entry(
        url: String = URL,
        withTotp: Boolean = false,
    ): Entry {
        val fields =
            buildMap {
                put(Entry.USER_NAME, FieldValue("ana"))
                put(Entry.PASSWORD, FieldValue("secret"))
                if (url.isNotEmpty()) put(Entry.URL, FieldValue(url))
                if (withTotp) put("otp", FieldValue(TOTP))
            }
        return Entry(id = UUID.randomUUID(), fields = fields)
    }

    @Test
    fun showsTheCoreRowsInOrder() {
        assertEquals(
            listOf(CoreField.UserName, CoreField.Password, CoreField.Url),
            coreFields(entry()),
        )
    }

    @Test
    fun addsTheOneTimeCodeWhenTheEntryHasOne() {
        assertEquals(
            listOf(CoreField.UserName, CoreField.Password, CoreField.Totp, CoreField.Url),
            coreFields(entry(withTotp = true)),
        )
    }

    @Test
    fun leavesTheAddressOutWhenItIsEmpty() {
        assertEquals(
            listOf(CoreField.UserName, CoreField.Password),
            coreFields(entry(url = "")),
        )
    }

    @Test
    fun joinsTheGroupAndTheDate() {
        assertEquals(
            "Personal › Correo · Modified 3 Oct 2026",
            detailSubtitle("Personal › Correo", "Modified 3 Oct 2026"),
        )
    }

    @Test
    fun showsOnlyTheGroupWhenThereIsNoDate() {
        assertEquals("Personal › Correo", detailSubtitle("Personal › Correo", ""))
    }

    @Test
    fun showsOnlyTheDateWhenThereIsNoGroup() {
        assertEquals("Modified 3 Oct 2026", detailSubtitle("", "Modified 3 Oct 2026"))
    }

    @Test
    fun hasNoSubtitleWhenNeitherIsThere() {
        assertEquals("", detailSubtitle("", ""))
    }
}
