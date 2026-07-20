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

    @Test
    fun m6NewScales_areExact() {
        assertEquals(18.dp, InkSpacing.X18)
        assertEquals(22.dp, InkSpacing.X22)
        assertEquals(26.dp, InkSpacing.X26)
        assertEquals(28.dp, InkSpacing.X28)
        assertEquals(30.dp, InkSpacing.X30)
        assertEquals(54.dp, InkSpacing.X54)
        assertEquals(64.dp, InkSpacing.X64)
        assertEquals(68.dp, InkSpacing.X68)
        assertEquals(76.dp, InkSpacing.X76)
        assertEquals(80.dp, InkSpacing.X80)
        assertEquals(84.dp, InkSpacing.X84)
        assertEquals(92.dp, InkSpacing.X92)
        assertEquals(108.dp, InkSpacing.X108)
        assertEquals(120.dp, InkSpacing.X120)
        assertEquals(320.dp, InkSpacing.X320)
        assertEquals(324.dp, InkSpacing.X324)
        assertEquals(360.dp, InkSpacing.X360)
        assertEquals(380.dp, InkSpacing.X380)
        assertEquals(420.dp, InkSpacing.X420)
        assertEquals(520.dp, InkSpacing.X520)
        assertEquals(600.dp, InkSpacing.X600)
    }

    @Test
    fun m6Aliases_referenceScales() {
        assertEquals(InkSpacing.X18, InkSpacing.SheetGapL)
        assertEquals(InkSpacing.X84, InkSpacing.OverlayThumbSize)
        assertEquals(InkSpacing.X28, InkSpacing.OverlayThumbBadgeSize)
        assertEquals(0.5f, InkSpacing.OverlayCardMaxHeightFraction, 0.0001f)
        assertEquals(InkSpacing.X16, InkSpacing.OverlayCardMarginH)
        assertEquals(InkSpacing.X16, InkSpacing.OverlayCardMarginV)
        assertEquals(InkSpacing.X120, InkSpacing.OverlayInputMinHeight)
        assertEquals(InkSpacing.X324, InkSpacing.OverlayInputMaxHeight)
        assertEquals(InkSpacing.X22, InkSpacing.ShareCardMarginX)
        assertEquals(InkSpacing.X26, InkSpacing.ShareCardLineGap)
        assertEquals(InkSpacing.X54, InkSpacing.ShareCardPaddingH)
        assertEquals(InkSpacing.X56, InkSpacing.ShareCardPaddingV)
        assertEquals(InkSpacing.X92, InkSpacing.ShareCardSealSize)
        assertEquals(InkSpacing.X108, InkSpacing.ShareCardImageSize)
        assertEquals(InkSpacing.X520, InkSpacing.ShareCardQuoteBarHeight)
        assertEquals(InkSpacing.X360, InkSpacing.ShareCardPreviewHeight)
        assertEquals(InkSpacing.X80, InkSpacing.ShareCardThemesTopPadding)
        assertEquals(InkSpacing.X4, InkSpacing.ShareCardElevation)
        assertEquals(InkSpacing.X84, InkSpacing.AttachmentThumbSize)
        assertEquals(InkSpacing.X76, InkSpacing.SingleImageThumbSize)
        assertEquals(InkSpacing.X88, InkSpacing.GridImageThumbSize)
        assertEquals(InkSpacing.X320, InkSpacing.DialogListMaxHeight)
        assertEquals(InkSpacing.X520, InkSpacing.SheetListMaxHeight)
        assertEquals(InkSpacing.X420, InkSpacing.UpdateDialogNotesMaxHeight)
        assertEquals(InkSpacing.X360, InkSpacing.CollectionsDialogMaxHeight)
        assertEquals(InkSpacing.X18, InkSpacing.SkeletonTextLineHeight)
        assertEquals(InkSpacing.X600, InkSpacing.TwoColumnMinWidth)
        assertEquals(InkSpacing.X380, InkSpacing.ProfileCalendarHeight)
        assertEquals(InkSpacing.X68, InkSpacing.EditorRowEndPadding)
        assertEquals(InkSpacing.X30, InkSpacing.TodoStatusIconSize)
        assertEquals(InkSpacing.X44, InkSpacing.CalendarCellMin)
        assertEquals(InkSpacing.X56, InkSpacing.CalendarCellMax)
        assertEquals(InkSpacing.X34, InkSpacing.CalendarDaySize)
    }
}
