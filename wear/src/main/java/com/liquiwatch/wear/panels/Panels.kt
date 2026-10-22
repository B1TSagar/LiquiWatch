package com.liquiwatch.wear.panels

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.liquiwatch.wear.FaceUpdates
import com.liquiwatch.wear.compass.Compass
import com.liquiwatch.wear.compass.LiveCompass
import com.liquiwatch.wear.health.HealthStore
import com.liquiwatch.wear.health.PhoneBatteryStore
import com.liquiwatch.wear.health.WeatherStore
import com.liquiwatch.wear.ui.Haptics

/**
 * The four glass panels on the LiquiWatch · Expedition face, each showing one of a few readings
 * at a time. Watch faces can't keep state of their own, so the companion keeps it: each panel's
 * slot on the face holds this panel's "page" complication, a ranged value that says where the
 * panel's pages stand. Tapping the panel runs [PageTapReceiver], which moves it on one, and the
 * face slides the next page in.
 *
 * [pages] is how many readings the panel has; [places] how many positions they go round: six
 * (each page twice or three times), so the face can slide a page in from either side and never
 * shows the one going round behind. The face works out the page as value % pages.
 *
 * While the watch charges, the face itself turns the compass panel to the battery and the date
 * panel to the weather (and back afterwards); taps on those two do nothing meanwhile. While a
 * song plays (as Cover Display says: see NowPlaying), the date panel shows the song instead of
 * its pages, and a tap on it opens Cover Display's Now Playing.
 */
enum class Panel(val key: String, val pages: Int, val service: Class<out PanelPageService>) {
    HEART("heart", 3, HeartPanelService::class.java),     // heart rate, sleep score, Energy score
    STEPS("steps", 3, StepsPanelService::class.java),     // steps, active calories, blood oxygen
    COMPASS("compass", 2, CompassPanelService::class.java), // compass heading, battery
    DATE("date", 2, DatePanelService::class.java),        // date and moon, weather
    ;

    val places: Int get() = 6

    /** The face turns it to a page of its own while the watch charges. */
    val heldWhileCharging: Boolean get() = this == COMPASS || this == DATE

    companion object {
        fun of(key: String?): Panel? = entries.firstOrNull { it.key == key }
    }
}

/** What a panel shows now (see Panels.visible). */
enum class Page { HEART_RATE, SLEEP, ENERGY, STEPS, KCAL, OXYGEN, COMPASS, BATTERY, DATE, WEATHER, SONG }

/** Where each panel's pages stand (kept across restarts), and telling the faces to look again. */
object Panels {
    private const val PREFS = "panel_pages"

    fun value(context: Context, panel: Panel): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(panel.key, 0).mod(panel.places)

    /** Moves [panel] on to its next page; returns the page now up (0 until pages - 1). */
    fun next(context: Context, panel: Panel): Int {
        val value = (value(context, panel) + 1) % panel.places
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(panel.key, value).apply()
        return value % panel.pages
    }

    /**
     * Asks every face showing [panel] to fetch it again ([rank]: how soon, when a beat can't take
     * all the faces' updates, by default a reading's; [group]: updates that go together; see
     * FaceUpdates).
     */
    fun refresh(context: Context, panel: Panel, rank: FaceUpdates.Rank = FaceUpdates.Rank.READING, group: String? = null) {
        FaceUpdates.request(context, panel.service, rank, group)
    }

    private const val SLOTS = "panel_slots"

    /** A face started or stopped showing a panel in one of its slots. */
    fun setSlot(context: Context, id: Int, showing: Boolean) {
        val p = context.getSharedPreferences(SLOTS, Context.MODE_PRIVATE)
        val slots = p.getStringSet(SLOTS, emptySet()).orEmpty().toMutableSet()
        val changed = if (showing) slots.add(id.toString()) else slots.remove(id.toString())
        if (changed) p.edit().putStringSet(SLOTS, slots).apply()
    }

    /** Whether a face shows the panels at all (Liquid and Covers don't: then nothing's worked out for them). */
    fun anyShown(context: Context): Boolean =
        context.getSharedPreferences(SLOTS, Context.MODE_PRIVATE).getStringSet(SLOTS, emptySet()).orEmpty().isNotEmpty()

    /** Whether the watch is charging (the face turns the compass and date panels by itself then). */
    fun charging(context: Context): Boolean =
        context.getSystemService(BatteryManager::class.java)?.isCharging == true

    /** The page [panel] shows now, as the face works it out. */
    fun visible(context: Context, panel: Panel): Page {
        val v = value(context, panel)
        return when (panel) {
            Panel.HEART -> arrayOf(Page.HEART_RATE, Page.SLEEP, Page.ENERGY)[v % 3]
            Panel.STEPS -> arrayOf(Page.STEPS, Page.KCAL, Page.OXYGEN)[v % 3]
            Panel.COMPASS -> if (charging(context)) Page.BATTERY else arrayOf(Page.COMPASS, Page.BATTERY)[v % 2]
            Panel.DATE -> when {
                NowPlaying.showing(context) -> Page.SONG
                charging(context) -> Page.WEATHER
                else -> arrayOf(Page.DATE, Page.WEATHER)[v % 2]
            }
        }
    }

    private const val SHOWN = "panel_shown"

    /**
     * What [panel]'s page complication shows besides where its pages stand, as the panel's
     * service works it out (its text, title and range minimum), for the readings that arrive from
     * the phone: the heart and steps panels' Samsung Health readings and the date panel's weather.
     * Null for the compass panel (its live compass sends its own).
     */
    fun shown(context: Context, panel: Panel): String? = when (panel) {
        Panel.HEART -> HealthStore.current(context).let { shownKey(HealthStore.sleepText(it), HealthStore.energyText(it), 0f) }
        Panel.STEPS -> HealthStore.current(context).let { shownKey(HealthStore.kcalText(it), HealthStore.oxygenText(it), 0f) }
        Panel.DATE -> DatePanelService.shown(context).let { (text, title, min) -> shownKey(text, title, min, NowPlaying.art(context)) }
        Panel.COMPASS -> null
    }

    fun shownKey(text: String, title: String, min: Float, icon: ByteArray? = null) =
        "$text\n$title\n$min" + (icon?.let { "\n" + it.contentHashCode() } ?: "")

    /** Notes what [panel]'s complication just handed the faces ([shown]: see shownKey). */
    fun noteShown(context: Context, panel: Panel, shown: String) {
        context.getSharedPreferences(SHOWN, Context.MODE_PRIVATE).edit().putString(panel.key, shown).apply()
    }

    /**
     * Has the faces fetch [panel] again only if what it shows has changed since it last handed it
     * to them (new readings that read the same, or the same weather sent again, are no work for
     * the face: every update makes the watch work the whole face out again, and readings the
     * phone sent while the screen was off can arrive as it comes on, when the face is busiest).
     */
    fun refreshIfChanged(context: Context, panel: Panel, rank: FaceUpdates.Rank = FaceUpdates.Rank.READING, group: String? = null) {
        val now = shown(context, panel) ?: return refresh(context, panel, rank, group)
        val last = context.getSharedPreferences(SHOWN, Context.MODE_PRIVATE).getString(panel.key, null)
        if (now != last) refresh(context, panel, rank, group)
    }

    /**
     * New readings for [panel]'s [pages]: handed to the faces (if they change what it shows) only
     * while one of those pages is up. Otherwise they're kept, and go with the page when it turns
     * up (the tap that turns it has the faces fetch the panel then).
     */
    fun refreshIfShown(context: Context, panel: Panel, vararg pages: Page) {
        if (visible(context, panel) in pages) refreshIfChanged(context, panel)
    }

    /** Whether the date panel last handed the faces a song (it may have gone since). */
    fun showsSong(context: Context): Boolean {
        val shown = context.getSharedPreferences(SHOWN, Context.MODE_PRIVATE).getString(Panel.DATE.key, null) ?: return false
        return shown.endsWith("\n" + DatePanelService.SONG_CODE) || shown.endsWith("\n" + DatePanelService.SONG_ART_CODE)
    }

    // The compass panel's phone battery as last handed to the faces (see refreshPhoneBattery).
    @Volatile
    private var phoneCode = Float.NaN

    /**
     * The phone's battery came: the compass panel fetched again if it changes what the battery
     * page shows, and only while that page is up.
     */
    fun refreshPhoneBattery(context: Context) {
        if (visible(context, Panel.COMPASS) != Page.BATTERY) return
        if (PhoneBatteryStore.code(context) != phoneCode) refresh(context, Panel.COMPASS)
    }

    /** The compass panel handed the faces the phone's battery as [code] (its range minimum). */
    fun notePhoneBattery(code: Float) {
        phoneCode = code
    }

    /** Each panel's tap: made once (a page goes out on every tap, and asking for one is a trip to the system). */
    private val taps = java.util.concurrent.ConcurrentHashMap<Panel, PendingIntent>()

    fun tap(context: Context, panel: Panel): PendingIntent = taps.getOrPut(panel) {
        val app = context.applicationContext
        PendingIntent.getBroadcast(
            app,
            panel.ordinal,
            // (Its own action for each panel, as well as its own request code: each panel's tap
            // is a PendingIntent of its own, whatever the system makes of the codes. Delivered as
            // a foreground broadcast: straight away, not queued behind the system's background
            // broadcasts.)
            Intent(app, PageTapReceiver::class.java).setAction(PageTapReceiver.ACTION_TAP + panel.key)
                .putExtra(PageTapReceiver.EXTRA_PANEL, panel.key)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** A panel's page complication. Its text and title carry what the face can't read itself. */
abstract class PanelPageService(private val panel: Panel) : SuspendingComplicationDataSourceService() {

    /** The panel's (text, title): readings only this app has. */
    protected abstract suspend fun readings(request: ComplicationRequest?): Pair<String, String>

    /**
     * The range's minimum: 0, or below zero to pass the face a number (the date panel's sky, or
     * that a song plays; the compass panel's phone battery): the value, where the pages stand, is
     * never below zero.
     */
    protected open fun rangeMin(): Float = 0f

    /** What a tap on the panel does: turns its page (the date panel's opens Now Playing while a song plays). */
    protected open fun tapAction(): PendingIntent = Panels.tap(this, panel)

    /** A picture the panel carries as its icon (the date panel's: the song's cover, round), as a PNG. */
    protected open fun icon(): ByteArray? = null

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        Panels.setSlot(this, complicationInstanceId, showing = true)
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        Panels.setSlot(this, complicationInstanceId, showing = false)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        // (How long the ask took to get here: asks are timed by it, to reach the face just after
        // one of the seconds hand's beats.)
        FaceUpdates.answered(this, javaClass)
        // (Also noted here, for a face that was showing it before this version.)
        Panels.setSlot(this, request.complicationInstanceId, showing = true)
        if (request.complicationType != ComplicationType.RANGED_VALUE) return null
        val readings = readings(request)
        val min = rangeMin()
        val icon = icon()
        Panels.noteShown(this, panel, Panels.shownKey(readings.first, readings.second, min, icon))
        return build(Panels.value(this, panel), readings, min, icon)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.RANGED_VALUE) build(0, preview(), 0f) else null

    protected open fun preview(): Pair<String, String> = "" to ""

    private fun build(value: Int, readings: Pair<String, String>, min: Float = rangeMin(), icon: ByteArray? = null): ComplicationData {
        val tap = tapAction()
        val b = RangedValueComplicationData.Builder(
            value = value.toFloat(),
            min = min,
            max = (panel.places - 1).toFloat(),
            contentDescription = text("${panel.key} panel, page ${value % panel.pages + 1} of ${panel.pages}"),
        )
            .setText(text(readings.first))
            .setTitle(text(readings.second))
            .setTapAction(tap)
        // (The format's "monochromatic" icon, which the face draws as it comes, untinted: the only
        // picture a ranged value carries on a Watch Face Format face.)
        icon?.let { b.setMonochromaticImage(MonochromaticImage.Builder(Icon.createWithData(it, 0, it.size)).build()) }
        return b.build()
    }

    protected fun text(s: String) = PlainComplicationText.Builder(s).build()
}

/** Heart panel: heart rate (the face reads it), then sleep score (text), then Energy score (title). */
class HeartPanelService : PanelPageService(Panel.HEART) {
    override suspend fun readings(request: ComplicationRequest?): Pair<String, String> {
        val h = HealthStore.current(this)
        return HealthStore.sleepText(h) to HealthStore.energyText(h)
    }

    override fun preview() = "84" to "78"
}

/** Steps panel: steps (the face reads them), then active calories (text), then blood oxygen (title). */
class StepsPanelService : PanelPageService(Panel.STEPS) {
    override suspend fun readings(request: ComplicationRequest?): Pair<String, String> {
        val h = HealthStore.current(this)
        return HealthStore.kcalText(h) to HealthStore.oxygenText(h)
    }

    override fun preview() = "412" to "97%"
}

/**
 * Compass panel: the heading's point (text) and degrees (title), then the battery: the watch's
 * (the face reads it) and the phone's (the range's minimum, while it's known: see
 * PhoneBatteryStore.code).
 */
class CompassPanelService : PanelPageService(Panel.COMPASS) {
    override fun rangeMin(): Float = PhoneBatteryStore.code(this).also { Panels.notePhoneBattery(it) }

    override suspend fun readings(request: ComplicationRequest?): Pair<String, String> {
        // The live compass's reading while it runs (it asks for this as the heading changes), or
        // the last one. Never a fresh reading here: that would hold up a page turn for a second
        // or more (the live compass keeps the heading fresh while the screen is on).
        val live = LiveCompass.latest?.takeIf { System.currentTimeMillis() - it.atMillis < LIVE_MS }
        val heading = live ?: Compass.last(this) ?: return "--" to "--"
        return Compass.point(heading.degrees) to Compass.degreesText(heading.degrees)
    }

    private companion object {
        const val LIVE_MS = 3_000L
    }

    override fun preview() = "NE" to "42°"
}

/**
 * Date panel: the date and moon (the face reads them), then the weather: the temperature now
 * (title), India's air quality index (text) and the sky's picture (the range's minimum), as the
 * phone fetched them (see WeatherStore). Without them the face shows the watch's own weather.
 * While a song plays (see NowPlaying): its name (text) and artist (title), and its cover, round,
 * as the complication's icon (made on the phone at the size the face shows it in the pill's
 * window), with the range's minimum at [SONG_ART_CODE] ([SONG_CODE] until the cover's come: the
 * face shows a note there then); and a tap opens Cover Display's Now Playing. (The cover comes
 * with the name in the one update, so the two change together.)
 */
class DatePanelService : PanelPageService(Panel.DATE) {
    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        super.onComplicationActivated(complicationInstanceId, type)
        // (Cover Display tells this app as the music changes; asked now in case it's playing.)
        NowPlaying.ask(this)
    }

    override suspend fun readings(request: ComplicationRequest?): Pair<String, String> =
        shown(this).let { (text, title, _) -> text to title }

    override fun rangeMin(): Float = shown(this).third

    override fun icon(): ByteArray? = NowPlaying.art(this)

    override fun tapAction(): PendingIntent =
        if (NowPlaying.showing(this)) NowPlaying.openNowPlaying(this) else super.tapAction()

    companion object {
        /** The range's minimum while a song plays: far below the sky's codes (the face's PILL_SONG). */
        const val SONG_CODE = -1000f

        /** ... and while its cover's here too (the face's PILL_ART: it leaves the window open for it). */
        const val SONG_ART_CODE = -2000f

        /** What the date panel carries now: (text, title, range minimum). */
        fun shown(context: Context): Triple<String, String, Float> {
            if (NowPlaying.showing(context)) {
                val s = NowPlaying.current(context)
                return Triple(s.title, s.artist, if (NowPlaying.art(context) != null) SONG_ART_CODE else SONG_CODE)
            }
            val w = WeatherStore.current(context)
            return Triple(WeatherStore.aqiText(w), WeatherStore.titleText(w), WeatherStore.skyCode(w))
        }
    }
}

/**
 * A tap on one of the face's panels: moves it on a page (with a tick on the wrist) and has the
 * faces fetch it again. Turning to a page with readings from the phone asks the phone for fresh
 * ones if the last are old (see OnScreen).
 */
class PageTapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val panel = Panel.of(intent.getStringExtra(EXTRA_PANEL)) ?: return
        // On the charger the face holds the compass panel on the battery and the date on the
        // weather; moving them on underneath would make them jump when it lets go.
        if (panel.heldWhileCharging && charging(context)) return
        // The page first (the face slides as soon as it has it: at the next beat, whatever else
        // is waiting), then the tick; nothing else stands in the way of either.
        Panels.next(context, panel)
        Panels.refresh(context, panel, FaceUpdates.Rank.TAP)
        Haptics(context).tick()
        // (A tap is a good moment to bring the live compass back if the system stopped it; on the
        // compass dial it also starts or stops reading, as the compass page comes up or goes.)
        if (panel == Panel.COMPASS) LiveCompass.pageTurned(context) else if (!LiveCompass.running) LiveCompass.start(context)
        // The page that's come up: its readings, if they're old (only what's on screen is fetched).
        OnScreen.check(context)
        // (Held until the page's update has gone: see FaceUpdates.finishWhenIdle.)
        FaceUpdates.finishWhenIdle(goAsync())
    }

    companion object {
        const val EXTRA_PANEL = "panel"
        const val ACTION_TAP = "com.liquiwatch.wear.PAGE_TAP."

        private fun charging(context: Context): Boolean = Panels.charging(context)
    }
}
