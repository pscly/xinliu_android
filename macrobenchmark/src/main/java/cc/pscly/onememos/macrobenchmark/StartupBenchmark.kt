package cc.pscly.onememos.macrobenchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 目的：仅测量 App 的冷启动耗时（不包含滚动、打开详情、返回等后续交互）。
 *
 * 说明：为了保证“启动完成”的判定稳定，这里复用 HomeScrollBenchmark 的就绪信号：等待主页上出现描述为“同步”的控件。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startup_cold() =
        rule.measureRepeated(
            packageName = "cc.pscly.onememos",
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.COLD,
            setupBlock = {
                // 保持与其他基准用例一致：每轮从桌面开始，避免残留状态影响启动链路。
                pressHome()
            },
        ) {
            startActivityAndWait()
            device.wait(Until.hasObject(By.desc("同步")), 10_000)
        }
}
