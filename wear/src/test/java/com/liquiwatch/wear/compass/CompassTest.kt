package com.liquiwatch.wear.compass

import org.junit.Assert.assertEquals
import org.junit.Test

/** The compass's arithmetic: the running total the faces turn by, and the point and degrees they show. */
class CompassTest {
    @Test
    fun runningTotalGoesTheShortWayRound() {
        assertEquals(361f, Compass.follow(359f, 1f), 1e-3f)
        assertEquals(-1f, Compass.follow(1f, 359f), 1e-3f)
        // Turns already counted stay counted, either way round.
        assertEquals(710f, Compass.follow(730f, 350f), 1e-3f)
        assertEquals(-5f, Compass.follow(-5f, 355f), 1e-3f)
        assertEquals(-370f, Compass.follow(-355f, 350f), 1e-3f)
        // Straight across: half a turn, one way.
        assertEquals(180f, kotlin.math.abs(Compass.follow(0f, 180f)), 1e-3f)
    }

    @Test
    fun pointIsTheNearestOfTheEight() {
        assertEquals("N", Compass.point(0f))
        assertEquals("N", Compass.point(22.4f))
        assertEquals("NE", Compass.point(22.5f))
        assertEquals("NW", Compass.point(337.4f))
        assertEquals("N", Compass.point(337.5f))
        assertEquals("N", Compass.point(721f))
        assertEquals("W", Compass.point(-90f))
    }

    @Test
    fun degreesAreWholeAndWithinATurn() {
        assertEquals("42°", Compass.degreesText(42.4f))
        assertEquals("0°", Compass.degreesText(359.6f))
        assertEquals("0°", Compass.degreesText(-0.4f))
        assertEquals("359°", Compass.degreesText(-1f))
        assertEquals("10°", Compass.degreesText(730f))
    }
}
