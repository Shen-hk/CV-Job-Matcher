package com.example.tielink

import com.example.tielink.domain.model.InterviewSessionTiming
import org.junit.Assert.assertEquals
import org.junit.Test

class InterviewSessionTimingTest {
    @Test
    fun elapsedSeconds_neverReturnsNegativeTime() {
        assertEquals(12, InterviewSessionTiming.elapsedSeconds(1_000L, 13_999L))
        assertEquals(0, InterviewSessionTiming.elapsedSeconds(5_000L, 4_000L))
    }
}
