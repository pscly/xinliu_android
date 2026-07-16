package cc.pscly.onememos.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成 Baseline Profile：启动、主页滚动、编辑返回，以及设置往返路径。
 *
 * 依赖 instrumentation（真机或模拟器）。选择器使用稳定中文文案/contentDescription，
 * 不依赖旧 Navigation route。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = "cc.pscly.onememos") {
            startActivityAndWait()
            device.wait(Until.hasObject(By.desc("同步")), 5_000)

            // 主页滚动（覆盖列表与图片缩略图等常见路径）
            val cx = device.displayWidth / 2
            val startY = (device.displayHeight * 0.82).toInt()
            val endY = (device.displayHeight * 0.22).toInt()
            repeat(10) {
                device.swipe(cx, startY, cx, endY, 30)
            }

            // best-effort：尝试点开一条记录（进入编辑页）后返回
            device.click(cx, (device.displayHeight * 0.32).toInt())
            device.wait(Until.hasObject(By.desc("返回")), 1_500)
            device.pressBack()

            // Settings 往返：打开抽屉 → 设置 → 账号与同步 → 返回设置 → 返回主页
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
