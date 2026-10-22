package com.liquiwatch.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the phone is, roughly, for the weather on the face: a recent fix if the phone has one,
 * else a fresh one (coarse is plenty). The last place is kept, so the weather can be fetched in
 * the background without asking for the phone's location then, unless it may always be read
 * ("Allow all the time"), in which case it follows the phone.
 */
object PhoneLocation {
    private const val PREFS = "weather_place"
    private const val LAT = "lat"
    private const val LON = "lon"

    /** A fix younger than this (ms) is taken as it is. */
    private const val RECENT_MS = 30 * 60_000L

    fun granted(context: Context): Boolean =
        has(context, Manifest.permission.ACCESS_COARSE_LOCATION) || has(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** Whether the location may be read with the app in the background too. */
    fun alwaysGranted(context: Context): Boolean =
        granted(context) && has(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** The last place the weather was fetched for, or null. */
    fun saved(context: Context): Pair<Double, Double>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(LAT) || !p.contains(LON)) return null
        return p.getFloat(LAT, 0f).toDouble() to p.getFloat(LON, 0f).toDouble()
    }

    /**
     * Where to fetch the weather for: where the phone is now, if its location may be read now
     * ([foreground]: the app's on screen), else the last place.
     */
    suspend fun forWeather(context: Context, foreground: Boolean): Pair<Double, Double>? {
        val app = context.applicationContext
        if (foreground || alwaysGranted(app)) {
            current(app)?.let { here ->
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putFloat(LAT, here.latitude.toFloat())
                    .putFloat(LON, here.longitude.toFloat())
                    .apply()
                return here.latitude to here.longitude
            }
        }
        return saved(app)
    }

    private suspend fun current(context: Context): Location? {
        if (!granted(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        try {
            val known = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
            if (known != null && System.currentTimeMillis() - known.time < RECENT_MS) return known
            if (Build.VERSION.SDK_INT >= 30) {
                for (provider in providers) {
                    val fresh = withTimeoutOrNull(10_000L) {
                        suspendCancellableCoroutine<Location?> { cont ->
                            val signal = CancellationSignal()
                            lm.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                            cont.invokeOnCancellation { signal.cancel() }
                        }
                    }
                    if (fresh != null) return fresh
                }
            }
            return known
        } catch (e: SecurityException) {
            return null
        }
    }
}
