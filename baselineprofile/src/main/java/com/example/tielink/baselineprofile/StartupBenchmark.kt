package com.example.tielink.baselineprofile

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 性能基线与优化对比基准（明天性能专项的核心工具）。
 *
 * 运行前提：
 * 1. 真机或模拟器已连接（API 28+），`adb devices` 可见
 * 2. 先装 release 变体（Macrobenchmark 要求不可调试包）：
 *    .\gradlew.bat :app:assembleRelease :baselineprofile:assemble
 *    adb install -r app/build/outputs/apk/release/app-release.apk
 * 3. 跑基准（结果在 baselineprofile/build/outputs/connected_android_test_additional_output/）：
 *    .\gradlew.bat :baselineprofile:connectedAndroidTest
 *    或单跑某类：-Pandroid.testInstrumentationRunnerArguments.class=com.example.tielink.baselineprofile.StartupBenchmark
 *
 * 产出解读：
 * - StartupTimingMetric: timeToInitialDisplayMs / timeToFullyDrawnMs —— 冷启动优化前后对比
 * - FrameTimingMetric: frameDurationCpuMs P50/P90/P99 —— 滚动流畅度优化前后对比
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** 冷启动 · 无编译优化（下限基线，最接近用户首次安装的体验） */
    @Test
    fun coldStartNoCompilation() = startup(CompilationMode.None())

    /** 冷启动 · Baseline Profile（验证 AOT 编译收益，与 NoCompilation 对比即 BP 收益） */
    @Test
    fun coldStartBaselineProfile() = startup(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)
    )

    /** Agent 聊天页滚动流畅度（FrameTiming P50/P90/P99，流式长列表优化前后对比） */
    @Test
    fun scrollAgentChat() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 10_000)
            device.waitForIdle()
        }
    ) {
        val scrollable = device.findObject(By.scrollable(true))
        if (scrollable != null) {
            repeat(3) {
                scrollable.fling(Direction.DOWN)
                device.waitForIdle()
            }
            repeat(3) {
                scrollable.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }

    /** Captures every frame while the app renders 480 deterministic high-frequency stream chunks. */
    @Test
    fun streamingChatFrameTiming() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        setupBlock = {
            device.executeShellCommand("am force-stop --user 0 $PACKAGE_NAME")
            pressHome()
        }
    ) {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.MainActivity")
                putExtra("com.example.tielink.BENCHMARK_STREAM", true)
            }
        )
        device.wait(Until.hasObject(By.textContains("第480段基准文本")), 10_000)
        // The newest text is at the top of the reverse-layout list. Traverse the
        // earlier card/text turns too, so card composition is included in timing.
        device.findObject(By.scrollable(true))?.let { conversation ->
            repeat(3) { conversation.fling(Direction.DOWN) }
            repeat(3) { conversation.fling(Direction.UP) }
        }
    }

    @Test
    fun streamingChatFrameTimingAt66ms() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        setupBlock = {
            device.executeShellCommand("am force-stop --user 0 $PACKAGE_NAME")
            pressHome()
        }
    ) {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.MainActivity")
                putExtra("com.example.tielink.BENCHMARK_STREAM", true)
                putExtra("com.example.tielink.BENCHMARK_STREAM_THROTTLE_MS", 66L)
            }
        )
        device.wait(Until.hasObject(By.textContains("第480段基准文本")), 10_000)
        device.findObject(By.scrollable(true))?.let { conversation ->
            repeat(3) { conversation.fling(Direction.DOWN) }
            repeat(3) { conversation.fling(Direction.UP) }
        }
    }

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 10_000)
        device.waitForIdle()
    }

    companion object {
        private const val PACKAGE_NAME = "com.example.tielink"
    }
}
