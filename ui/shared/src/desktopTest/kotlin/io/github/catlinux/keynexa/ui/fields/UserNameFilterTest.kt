package io.github.catlinux.keynexa.ui.fields

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SUGGESTION_LIMIT = 6
private const val SMALL_LIMIT = 2

class UserNameFilterTest {
    private val all = listOf("Ana", "ana", "Bob", "Bobby", "Carla", "Dani", "Eva", "Ana María")

    @Test
    fun anEmptyFieldShowsTheFirstSixInOrder() {
        assertEquals(all.take(SUGGESTION_LIMIT), filterSuggestions(all, ""))
    }

    @Test
    fun theFilterIsCaseInsensitive() {
        assertEquals(listOf("Ana", "ana", "Ana María"), filterSuggestions(all, "ANA"))
    }

    @Test
    fun theExactValueWrittenIsNotOfferedAgain() {
        assertEquals(listOf("ana", "Ana María"), filterSuggestions(all, "Ana"))
    }

    @Test
    fun theLimitIsRespectedAndTheOrderPreserved() {
        assertEquals(listOf("Bob", "Bobby"), filterSuggestions(all, "bo", limit = SMALL_LIMIT))
    }

    @Test
    fun nothingMatchingGivesAnEmptyList() {
        assertTrue(filterSuggestions(all, "zzz").isEmpty())
    }

    @Test
    fun aBlankTypedValueBehavesLikeAnEmptyOne() {
        assertEquals(all.take(SUGGESTION_LIMIT), filterSuggestions(all, "   "))
    }
}
