package com.liquiwatch.phone

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The weather and the air where the phone is, from Open-Meteo (open-meteo.com: free, no account
 * or key; its air quality is the Copernicus CAMS forecast). Only the place's coordinates go,
 * rounded to about a kilometre.
 */
object OpenMeteo {
    private const val TIMEOUT_MS = 15_000

    /** The temperature now (rounded), its WMO weather code, and whether it's day there. */
    class Now(val temperature: Int, val code: Int, val day: Boolean)

    /** Throws [IOException] if Open-Meteo can't be reached or answers oddly. */
    suspend fun now(lat: Double, lon: Double, fahrenheit: Boolean): Now {
        val json = get(
            "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current=temperature_2m,weather_code,is_day&temperature_unit=%s&timezone=auto"
                .format(Locale.US, coordinate(lat), coordinate(lon), if (fahrenheit) "fahrenheit" else "celsius"),
        )
        val current = json.optJSONObject("current") ?: throw IOException("No current weather")
        val t = current.optDouble("temperature_2m", Double.NaN)
        if (t.isNaN()) throw IOException("No temperature")
        return Now(Math.round(t).toInt(), current.optInt("weather_code", -1), current.optInt("is_day", 1) == 1)
    }

    /**
     * The last day's hourly pollutant readings up to the hour now (see [CpcbAqi.Readings]).
     * Throws [IOException] if Open-Meteo can't be reached or answers oddly.
     */
    suspend fun air(lat: Double, lon: Double): CpcbAqi.Readings {
        val json = get(
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%s&longitude=%s&hourly=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone,ammonia&past_days=1&forecast_days=1&timezone=auto"
                .format(Locale.US, coordinate(lat), coordinate(lon)),
        )
        val hourly = json.optJSONObject("hourly") ?: throw IOException("No air quality")
        val times = hourly.optJSONArray("time") ?: throw IOException("No air quality times")
        return upToNow(List(times.length()) { times.optString(it) }, json.optInt("utc_offset_seconds", 0), Instant.now()) { name ->
            val a: JSONArray = hourly.optJSONArray(name) ?: return@upToNow emptyList()
            List(a.length()) { i -> if (a.isNull(i)) null else a.optDouble(i, Double.NaN).takeIf { !it.isNaN() } }
        }
    }

    /**
     * The pollutants' hourly readings ([series], by Open-Meteo's name, lined up with [times]) for the
     * hours up to [now]. Throws [IOException] if none is up to now yet.
     */
    fun upToNow(times: List<String>, offsetSeconds: Int, now: Instant, series: (String) -> List<Double?>): CpcbAqi.Readings {
        // The hours up to now, in the place's own time (as Open-Meteo gives them).
        val local = LocalDateTime.ofInstant(now, ZoneOffset.ofTotalSeconds(offsetSeconds))
        var last = -1
        for (i in times.indices) {
            val at = try {
                LocalDateTime.parse(times[i])
            } catch (e: Exception) {
                continue
            }
            if (!at.isAfter(local)) last = i
        }
        if (last < 0) throw IOException("No air quality readings yet")
        // (A series shorter than the hours is missing its last ones: null, so the rest stay on their hours.)
        fun cut(name: String): List<Double?> = series(name).let { a -> List(last + 1) { a.getOrNull(it) } }
        return CpcbAqi.Readings(
            pm10 = cut("pm10"),
            pm25 = cut("pm2_5"),
            no2 = cut("nitrogen_dioxide"),
            so2 = cut("sulphur_dioxide"),
            o3 = cut("ozone"),
            co = cut("carbon_monoxide"),
            nh3 = cut("ammonia"),
        )
    }

    /** About a kilometre: enough for the weather, and no closer than that leaves the phone. */
    private fun coordinate(v: Double): String = String.format(Locale.US, "%.2f", v)

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Open-Meteo answered $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            try {
                JSONObject(body)
            } catch (e: Exception) {
                throw IOException("Open-Meteo's answer couldn't be read", e)
            }
        } finally {
            connection.disconnect()
        }
    }
}
