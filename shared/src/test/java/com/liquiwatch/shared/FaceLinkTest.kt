package com.liquiwatch.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the phone sends the watch comes back the same: missing readings stay missing, zero stays zero. */
class FaceLinkTest {
    @Test
    fun healthThereAndBack() {
        val full = HealthSnapshot(84, 1_000L, 0, 20_000L, 97, 2_000L, 412, 20_001L, 3_000L)
        val map = full.toMap()
        assertEquals(full, HealthSnapshot.fromValues { map[it] })
        val empty = HealthSnapshot(syncedMillis = 5L)
        val m2 = empty.toMap()
        assertEquals(empty, HealthSnapshot.fromValues { m2[it] })
        // Nothing stored at all: nothing measured.
        assertEquals(HealthSnapshot(), HealthSnapshot.fromValues { null })
    }

    @Test
    fun weatherThereAndBack() {
        for (w in listOf(
            WeatherSnapshot(0, false, 8, true, 0, 1L),
            WeatherSnapshot(-5, true, 14, false, 499, 2L),
            WeatherSnapshot(null, false, 0, true, null, 3L),
        )) {
            val map = w.toMap()
            assertEquals(w, WeatherSnapshot.fromValues { map[it] })
        }
        // A condition past the face's last is held to it.
        assertEquals(15, WeatherSnapshot.fromValues { if (it == WeatherSnapshot.K_CONDITION) 99L else null }.condition)
        assertEquals(WeatherSnapshot(), WeatherSnapshot.fromValues { null })
    }

    @Test
    fun phoneBatteryThereAndBack() {
        val b = PhoneBattery(57, true, 1_700_000_000_000L)
        assertEquals(b, PhoneBattery.fromBytes(b.toBytes()))
        assertEquals(100, PhoneBattery.fromBytes("150,0,1".toByteArray())?.level)
        assertNull(PhoneBattery.fromBytes("57,1".toByteArray()))
        assertNull(PhoneBattery.fromBytes("x,1,2".toByteArray()))
        assertNull(PhoneBattery.fromBytes(ByteArray(0)))
    }
}
