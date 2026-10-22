package com.liquiwatch.shared

/**
 * The readings the phone half of the face companion takes from Samsung Health and hands the
 * watch half, for the watch faces' panels. Any of them can be missing (not measured yet, or no
 * access to Samsung Health).
 *
 * @property sleepScore last night's sleep score (0..100), with [sleepEndMillis] when that sleep ended.
 * @property energyScore today's Energy score (0..100), for [energyEpochDay] (a LocalDate's epoch day).
 * @property oxygen the latest blood oxygen reading (SpO2, %), taken at [oxygenMillis].
 * @property activeKcal calories burned in activity today, as of [syncedMillis], for [kcalEpochDay].
 */
data class HealthSnapshot(
    val sleepScore: Int? = null,
    val sleepEndMillis: Long = 0L,
    val energyScore: Int? = null,
    val energyEpochDay: Long = 0L,
    val oxygen: Int? = null,
    val oxygenMillis: Long = 0L,
    val activeKcal: Int? = null,
    val kcalEpochDay: Long = 0L,
    val syncedMillis: Long = 0L,
) {
    /** As plain values (for a Data Layer DataMap or preferences). A missing reading is -1. */
    fun toMap(): Map<String, Long> = mapOf(
        K_SLEEP to (sleepScore?.toLong() ?: -1L),
        K_SLEEP_END to sleepEndMillis,
        K_ENERGY to (energyScore?.toLong() ?: -1L),
        K_ENERGY_DAY to energyEpochDay,
        K_OXYGEN to (oxygen?.toLong() ?: -1L),
        K_OXYGEN_AT to oxygenMillis,
        K_KCAL to (activeKcal?.toLong() ?: -1L),
        K_KCAL_DAY to kcalEpochDay,
        K_SYNCED to syncedMillis,
    )

    companion object {
        const val K_SLEEP = "sleep"
        const val K_SLEEP_END = "sleep_end"
        const val K_ENERGY = "energy"
        const val K_ENERGY_DAY = "energy_day"
        const val K_OXYGEN = "oxygen"
        const val K_OXYGEN_AT = "oxygen_at"
        const val K_KCAL = "kcal"
        const val K_KCAL_DAY = "kcal_day"
        const val K_SYNCED = "synced"

        /** Back from plain values ([get] gives null for a key that isn't there). */
        fun fromValues(get: (String) -> Long?): HealthSnapshot {
            fun reading(key: String) = get(key)?.takeIf { it >= 0 }?.toInt()
            return HealthSnapshot(
                sleepScore = reading(K_SLEEP),
                sleepEndMillis = get(K_SLEEP_END) ?: 0L,
                energyScore = reading(K_ENERGY),
                energyEpochDay = get(K_ENERGY_DAY) ?: 0L,
                oxygen = reading(K_OXYGEN),
                oxygenMillis = get(K_OXYGEN_AT) ?: 0L,
                activeKcal = reading(K_KCAL),
                kcalEpochDay = get(K_KCAL_DAY) ?: 0L,
                syncedMillis = get(K_SYNCED) ?: 0L,
            )
        }
    }
}

/**
 * The weather where the phone is, as the phone fetched it for the watch faces' date panel: the
 * temperature now, what the sky is doing, and the air quality (India's national AQI, CPCB).
 *
 * @property temperature the temperature now, rounded, in [fahrenheit] or Celsius.
 * @property condition what the sky is doing, as the Watch Face Format's [WEATHER.CONDITION]
 *   (0 unknown, 1 clear, 2 cloudy, 3 fog, 4 heavy rain, 5 heavy snow, 6 rain, 7 snow, 8 sunny,
 *   9 thunderstorm, 10 sleet, 11 light snow, 12 light rain, 13 mist, 14 partly cloudy, 15 windy).
 * @property day whether it's day there (the sun is up).
 * @property aqi the air quality index (0..500, CPCB), or null if it couldn't be worked out.
 * @property fetchedMillis when the phone fetched it (0: never).
 */
data class WeatherSnapshot(
    val temperature: Int? = null,
    val fahrenheit: Boolean = false,
    val condition: Int = 0,
    val day: Boolean = true,
    val aqi: Int? = null,
    val fetchedMillis: Long = 0L,
) {
    /** As plain values (for a Data Layer DataMap or preferences). Missing is -1000 (temperature) or -1. */
    fun toMap(): Map<String, Long> = mapOf(
        K_TEMP to (temperature?.toLong() ?: NO_TEMP),
        K_FAHRENHEIT to (if (fahrenheit) 1L else 0L),
        K_CONDITION to condition.toLong(),
        K_DAY to (if (day) 1L else 0L),
        K_AQI to (aqi?.toLong() ?: -1L),
        K_FETCHED to fetchedMillis,
    )

    companion object {
        const val K_TEMP = "temp"
        const val K_FAHRENHEIT = "fahrenheit"
        const val K_CONDITION = "condition"
        const val K_DAY = "day"
        const val K_AQI = "aqi"
        const val K_FETCHED = "fetched"
        private const val NO_TEMP = -1000L

        /** Back from plain values ([get] gives null for a key that isn't there). */
        fun fromValues(get: (String) -> Long?): WeatherSnapshot = WeatherSnapshot(
            temperature = get(K_TEMP)?.takeIf { it != NO_TEMP }?.toInt(),
            fahrenheit = get(K_FAHRENHEIT) == 1L,
            condition = (get(K_CONDITION) ?: 0L).toInt().coerceIn(0, 15),
            day = get(K_DAY) != 0L,
            aqi = get(K_AQI)?.takeIf { it >= 0 }?.toInt(),
            fetchedMillis = get(K_FETCHED) ?: 0L,
        )
    }
}

/**
 * The phone's battery, as the phone read it when the watch asked (the watch asks only while the
 * Expedition face's battery page is up): its [level] (0..100), whether it's [charging], and when
 * it was read ([atMillis], the phone's clock).
 */
data class PhoneBattery(val level: Int, val charging: Boolean, val atMillis: Long) {
    /** As a message's payload: "level,charging,at". */
    fun toBytes(): ByteArray = "$level,${if (charging) 1 else 0},$atMillis".toByteArray(Charsets.UTF_8)

    companion object {
        fun fromBytes(bytes: ByteArray): PhoneBattery? = try {
            val (level, charging, at) = String(bytes, Charsets.UTF_8).split(',')
            PhoneBattery(level.toInt().coerceIn(0, 100), charging == "1", at.toLong())
        } catch (e: Exception) {
            null
        }
    }
}

/** Data Layer paths between the companion's phone and watch apps. */
object FaceLink {
    /** The phone's latest [HealthSnapshot] (a DataItem, kept in sync by the Data Layer): sent when the watch asks. */
    const val HEALTH_PATH = "/face/health"

    /** The watch asks the phone to read Samsung Health again now (a message, no payload). */
    const val HEALTH_REQUEST_PATH = "/face/health/request"

    /** The phone's latest [WeatherSnapshot] (a DataItem, kept in sync by the Data Layer). */
    const val WEATHER_PATH = "/face/weather"

    /** The watch asks the phone to fetch the weather again now (a message, no payload). */
    const val WEATHER_REQUEST_PATH = "/face/weather/request"

    /** The phone's battery, sent back when the watch asks ([PhoneBattery.toBytes], a message). */
    const val PHONE_BATTERY_PATH = "/face/phone/battery"

    /** The watch asks the phone for its battery (a message, no payload). */
    const val PHONE_BATTERY_REQUEST_PATH = "/face/phone/battery/request"
}
