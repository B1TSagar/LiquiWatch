package com.liquiwatch.wear.compass

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.liquiwatch.wear.FaceUpdates
import com.liquiwatch.wear.R
import com.liquiwatch.wear.panels.OnScreen
import com.liquiwatch.wear.panels.Page
import com.liquiwatch.wear.panels.Panel
import com.liquiwatch.wear.panels.Panels
import kotlin.math.abs

/*
 * The live compass for the watch faces.
 *
 * A complication can't read the compass itself, and Wear OS only asks a complication for new
 * data every few minutes at the most. So while a watch face shows the Compass complication,
 * [LiveCompassService] keeps reading the compass whenever the screen is on (never while it's off
 * or dimmed, while the Compass app is open, which hands the faces its heading as it closes, or
 * while the watch is on its charger, whose magnets turn the heading by themselves: the faces keep
 * the last heading read until it comes off)
 * and hands each new heading to the faces itself as the wrist turns. Wear OS lets an app read the
 * sensors in the background only from a "foreground service", which shows an ongoing notification
 * while it runs; it stops by itself when no face shows the compass any more.
 *
 * Each new heading costs the watch work: it goes to the system's complication service, which
 * hands it to the face, and the Expedition face glides its card there, steadily, over 1.25 s (its
 * own animation), drawing all the while. So a heading goes only once it has turned a degree from
 * the last one handed over, and no sooner than a second ([GLIDE_MS]) after it: while the wrist
 * turns, each comes before the card has got to the last, and the card glides on without stopping
 * (it eased in and out of every glide, which showed as a pulse a second); once it stops, the
 * card stops there. The heading handed over is the one the compass reads then, not one guessed
 * ahead, so the card never runs on past where the wrist stops. The compass is read about 15 times
 * a second and smoothed while the wrist is still (its jitter isn't turning) and hardly at all
 * while it turns. For a moment after the screen comes on the sensor settles, so the first heading
 * waits a fifth of a second for that. The compass panel's reading changes at most every three
 * seconds.
 */
object LiveCompass {
    private const val TAG = "LiveCompass"
    private const val PREFS = "live_compass"
    private const val KEY_SLOTS = "slots"
    private const val KEY_TURNED = "turned"

    /** The latest heading the live compass read (in this process), and when. */
    @Volatile
    var latest: Heading? = null
        private set

    /**
     * The heading as a running total (it keeps counting past 360°, or below 0°, as the wrist
     * turns round), so the face's knob always glides the short way: from 359° to 1° is +2°.
     */
    @Volatile
    var turned: Float = Float.NaN
        private set

    /** Whether a watch face shows the Compass complication (Liquid's and Covers' needles). */
    fun cardShown(context: Context): Boolean =
        prefs(context).getStringSet(KEY_SLOTS, emptySet()).orEmpty().isNotEmpty()

    /**
     * Whether the live compass is wanted at all: a face shows the Compass complication, or the
     * panels (the Expedition face's 6 o'clock dial turns to the heading; it has no card).
     */
    fun wanted(context: Context): Boolean = cardShown(context) || Panels.anyShown(context)

    /** The compass dial turned to another page: the live compass starts or stops reading to match. */
    fun pageTurned(context: Context) {
        if (!running) return start(context)
        // (A plain start: the app runs a foreground service, so it may.)
        try {
            context.startService(Intent(context, LiveCompassService::class.java))
        } catch (e: IllegalStateException) {
            Log.i(TAG, "Live compass didn't hear of the page")
        }
    }

    /** Whether the live compass is running (in this process). */
    @Volatile
    var running = false
        internal set

    /** A face started or stopped showing the compass in one of its slots. */
    fun setSlot(context: Context, id: Int, showing: Boolean) {
        val p = prefs(context)
        val slots = p.getStringSet(KEY_SLOTS, emptySet()).orEmpty().toMutableSet()
        val changed = if (showing) slots.add(id.toString()) else slots.remove(id.toString())
        if (changed) p.edit().putStringSet(KEY_SLOTS, slots).apply()
        if (showing) {
            stopLater?.let(main::removeCallbacks)
            stopLater = null
            if (!running) start(context)
        } else if (slots.isEmpty() && stopLater == null) {
            // Not at once: a face updated, or put on again, drops its slots and takes them back a
            // moment later, and by then Android no longer lets this app start the live compass from
            // the background (seen on the watch), so it stayed off until the knob was tapped.
            val app = context.applicationContext
            val r = Runnable {
                stopLater = null
                if (!wanted(app)) stop(app)
            }
            stopLater = r
            main.postDelayed(r, STOP_AFTER_MS)
        }
    }

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var stopLater: Runnable? = null

    /** How long the live compass runs on after the last face showing it let go (ms). */
    private const val STOP_AFTER_MS = 30_000L

    /** Whether it's about to stop, the last face having let go a moment ago (it may come back). */
    val stopPending: Boolean
        get() = stopLater != null

    /** Takes a new reading: the running total follows it the short way round. */
    fun note(context: Context, h: Heading): Float {
        val was = if (turned.isNaN()) lastTurned(context) else turned
        turned = if (was.isNaN()) h.degrees else Compass.follow(was, h.degrees)
        latest = h
        return turned
    }

    /**
     * Takes whole turns off the running total once it's far from zero, so it never loses precision
     * however long the wrist goes round one way: only as reading stops (the screen going off), as
     * the Expedition face's card glides to every new total, and would spin round all those turns.
     */
    fun recentre() {
        if (abs(turned) <= 36_000f) return
        turned -= 360f * Math.round(turned / 360f)
        handedTo = Float.NaN
    }

    /** The heading the faces were last handed (NaN before the first), and when (elapsed ms). */
    @Volatile
    var handedTo = Float.NaN
        private set

    @Volatile
    var handedAt = 0L
        private set

    /** How many headings the faces have been handed (so the live compass knows its ask was answered). */
    @Volatile
    var handOvers = 0L
        private set

    /** The faces were handed heading [to] (as the dial's value, or as the text, whichever they show). */
    fun handedOver(to: Float) {
        handedTo = to
        handedAt = SystemClock.elapsedRealtime()
        handOvers++
    }

    /**
     * A heading goes at most this often while the wrist turns (ms): a little more often than the
     * Expedition face's card takes to glide to one (1.25 s, steadily), so it never stops between them.
     */
    const val GLIDE_MS = 1_000L

    /** The heading to hand the faces: the running total, or the last saved reading. */
    fun current(context: Context): Float? {
        if (!turned.isNaN()) return turned
        val t = lastTurned(context)
        if (!t.isNaN()) return t
        return Compass.last(context)?.degrees
    }

    /** Keeps the running total (so a restarted compass carries on from it). */
    fun keep(context: Context) {
        if (!turned.isNaN()) prefs(context).edit().putFloat(KEY_TURNED, turned).apply()
    }

    private fun lastTurned(context: Context): Float {
        val p = prefs(context)
        return if (p.contains(KEY_TURNED)) p.getFloat(KEY_TURNED, 0f) else Float.NaN
    }

    /**
     * Starts the live compass if a face shows the compass (fine to call often). [appOpen]: the
     * Compass app opened (it reads the compass itself meanwhile) or closed.
     */
    fun start(context: Context, appOpen: Boolean? = null) {
        if (!wanted(context) || !Compass.available(context)) return
        val intent = Intent(context, LiveCompassService::class.java)
        if (appOpen != null) intent.putExtra(LiveCompassService.EXTRA_APP_OPEN, appOpen)
        try {
            // (As the app closes it's no longer in the foreground, and may not start a foreground
            // service: the live compass runs already, and may be handed the news as a plain start.)
            if (appOpen == false) context.startService(intent) else context.startForegroundService(intent)
        } catch (e: IllegalStateException) {
            // Not allowed from the background just now (Android 12+): it starts next time the
            // compass screen opens, the watch restarts, or the app is updated.
            if (Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException) {
                Log.i(TAG, "Live compass can't start from the background now")
            } else {
                Log.w(TAG, "Live compass didn't start", e)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Live compass didn't start", e)
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, LiveCompassService::class.java))
    }

    // (Read afresh in each process: the Compass app, in a process of its own, starts the live
    // compass if a face wants it.)
    @Suppress("DEPRECATION")
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS)
}

/** See [LiveCompass]. */
class LiveCompassService : Service() {
    private lateinit var sensor: HeadingSensor
    private val main = Handler(Looper.getMainLooper())
    private var reading = false
    private var registered = false

    // When the faces were last asked for the heading (elapsed ms), and how many headings they had
    // been handed then (they've fetched it once there's another).
    private var sentAt = 0L
    private var sentFor = -1L
    // Whether the Compass app is open (until it says it's closed, or the screen goes off, should
    // its process have gone without saying).
    private var appOpen = false
    // Whether the watch is on its charger (or any power).
    private var charging = false
    // Whether a face shows the Compass complication (else only the compass dial wants the heading).
    private var cards = false
    private var panelText: String? = null
    private var panelAt = 0L
    private var panelLater: Runnable? = null

    // The heading as sent to the faces: the sensor's (lightly smoothed already) through a "one
    // euro" filter, which smooths it hard while the wrist is still, so its jitter doesn't count
    // as turning, and less the faster the wrist turns, so the card keeps up. Worked on the
    // heading as a running total (359° to 1° is 2° on).
    private var euroIn = Float.NaN // the last reading, as a running total (degrees)
    private var euroOut = 0f // the smoothed heading, as a running total (degrees)
    private var euroSpeed = 0f // how fast the wrist turns, smoothed (degrees a second)
    private var euroAt = 0L

    // Just after the screen comes on: until then, nothing goes to the faces.
    private var settledAt = 0L
    private var settle: Runnable? = null

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // (On or off the charger the face turns two panels by itself: their readings, if old.)
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> {
                    charging = intent.action == Intent.ACTION_POWER_CONNECTED
                    update()
                    if (looking) look(CHARGER_LOOK_MS)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    appOpen = false
                    update()
                }
                else -> update()
            }
        }
    }

    // While the screen's on: the panels' pages up are looked at (OnScreen), a moment after the
    // screen comes on and every minute after.
    private var looking = false
    private val looker = Runnable {
        OnScreen.check(this)
        look(OnScreen.EVERY_MS)
    }

    private fun look(afterMs: Long) {
        main.removeCallbacks(looker)
        main.postDelayed(looker, afterMs)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // (Smoothed only a little here: the one-euro filter below smooths what needs it, hard
        // while the wrist is still, and two smoothings one after the other only add lag.)
        // (No magnetometer stream for its calibration state: the faces don't show it, and it was
        // five more wake-ups a second of this app while the screen is on, as the face draws.)
        sensor = HeadingSensor(this, SENSOR_SMOOTHING_S, tracksCalibration = false)
        sensor.onReading = { h, _ -> onHeading(h) }
        goForeground()
        LiveCompass.running = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(screen, filter)
        registered = true
        charging = Compass.onCharger(this)
        keepCoverDisplay()
    }

    // ---- Keeping Cover Display awake -----------------------------------------------------------

    /**
     * While this runs (the Expedition face is up), Cover Display, the music app, is kept bound: a
     * Galaxy Watch freezes an app it isn't showing once it's been idle a few seconds, and frozen,
     * Cover Display isn't handed the phone's messages, so the face's album art and the date pill's
     * song never came (seen in the watch's log). Bound by this app, which runs in the foreground,
     * it isn't idle. (Nothing passes between them this way: the binding only keeps it awake.) Bound
     * as this starts, and again each time the screen comes on if it isn't: so a Cover Display
     * installed or updated while this runs is kept too.
     */
    private var keeping = false
    private var keeperRetry: Runnable? = null

    private val keeper = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
            Log.i(TAG, "Keeping Cover Display awake for the face")
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            // (Its process went: the system brings it back by itself, the binding stands.)
        }

        override fun onBindingDied(name: android.content.ComponentName?) {
            // (Updated or reinstalled: bound again a moment later.)
            dropKeeper()
            keeperRetry?.let(main::removeCallbacks)
            keeperRetry = Runnable {
                keeperRetry = null
                keepCoverDisplay()
            }.also { main.postDelayed(it, KEEPER_RETRY_MS) }
        }

        override fun onNullBinding(name: android.content.ComponentName?) {
            dropKeeper()
        }
    }

    private fun keepCoverDisplay() {
        if (keeping) return
        keeping = try {
            bindService(
                Intent().setComponent(android.content.ComponentName(COVER_DISPLAY, COVER_DISPLAY_KEEPER)),
                keeper,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't keep Cover Display awake", e)
            false
        }
        if (!keeping) {
            // (Not installed, or an older Cover Display without the service: let go of the attempt.)
            try {
                unbindService(keeper)
            } catch (e: Exception) {
                // (Never bound.)
            }
        }
    }

    private fun dropKeeper() {
        if (!keeping) return
        keeping = false
        try {
            unbindService(keeper)
        } catch (e: Exception) {
            // (Already let go.)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (intent?.hasExtra(EXTRA_APP_OPEN) == true) appOpen = intent.getBooleanExtra(EXTRA_APP_OPEN, false)
        update()
        return START_STICKY
    }

    override fun onDestroy() {
        keeperRetry?.let(main::removeCallbacks)
        main.removeCallbacks(looker)
        looking = false
        stopReading()
        if (registered) unregisterReceiver(screen)
        registered = false
        panelLater?.let(main::removeCallbacks)
        settle?.let(main::removeCallbacks)
        LiveCompass.keep(this)
        LiveCompass.running = false
        dropKeeper()
        super.onDestroy()
    }

    /**
     * Reads the compass only while a face shows it (the Compass complication, or the Expedition
     * face's compass dial with its compass page up), the screen is on (not dimmed), the Compass app
     * is shut and the watch is off its charger (its magnets turned the heading by themselves, and
     * the face's card glided after it all the while, which also slowed the face).
     */
    private fun update() {
        if (!LiveCompass.wanted(this)) {
            // (Unless the last face let go only a moment ago: see LiveCompass.setSlot.)
            if (!LiveCompass.stopPending) stopSelf()
            return
        }
        val power = getSystemService(PowerManager::class.java)
        cards = LiveCompass.cardShown(this)
        val page = Panels.anyShown(this) && Panels.visible(this, Panel.COMPASS) == Page.COMPASS
        val on = power != null && power.isInteractive && !power.isPowerSaveMode && !appOpen && !charging && (cards || page)
        if (on) startReading() else stopReading()
        // The panels' readings: looked at only while the screen's on (whether or not the
        // compass can be read), the first time a moment after it comes on, when the face is
        // busiest (OnScreen.WAKE_DELAY_MS).
        val interactive = power != null && power.isInteractive
        if (interactive && !keeping) keepCoverDisplay()
        if (interactive && !looking) {
            looking = true
            look(OnScreen.WAKE_DELAY_MS)
        } else if (!interactive && looking) {
            looking = false
            main.removeCallbacks(looker)
        }
    }

    private fun startReading() {
        if (reading) return
        euroIn = Float.NaN
        // About 15 readings a second (it was 50): the face is handed a heading at most once a
        // second, so more only made work for the watch (its sensor service, this app and the
        // system, all the while the screen is on, as the face draws).
        reading = sensor.start(SensorManager.SENSOR_DELAY_UI)
        if (!reading) return
        // The sensor settles, and the watch finishes waking up, before the first heading goes.
        settledAt = SystemClock.elapsedRealtime() + WAKE_SETTLE_MS
        // (The compass panel's reading waits longer: the face is busiest just after the screen
        // comes on, and the card shows the heading meanwhile.)
        panelAt = maxOf(panelAt, SystemClock.elapsedRealtime() - PANEL_GAP_MS + WAKE_PANEL_MS)
        settle?.let(main::removeCallbacks)
        val r = Runnable {
            settle = null
            send()
        }
        settle = r
        main.postDelayed(r, WAKE_SETTLE_MS)
    }

    private fun stopReading() {
        if (!reading) return
        sensor.stop()
        reading = false
        settle?.let(main::removeCallbacks)
        settle = null
        // The faces keep the last heading.
        LiveCompass.latest?.let { Compass.save(this, it) }
        LiveCompass.recentre()
        LiveCompass.keep(this)
        send(force = true)
    }

    private fun onHeading(raw: Heading) {
        val h = smooth(raw)
        val turned = LiveCompass.note(this, h)
        val now = SystemClock.elapsedRealtime()
        if (now < settledAt) return // (the first goes once it's settled: see startReading)
        // (No face shows the Compass complication: only the compass dial's reading, at most every
        // PANEL_GAP_MS while its compass page is up.)
        if (!cards) return panel(force = false)
        if (waiting(now)) return // (asked already: the face hasn't fetched it yet)
        val last = LiveCompass.handedTo
        // Once the heading has turned a degree from the last one handed over, as the card's glide
        // to that is about over (so the glides run on one into the next).
        if (last.isNaN() || (abs(turned - last) >= STEP_DEG && now - LiveCompass.handedAt >= LiveCompass.GLIDE_MS - ASK_LEAD_MS)) send()
    }

    /** Whether the faces were asked for the heading and haven't fetched it yet (for a moment at most). */
    private fun waiting(now: Long): Boolean =
        sentAt != 0L && LiveCompass.handOvers == sentFor && now - sentAt < ASK_TIMEOUT_MS

    /** [raw] through the one-euro filter (see [euroIn]). */
    private fun smooth(raw: Heading): Heading {
        val now = SystemClock.elapsedRealtime()
        if (euroIn.isNaN()) {
            euroIn = raw.degrees
            euroOut = raw.degrees
            euroSpeed = 0f
        } else {
            val dt = ((now - euroAt) / 1000f).coerceIn(0.005f, 1f)
            val x = Compass.follow(euroIn, raw.degrees)
            val speed = (x - euroIn) / dt
            euroSpeed += (speed - euroSpeed) * alpha(SPEED_CUTOFF_HZ, dt)
            euroOut += (x - euroOut) * alpha(STILL_CUTOFF_HZ + SPEED_GAIN * abs(euroSpeed), dt)
            euroIn = x
            if (abs(euroIn) > 36_000f) {
                // (Kept within a few turns of zero, a whole number of turns at a time.)
                val turns = 360f * Math.round(euroIn / 360f)
                euroIn -= turns
                euroOut -= turns
            }
        }
        euroAt = now
        return raw.copy(degrees = (euroOut % 360f + 360f) % 360f)
    }

    /** How far a low-pass filter at [cutoffHz] moves towards a new reading [dt] seconds on. */
    private fun alpha(cutoffHz: Float, dt: Float): Float {
        val tau = 1f / (2f * Math.PI.toFloat() * cutoffHz)
        return 1f / (1f + tau / dt)
    }

    /**
     * Asks the faces to fetch the heading (the compass complication hands them the latest), just
     * after the seconds hand's next beat (FaceUpdates), and the compass panel its reading when that
     * changes. [force]: even while the last ask is still waiting.
     */
    private fun send(force: Boolean = false) {
        if (LiveCompass.turned.isNaN()) return
        val now = SystemClock.elapsedRealtime()
        if (!force && waiting(now)) return
        sentAt = now
        sentFor = LiveCompass.handOvers
        if (cards) FaceUpdates.request(this, CompassComplicationService::class.java)
        panel(force)
    }

    /**
     * The compass panel's reading, when it changes: at most every [PANEL_GAP_MS], the latest then;
     * and only while its compass page is up (on the battery page the heading isn't shown: a tap
     * back to it has the face fetch it then, with the latest heading).
     */
    private fun panel(force: Boolean) {
        val h = LiveCompass.latest ?: return
        if (!Panels.anyShown(this) || Panels.visible(this, Panel.COMPASS) != Page.COMPASS) return
        val text = Compass.point(h.degrees) + " " + Compass.degreesText(h.degrees)
        if (text == panelText) return
        val now = SystemClock.elapsedRealtime()
        if (force || now - panelAt >= PANEL_GAP_MS) {
            panelLater?.let(main::removeCallbacks)
            panelLater = null
            panelText = text
            panelAt = now
            Compass.save(this, h)
            Panels.refresh(this, Panel.COMPASS)
        } else if (panelLater == null) {
            val r = Runnable {
                panelLater = null
                panel(force = false)
            }
            panelLater = r
            main.postDelayed(r, PANEL_GAP_MS - (now - panelAt))
        }
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.live_compass_channel), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, CompassActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_compass)
            .setContentTitle(getString(R.string.live_compass_title))
            .setContentText(getString(R.string.live_compass_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        /** Started with this: whether the Compass app opened (true) or closed (false). */
        const val EXTRA_APP_OPEN = "app_open"

        private const val TAG = "LiveCompass"
        const val CHANNEL = "live_compass"
        const val NOTIFICATION_ID = 7

        /** Cover Display, and its service that keeps it awake while bound (see keepCoverDisplay). */
        const val COVER_DISPLAY = "com.coverflow.app"
        const val COVER_DISPLAY_KEEPER = "com.coverflow.wear.face.FaceKeeperService"

        /** After Cover Display is updated, it's bound again this much later (ms). */
        const val KEEPER_RETRY_MS = 3_000L

        /**
         * A heading goes once it has turned this far from the last one handed over (degrees): more
         * than a still wrist wobbles. The face animates every glide (a second, many frames of
         * work), and at 0.3 a resting wrist's wobble, or a charger's magnets, kept it gliding.
         */
        const val STEP_DEG = 1.0f

        /** About how long an ask takes to reach the face (ms): the next one goes this much before the glide ends. */
        const val ASK_LEAD_MS = 150L

        /** An ask the faces haven't answered in this long (ms) is given up, and asked again. */
        const val ASK_TIMEOUT_MS = 500L

        /** The sensor's own smoothing here (s): light, the one-euro filter does the rest. */
        const val SENSOR_SMOOTHING_S = 0.03f

        /**
         * The one-euro filter: how hard a still wrist's heading is smoothed (a 1 Hz low-pass,
         * about a sixth of a second: harder held the card back as a turn began, and the card
         * only moves off once the heading is [STEP_DEG] from it, so a little jitter sends
         * nothing), how much less for each degree a second it turns, and how its turning speed
         * is smoothed.
         */
        const val STILL_CUTOFF_HZ = 1.0f
        const val SPEED_GAIN = 0.05f
        const val SPEED_CUTOFF_HZ = 3.0f

        /**
         * After the screen comes on, the first heading waits this long (ms): the sensor's first
         * readings settle in about that (it used to be half a second, which showed as the card
         * sitting still for a moment after the screen came on).
         */
        const val WAKE_SETTLE_MS = 200L

        /** The compass panel's reading changes at most every three seconds. */
        const val PANEL_GAP_MS = 3_000L

        /** After the screen comes on, the compass panel's reading changes no sooner than this (ms). */
        const val WAKE_PANEL_MS = 1_500L

        /** On or off the charger, the panels' readings are looked at this much later (ms): the face turns first. */
        const val CHARGER_LOOK_MS = 1_000L
    }
}

/**
 * Starts the live compass again after the watch restarts or the app is updated (the system lets
 * an app start a foreground service for a few seconds after either). It also asks the faces for
 * the compass and the panels, so a face that was already showing them (before this version knew
 * to look) is noted, and starts it, within those seconds; and asks Cover Display what's playing.
 */
class LiveCompassStarter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                LiveCompass.start(context)
                CompassComplicationService.refresh(context)
                // (And the panels, so a face showing them is noted: see Panels.anyShown.)
                for (panel in Panel.entries) Panels.refresh(context, panel)
                // (And what's playing, for the date pill: Cover Display tells it as it changes.)
                com.liquiwatch.wear.panels.NowPlaying.ask(context)
            }
        }
    }
}
