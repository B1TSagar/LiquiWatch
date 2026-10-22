package com.liquiwatch.phone

import kotlin.math.roundToInt

/**
 * India's National Air Quality Index (CPCB, 2014), worked out from hourly pollutant readings:
 * each pollutant's sub-index from its average over the last 24 hours (the last 8 hours for ozone
 * and carbon monoxide), and the index the highest of them, given at least three pollutants with
 * PM10 or PM2.5 among them (as CPCB has it). Between the bands' breakpoints a sub-index runs
 * straight from one to the next.
 *
 * Bands: Good 0-50, Satisfactory 51-100, Moderate 101-200, Poor 201-300, Very poor 301-400,
 * Severe 401-500.
 */
object CpcbAqi {
    /** Where each band ends, on the index. */
    private val INDEX_TOPS = intArrayOf(50, 100, 200, 300, 400, 500)

    // Where each band ends, in µg/m³ (carbon monoxide in mg/m³), as CPCB's breakpoints. CPCB's
    // severe band has no top: it's taken here as wide as the very poor band below it (as most
    // calculators of the index take it), and past it the index stays at 500.
    val PM10 = doubleArrayOf(50.0, 100.0, 250.0, 350.0, 430.0, 510.0)
    val PM25 = doubleArrayOf(30.0, 60.0, 90.0, 120.0, 250.0, 380.0)
    val NO2 = doubleArrayOf(40.0, 80.0, 180.0, 280.0, 400.0, 520.0)
    val O3 = doubleArrayOf(50.0, 100.0, 168.0, 208.0, 748.0, 1288.0)
    val CO = doubleArrayOf(1.0, 2.0, 10.0, 17.0, 34.0, 51.0)
    val SO2 = doubleArrayOf(40.0, 80.0, 380.0, 800.0, 1600.0, 2400.0)
    val NH3 = doubleArrayOf(200.0, 400.0, 800.0, 1200.0, 1800.0, 2400.0)

    /**
     * Hourly readings, oldest first, the last the hour now, in µg/m³ (carbon monoxide too: it's
     * turned into mg/m³ here). A missing hour is null.
     */
    class Readings(
        val pm10: List<Double?>,
        val pm25: List<Double?>,
        val no2: List<Double?>,
        val so2: List<Double?>,
        val o3: List<Double?>,
        val co: List<Double?>,
        val nh3: List<Double?> = emptyList(),
    )

    /** A pollutant's sub-index for its average concentration [c], on the scale [tops]. */
    fun subIndex(c: Double, tops: DoubleArray): Int {
        var lowC = 0.0
        var lowI = 0
        for (k in tops.indices) {
            val highC = tops[k]
            val highI = INDEX_TOPS[k]
            if (c <= highC) return (lowI + (highI - lowI) * (c - lowC) / (highC - lowC)).roundToInt().coerceIn(0, 500)
            lowC = highC
            lowI = highI
        }
        return 500
    }

    /**
     * The average of the last [hours] readings in [values], if at least two thirds of them are
     * there (CPCB asks 16 hours of 24 for a day's average); null otherwise.
     */
    fun average(values: List<Double?>, hours: Int): Double? {
        val last = values.takeLast(hours).filterNotNull().filter { it >= 0.0 }
        if (last.size * 3 < hours * 2) return null
        return last.average()
    }

    /** The index, or null if there aren't enough readings for it. */
    fun index(r: Readings): Int? {
        val subs = HashMap<String, Int>()
        average(r.pm10, 24)?.let { subs["pm10"] = subIndex(it, PM10) }
        average(r.pm25, 24)?.let { subs["pm25"] = subIndex(it, PM25) }
        average(r.no2, 24)?.let { subs["no2"] = subIndex(it, NO2) }
        average(r.so2, 24)?.let { subs["so2"] = subIndex(it, SO2) }
        average(r.nh3, 24)?.let { subs["nh3"] = subIndex(it, NH3) }
        average(r.o3, 8)?.let { subs["o3"] = subIndex(it, O3) }
        average(r.co, 8)?.let { subs["co"] = subIndex(it / 1000.0, CO) }
        if (subs.size < 3 || ("pm10" !in subs && "pm25" !in subs)) return null
        return subs.values.max()
    }

    /** The index's band, as CPCB names it. */
    fun band(aqi: Int): String = when {
        aqi <= 50 -> "Good"
        aqi <= 100 -> "Satisfactory"
        aqi <= 200 -> "Moderate"
        aqi <= 300 -> "Poor"
        aqi <= 400 -> "Very poor"
        else -> "Severe"
    }
}

/** Open-Meteo's weather codes (WMO) as the Watch Face Format's weather conditions. */
object WmoCodes {
    fun toWff(code: Int): Int = when (code) {
        0, 1 -> 1 // clear (the face draws the sun by day, the moon by night)
        2 -> 14 // partly cloudy
        3 -> 2 // cloudy
        45, 48 -> 3 // fog
        51, 53, 55, 61, 80 -> 12 // drizzle, light rain, light showers
        63, 81 -> 6 // rain
        65, 82 -> 4 // heavy rain
        56, 57, 66, 67 -> 10 // freezing drizzle and rain: sleet
        71, 85 -> 11 // light snow
        73, 77 -> 7 // snow
        75, 86 -> 5 // heavy snow
        95, 96, 99 -> 9 // thunderstorm
        else -> 0
    }

    /** What the sky's doing, in words, for the app's screen. */
    fun words(wff: Int): String = when (wff) {
        1 -> "Clear"
        2 -> "Cloudy"
        3 -> "Fog"
        4 -> "Heavy rain"
        5 -> "Heavy snow"
        6 -> "Rain"
        7 -> "Snow"
        8 -> "Sunny"
        9 -> "Thunderstorm"
        10 -> "Sleet"
        11 -> "Light snow"
        12 -> "Light rain"
        13 -> "Mist"
        14 -> "Partly cloudy"
        15 -> "Windy"
        else -> "--"
    }
}
