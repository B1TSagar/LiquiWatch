package com.liquiwatch.wear


/** The face companion's own knobs (its glass looks like Cover Display's, but the apps share no code). */
object FaceTuning {
    // ---- Liquid glass (the live compass) ------------------------------------------------------

    /** The domed crystal over the whole screen: the picture stretches a little toward the rim (0 = off). */
    const val GLASS_SCREEN = 1f

    /** How strongly glass bends what's behind it at its rim (1 = default, 0 = flat glass). */
    const val GLASS_BEND = 1f

    /** How close (dp) two pieces of glass must come before they start to melt into one. */
    const val GLASS_MERGE_DP = 10f

    /** Light on the glass rims: glow, hair-thin highlight, outline and volume shading (0 = off). */
    const val GLASS_LIGHT = 1f

    /** The soft shadow under each piece of glass (0 = off, 1 = strong). */
    const val GLASS_SHADOW = 0.25f

    /** The spring moving glass follows: damping (1 = no overshoot) and stiffness (higher = quicker). */
    const val GLASS_SPRING_DAMPING = 0.8f
    const val GLASS_SPRING_STIFFNESS = 260f

    // ---- Haptics ----------------------------------------------------------------------------

    /** Wrist ticks: passing N, E, S or W on the compass, and turning a face panel's page. */
    const val HAPTICS = true
    const val TICK_MS = 20L
    const val TICK_STRENGTH = 200 // 1..255, used when the motor supports amplitude control
    const val MIN_TICK_GAP_MS = 35L

    // ---- The faces' panels ------------------------------------------------------------------

    /** A Samsung Health reading older than this shows as "--" (last night's sleep, today's energy, a recent SpO2). */
    const val SLEEP_FRESH_HOURS = 30L
    const val OXYGEN_FRESH_HOURS = 30L

    /**
     * While a health page is up (as it turns up, as the screen comes on, and every minute while
     * it's on), the phone is asked for fresh readings if the last ones are older than this.
     */
    const val HEALTH_REFRESH_MINUTES = 15L
}
