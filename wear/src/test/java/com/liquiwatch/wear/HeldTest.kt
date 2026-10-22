package com.liquiwatch.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A receiver's result held by FaceUpdates.finishWhenIdle: finished once, whichever comes first. */
class HeldTest {
    private class Result

    @Test
    fun theTimeRunningOutAfterTheLastAskFindsNothingToFinish() {
        val held = FaceUpdates.Held<Result>()
        val a = Result()
        held.add(a)
        assertEquals(listOf(a), held.takeAll())
        assertFalse(held.take(a))
    }

    @Test
    fun theLastAskAfterTheTimeRanOutFindsNothingToFinish() {
        val held = FaceUpdates.Held<Result>()
        val a = Result()
        val b = Result()
        held.add(a)
        held.add(b)
        assertTrue(held.take(a))
        assertFalse(held.take(a))
        assertEquals(listOf(b), held.takeAll())
        assertEquals(emptyList<Result>(), held.takeAll())
    }
}
