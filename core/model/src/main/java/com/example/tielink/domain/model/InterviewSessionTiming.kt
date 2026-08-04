package com.example.tielink.domain.model

object InterviewSessionTiming {
    fun elapsedSeconds(startedAtMs: Long, nowMs: Long): Int =
        ((nowMs - startedAtMs) / 1_000L).toInt().coerceAtLeast(0)
}
