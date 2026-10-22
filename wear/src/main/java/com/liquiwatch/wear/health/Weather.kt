package com.liquiwatch.wear.health

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.liquiwatch.shared.FaceLink
import com.liquiwatch.shared.WeatherSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The weather the phone half of the companion last sent (the temperature now, the sky, India's
 * air quality index), and how the date panel shows it. The watch's own weather doesn't reach the
 * face on every watch, so the panel shows this one when it's here.
 */
object WeatherStore {
    private const val PREFS = "weather"
    private const val LAST_ASK = "last_ask"

    /** Older than this (ms), it isn't shown: the face goes back to the watch's own weather, if any. */
    private const val SHOWN_MS = 3 * 3_600_000L

    /**
     * Older than this (ms), the phone is asked for it again while the weather is up (as the panel
     * turns to it, as the screen comes on with it up, and every minute while it's on).
     */
    private const val STALE_MS = 30 * 60_000L

    fun current(context: Context): WeatherSnapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WeatherSnapshot.fromValues { key -> if (p.contains(key)) p.getLong(key, -1L) else null }
    }

    /** Keeps [w] (the date panel shows it if it's new enough: see Panels.refreshIfChanged). */
    fun save(context: Context, w: WeatherSnapshot) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        w.toMap().forEach { (k, v) -> e.putLong(k, v) }
        e.apply()
    }

    /** The weather to show, or null when there's none recent enough. */
    fun fresh(w: WeatherSnapshot): WeatherSnapshot? =
        w.takeIf { it.temperature != null && System.currentTimeMillis() - it.fetchedMillis < SHOWN_MS }

    /** The temperature now, as the panel's top line ("29°"), or "" if there's none to show. */
    fun titleText(w: WeatherSnapshot): String = fresh(w)?.temperature?.let { "$it°" } ?: ""

    /** The air quality, as the panel's second line ("AQI 78"), or "" if there's none to show. */
    fun aqiText(w: WeatherSnapshot): String = fresh(w)?.let { f -> f.aqi?.let { "AQI $it" } ?: "AQI --" } ?: ""

    /**
     * The sky's picture for the face, as the date panel's range minimum: below zero while there's
     * weather to show, -(1 + the condition + 16 if it's day), else 0 (the face then reads the
     * watch's own weather, if it has any).
     */
    fun skyCode(w: WeatherSnapshot): Float = fresh(w)?.let { -(1f + it.condition + if (it.day) 16f else 0f) } ?: 0f

    /** Asks the phone to fetch the weather again, if the last is a while old (only while it's up: see OnScreen). */
    fun askPhoneIfStale(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stale = now - current(app).fetchedMillis > STALE_MS
        val askedLately = now - p.getLong(LAST_ASK, 0L) < 60_000L
        if (!stale || askedLately) return
        p.edit().putLong(LAST_ASK, now).apply()
        scope.launch {
            try {
                val client = Wearable.getMessageClient(app)
                for (node in Wearable.getNodeClient(app).connectedNodes.await()) {
                    client.sendMessage(node.id, FaceLink.WEATHER_REQUEST_PATH, ByteArray(0)).await()
                }
            } catch (e: Exception) {
                // The phone isn't reachable: asked again the next time the weather is looked at.
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
