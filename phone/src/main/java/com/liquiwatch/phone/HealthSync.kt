package com.liquiwatch.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.google.android.gms.tasks.Tasks
import com.liquiwatch.shared.FaceLink
import com.liquiwatch.shared.HealthSnapshot
import com.liquiwatch.shared.PhoneBattery
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.tasks.await

/** Something stopping Samsung Health being read (its message is Samsung's, or ours). */
class HealthProblem(message: String, cause: Throwable?) : Exception(message, cause)

/**
 * Reads Samsung Health and hands the readings to the watch: when the watch asks (only while one of
 * the face's health pages is up and the readings it has are old: as the panel turns to it, as the
 * screen comes on, and every so often while it stays on), and when the app opens. Nothing on a
 * schedule: what isn't on the watch's screen isn't fetched.
 */
object HealthSync {
    private const val PREFS = "health"
    private const val LAST_ERROR = "last_error"
    private const val LAST_ERROR_AT = "last_error_at"

    /** After Samsung Health couldn't be read, the watch's asks are let be this long (ms): it won't mend by itself. */
    private const val FAILED_WAIT_MS = 15 * 60_000L

    /** Reads and sends now. Throws [HealthProblem] if Samsung Health can't be read. */
    suspend fun run(context: Context): HealthSnapshot {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        try {
            val h = SamsungHealth.read(app)
            send(app, h)
            prefs.edit().apply {
                h.toMap().forEach { (k, v) -> putLong(k, v) }
                remove(LAST_ERROR)
                remove(LAST_ERROR_AT)
            }.apply()
            return h
        } catch (e: HealthProblem) {
            prefs.edit().putString(LAST_ERROR, e.message).putLong(LAST_ERROR_AT, System.currentTimeMillis()).apply()
            throw e
        }
    }

    /** What was last read (for the app's screen). */
    fun last(context: Context): HealthSnapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return HealthSnapshot.fromValues { key -> if (p.contains(key)) p.getLong(key, -1L) else null }
    }

    /** Whether Samsung Health last couldn't be read under 15 minutes ago (the watch's asking waits). */
    fun failedLately(context: Context): Boolean =
        System.currentTimeMillis() - context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_ERROR_AT, 0L) in 0 until FAILED_WAIT_MS

    private suspend fun send(context: Context, h: HealthSnapshot) {
        val request = PutDataMapRequest.create(FaceLink.HEALTH_PATH)
        h.toMap().forEach { (k, v) -> request.dataMap.putLong(k, v) }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
    }

    /**
     * Stops the reading every 15 minutes an earlier version scheduled (WorkManager keeps it across
     * restarts and updates): the watch asks when it needs readings now.
     */
    fun unschedule(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("health")
    }

    /** Reads once, soon (in the background). */
    fun soon(context: Context) {
        // (KEEP: a reading under way finishes rather than being cut off by the watch's next ask.)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "health_now",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build(),
        )
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        HealthSync.run(applicationContext)
        Result.success()
    } catch (e: HealthProblem) {
        Result.success() // nothing to retry until it's fixed in the app
    } catch (e: Exception) {
        // (A couple of tries: the watch asks again while the page is up.)
        if (runAttemptCount < 2) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = fetchingForeground(applicationContext, 1)
}

/**
 * The quiet notification expedited work needs before Android 12, where it runs as a foreground
 * service (from 12 on it's a job, with none).
 */
fun fetchingForeground(context: Context, id: Int): ForegroundInfo {
    val channel = "fetching"
    context.getSystemService(NotificationManager::class.java)
        .createNotificationChannel(NotificationChannel(channel, "Fetching for the watch", NotificationManager.IMPORTANCE_LOW))
    val notification = Notification.Builder(context, channel)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle("Fetching for the watch")
        .build()
    return ForegroundInfo(id, notification)
}

/** The watch asking for fresh readings, fresh weather, or the phone's battery. */
class WatchRequestService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            // (Not while the last failure, one that lasts, is recent: the watch would have the phone try every minute.)
            FaceLink.HEALTH_REQUEST_PATH -> if (!HealthSync.failedLately(this)) HealthSync.soon(this)
            FaceLink.WEATHER_REQUEST_PATH -> if (!WeatherSync.failedLately(this)) WeatherSync.soon(this)
            FaceLink.PHONE_BATTERY_REQUEST_PATH -> PhoneBatteryReply.send(this, event.sourceNodeId)
        }
    }
}

/**
 * The phone's battery, read the moment the watch asks (the face's battery page is up) and sent
 * straight back to it: its level and whether it's charging (the system's last battery broadcast,
 * which it keeps).
 */
object PhoneBatteryReply {
    fun read(context: Context): PhoneBattery? {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return null
        val state = status.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        // (Charging: as the phone's own status says; plugged in and full counts too.)
        val charging = state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL
        return PhoneBattery((level * 100f / scale).roundToInt().coerceIn(0, 100), charging, System.currentTimeMillis())
    }

    /** Reads and sends to [nodeId] (blocking: the listener's own thread). */
    fun send(context: Context, nodeId: String) {
        val b = read(context) ?: return
        try {
            Tasks.await(Wearable.getMessageClient(context).sendMessage(nodeId, FaceLink.PHONE_BATTERY_PATH, b.toBytes()), 10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // The watch went out of reach: it asks again while the page is up.
        }
    }
}
