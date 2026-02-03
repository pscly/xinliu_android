package cc.pscly.onememos.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成 Baseline Profile：让 Release 安装后能直接受益于 AOT 优化（启动/滚动更稳）。
 *
 * 说明：
 * - 该测试依赖 instrumentation，需要真机或模拟器（建议真机，结果更稳定）。
 * - 生成的 profile 会由 Gradle 插件复制到 app 中并入库。
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
        }
}

