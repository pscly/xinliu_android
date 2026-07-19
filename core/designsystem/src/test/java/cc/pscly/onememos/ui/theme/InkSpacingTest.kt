package cc.pscly.onememos.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [InkSpacing] 数值尺度与布局令牌的精确值回归（M4 Pre-B）。
 *
 * 锁定后续等值迁移依赖的字面量，防止令牌漂移。
 */
class InkSpacingTest {
    @Test
    fun x4_isExactly4dp() {
        assertEquals(4.dp, InkSpacing.X4)
    }

    @Test
    fun contentMaxWidth_isExactly720dp() {
        assertEquals(720.dp, InkSpacing.ContentMaxWidth)
    }
}
