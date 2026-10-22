package com.liquiwatch.phone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.liquiwatch.shared.HealthSnapshot
import com.liquiwatch.shared.WeatherSnapshot
import kotlinx.coroutines.launch

/**
 * LiquiWatch on the phone (the faces' companion): lets it read Samsung Health, shows the latest readings it
 * sent the watch, and sends them again on request. And the weather for the date panel: the temperature, the
 * sky and India's air quality index where the phone is. The watch asks for each only while a panel shows it
 * (and for the phone's battery while the battery page is up): nothing runs on a schedule.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // (An earlier version read and fetched on a schedule: stopped.)
        HealthSync.unschedule(this)
        WeatherSync.unschedule(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) { Screen(this) }
        }
    }
}

private val Accent = Color(0xFF3FA9F5)

private sealed interface Access {
    data object Checking : Access
    data object NotInBuild : Access
    data object NeedsAccess : Access
    data object Ready : Access
    data class Problem(val problem: HealthProblem) : Access
}

@Composable
private fun Screen(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf<Access>(Access.Checking) }
    var latest by remember { mutableStateOf(HealthSync.last(activity)) }
    var busy by remember { mutableStateOf(false) }

    suspend fun check() {
        access = if (!SamsungHealth.INCLUDED) {
            Access.NotInBuild
        } else {
            try {
                if (SamsungHealth.hasAccess(activity)) {
                    latest = HealthSync.run(activity)
                    Access.Ready
                } else {
                    Access.NeedsAccess
                }
            } catch (p: HealthProblem) {
                Access.Problem(p)
            } catch (e: Exception) {
                Access.Problem(HealthProblem(e.message ?: "Couldn't reach the watch.", e))
            }
        }
    }

    // Look again whenever the app comes back (e.g. from Samsung Health's settings).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { check() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("LiquiWatch", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(
            "Readings for the LiquiWatch watch faces' panels: sleep score, Energy score, blood oxygen " +
                "and active calories, from Samsung Health. Tap a panel on the watch face to flip through them.",
            color = Color.White.copy(alpha = 0.7f),
        )

        Section("Samsung Health") {
            when (val a = access) {
                Access.Checking -> Text("Checking…", color = Color.White.copy(alpha = 0.7f))
                Access.NotInBuild -> Text(
                    "This build doesn't include Samsung's Health Data SDK. Put its .aar file in the " +
                        "project's phone/libs folder and build again (see the README).",
                    color = Color.White,
                )
                Access.NeedsAccess -> {
                    Text("Allow this app to read your sleep, Energy score, blood oxygen and activity.", color = Color.White)
                    Button(onClick = {
                        scope.launch {
                            try {
                                SamsungHealth.requestAccess(activity)
                            } catch (p: HealthProblem) {
                                access = Access.Problem(p)
                            }
                            check()
                        }
                    }) { Text("Allow access") }
                }
                Access.Ready -> Text("Connected. The watch asks for readings while a panel shows them.", color = Color.White)
                is Access.Problem -> {
                    Text(a.problem.message ?: "Samsung Health can't be read.", color = Color(0xFFFFC53D))
                    Text(
                        "If Samsung Health is up to date, turn on its developer mode for data read: in Samsung " +
                            "Health, ⋮ > Settings > About Samsung Health, tap the version 10 times, open " +
                            "\"Developer mode (Samsung Health Data SDK)\" and turn on \"Developer Mode for Data Read\".",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                    )
                    OutlinedButton(onClick = { SamsungHealth.resolve(activity, a.problem) }) { Text("Fix in Samsung Health") }
                }
            }
        }

        WeatherSection(activity)

        Section("Sent to the watch") {
            Readings(latest)
            Button(
                enabled = !busy && access == Access.Ready,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            latest = HealthSync.run(activity)
                        } catch (p: HealthProblem) {
                            access = Access.Problem(p)
                        } catch (e: Exception) {
                            // (The Wearable API failing: as check() shows it, rather than crash.)
                            access = Access.Problem(HealthProblem(e.message ?: "Couldn't reach the watch.", e))
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "Sending…" else "Send now") }
        }
    }
}

/** How old (ms) the weather sent may be when the app opens before it's fetched again. */
private const val WEATHER_STALE_MS = 20 * 60_000L

@Composable
private fun WeatherSection(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    var weather by remember { mutableStateOf(WeatherSync.last(activity)) }
    var problem by remember { mutableStateOf(WeatherSync.lastError(activity)) }
    var granted by remember { mutableStateOf(PhoneLocation.granted(activity)) }
    var always by remember { mutableStateOf(PhoneLocation.alwaysGranted(activity)) }
    var busy by remember { mutableStateOf(false) }

    suspend fun fetch() {
        busy = true
        try {
            weather = WeatherSync.run(activity, foreground = true)
            problem = null
        } catch (p: WeatherProblem) {
            problem = p.message
        } catch (e: Exception) {
            problem = "Couldn't send it to the watch (${e.message})."
        }
        busy = false
    }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = PhoneLocation.granted(activity)
        always = PhoneLocation.alwaysGranted(activity)
        if (ok) scope.launch { fetch() }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = PhoneLocation.granted(activity)
        always = PhoneLocation.alwaysGranted(activity)
        weather = WeatherSync.last(activity)
        if (granted && !busy && System.currentTimeMillis() - weather.fetchedMillis > WEATHER_STALE_MS) scope.launch { fetch() }
    }

    Section("Weather") {
        Text(
            "The face's date panel turns to the weather: the temperature now and India's air quality index " +
                "(CPCB) where your phone is, from Open-Meteo, when the watch shows it (at most every half hour).",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
        )
        WeatherNow(weather)
        problem?.let { Text(it, color = Color(0xFFFFC53D)) }
        if (!granted) {
            Button(onClick = { ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text("Allow location") }
        } else {
            Button(enabled = !busy, onClick = { scope.launch { fetch() } }) { Text(if (busy) "Fetching…" else "Send now") }
            if (!always) {
                Text(
                    "It keeps to where the phone was when this app was last open. To have it follow you, set " +
                        "this app's location to \"Allow all the time\".",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
                OutlinedButton(onClick = {
                    activity.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
                    )
                }) { Text("Open settings") }
            }
        }
    }
}

@Composable
private fun WeatherNow(w: WeatherSnapshot) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Reading("Now", w.temperature?.let { "$it°" } ?: "--")
        Reading("AQI", w.aqi?.let { "$it" } ?: "--")
        Reading("Air", w.aqi?.let { CpcbAqi.band(it) } ?: "--")
    }
    Text(
        if (w.fetchedMillis == 0L) "Nothing sent yet"
        else WmoCodes.words(w.condition) + " · sent " + DateUtils.getRelativeTimeSpanString(w.fetchedMillis),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 13.sp,
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161C24)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Accent)
            content()
        }
    }
}

@Composable
private fun Readings(h: HealthSnapshot) {
    fun or(v: Int?, unit: String = "") = v?.let { "$it$unit" } ?: "--"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Reading("Sleep", or(h.sleepScore))
        Reading("Energy", or(h.energyScore))
        Reading("SpO₂", or(h.oxygen, "%"))
        Reading("Active kcal", or(h.activeKcal))
    }
    Text(
        if (h.syncedMillis == 0L) "Nothing sent yet" else "Sent " + DateUtils.getRelativeTimeSpanString(h.syncedMillis),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 13.sp,
    )
}

@Composable
private fun Reading(label: String, value: String) {
    Column {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
    }
}
