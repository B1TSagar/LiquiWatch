package com.liquiwatch.wear

import org.junit.Assert.assertEquals
import org.junit.Test

/** The seconds hand's beats, as the face sees them (eight a second, 1/24 s into each eighth). */
class BeatTest {
    @Test
    fun aBeatIsSeenFromItsTimeOn() {
        for (n in listOf(0L, 1L, 7L, 8L, 13_816_987_200L)) {
            assertEquals(n, Beat.at(Beat.time(n)))
            assertEquals(n - 1, Beat.at(Beat.time(n) - 1))
            assertEquals(n, Beat.at(Beat.time(n + 1) - 1))
        }
    }

    @Test
    fun beatsComeAThirdOfTheWayThroughEachEighth() {
        // 41.67 ms into the second, seen from the next whole millisecond.
        assertEquals(42L, Beat.time(0))
        assertEquals(1_042L, Beat.time(8))
        assertEquals(Beat.MS, Beat.time(9) - Beat.time(8))
        assertEquals(-1L, Beat.at(41))
    }
}
