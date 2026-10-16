package io.github.catlinux.keynexa.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hexagon of the brand: six vertices that stay inside their box and point upwards. */
class HexagonTest {
    @Test
    fun theSixVerticesStayInsideTheSize() {
        val size = 40f

        val vertices = hexagonVertices(size)

        assertEquals(6, vertices.size)
        vertices.forEach { (x, y) ->
            assertTrue("x=$x is outside 0..$size", x in 0f..size)
            assertTrue("y=$y is outside 0..$size", y in 0f..size)
        }
    }

    @Test
    fun theHexagonPointsUpWithItsTopAndBottomOnTheMiddleLine() {
        val vertices = hexagonVertices(100f)

        val top = vertices.minBy { it.second }
        val bottom = vertices.maxBy { it.second }

        assertEquals(50f, top.first, 0.001f)
        assertEquals(5f, top.second, 0.001f)
        assertEquals(50f, bottom.first, 0.001f)
        assertEquals(95f, bottom.second, 0.001f)
    }
}
