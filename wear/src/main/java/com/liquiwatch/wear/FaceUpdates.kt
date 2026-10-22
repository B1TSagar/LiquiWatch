package com.liquiwatch.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.liquiwatch.wear.compass.CompassComplicationService
import com.liquiwatch.wear.panels.DatePanelService
import com.liquiwatch.wear.panels.NowPlaying

/**
 * Asks the watch faces to fetch one of this app's complications again (the compass heading, a
 * panel's page, the playing song on the date pill).
 *
 * The complications library this app uses (1.3.0) hands the new data straight to the watch's
 * complication system on Wear OS 6, through the Wear SDK. The way before took a broadcast to the
 * Wear OS app, which passed it on to the system's complication service, which then bound to this
 * app's complication service to ask for the data: on a Galaxy Watch4 that cost the watch about
 * 150 ms of work for every update, and several times that while it was short of memory
 * (measured). Every compass heading and every turn of a panel's page is one update.
 *
 * Should the new way fail to start (an older watch takes the old way by itself), it falls back
 * to the old broadcast, so the faces still update.
 *
 * On the beat. Each ask waits for one of the seconds hand's beats ([Beat]): it goes at the moment
 * that has the face fetch it [AFTER_BEAT_MS] after the beat, by how long asks have lately taken
 * to reach this app's complication services ([answered]). Everything on the Expedition face
 * changes as the seconds hand beats, so an update that came between beats would have the watch
 * draw a frame of its own for it. Asks made while one waits go with it.
 *
 * Rolling load. Each beat takes only so much of this work ([Rank], [costOf]: a number or a line of
 * text is 1, a picture 2 or 3), and what doesn't fit rolls on to the next beat, and the next: a
 * budget that fills up again by so much each beat, with so much at most ready at once (a bucket,
 * [refill] and [capacity]). How much depends on how busy the watch is ([load]): how late the
 * beats' asks get to go (this app's main thread waits for the watch's two cores, which the face
 * keeps busy), and how much longer than usual asks take to reach the complication services (the
 * watch's complication system and the face are behind). The busier the watch, the less work a
 * beat takes: the face spreads it over the next few frames rather than dropping any. A tap's page
 * goes at the next beat whatever ([Rank.TAP]); the compass heading next ([Rank.MOTION]: the
 * card glides to it, see LiveCompass);
 * the song's name and cover (the date pill's, one update: [Rank.SONG]), after Cover Display's own
 * heavy work for a new song ([holdSong]); readings last. Asks of a [group] go in the same beat.
 * Nothing waits more than [MAX_WAIT_BEATS].
 */
object FaceUpdates {
    private const val TAG = "FaceUpdates"

    // The old way's broadcast (as the library sent it before Wear OS 6).
    private const val RECEIVER_PACKAGE = "com.google.android.wearable.app"
    private const val ACTION_REQUEST_UPDATE_ALL = "android.support.wearable.complications.ACTION_REQUEST_UPDATE_ALL"
    private const val EXTRA_PROVIDER_COMPONENT = "android.support.wearable.complications.EXTRA_PROVIDER_COMPONENT"
    private const val EXTRA_PENDING_INTENT = "android.support.wearable.complications.EXTRA_PENDING_INTENT"

    /** An ask is timed so the complication service is asked for the data this long after a beat (ms). */
    const val AFTER_BEAT_MS = 20L

    /** How long asks are taken to take before any has been timed (ms): a few tens on Wear OS 6. */
    private const val TRIP_START_MS = 40f

    /** What an ask is for: the order asks go in, when a beat can't take them all. */
    enum class Rank { TAP, MOTION, SONG, READING }

    /**
     * How much work a fetch of [component]'s complication is for the face (units: see the class's
     * comment): the date pill while it carries the song's cover, a new picture for the face to
     * unpack and draw with the name (2); the rest numbers and a line or two of text (1).
     */
    private fun costOf(component: ComponentName): Float {
        if (component.className != DatePanelService::class.java.name) return 1f
        val ctx = app ?: return 1f
        return if (NowPlaying.art(ctx) != null) 2f else 1f
    }

    /** The rank an ask has unless it's given one: the compass is motion, the rest readings. */
    private fun rankOf(component: ComponentName): Rank =
        if (component.className == CompassComplicationService::class.java.name) Rank.MOTION else Rank.READING

    /** However much there is to do, an ask goes once it has waited this many beats (1 s). */
    private const val MAX_WAIT_BEATS = 8L

    // ---- How busy the watch is -------------------------------------------------------------------

    /**
     * How busy the watch is: 0 when it keeps up with ease, 1 when the beats' asks go [LATE_UNIT_MS]
     * late or asks take [TRIP_UNIT_MS] longer than usual to arrive, and so on, smoothed (it rises
     * quickly and falls slowly, so a burst of work doesn't let the next straight in).
     */
    @Volatile
    private var load = 0f
    private const val LATE_UNIT_MS = 30f
    private const val TRIP_UNIT_MS = 60f

    /**
     * How busy the watch is now: 0 calm, 1 busy, 2 very busy. While it settles after a song starts
     * (as Cover Display says: [quietUntil]) busy at the least.
     */
    fun level(): Int {
        val measured = when {
            load < 0.6f -> 0
            load < 1.5f -> 1
            else -> 2
        }
        return if (System.currentTimeMillis() < quietUntil) maxOf(measured, 1) else measured
    }

    /**
     * Until when (wall clock, ms) the watch settles after a song started: the Now bar slides in
     * and Cover Display puts the song on the face (see [quiet]).
     */
    @Volatile
    private var quietUntil = 0L

    /**
     * The watch settles until [untilMs] (wall clock; 0: it has), as Cover Display says as a song
     * starts playing: readings wait until then, and the beats' budget is a busy
     * watch's at the least. A tap's page, the compass heading and the song go as before.
     */
    fun quiet(untilMs: Long) {
        synchronized(pending) {
            quietUntil = untilMs
            val after = if (untilMs > 0L) Beat.at(untilMs) + 1 else Long.MIN_VALUE
            for (a in pending.values) if (a.rank == Rank.READING) a.after = after
            schedule()
        }
    }

    // Each beat's budget, by level: what it fills up by each beat, and what it holds at most.
    // Calm, a beat takes up to four (a cover and a reading, say, but not a cover and its colour);
    // very busy, about one a beat.
    private val REFILL = floatArrayOf(3f, 2f, 1f)
    private val CAPACITY = floatArrayOf(4f, 3f, 3f)
    private fun refill() = REFILL[level()]
    private fun capacity() = CAPACITY[level()]

    private fun noteLoad(sample: Float) {
        val s = sample.coerceIn(0f, 4f)
        load += (s - load) * (if (s > load) 0.5f else 0.15f)
    }

    // ---- The asks waiting ------------------------------------------------------------------------

    /** One complication's ask: its rank, cost, group, and from when. */
    private class Ask(var rank: Rank, val cost: Float, var group: String?, val since: Long, var after: Long)

    /** One requester for each complication, made once (the library means them to be kept). */
    private val requesters = java.util.concurrent.ConcurrentHashMap<ComponentName, ComplicationDataSourceUpdateRequester>()

    private val main = Handler(Looper.getMainLooper())

    private val pending = LinkedHashMap<ComponentName, Ask>()
    private var waiting = false
    private var plannedBeat = 0L
    private var plannedAt = 0L
    private var tokens = CAPACITY[0]
    private var tokensBeat = Long.MIN_VALUE
    private var songHold = Long.MIN_VALUE
    @Volatile
    private var app: Context? = null

    // When each complication was last asked for (elapsed ms), and how long asks have been taking
    // to reach the complication services (ms, smoothed: half way to the latest each time), and
    // about the least they take (it creeps up slowly between, so it follows the watch).
    private val askedAt = java.util.concurrent.ConcurrentHashMap<ComponentName, Long>()
    @Volatile
    private var tripMs = TRIP_START_MS
    @Volatile
    private var tripFloor = TRIP_START_MS
    private const val FLOOR_CREEP_MS = 0.1f

    /** An ask taking longer than this to arrive (ms) counts toward how busy the watch is, whatever the usual. */
    private const val TRIP_SLOW_MS = 120f

    private val sendWaiting = Runnable {
        // (How late this ran: the watch's cores were busy.)
        noteLoad((SystemClock.uptimeMillis() - plannedAt) / LATE_UNIT_MS)
        val asks = synchronized(pending) {
            waiting = false
            pick(plannedBeat)
        }
        val context = app
        if (context != null) for (component in asks) send(context, component)
        val done = synchronized(pending) {
            schedule()
            if (pending.isEmpty() && !waiting) toFinish.takeAll() else emptyList()
        }
        done.forEach { it.finish() }
    }

    /**
     * Every face showing [service]'s complication fetches it again, just after one of the seconds
     * hand's next beats: the next, unless that has its fill of work. [rank] if given (a tap's
     * page: [Rank.TAP]), else the complication's own; [group]: asks that go together.
     */
    fun request(context: Context, service: Class<*>, rank: Rank? = null, group: String? = null) {
        val ctx = context.applicationContext
        app = ctx
        val component = ComponentName(ctx, service)
        val r = rank ?: rankOf(component)
        val cost = costOf(component)
        val now = System.currentTimeMillis()
        val beat = Beat.at(now)
        synchronized(pending) {
            val had = pending[component]
            val after = when {
                r == Rank.SONG && songHold >= beat -> songHold + 1
                r == Rank.READING && now < quietUntil -> Beat.at(quietUntil) + 1
                else -> Long.MIN_VALUE
            }
            if (had == null) {
                pending[component] = Ask(r, cost, group, beat, after)
            } else {
                if (r < had.rank) {
                    had.rank = r
                    had.after = after
                }
                if (group != null) had.group = group
            }
            schedule()
        }
    }

    /**
     * Cover Display has work for the face until [untilMs] (wall clock: a new song's cover and its
     * colour, as it plans them), so the song's name and cover here wait for the beat after.
     */
    fun holdSong(untilMs: Long) {
        synchronized(pending) {
            // (Told again as Cover Display's work goes: sooner, or later, than it said before.)
            songHold = if (untilMs > 0L) Beat.at(untilMs) else Long.MIN_VALUE
            val after = if (untilMs > 0L) songHold + 1 else Long.MIN_VALUE
            for (a in pending.values) if (a.rank == Rank.SONG) a.after = after
            schedule()
        }
    }

    /** Whether every ask has gone (nothing waits for a beat). */
    fun idle(): Boolean = synchronized(pending) { pending.isEmpty() && !waiting }

    /**
     * For a broadcast's receiver: finishes [result] once every ask has gone ([idle]), [maxMs] at
     * the most. The watch freezes an app it isn't showing soon after its receiver is done, and an
     * ask still waiting for its beat (or for Cover Display's work for a new song) would wait
     * until the app next woke.
     */
    fun finishWhenIdle(result: android.content.BroadcastReceiver.PendingResult, maxMs: Long = 2_500L) {
        main.post {
            if (idle()) {
                result.finish()
            } else {
                // (Finished as the last ask goes, see sendWaiting, or at maxMs: whichever takes
                // it first. Finished twice, it would crash the app: with SharedPreferences work
                // still being written, finish() queues the real finish for later, out of reach
                // of any try here, and the second throws "Broadcast already finished".)
                toFinish.add(result)
                main.postDelayed({ if (toFinish.take(result)) result.finish() }, maxMs)
            }
        }
    }

    // Receivers' results waiting for every ask to go (see finishWhenIdle).
    private val toFinish = Held<android.content.BroadcastReceiver.PendingResult>()

    /** Things held until they're taken: each taken once, by whichever asks first. */
    internal class Held<T> {
        private val held = ArrayList<T>()

        @Synchronized fun add(item: T) {
            held.add(item)
        }

        /** Whether [item] was still held: only then is it the caller's. */
        @Synchronized fun take(item: T): Boolean = held.remove(item)

        @Synchronized fun takeAll(): List<T> = held.toList().also { held.clear() }
    }

    /**
     * Plans the next beat's asks (under the lock): just after the first beat an ask can reach.
     * Planned already, it's only brought forward: a tap or a heading that comes while the song
     * waits for Cover Display's work (or readings for the watch to settle) still goes at once.
     */
    private fun schedule() {
        if (pending.isEmpty()) return
        val now = System.currentTimeMillis()
        val trip = tripMs.toLong()
        val reach = Beat.at(now + trip - AFTER_BEAT_MS - 1) + 1
        val beat = maxOf(reach, pending.values.minOf { it.after })
        if (waiting) {
            if (beat >= plannedBeat) return
            main.removeCallbacks(sendWaiting)
        }
        val delay = (Beat.time(beat) + AFTER_BEAT_MS - trip - now).coerceAtLeast(0L)
        plannedBeat = beat
        plannedAt = SystemClock.uptimeMillis() + delay
        waiting = true
        main.postDelayed(sendWaiting, delay)
    }

    /**
     * The asks that go at [beat] (under the lock), taken off the waiting list: in rank order, then
     * oldest first, as many as the beat's budget takes; a tap's, and one that's waited
     * [MAX_WAIT_BEATS], whatever; and, with one, those of its group.
     */
    private fun pick(beat: Long): List<ComponentName> {
        val cap = capacity()
        tokens = if (tokensBeat == Long.MIN_VALUE) cap else minOf(cap, tokens + refill() * (beat - tokensBeat).coerceAtLeast(0L))
        tokensBeat = beat
        val out = ArrayList<ComponentName>()
        val order = pending.entries.filter { it.value.after <= beat }.sortedWith(compareBy({ it.value.rank }, { it.value.since }))
        val groups = HashSet<String>()
        for ((component, a) in order) {
            val fits = a.cost <= tokens + 0.001f || (out.isEmpty() && tokens >= cap - 0.001f)
            val must = a.rank == Rank.TAP || beat - a.since >= MAX_WAIT_BEATS || (a.group != null && a.group in groups)
            if (!fits && !must) continue
            tokens -= a.cost
            a.group?.let { groups.add(it) }
            out.add(component)
        }
        // (Those of a group one of which went before its fellows were looked at.)
        if (groups.isNotEmpty()) {
            for ((component, a) in order) {
                if (a.group in groups && component !in out) {
                    tokens -= a.cost
                    out.add(component)
                }
            }
        }
        for (component in out) pending.remove(component)
        if (Trace.isEnabled()) {
            Trace.setCounter("face load x100", (load * 100f).toLong())
            Trace.setCounter("face budget x10", (tokens * 10f).toLong())
            Trace.setCounter("face asks waiting", pending.size.toLong())
        }
        return out
    }

    /**
     * A face asked [service]'s complication for its data (from its onComplicationRequest): how long
     * that took from the ask is noted, asks are timed by it, and how much longer than usual it
     * took is part of how busy the watch is.
     */
    fun answered(context: Context, service: Class<*>) {
        val at = askedAt.remove(ComponentName(context.applicationContext, service)) ?: return
        val trip = (SystemClock.elapsedRealtime() - at).toFloat()
        if (trip < 0f || trip > 3_000f) return
        tripMs += (trip - tripMs) * 0.5f
        // (The usual: about the least they take, followed down a third of the way at a time, so
        // one quick one doesn't make the rest look slow, and up by a hair at a time.)
        tripFloor = if (trip < tripFloor) tripFloor + (trip - tripFloor) * 0.3f else tripFloor + FLOOR_CREEP_MS
        // (Slow next to the usual, or slow at all: a watch busy for long makes the usual slow too.)
        noteLoad(maxOf(trip - tripFloor, trip - TRIP_SLOW_MS) / TRIP_UNIT_MS)
    }

    private fun send(app: Context, component: ComponentName) {
        // (Marked in a system trace of the watch that traces this app, as the trace recorder in
        // tools/trace does: each ask shows, by complication, next to what the watch does with it.)
        val traced = Trace.isEnabled()
        if (traced) Trace.beginSection("ask faces: " + component.shortClassName.substringAfterLast('.'))
        askedAt[component] = SystemClock.elapsedRealtime()
        try {
            val requester = requesters.getOrPut(component) { ComplicationDataSourceUpdateRequester.create(app, component) }
            requester.requestUpdateAll()
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't ask for ${component.shortClassName} the new way; asking the old way", t)
            broadcast(app, component)
        } finally {
            if (traced) Trace.endSection()
        }
    }

    private fun broadcast(context: Context, component: ComponentName) {
        try {
            val intent = Intent(ACTION_REQUEST_UPDATE_ALL)
                .setPackage(RECEIVER_PACKAGE)
                .putExtra(EXTRA_PROVIDER_COMPONENT, component)
                // (The system checks the request comes from the complication's own app by this.)
                .putExtra(EXTRA_PENDING_INTENT, PendingIntent.getActivity(context, 0, Intent(""), PendingIntent.FLAG_IMMUTABLE))
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't ask the faces for ${component.shortClassName}", t)
        }
    }
}
