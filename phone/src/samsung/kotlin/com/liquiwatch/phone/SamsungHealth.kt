package com.liquiwatch.phone

import android.app.Activity
import android.content.Context
import com.liquiwatch.shared.HealthSnapshot
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalDateFilter
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Samsung Health, through Samsung's Health Data SDK (on the phone: Samsung Health there holds
 * what the watch measured). Reads only.
 */
object SamsungHealth {
    const val INCLUDED = true

    private val permissions: Set<Permission> = setOf(
        Permission.of(DataTypes.SLEEP, AccessType.READ),
        Permission.of(DataTypes.ENERGY_SCORE, AccessType.READ),
        Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ),
        Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ),
    )

    private fun store(context: Context): HealthDataStore = HealthDataService.getStore(context.applicationContext)

    /** Whether this app may read all four. */
    suspend fun hasAccess(context: Context): Boolean = guarded {
        store(context).getGrantedPermissions(permissions).containsAll(permissions)
    }

    /** Shows Samsung Health's own permission screen; true if everything was allowed. */
    suspend fun requestAccess(activity: Activity): Boolean = guarded {
        store(activity).requestPermissions(permissions, activity).containsAll(permissions)
    }

    /** Lets Samsung Health fix what it can (install or update itself, say). */
    fun resolve(activity: Activity, problem: HealthProblem) {
        (problem.cause as? ResolvablePlatformException)?.takeIf { it.hasResolution }?.resolve(activity)
    }

    /** The latest readings. */
    suspend fun read(context: Context): HealthSnapshot = guarded {
        val store = store(context)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        // Last night's sleep (the latest sleep with a score that ended in the past day and a half:
        // a nap since has none) and its score.
        val sleep = store.readData(
            DataTypes.SLEEP.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(now.minusHours(36), now))
                .setOrdering(Ordering.DESC)
                .setLimit(5)
                .build(),
        ).dataList.firstOrNull { it.getValue(DataType.SleepType.SLEEP_SCORE) != null }

        // Today's Energy score.
        val energy = store.readData(
            DataTypes.ENERGY_SCORE.readDataRequestBuilder
                .setLocalDateFilter(LocalDateFilter.of(today, today.plusDays(1)))
                .setOrdering(Ordering.DESC)
                .setLimit(1)
                .build(),
        ).dataList.firstOrNull()

        // The latest blood oxygen reading (the watch takes them during sleep, or when asked).
        val oxygen = store.readData(
            DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(now.minusHours(36), now))
                .setOrdering(Ordering.DESC)
                .setLimit(1)
                .build(),
        ).dataList.firstOrNull()

        // Calories burned in activity today (what Samsung Health's activity ring counts).
        val kcal = store.aggregateData(
            DataType.ActivitySummaryType.TOTAL_ACTIVE_CALORIES_BURNED.requestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(today.atStartOfDay(), now))
                .build(),
        ).dataList.firstOrNull()?.value

        HealthSnapshot(
            sleepScore = sleep?.getValue(DataType.SleepType.SLEEP_SCORE),
            sleepEndMillis = sleep?.endTime?.toEpochMilli() ?: 0L,
            energyScore = energy?.getValue(DataType.EnergyScoreType.ENERGY_SCORE)?.roundToInt(),
            energyEpochDay = energy?.startTime?.atZone(ZoneId.systemDefault())?.toLocalDate()?.toEpochDay() ?: 0L,
            oxygen = oxygen?.getValue(DataType.BloodOxygenType.OXYGEN_SATURATION)?.roundToInt(),
            oxygenMillis = oxygen?.endTime?.toEpochMilli() ?: 0L,
            activeKcal = kcal?.roundToInt(),
            kcalEpochDay = today.toEpochDay(),
            syncedMillis = System.currentTimeMillis(),
        )
    }

    /** Samsung's errors as a [HealthProblem] (with Samsung's own message). */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: HealthDataException) {
        throw HealthProblem(e.errorMessage ?: e.message ?: "Samsung Health isn't available.", e)
    }
}
