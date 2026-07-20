package cc.pscly.onememos.ui.feature.home

import cc.pscly.onememos.ui.theme.InkSpacing
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFabClearanceTest {

    @Test
    fun `显示回到顶部时 - 净空包含图标按钮触控高度加间距加创建按钮加缓冲`() {
        val expected =
            InkSpacing.TouchTargetMin + InkSpacing.X10 + InkSpacing.SealButtonSize + InkSpacing.X16
        assertEquals(expected, HomeFabClearance.fabBottomClearance(showScrollToTop = true))
    }

    @Test
    fun `不显示回到顶部时 - 净空只包含创建按钮加缓冲`() {
        val expected = InkSpacing.SealButtonSize + InkSpacing.X16
        assertEquals(expected, HomeFabClearance.fabBottomClearance(showScrollToTop = false))
    }
}
