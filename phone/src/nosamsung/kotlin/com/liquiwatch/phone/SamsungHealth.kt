package com.liquiwatch.phone

import android.app.Activity
import android.content.Context
import com.liquiwatch.shared.HealthSnapshot

/**
 * Stands in for Samsung Health while Samsung's Health Data SDK isn't in phone/libs (see the
 * README): nothing can be read, so the health pages show "--".
 */
object SamsungHealth {
    const val INCLUDED = false

    suspend fun hasAccess(context: Context): Boolean = false

    suspend fun requestAccess(activity: Activity): Boolean = false

    fun resolve(activity: Activity, problem: HealthProblem) {}

    suspend fun read(context: Context): HealthSnapshot =
        throw HealthProblem("Samsung's Health Data SDK isn't in this build.", null)
}
