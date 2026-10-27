package io.github.catlinux.keynexa.desktop.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** [AppVersion] must report the version the build was made from, which lives in the VERSION file at the root. */
class AppVersionTest {
    @Test
    fun currentIsTheVersionFile() {
        val expected = versionFile().readText().trim()

        assertEquals(expected, AppVersion.current.toString())
    }

    /** Walks up from the working directory of the test, so it does not need to know where Gradle put it. */
    private fun versionFile(): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, VERSION)
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        throw AssertionError("no $VERSION file above the working directory")
    }

    private companion object {
        const val VERSION = "VERSION"
    }
}
