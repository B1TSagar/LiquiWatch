package com.liquiwatch.wear.compass

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Trace
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.liquiwatch.wear.FaceUpdates
import com.liquiwatch.wear.R

/**
 * The compass for watch faces (the LiquiWatch faces use it by default for their bezel; any face
 * with a "ranged value" or "short text" slot can pick "Compass" from the face companion).
 *
 * Watch faces can't read the compass themselves, so this hands them the heading: as a ranged
 * value (the faces turn their needle or knob by it), the compass point as the text ("NE") and
 * the degrees as the title ("42°"). Tapping it opens the live compass.
 *
 * While a face shows it, the live compass ([LiveCompass]) reads the compass whenever the screen
 * is on and hands the face each new heading a few times a second as the wrist turns. The value
 * is the heading as a running total (it counts on past 360°, or below 0°, as the wrist goes
 * round), so a face that glides its needle from one heading to the next always takes the short
 * way: 359° to 1° is 2° on, not 358° back.
 */
class CompassComplicationService : SuspendingComplicationDataSourceService() {

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        LiveCompass.setSlot(this, complicationInstanceId, showing = true)
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        LiveCompass.setSlot(this, complicationInstanceId, showing = false)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val type = request.complicationType
        if (type != ComplicationType.RANGED_VALUE && type != ComplicationType.SHORT_TEXT) return null
        // (How long the heading took to be asked for: asks are timed by it, to reach the face just
        // after a beat.)
        FaceUpdates.answered(this, CompassComplicationService::class.java)
        // (Also noted here, for a face that was showing it before this version.)
        LiveCompass.setSlot(this, request.complicationInstanceId, showing = true)
        val live = LiveCompass.latest
        val nowMs = System.currentTimeMillis()
        val heading = if (live != null && nowMs - live.atMillis < LIVE_MS) {
            live
        } else {
            // No live reading just now (the screen was off): a fresh one if there's time (not on
            // the charger, whose magnets turn it: the last one read off it stands).
            val fresh = if (request.immediateResponseRequired || !Compass.available(this) || Compass.onCharger(this)) null else Compass.readOnce(this)
            // (A saved heading newer than the live compass's, from the Compass app, is taken into
            // its running total too, so the dial points where the text says.)
            fresh?.also { LiveCompass.note(this, it) }
                ?: Compass.last(this)?.also { if (it.atMillis > (LiveCompass.latest?.atMillis ?: 0L)) LiveCompass.note(this, it) }
        }
        val recent = heading != null && nowMs - heading.atMillis < STALE_AFTER_MS
        val turned = if (heading != null) LiveCompass.current(this) ?: heading.degrees else null
        turned?.let(LiveCompass::handedOver)
        // (Marked in a system trace of the watch that traces this app, as the trace recorder in
        // tools/trace does: each fetch, and the heading handed over, in tenths of a degree.)
        if (Trace.isEnabled()) {
            Trace.setCounter("compass fetches", ++fetches)
            if (turned != null) Trace.setCounter("compass heading x10", Math.round(turned * 10f).toLong())
        }
        return build(this, type, heading?.degrees, turned, recent)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(this, type, 42f, 42f, recent = true)

    companion object {
        /** After this long without a new reading, the title says "tap" rather than the degrees. */
        private const val STALE_AFTER_MS = 5 * 60_000L

        /** A live reading this recent is handed on as it is. */
        private const val LIVE_MS = 3_000L

        /** The range the running total is handed in (it never gets near either end). */
        private const val RANGE = 100_000f

        /** How many times the faces fetched the heading (in this process: for a system trace). */
        @Volatile
        private var fetches = 0L

        /** Asks every watch face showing the compass to fetch it again (e.g. as the compass screen closes). */
        fun refresh(context: Context) {
            FaceUpdates.request(context, CompassComplicationService::class.java)
        }

        private fun text(s: String) = PlainComplicationText.Builder(s).build()

        /** Opening the live compass: made once (a heading goes out a few times a second). */
        @Volatile
        private var tap: PendingIntent? = null

        private fun tapAction(context: Context): PendingIntent = tap ?: PendingIntent.getActivity(
            context.applicationContext,
            0,
            Intent(context.applicationContext, CompassActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ).also { tap = it }

        private fun build(
            context: Context,
            type: ComplicationType,
            degrees: Float?,
            turned: Float?,
            recent: Boolean,
        ): ComplicationData? {
            val point = degrees?.let { Compass.point(it) } ?: "--"
            val title = when {
                degrees == null -> "tap"
                recent -> Compass.degreesText(degrees)
                else -> "tap"
            }
            val description = text(
                if (degrees == null) "Compass: tap to open" else "Heading ${Compass.degreesText(degrees)} $point",
            )
            val tap = tapAction(context)
            return when (type) {
                ComplicationType.RANGED_VALUE ->
                    RangedValueComplicationData.Builder(
                        value = (turned ?: 0f).coerceIn(-RANGE, RANGE),
                        min = -RANGE,
                        max = RANGE,
                        contentDescription = description,
                    )
                        .setText(text(point))
                        .setTitle(text(title))
                        // (No icon: it changes nothing, and this is sent often while the wrist turns.)
                        .setTapAction(tap)
                        .build()
                ComplicationType.SHORT_TEXT ->
                    ShortTextComplicationData.Builder(text(point), description)
                        .setTitle(text(title))
                        .setMonochromaticImage(MonochromaticImage.Builder(Icon.createWithResource(context, R.drawable.ic_compass)).build())
                        .setTapAction(tap)
                        .build()
                else -> null
            }
        }
    }
}
