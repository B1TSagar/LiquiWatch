package com.liquiwatch.wear.panels

import android.content.Context
import com.liquiwatch.wear.health.HealthStore
import com.liquiwatch.wear.health.PhoneBatteryStore
import com.liquiwatch.wear.health.WeatherStore

/**
 * Only what's on screen is fetched. Each panel's readings from the phone (Samsung Health's, the
 * weather, the phone's battery) are asked for only while a page showing them is up, and only if
 * the last ones are old: when the panel turns to it (PageTapReceiver), when the screen comes on
 * with it up and every minute while the screen stays on (LiveCompassService, which runs while
 * the screen is on), when the watch goes on or comes off the charger (the face turns two panels
 * by itself then), and when the music stops (the date pill's pages come back). Readings that
 * arrive for a page that isn't up are kept, not handed to the face: it gets them with the page.
 * The phone fetches nothing by itself.
 */
object OnScreen {
    /** After the screen comes on, the face is busiest: what it needs is asked for this much later (ms). */
    const val WAKE_DELAY_MS = 1_500L

    /** While the screen stays on, how often the pages up are looked at again (ms). */
    const val EVERY_MS = 60_000L

    /**
     * The readings the pages up show, asked of the phone if they're old; and the pages up handed
     * to the face again if what they show has gone out of date meanwhile (a reading too old to
     * show, a day's total at midnight, a song the phone went out of reach in the middle of).
     */
    fun check(context: Context) {
        if (!Panels.anyShown(context)) return
        val heart = Panels.visible(context, Panel.HEART)
        val steps = Panels.visible(context, Panel.STEPS)
        val heartHealth = heart == Page.SLEEP || heart == Page.ENERGY
        val stepsHealth = steps == Page.KCAL || steps == Page.OXYGEN
        if (heartHealth || stepsHealth) HealthStore.askPhoneIfStale(context)
        if (heartHealth) Panels.refreshIfChanged(context, Panel.HEART)
        if (stepsHealth) Panels.refreshIfChanged(context, Panel.STEPS)
        val date = Panels.visible(context, Panel.DATE)
        if (date == Page.WEATHER) WeatherStore.askPhoneIfStale(context)
        if (date == Page.WEATHER || date == Page.SONG || Panels.showsSong(context)) Panels.refreshIfChanged(context, Panel.DATE)
        if (Panels.visible(context, Panel.COMPASS) == Page.BATTERY) {
            PhoneBatteryStore.askPhoneIfStale(context)
            Panels.refreshPhoneBattery(context)
        }
    }
}
