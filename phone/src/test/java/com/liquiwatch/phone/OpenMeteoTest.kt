package com.liquiwatch.phone

import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Open-Meteo's answers as the phone reads them: the hours up to now, and the sky's codes. */
class OpenMeteoTest {
    // Two days of hours from midnight on the 18th, in the place's own time, as Open-Meteo gives them.
    private val times = List(48) { LocalDateTime.of(2026, 10, 18, 0, 0).plusHours(it.toLong()).toString() }
    private val india = 19_800 // +05:30

    private fun upTo(now: String, series: Map<String, List<Double?>>) =
        OpenMeteo.upToNow(times, india, Instant.parse(now)) { series[it] ?: emptyList() }

    @Test
    fun cutIsAtTheHourNowInThePlacesOwnTime() {
        val all = List(48) { it.toDouble() }
        // 08:45 UTC is 14:15 in India: up to the 19th's 14:00 (not 08:00).
        val r = upTo("2026-10-19T08:45:00Z", mapOf("pm10" to all))
        assertEquals(39, r.pm10.size)
        assertEquals(38.0, r.pm10.last()!!, 1e-9)
        // The hour itself counts the moment it starts.
        assertEquals(38.0, upTo("2026-10-19T08:30:00Z", mapOf("pm10" to all)).pm10.last()!!, 1e-9)
        assertEquals(37.0, upTo("2026-10-19T08:29:59Z", mapOf("pm10" to all)).pm10.last()!!, 1e-9)
    }

    @Test
    fun shorterSeriesKeepsToItsHours() {
        val r = upTo("2026-10-19T08:45:00Z", mapOf("pm10" to List(48) { 1.0 }, "pm2_5" to List(30) { 2.0 }))
        // As long as the others, the hours it lacks missing (not its last reading taken as now's).
        assertEquals(39, r.pm25.size)
        assertEquals(2.0, r.pm25[29]!!, 1e-9)
        assertNull(r.pm25[30])
        assertNull(r.pm25.last())
        assertEquals(emptyList<Double?>(), upTo("2026-10-19T08:45:00Z", emptyMap()).o3.filterNotNull())
    }

    @Test(expected = IOException::class)
    fun noHourUpToNowYet() {
        upTo("2026-10-17T18:00:00Z", emptyMap())
    }

    @Test
    fun wmoCodesAsTheFacesConditions() {
        val table = mapOf(
            0 to 1, 1 to 1, // clear
            2 to 14, // partly cloudy
            3 to 2, // cloudy
            45 to 3, 48 to 3, // fog
            51 to 12, 53 to 12, 55 to 12, 61 to 12, 80 to 12, // light rain
            63 to 6, 81 to 6, // rain
            65 to 4, 82 to 4, // heavy rain
            56 to 10, 57 to 10, 66 to 10, 67 to 10, // sleet
            71 to 11, 85 to 11, // light snow
            73 to 7, 77 to 7, // snow
            75 to 5, 86 to 5, // heavy snow
            95 to 9, 96 to 9, 99 to 9, // thunderstorm
            -1 to 0, 4 to 0, 100 to 0, // unknown
        )
        table.forEach { (wmo, wff) -> assertEquals("WMO $wmo", wff, WmoCodes.toWff(wmo)) }
    }
}
