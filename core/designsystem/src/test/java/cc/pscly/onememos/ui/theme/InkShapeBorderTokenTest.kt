package cc.pscly.onememos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [InkShape] / [InkBorder] M6 新增令牌的精确值回归。
 */
class InkShapeBorderTokenTest {
    @Test
    fun newShapeRadii_areExact() {
        assertEquals(8.dp, InkShape.RadiusXss)
        assertEquals(18.dp, InkShape.RadiusXl)
        assertEquals(3.dp, InkShape.RadiusMicro)
    }

    @Test
    fun semanticShapes_areExact() {
        assertEquals(RoundedCornerShape(8.dp), InkShape.Skeleton)
        assertEquals(RoundedCornerShape(8.dp), InkShape.CanvasSub)
        assertEquals(RoundedCornerShape(18.dp), InkShape.SkeletonCard)
        assertEquals(RoundedCornerShape(3.dp), InkShape.Legend)
        assertEquals(RoundedCornerShape(percent = 50), InkShape.Pill)
        assertEquals(RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50), InkShape.PillStart)
        assertEquals(RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50), InkShape.PillEnd)
    }

    @Test
    fun newBorderStrokes_areExact() {
        assertEquals(3.dp, InkBorder.CanvasStroke)
        assertEquals(1.6.dp, InkBorder.CalendarRing)
        assertEquals(2.dp, InkBorder.SpinnerStroke)
    }

    @Test
    fun aliases_referenceTokens() {
        assertEquals(InkBorder.Stamp, InkBorder.SpinnerStroke)
        assertEquals(RoundedCornerShape(InkShape.RadiusXss), InkShape.Skeleton)
        assertEquals(RoundedCornerShape(InkShape.RadiusXss), InkShape.CanvasSub)
        assertEquals(RoundedCornerShape(InkShape.RadiusXl), InkShape.SkeletonCard)
        assertEquals(RoundedCornerShape(InkShape.RadiusMicro), InkShape.Legend)
    }
}
