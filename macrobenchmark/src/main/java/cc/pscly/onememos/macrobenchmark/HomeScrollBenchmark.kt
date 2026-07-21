package cc.pscly.onememos.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * 冷启动 + 主页连续滑动 + 点开一条真实 memo 再返回。
 *
 * fail-fast：无 memo fixture 时明确失败，不接受坐标点击或空数据 best-effort。
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
            waitForObject(device, By.desc("同步"), 10_000, "首页同步按钮未出现")

            val cx = device.displayWidth / 2
            val startY = (device.displayHeight * 0.82).toInt()
            val endY = (device.displayHeight * 0.22).toInt()
            repeat(6) {
                device.swipe(cx, startY, cx, endY, 30)
            }

            val memo =
                waitForObject(
                    device,
                    By.res(Pattern.compile("home_memo_item_.*")),
                    10_000,
                    "无 memo fixture：未找到 home_memo_item_* 资源 id，请先在设备准备至少一条随笔",
                )
            memo.click()
            assertTrue(
                "点击 memo 后未进入编辑器（缺少返回）",
                device.wait(Until.hasObject(By.desc("返回")), 5_000),
            )
            device.pressBack()
            assertTrue(
                "返回后未回到首页（缺少同步）",
                device.wait(Until.hasObject(By.desc("同步")), 5_000),
            )
        }

    private fun waitForObject(
        device: UiDevice,
        selector: androidx.test.uiautomator.BySelector,
        timeoutMs: Long,
        errorMessage: String,
    ): UiObject2 {
        val found = device.wait(Until.findObject(selector), timeoutMs)
        assertNotNull(errorMessage, found)
        return found!!
    }
}
