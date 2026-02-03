package cc.pscly.onememos.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
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
 * 目的：把“冷启动 + 主页连续滑动 + 点开一条记录再返回”的真实链路做成可重复的跑分用例。
 *
 * 注意：
 * - 该用例偏“最佳努力”：如果你的主页没有任何数据，点击第一条记录可能不会进入编辑页，但不会影响滚动跑分。
 * - 真正对比时建议固定同一台设备、同一套数据集（或先手动准备一些离线记录）。
 */
@RunWith(AndroidJUnit4::class)
class HomeScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startup_scroll_open_and_back() =
        rule.measureRepeated(
            packageName = "cc.pscly.onememos",
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
            device.wait(Until.hasObject(By.desc("同步")), 5_000)

            val cx = device.displayWidth / 2
            val startY = (device.displayHeight * 0.82).toInt()
            val endY = (device.displayHeight * 0.22).toInt()
            repeat(6) {
                device.swipe(cx, startY, cx, endY, 30)
            }

            // 打开第一条记录（在数据为空时可能无效，属于 best-effort）
            device.click(cx, (device.displayHeight * 0.32).toInt())
            device.wait(Until.hasObject(By.desc("返回")), 3_000)
            device.pressBack()
        }
}
