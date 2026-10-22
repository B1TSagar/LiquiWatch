package com.liquiwatch.wear.panels

import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.liquiwatch.wear.FaceUpdates

/**
 * What's playing, as Cover Display (the music app, on the same watch) tells this app: the song's
 * name and artist, whether it plays, and its cover, round (made on the phone at the very size the
 * face shows it). While a song plays the Expedition face's date pill shows them in place of the
 * date and the weather, the cover in the pill's window, and a tap on it opens Cover Display's Now
 * Playing (see DatePanelService, whose complication carries them). Cover Display tells it as a song
 * starts, as the music skips, once a pause has lasted a moment, and when a song's cover comes
 * after the song; this app asks it again when it starts afresh.
 *
 * On Wear OS 5 and later each app tells the other who it is with the broadcast (shared identity),
 * and only takes the other's.
 */
object NowPlaying {
    private const val TAG = "NowPlaying"
    private const val PREFS = "now_playing"

    const val COVER_DISPLAY = "com.coverflow.app"
    const val ACTION_NOW_PLAYING = "com.liquiwatch.action.NOW_PLAYING"
    private const val ACTION_REQUEST = "com.coverflow.action.NOW_PLAYING_REQUEST"
    private const val NOW_PLAYING_ACTIVITY = "com.coverflow.wear.NowPlayingLauncher"

    /**
     * A song this long (ms) past its end with no word from Cover Display (the phone went out of
     * reach) no longer shows: the pill goes back to the date the next time it's drawn.
     */
    private const val STALE_MS = 30_000L

    data class Song(val playing: Boolean, val title: String, val artist: String, val endsAt: Long, val artKey: String = "")

    fun current(context: Context): Song {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Song(
            playing = p.getBoolean("playing", false),
            title = p.getString("title", null) ?: "",
            artist = p.getString("artist", null) ?: "",
            endsAt = p.getLong("ends_at", 0L),
            artKey = p.getString("art_key", null) ?: "",
        )
    }

    fun save(context: Context, s: Song) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("playing", s.playing)
            .putString("title", s.title)
            .putString("artist", s.artist)
            .putLong("ends_at", s.endsAt)
            .putString("art_key", s.artKey)
            .apply()
    }

    // ---- The song's cover, round -----------------------------------------------------------------

    /** The round cover last sent (a PNG, a few KB), and whose (its art key). */
    private const val ART_FILE = "song_art.png"
    private const val KEY_ART_OF = "art_of"

    /** The round cover in memory, as last kept or read (art key and PNG). */
    private class Art(val key: String, val png: ByteArray)

    @Volatile
    private var art: Art? = null

    /** Keeps [png] as [key]'s round cover (unless it's the very one kept already). */
    fun keepArt(context: Context, key: String, png: ByteArray) {
        if (key.isEmpty() || png.isEmpty()) return
        val had = art
        if (had != null && had.key == key && had.png.contentEquals(png)) return
        art = Art(key, png)
        try {
            val file = java.io.File(context.filesDir, ART_FILE)
            val tmp = java.io.File(context.filesDir, "$ART_FILE.new")
            tmp.writeBytes(png)
            if (!tmp.renameTo(file)) {
                file.writeBytes(png)
                tmp.delete()
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ART_OF, key).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't keep the song's cover", e)
        }
    }

    /** The playing song's round cover (PNG), while the pill shows the song and it's here; else null. */
    fun art(context: Context): ByteArray? {
        if (!showing(context)) return null
        val key = current(context).artKey
        if (key.isEmpty()) return null
        art?.let { return if (it.key == key) it.png else null }
        // (After a restart: read once.)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString(KEY_ART_OF, null) != key) return null
        val png = try {
            java.io.File(context.filesDir, ART_FILE).takeIf { it.exists() }?.readBytes()
        } catch (e: Exception) {
            null
        } ?: return null
        art = Art(key, png)
        return png
    }

    /** Whether the date pill shows the song now. */
    fun showing(context: Context): Boolean {
        val s = current(context)
        if (!s.playing || s.title.isBlank()) return false
        return s.endsAt <= 0L || System.currentTimeMillis() < s.endsAt + STALE_MS
    }

    /** Asks Cover Display to say what's playing (this app has started afresh). */
    fun ask(context: Context) {
        try {
            val intent = Intent(ACTION_REQUEST).setPackage(COVER_DISPLAY).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            if (Build.VERSION.SDK_INT >= 34) {
                val options = BroadcastOptions.makeBasic().apply { setShareIdentityEnabled(true) }
                context.sendBroadcast(intent, null, options.toBundle())
            } else {
                context.sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't ask Cover Display what's playing", e)
        }
    }

    /** Opens Cover Display's Now Playing: the date pill's tap while a song plays. Made once. */
    @Volatile
    private var open: PendingIntent? = null

    fun openNowPlaying(context: Context): PendingIntent = open ?: PendingIntent.getActivity(
        context.applicationContext,
        REQUEST_OPEN,
        Intent().setComponent(ComponentName(COVER_DISPLAY, NOW_PLAYING_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    ).also { open = it }

    private const val REQUEST_OPEN = 77
}

/**
 * Cover Display says what's playing: kept, and the faces' date pill fetched again if that changes
 * what it shows. When the music stops, the pill's pages are back: if the weather's up, its
 * reading is fetched if it's old (see OnScreen).
 */
class NowPlayingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NowPlaying.ACTION_NOW_PLAYING) return
        // (Only Cover Display's: before Android 14 the watch can't tell who sent it, and any app
        // could set the pill's song; the face that shows it needs Wear OS 6 anyway.)
        if (Build.VERSION.SDK_INT < 34 || sentFromPackage != NowPlaying.COVER_DISPLAY) return
        val was = NowPlaying.showing(context)
        val artKey = intent.getStringExtra("art_key") ?: ""
        NowPlaying.save(
            context,
            NowPlaying.Song(
                playing = intent.getBooleanExtra("playing", false),
                title = intent.getStringExtra("title") ?: "",
                artist = intent.getStringExtra("artist") ?: "",
                endsAt = intent.getLongExtra("ends_at", 0L),
                artKey = artKey,
            ),
        )
        intent.getByteArrayExtra("art")?.let { NowPlaying.keepArt(context, artKey, it) }
        // As the music starts, the watch is busiest (its Now bar slides in, and Cover Display puts
        // the song on the face): readings wait until it's settled.
        FaceUpdates.quiet(intent.getLongExtra("quiet_until", 0L))
        // The song's name and its cover (one update: the cover is the date complication's icon)
        // go to the face after Cover Display's own work for the face with a new song (its
        // background and colour), which it says how long it plans (see FaceUpdates). It says
        // again once the watch has settled after the music started, and the colour is on its way.
        FaceUpdates.holdSong(intent.getLongExtra("busy_until", 0L))
        Panels.refreshIfChanged(context, Panel.DATE, FaceUpdates.Rank.SONG)
        Log.i(TAG, "Cover Display says: ${if (intent.getBooleanExtra("playing", false)) "a song plays" else "nothing plays"}")
        if (was && !NowPlaying.showing(context)) OnScreen.check(context)
        // (Held until the date pill's update has gone: see FaceUpdates.finishWhenIdle.)
        FaceUpdates.finishWhenIdle(goAsync())
    }

    private companion object {
        const val TAG = "NowPlaying"
    }
}
