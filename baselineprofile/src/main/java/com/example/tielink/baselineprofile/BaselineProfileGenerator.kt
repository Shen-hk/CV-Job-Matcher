package com.example.tielink.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for the app.
 *
 * Run with:  .\gradlew.bat :app:generateReleaseBaselineProfile
 * (requires a connected device / emulator on API 28+)
 *
 * The collected profile is written into app/src/release/generated/baselineProfiles/
 * and bundled into every subsequent release build, so ART can AOT-compile the
 * exercised code paths (startup, first compose, keyboard, scrolling) ahead of time.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE_NAME,
        // Include the whole startup + first-frame path
        includeInStartupProfile = true
    ) {
        // Cold start to the Agent chat entry screen
        pressHome()
        startActivityAndWait()

        // Wait for the chat UI to settle
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 5_000)
        device.waitForIdle()

        // Exercise the chat list scroll path if any content is present
        device.findObject(By.scrollable(true))?.let { scrollable ->
            scrollable.fling(Direction.DOWN)
            device.waitForIdle()
            scrollable.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    companion object {
        private const val PACKAGE_NAME = "com.example.tielink"
    }
}
