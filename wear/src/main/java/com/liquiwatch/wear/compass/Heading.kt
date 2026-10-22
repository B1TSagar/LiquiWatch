package com.liquiwatch.wear.compass

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The compass: which way the watch's 12 o'clock points, in degrees from magnetic north
 * (0 = N, 90 = E), however the watch is tilted.
 *
 * It reads the rotation vector sensor (the watch's fused orientation), or the accelerometer and
 * magnetometer where that's missing. "Which way the watch points" is worked out from two
 * directions on the watch: its 12 o'clock (along the face) and straight out of its back. Lying
 * flat, 12 o'clock points the way you face; held up to your eyes it points at the sky, but then
 * the back of the watch points the way you face; tilted halfway (the usual glance) both do. Their
 * sum, laid flat, is the way you face at every tilt in between, with no jump from one to the
 * other.
 */

/** A compass reading: [degrees] from magnetic north, and how well the magnetometer is calibrated. */
data class Heading(val degrees: Float, val accuracy: Int, val atMillis: Long) {
    /** The magnetometer needs calibrating (a figure-8 with the wrist fixes it). */
    val unreliable: Boolean get() = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
}

object Compass {
    private const val PREFS = "compass"
    private const val KEY_DEGREES = "degrees"
    private const val KEY_ACCURACY = "accuracy"
    private const val KEY_AT = "at"

    private val POINTS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /** The nearest of the eight compass points ("N", "NE", …) to [degrees]. */
    fun point(degrees: Float): String = POINTS[(((degrees % 360f + 360f) % 360f + 22.5f) / 45f).toInt() % 8]

    /** The running total [total] (degrees, counting on past 360°) moved on to a reading of [degrees], the short way round. */
    fun follow(total: Float, degrees: Float): Float {
        val at = (total % 360f + 360f) % 360f
        return total + (((degrees - at) % 360f + 540f) % 360f - 180f)
    }

    /** Whole degrees 0..359 ("42°"). */
    fun degreesText(degrees: Float): String = "${((degrees.roundToInt() % 360) + 360) % 360}°"

    /**
     * The saved reading, read afresh whichever process wrote it last: the Compass app runs in a
     * process of its own, and the live compass and the complications in the app's main one.
     */
    @Suppress("DEPRECATION")
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS)

    /** Remembers the latest reading (what the watch face shows when it can't read the sensor). */
    fun save(context: Context, h: Heading) {
        prefs(context).edit()
            .putFloat(KEY_DEGREES, h.degrees)
            .putInt(KEY_ACCURACY, h.accuracy)
            .putLong(KEY_AT, h.atMillis)
            .apply()
    }

    /** The last reading saved, if any. */
    fun last(context: Context): Heading? {
        val p = prefs(context)
        if (!p.contains(KEY_DEGREES)) return null
        return Heading(p.getFloat(KEY_DEGREES, 0f), p.getInt(KEY_ACCURACY, 0), p.getLong(KEY_AT, 0L))
    }

    /**
     * Whether the watch is on its charger (or any power): the charger's magnets turn the heading by
     * themselves, so the faces' compass isn't read meanwhile (the Compass app still reads it).
     */
    fun onCharger(context: Context): Boolean =
        (context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    /** Whether this watch has what a compass needs. */
    fun available(context: Context): Boolean {
        val sm = context.getSystemService(SensorManager::class.java) ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
            (sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null && sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)
    }

    /**
     * One fresh reading, for the watch face: listens for up to [timeoutMs] and returns once the
     * sensor has settled (null if it gave nothing, e.g. when the system holds sensor events
     * back from apps in the background). Saves what it read.
     */
    suspend fun readOnce(context: Context, timeoutMs: Long = 1_500L): Heading? {
        val sensor = HeadingSensor(context)
        val read: Heading? = try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Heading?> { cont ->
                    sensor.onReading = { h, samples ->
                        // The first few samples are still settling (the filter starts from the first one).
                        if (samples >= SETTLE_SAMPLES && cont.isActive) {
                            sensor.onReading = null
                            cont.resumeWith(Result.success(h))
                        }
                    }
                    cont.invokeOnCancellation { sensor.stop() }
                    if (!sensor.start()) cont.resumeWith(Result.success(null))
                }
            } ?: sensor.reading.value // timed out: whatever it managed, if anything
        } finally {
            sensor.stop()
        }
        if (read != null) save(context, read)
        return read
    }

    private const val SETTLE_SAMPLES = 12
}

/**
 * A live compass. [start] begins listening (about 50 readings a second, smoothed over
 * [smoothingS] seconds); [stop] ends it. The latest smoothed reading is in [reading].
 */
class HeadingSensor(
    context: Context,
    private val smoothingS: Float = SMOOTHING_S,
    /** Listen to the magnetometer for its calibration state too (only the Compass app shows it). */
    private val tracksCalibration: Boolean = true,
) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val rotation = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magnet = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accel = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _reading = MutableStateFlow<Heading?>(null)
    val reading: StateFlow<Heading?> = _reading

    /** Called on every new reading, with how many readings there have been since [start]. */
    var onReading: ((Heading, Int) -> Unit)? = null

    private val matrix = FloatArray(9)
    private val gravity = FloatArray(3)
    private val field = FloatArray(3)
    private var haveGravity = false
    private var haveField = false

    // The smoothed direction, as a unit vector (so 359° and 1° average to 0°, not 180°).
    private var sx = 0f
    private var sy = 0f
    private var lastNanos = 0L
    private var samples = 0
    private var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
    private var running = false

    /**
     * Starts listening, [rate] as SensorManager's delays (the compass screen reads fast; the
     * live compass for the faces at the UI rate); false if this watch has no compass.
     */
    fun start(rate: Int = SensorManager.SENSOR_DELAY_GAME): Boolean {
        val m = manager ?: return false
        if (running) return true
        samples = 0
        lastNanos = 0L
        haveGravity = false
        haveField = false
        val ok = if (rotation != null) {
            // The magnetometer only for its calibration state (the rotation vector already uses it).
            if (tracksCalibration) magnet?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            m.registerListener(this, rotation, rate)
        } else if (magnet != null && accel != null) {
            m.registerListener(this, magnet, rate) && m.registerListener(this, accel, rate)
        } else {
            false
        }
        running = ok
        if (!ok) m.unregisterListener(this)
        return ok
    }

    fun stop() {
        if (!running) return
        running = false
        manager?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) this.accuracy = accuracy
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                update(event.timestamp)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (event.accuracy != accuracy) accuracy = event.accuracy
                if (rotation != null) return
                System.arraycopy(event.values, 0, field, 0, 3)
                haveField = true
                if (haveGravity && SensorManager.getRotationMatrix(matrix, null, gravity, field)) update(event.timestamp)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Gravity: the accelerometer with the jolts of the arm smoothed out.
                if (!haveGravity) {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                    haveGravity = true
                } else {
                    for (i in 0..2) gravity[i] += 0.15f * (event.values[i] - gravity[i])
                }
            }
        }
    }

    private fun update(nanos: Long) {
        val d = facing(matrix) ?: return
        val rad = Math.toRadians(d.toDouble())
        val x = sin(rad).toFloat()
        val y = cos(rad).toFloat()
        if (samples == 0) {
            sx = x
            sy = y
        } else {
            // About a 0.12 s settling time (unless asked for less): steady, but still keeps up
            // with a turning wrist.
            val dt = ((nanos - lastNanos) / 1e9f).coerceIn(0f, 0.5f)
            val k = 1f - exp(-dt / smoothingS)
            sx += (x - sx) * k
            sy += (y - sy) * k
        }
        lastNanos = nanos
        samples++
        val deg = ((Math.toDegrees(atan2(sx.toDouble(), sy.toDouble())).toFloat()) + 360f) % 360f
        val h = Heading(deg, accuracy, System.currentTimeMillis())
        _reading.value = h
        onReading?.invoke(h, samples)
    }

    companion object {
        const val SMOOTHING_S = 0.12f

        /**
         * Which way the watch faces, in degrees from north, from its rotation matrix [r]
         * (device to world: world x = east, y = north, z = up). Null when the watch lies on
         * its face or its side, where there's no "forward" to speak of.
         */
        fun facing(r: FloatArray): Float? {
            // 12 o'clock (the device's y axis) and out of the back (−z), in world coordinates;
            // only their east and north parts matter.
            val east = r[1] - r[2]
            val north = r[4] - r[5]
            if (hypot(east, north) < 0.15f) return null
            return ((Math.toDegrees(atan2(east.toDouble(), north.toDouble())).toFloat()) + 360f) % 360f
        }
    }
}
