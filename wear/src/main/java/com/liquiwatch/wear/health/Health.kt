package com.liquiwatch.wear.health

import android.content.Context
import com.liquiwatch.wear.FaceTuning
import com.liquiwatch.wear.panels.Page
import com.liquiwatch.wear.panels.Panel
import com.liquiwatch.wear.panels.Panels
import com.liquiwatch.shared.FaceLink
import com.liquiwatch.shared.HealthSnapshot
import com.liquiwatch.shared.PhoneBattery
import com.liquiwatch.shared.WeatherSnapshot
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The Samsung Health readings the phone half of the companion last sent (sleep score, Energy
 * score, blood oxygen, active calories), and how the panels show them: "--" for one that's
 * missing or too old to be today's.
 */
object HealthStore {
    private const val PREFS = "health"
    private const val LAST_ASK = "last_ask"

    fun current(context: Context): HealthSnapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return HealthSnapshot.fromValues { key -> if (p.contains(key)) p.getLong(key, -1L) else null }
    }

    fun save(context: Context, h: HealthSnapshot) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        h.toMap().forEach { (k, v) -> e.putLong(k, v) }
        e.apply()
    }

    private fun hoursSince(millis: Long) = (System.currentTimeMillis() - millis) / 3_600_000L

    fun sleepText(h: HealthSnapshot): String =
        h.sleepScore?.takeIf { hoursSince(h.sleepEndMillis) < FaceTuning.SLEEP_FRESH_HOURS }?.toString() ?: "--"

    fun energyText(h: HealthSnapshot): String =
        h.energyScore?.takeIf { h.energyEpochDay == LocalDate.now().toEpochDay() }?.toString() ?: "--"

    fun oxygenText(h: HealthSnapshot): String =
        h.oxygen?.takeIf { hoursSince(h.oxygenMillis) < FaceTuning.OXYGEN_FRESH_HOURS }?.let { "$it%" } ?: "--"

    fun kcalText(h: HealthSnapshot): String =
        h.activeKcal?.takeIf { h.kcalEpochDay == LocalDate.now().toEpochDay() }?.toString() ?: "--"

    /** Asks the phone to read Samsung Health again, if the last readings are a while old. */
    fun askPhoneIfStale(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stale = now - current(app).syncedMillis > FaceTuning.HEALTH_REFRESH_MINUTES * 60_000L
        val askedLately = now - p.getLong(LAST_ASK, 0L) < 60_000L
        if (!stale || askedLately) return
        p.edit().putLong(LAST_ASK, now).apply()
        scope.launch {
            try {
                val client = Wearable.getMessageClient(app)
                for (node in Wearable.getNodeClient(app).connectedNodes.await()) {
                    client.sendMessage(node.id, FaceLink.HEALTH_REQUEST_PATH, ByteArray(0)).await()
                }
            } catch (e: Exception) {
                // The phone isn't reachable: asked again the next time the page is looked at.
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

/**
 * Takes the phone's readings, weather and battery as they arrive (with the face companion's own
 * screens closed): kept, and handed to the face only if a page showing them is up and they change
 * what it shows (every update is work for the face; see Panels.refreshIfShown).
 */
class HealthListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            fun value(key: String): Long? = if (map.containsKey(key)) map.getLong(key) else null
            when (event.dataItem.uri.path) {
                FaceLink.HEALTH_PATH -> {
                    HealthStore.save(this, HealthSnapshot.fromValues(::value))
                    Panels.refreshIfShown(this, Panel.HEART, Page.SLEEP, Page.ENERGY)
                    Panels.refreshIfShown(this, Panel.STEPS, Page.KCAL, Page.OXYGEN)
                }
                FaceLink.WEATHER_PATH -> {
                    WeatherStore.save(this, WeatherSnapshot.fromValues(::value))
                    Panels.refreshIfShown(this, Panel.DATE, Page.WEATHER)
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != FaceLink.PHONE_BATTERY_PATH) return
        val b = PhoneBattery.fromBytes(event.data) ?: return
        PhoneBatteryStore.save(this, b)
        Panels.refreshPhoneBattery(this)
    }
}
