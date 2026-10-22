package com.liquiwatch.wear.compass

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.liquiwatch.wear.panels.Panel
import com.liquiwatch.wear.panels.Panels
import com.liquiwatch.wear.ui.GlassArc
import com.liquiwatch.wear.ui.GlassLook
import com.liquiwatch.wear.ui.GlassShape
import com.liquiwatch.wear.ui.GlassSurface
import com.liquiwatch.wear.ui.InfoCard
import com.liquiwatch.wear.ui.glass
import com.liquiwatch.wear.ui.glassBackdrop
import com.liquiwatch.wear.ui.rememberGlassScene
import com.liquiwatch.wear.ui.rememberHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull

/**
 * The live compass, in the app's liquid glass: a compass card that turns under a ring of glass
 * so north stays north, a glass lens at 12 o'clock magnifying the heading's tick, and the
 * heading in the middle. A tick on the wrist each time you pass N, E, S or W.
 *
 * Opens from the compass on the LiquiWatch watch faces, from any face's compass complication,
 * and from its own "Compass" entry in the app list. Swipe right or press Back to leave; the
 * watch faces then pick up the last heading.
 */
class CompassActivity : ComponentActivity() {
    private lateinit var sensor: HeadingSensor
    private val handler = Handler(Looper.getMainLooper())

    /** The screen stays on while the compass is in use, then times out as usual. */
    private val releaseScreen = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensor = HeadingSensor(this)
        val available = Compass.available(this)
        val saved = Compass.last(this)
        setContent {
            val reading = sensor.reading.collectAsState()
            if (available) {
                CompassScreen(reading, saved)
            } else {
                InfoCard("No compass", "This watch has no magnetic sensor.")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        sensor.start()
        keepScreenOn()
        // The live compass for the watch faces (it can only be started from the foreground): it
        // stops reading while this is open, and this hands the faces the heading as it closes.
        LiveCompass.start(this, appOpen = true)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        keepScreenOn()
    }

    override fun onStop() {
        sensor.stop()
        handler.removeCallbacks(releaseScreen)
        // Hand the watch faces the latest heading.
        sensor.reading.value?.let { Compass.save(this, it) }
        CompassComplicationService.refresh(this)
        Panels.refresh(this, Panel.COMPASS)
        LiveCompass.start(this, appOpen = false)
        super.onStop()
    }

    private fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.removeCallbacks(releaseScreen)
        handler.postDelayed(releaseScreen, KEEP_ON_MS)
    }

    private companion object {
        const val KEEP_ON_MS = 60_000L
    }
}

private val Accent = Color(0xFF3FA9F5)
private val AccentLight = Color(0xFFA8DBFF)
private val North = Color(0xFFFF453A)

// Sizes (dp), measured in from the screen's edge.
private const val TICKS_FROM = 5f
private const val RING_AT = 16f // the middle of the glass ring
private const val RING_HALF = 11f
private const val LABELS_AT = 40f
private const val CENTER_SIZE = 86f

/** The glass ring: barely frosted, so the ticks under it stay crisp as they bend. */
private val RingLook = GlassLook(tintAmount = 0.10f, frost = 0.6.dp, rim = 5.dp, refraction = 7.dp, fringe = 1.5.dp, merge = 0.dp)

/** The lens at 12 o'clock: blue glass that magnifies the tick of the heading. */
private val LubberLook = GlassLook(
    tint = AccentLight,
    tintEnd = Accent,
    tintAmount = 0.22f,
    zoom = 1.35f,
    rim = 5.dp,
    refraction = 8.dp,
    fringe = 2.dp,
    lens = true,
)

/** The middle: a round of frosted glass holding the heading. */
private val CenterLook = GlassLook(tintAmount = 0.12f, frost = 5.dp, rim = 9.dp, refraction = 12.dp, fringe = 2.dp, merge = 0.dp)

/** Follows a heading round and round without jumping from 359° back to 0° (so the card never spins the long way). */
private class Unwrap {
    private var total = Float.NaN

    fun feed(degrees: Float): Float {
        if (total.isNaN()) {
            total = degrees
        } else {
            val was = (total % 360f + 360f) % 360f
            total += ((degrees - was + 540f) % 360f) - 180f
        }
        return total
    }
}

@Composable
private fun CompassScreen(reading: State<Heading?>, saved: Heading?) {
    val haptics = rememberHaptics()
    val unwrap = remember { Unwrap() }
    // Read where it's drawn, so the card turns without recomposing the screen.
    val dial = remember { derivedStateOf { reading.value?.degrees?.let(unwrap::feed) ?: saved?.degrees ?: 0f } }
    val whole by remember { derivedStateOf { reading.value?.degrees?.let { (it.roundToInt() % 360 + 360) % 360 } } }
    val unreliable by remember { derivedStateOf { reading.value?.unreliable == true } }
    val live by remember { derivedStateOf { reading.value != null } }

    // A tick on the wrist when passing a cardinal point (re-armed once well clear of it).
    LaunchedEffect(Unit) {
        var armed = true
        snapshotFlow { reading.value?.degrees }.filterNotNull().collect { d ->
            val off = abs(((d + 45f) % 90f + 90f) % 90f - 45f)
            if (armed && off < 2f) {
                haptics.tick()
                armed = false
            } else if (off > 8f) {
                armed = true
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val scene = rememberGlassScene()
        val measurer = rememberTextMeasurer()
        val labels = remember(measurer) { DialLabels(measurer) }

        // Behind the glass: the background and the turning compass card.
        Box(Modifier.fillMaxSize().glassBackdrop(scene)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(Color(0xFF18324A), Color(0xFF0A1420), Color(0xFF04070B)))),
            )
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = -dial.value },
            ) { drawDial(labels) }
        }

        GlassSurface(scene) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = min(size.width, size.height) / 2f
            listOf(GlassShape.arc(GlassArc(c, r - RING_AT.dp.toPx(), RING_HALF.dp.toPx(), 0f, PI.toFloat()), RingLook))
        }

        // The lubber lens at 12 o'clock, on the ring.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (RING_AT - 14f).dp)
                .size(15.dp, 28.dp)
                .glass(scene, LubberLook),
        )

        Column(
            Modifier
                .align(Alignment.Center)
                .size(CENTER_SIZE.dp)
                .glass(scene, CenterLook),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = whole?.let { "$it°" } ?: "--",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = whole?.let { Compass.point(it.toFloat()) } ?: "",
                color = AccentLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = when {
                !live -> "Starting…"
                unreliable -> "Calibrate: draw a figure 8 with your wrist"
                else -> "Magnetic north"
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .fillMaxWidth(0.56f),
            color = if (unreliable) Color(0xFFFFC53D) else Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** The compass card's letters and numbers, measured once. */
private class DialLabels(private val measurer: TextMeasurer) {
    private fun m(text: String, size: Int, weight: FontWeight, color: Color) =
        measurer.measure(text, TextStyle(color = color, fontSize = size.sp, fontWeight = weight))

    val cardinals: List<Pair<Float, TextLayoutResult>> = listOf(
        0f to m("N", 18, FontWeight.Bold, North),
        90f to m("E", 16, FontWeight.SemiBold, Color.White),
        180f to m("S", 16, FontWeight.SemiBold, Color.White),
        270f to m("W", 16, FontWeight.SemiBold, Color.White),
    )
    val numbers: List<Pair<Float, TextLayoutResult>> = listOf(30, 60, 120, 150, 210, 240, 300, 330).map {
        it.toFloat() to m(it.toString(), 10, FontWeight.Medium, Color.White.copy(alpha = 0.55f))
    }
}

/** The compass card, with north up (the screen turns it by the heading). */
private fun DrawScope.drawDial(labels: DialLabels) {
    val c = center
    val r = size.minDimension / 2f
    val outer = r - TICKS_FROM.dp.toPx()

    // Ticks every 5°, longer every 10° and 30°.
    for (deg in 0 until 360 step 5) {
        if (deg == 0) continue // north gets its arrowhead
        val (len, width, alpha) = when {
            deg % 30 == 0 -> Triple(13f, 2.2f, 0.95f)
            deg % 10 == 0 -> Triple(9f, 1.5f, 0.6f)
            else -> Triple(6f, 1.2f, 0.32f)
        }
        rotate(deg.toFloat(), c) {
            drawLine(
                Color.White.copy(alpha = alpha),
                Offset(c.x, c.y - outer),
                Offset(c.x, c.y - outer + len.dp.toPx()),
                strokeWidth = width.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }

    // North's arrowhead, pointing out.
    val tip = c.y - outer
    val base = tip + 15.dp.toPx()
    val half = 6.dp.toPx()
    val arrow = Path().apply {
        moveTo(c.x, tip)
        lineTo(c.x + half, base)
        lineTo(c.x - half, base)
        close()
    }
    drawPath(arrow, North)

    // A faint circle inside the ring.
    drawCircle(Color.White.copy(alpha = 0.07f), radius = r - (RING_AT + RING_HALF + 3f).dp.toPx(), center = c, style = Stroke(1.dp.toPx()))

    // N, E, S, W and the degrees, upright to the card's edge; dots between at NE, SE, SW, NW.
    val at = r - LABELS_AT.dp.toPx()
    for ((deg, layout) in labels.cardinals + labels.numbers) {
        rotate(deg, c) {
            drawText(
                layout,
                topLeft = Offset(c.x - layout.size.width / 2f, c.y - at - layout.size.height / 2f),
            )
        }
    }
    for (deg in listOf(45f, 135f, 225f, 315f)) {
        rotate(deg, c) {
            drawCircle(AccentLight.copy(alpha = 0.7f), radius = 2.dp.toPx(), center = Offset(c.x, c.y - at))
        }
    }
}
