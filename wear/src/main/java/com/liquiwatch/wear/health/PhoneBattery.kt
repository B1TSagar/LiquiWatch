package com.liquiwatch.wear.health

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.liquiwatch.shared.FaceLink
import com.liquiwatch.shared.PhoneBattery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The phone's battery, for the Expedition face's battery page (the compass panel's second page):
 * its level and whether it's charging, as the phone last said. It's asked for only while that
 * page is up (see OnScreen): when the panel turns to it, when the screen comes on with it up, and
 * every minute while the screen stays on.
 */
object PhoneBatteryStore {
    private const val PREFS = "phone_battery"
    private const val K_LEVEL = "level"
    private const val K_CHARGING = "charging"
    private const val K_AT = "at"
    private const val K_ASKED = "asked"

    /** Older than this (ms, by when it came), the page shows the phone's battery as unknown. */
    private const val SHOWN_MS = 15 * 60_000L

    /** Older than this (ms), the phone is asked again (while the page is up). */
    const val STALE_MS = 60_000L

    /** Asked at most this often (ms), whatever asks. */
    private const val ASK_GAP_MS = 20_000L

    /** The last reading and when it came (the watch's clock), or null. */
    fun current(context: Context): Pair<PhoneBattery, Long>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(K_AT)) return null
        val came = p.getLong(K_AT, 0L)
        return PhoneBattery(p.getInt(K_LEVEL, 0), p.getBoolean(K_CHARGING, false), came) to came
    }

    fun save(context: Context, b: PhoneBattery) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(K_LEVEL, b.level)
            .putBoolean(K_CHARGING, b.charging)
            .putLong(K_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * The phone's battery as the face reads it, the compass panel's range minimum: -(1 + the
     * level, and 101 more while it charges) while there's a recent reading, else 0.
     */
    fun code(context: Context): Float {
        val (b, came) = current(context) ?: return 0f
        if (System.currentTimeMillis() - came > SHOWN_MS) return 0f
        return -(1f + b.level + if (b.charging) 101f else 0f)
    }

    /** Asks the phone for its battery if the last reading is older than [STALE_MS]. */
    fun askPhoneIfStale(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val came = current(app)?.second ?: 0L
        if (now - came < STALE_MS || now - p.getLong(K_ASKED, 0L) < ASK_GAP_MS) return
        p.edit().putLong(K_ASKED, now).apply()
        scope.launch {
            try {
                val client = Wearable.getMessageClient(app)
                for (node in Wearable.getNodeClient(app).connectedNodes.await()) {
                    client.sendMessage(node.id, FaceLink.PHONE_BATTERY_REQUEST_PATH, ByteArray(0)).await()
                }
            } catch (e: Exception) {
                // The phone isn't reachable: the page shows its battery as unknown once it's old.
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
