package cc.pscly.onememos.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings 导航往返帧耗时：冷启动后打开抽屉 → 设置 → 账号与同步 → 返回设置 → 返回主页。
 * 选择器使用稳定中文文案，不依赖旧 Navigation route。
 */
@RunWith(AndroidJUnit4::class)
class SettingsNavigationBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun settings_round_trip() =
        rule.measureRepeated(
            packageName = "cc.pscly.onememos",
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
            device.wait(Until.hasObject(By.desc("同步")), 10_000)

            val menu = device.wait(Until.findObject(By.desc("菜单")), 3_000)
            if (menu != null) {
                menu.click()
            } else {
                device.click((device.displayWidth * 0.08).toInt(), (device.displayHeight * 0.08).toInt())
            }
            device.wait(Until.hasObject(By.text("设置")), 3_000)
            device.findObject(By.text("设置"))?.click()
            device.wait(Until.hasObject(By.text("账号与同步")), 5_000)
            device.findObject(By.text("账号与同步"))?.click()
            device.wait(Until.hasObject(By.text("账号与同步")), 3_000)
            device.pressBack()
            device.wait(Until.hasObject(By.text("设置")), 3_000)
            device.pressBack()
            device.wait(Until.hasObject(By.desc("同步")), 5_000)
        }
}
