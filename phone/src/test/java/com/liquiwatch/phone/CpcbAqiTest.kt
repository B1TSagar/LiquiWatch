package com.liquiwatch.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** India's AQI as the README describes it (CPCB's breakpoints, averages and rules). */
class CpcbAqiTest {
    private fun hours(n: Int, v: Double?) = List(n) { v }

    @Test
    fun subIndexRunsStraightBetweenBreakpoints() {
        assertEquals(0, CpcbAqi.subIndex(0.0, CpcbAqi.PM25))
        assertEquals(50, CpcbAqi.subIndex(30.0, CpcbAqi.PM25))
        assertEquals(75, CpcbAqi.subIndex(45.0, CpcbAqi.PM25))
        assertEquals(100, CpcbAqi.subIndex(100.0, CpcbAqi.PM10))
        assertEquals(500, CpcbAqi.subIndex(10_000.0, CpcbAqi.PM10))
    }

    @Test
    fun averageNeedsTwoThirdsOfTheHours() {
        assertNotNull(CpcbAqi.average(hours(16, 10.0) + hours(8, null), 24))
        assertNull(CpcbAqi.average(hours(15, 10.0) + hours(9, null), 24))
        // Only the last hours count, and negative readings are dropped.
        assertEquals(20.0, CpcbAqi.average(hours(30, 99.0) + hours(23, 20.0) + listOf(-1.0), 24)!!, 1e-9)
        assertNull(CpcbAqi.average(emptyList(), 8))
    }

    @Test
    fun indexIsTheHighestSubIndexGivenThreePollutantsWithPm() {
        val day = 24
        val r = CpcbAqi.Readings(
            pm10 = hours(day, 100.0), // 100
            pm25 = hours(day, null),
            no2 = hours(day, 40.0), // 50
            so2 = hours(day, 80.0), // 100
            o3 = hours(8, null),
            co = hours(8, 2_000.0), // µg/m³: 2 mg/m³ -> 100
        )
        assertEquals(100, CpcbAqi.index(r))
    }

    @Test
    fun noIndexWithoutEnoughPollutantsOrWithoutPm() {
        val day = 24
        val twoOnly = CpcbAqi.Readings(hours(day, 100.0), hours(day, null), hours(day, 40.0), hours(day, null), hours(8, null), hours(8, null))
        assertNull(CpcbAqi.index(twoOnly))
        val noPm = CpcbAqi.Readings(hours(day, null), hours(day, null), hours(day, 40.0), hours(day, 80.0), hours(8, 50.0), hours(8, null))
        assertNull(CpcbAqi.index(noPm))
    }

    @Test
    fun bands() {
        assertEquals("Good", CpcbAqi.band(50))
        assertEquals("Satisfactory", CpcbAqi.band(51))
        assertEquals("Severe", CpcbAqi.band(401))
    }
}
