package com.liquiwatch.wear.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.liquiwatch.wear.FaceTuning

/** A message on a card of glass (e.g. a watch without a compass). */
@Composable
fun InfoCard(title: String, body: String) {
    val scene = rememberGlassScene()
    Box(Modifier.fillMaxSize().glassBackdrop(scene).background(Color.Black))
    GlassSurface(scene)
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .glass(scene, GlassLooks.Card)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (body.isNotBlank()) {
                Text(
                    body,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Short ticks on the wrist. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = run {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        v?.takeIf { it.hasVibrator() }
    }
    private val amplitude = vibrator?.hasAmplitudeControl() == true
    private val tickEffect = VibrationEffect.createOneShot(
        FaceTuning.TICK_MS,
        if (amplitude) FaceTuning.TICK_STRENGTH.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE,
    )
    private var lastTick = 0L

    /** One tick (ticks closer together than the motor can render are merged). */
    fun tick() {
        if (!FaceTuning.HAPTICS) return
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < FaceTuning.MIN_TICK_GAP_MS) return
        lastTick = now
        try {
            vibrator?.vibrate(tickEffect)
        } catch (e: Exception) {
            // Vibration is best-effort.
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context) }
}
