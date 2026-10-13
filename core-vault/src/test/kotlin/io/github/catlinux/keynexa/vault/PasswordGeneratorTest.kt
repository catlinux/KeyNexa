package io.github.catlinux.keynexa.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * The password generator's promises: the length asked for, one character of every class enabled, no look-alike
 * characters when they are left out, options that cannot be built rejected, and reproducible randomness when a
 * seeded source is injected. Failure messages never carry a generated password.
 */
class PasswordGeneratorTest {
    private val generator = PasswordGenerator(Random(SEED))

    @Test
    fun theLengthAskedForIsTheLengthBuilt() {
        listOf(Options.MIN_LENGTH, MIDDLE_LENGTH, Options.MAX_LENGTH).forEach { length ->
            assertEquals(length, generator.generate(Options(length = length)).size)
        }
    }

    @Test
    fun everyEnabledClassAppearsInEveryPassword() {
        repeat(GENERATIONS) {
            val password = generator.generate(Options(excludeAmbiguous = true))

            assertTrue("a password had no lower-case letter", password.any { it in 'a'..'z' })
            assertTrue("a password had no upper-case letter", password.any { it in 'A'..'Z' })
            assertTrue("a password had no digit", password.any { it.isDigit() })
            assertTrue("a password had no symbol", password.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun aClassThatIsTurnedOffIsNotUsed() {
        val options = Options(lower = false, upper = false, digits = true, symbols = false)

        repeat(GENERATIONS) {
            val password = generator.generate(options)

            assertTrue("a password used a character outside the enabled class", password.all { it.isDigit() })
        }
    }

    @Test
    fun lookAlikeCharactersAreLeftOut() {
        repeat(GENERATIONS) {
            val password = generator.generate(Options(excludeAmbiguous = true))

            assertTrue("a password used a look-alike character", password.none { it in LOOK_ALIKES })
        }
    }

    @Test
    fun optionsThatCannotBeBuiltAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Options(length = Options.MIN_LENGTH - 1) }
        assertThrows(IllegalArgumentException::class.java) { Options(length = Options.MAX_LENGTH + 1) }
        assertThrows(IllegalArgumentException::class.java) {
            Options(lower = false, upper = false, digits = false, symbols = false)
        }
    }

    @Test
    fun twoPasswordsAreNotTheSame() {
        val first = PasswordGenerator().generate(Options())
        val second = PasswordGenerator().generate(Options())

        assertFalse("two passwords came out identical", first.contentEquals(second))
    }

    @Test
    fun theSameSeedBuildsTheSamePassword() {
        val first = PasswordGenerator(Random(SEED)).generate(Options())
        val second = PasswordGenerator(Random(SEED)).generate(Options())

        assertTrue("the same seed built two different passwords", first.contentEquals(second))
    }

    private companion object {
        const val GENERATIONS = 1_000
        const val MIDDLE_LENGTH = 40
        const val SEED = 20261013L
        const val LOOK_ALIKES = "0Oo1lI|"
    }
}
