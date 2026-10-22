package com.liquiwatch.phone

import android.content.Context
import android.icu.util.LocaleData
import android.icu.util.ULocale
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.liquiwatch.shared.FaceLink
import com.liquiwatch.shared.WeatherSnapshot
import java.io.IOException
import kotlinx.coroutines.tasks.await

/** Something stopping the weather being fetched (the message is for the app's screen). */
class WeatherProblem(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Fetches the weather where the phone is (the temperature now, the sky, and India's air quality
 * index) and hands it to the watch for the Expedition face's date panel: when the watch asks (only
 * while the weather is up there and the reading it has is over half an hour old), and when the
 * app opens. Nothing on a schedule: what isn't on the watch's screen isn't fetched.
 */
object WeatherSync {
    private const val PREFS = "weather"
    private const val LAST_ERROR = "last_error"
    private const val LAST_ERROR_AT = "last_error_at"

    /** After the weather couldn't be fetched (not for the network), the watch's asks are let be this long (ms): it won't mend by itself. */
    private const val FAILED_WAIT_MS = 30 * 60_000L

    /** Fetches and sends now. Throws [WeatherProblem] if it can't. [foreground]: the app's on screen. */
    suspend fun run(context: Context, foreground: Boolean = false): WeatherSnapshot {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        try {
            if (!PhoneLocation.granted(app) && PhoneLocation.saved(app) == null) {
                throw WeatherProblem("Allow the app your location to fetch the weather where you are.")
            }
            val (lat, lon) = PhoneLocation.forWeather(app, foreground)
                ?: throw WeatherProblem("The phone doesn't know where it is yet: turn on Location and try again.")
            val fahrenheit = LocaleData.getMeasurementSystem(ULocale.getDefault()) == LocaleData.MeasurementSystem.US
            val now = try {
                OpenMeteo.now(lat, lon, fahrenheit)
            } catch (e: IOException) {
                throw WeatherProblem("Couldn't reach the weather service (${e.message}).", e)
            }
            // (The air quality alone can fail: the weather still goes.)
            val aqi = try {
                CpcbAqi.index(OpenMeteo.air(lat, lon))
            } catch (e: IOException) {
                null
            }
            val w = WeatherSnapshot(
                temperature = now.temperature,
                fahrenheit = fahrenheit,
                condition = WmoCodes.toWff(now.code),
                day = now.day,
                aqi = aqi,
                fetchedMillis = System.currentTimeMillis(),
            )
            send(app, w)
            prefs.edit().apply {
                w.toMap().forEach { (k, v) -> putLong(k, v) }
                remove(LAST_ERROR)
                remove(LAST_ERROR_AT)
            }.apply()
            return w
        } catch (p: WeatherProblem) {
            prefs.edit().apply {
                putString(LAST_ERROR, p.message)
                // (Not when the service was out of reach: that passes, so the watch may ask again.)
                if (p.cause !is IOException) putLong(LAST_ERROR_AT, System.currentTimeMillis())
            }.apply()
            throw p
        }
    }

    /** What was last sent (for the app's screen). */
    fun last(context: Context): WeatherSnapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WeatherSnapshot.fromValues { key -> if (p.contains(key)) p.getLong(key, -1L) else null }
    }

    fun lastError(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_ERROR, null)

    /** Whether the weather last couldn't be fetched, not for the network, under 30 minutes ago (the watch's asking waits). */
    fun failedLately(context: Context): Boolean =
        System.currentTimeMillis() - context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_ERROR_AT, 0L) in 0 until FAILED_WAIT_MS

    private suspend fun send(context: Context, w: WeatherSnapshot) {
        val request = PutDataMapRequest.create(FaceLink.WEATHER_PATH)
        w.toMap().forEach { (k, v) -> request.dataMap.putLong(k, v) }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
    }

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Stops the fetching every 30 minutes an earlier version scheduled (WorkManager keeps it across
     * restarts and updates): the watch asks when it needs the weather now.
     */
    fun unschedule(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("weather")
    }

    /** Fetches once, soon (in the background). */
    fun soon(context: Context) {
        // (KEEP: a slow fetch finishes rather than being cut off by the watch's next ask.)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "weather_now",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WeatherWorker>()
                .setConstraints(online)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
    }
}

class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        WeatherSync.run(applicationContext)
        Result.success()
    } catch (p: WeatherProblem) {
        // (Unreachable: try again in a while, a couple of times; anything else waits for the app.)
        if (p.cause is IOException && runAttemptCount < 2) Result.retry() else Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 2) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = fetchingForeground(applicationContext, 2)
}
