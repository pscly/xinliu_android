package cc.pscly.onememos.ui.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter as JavaDateTimeFormatter
import java.util.Locale

/**
 * 日期分组头部：大号中文日期 + 细墨线分隔线。
 *
 * 字体取自 [MaterialTheme.typography]，由 `OneMemosTheme` 按主题档下发文楷或系统字体。
 * 描边透明度使用 [InkBorder.OutlineSoft] 产生若隐若现的纸面墨线效果。
 */
@Composable
fun DateHeader(
    dateKey: String, // yyyy-MM-dd
    epochMillis: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = InkSpacing.CardPadding),
    ) {
        Text(
            text = formatChineseDate(epochMillis),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = InkSpacing.X8),
        )

        Spacer(modifier = Modifier.height(InkSpacing.X8))

        // 细墨线分隔线（发丝线 + 低透明度 = 纸面若隐若现效果）
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(InkBorder.Hairline)
                    .alpha(InkBorder.OutlineSoft)
                    .background(MaterialTheme.colorScheme.outline),
        )
    }
}

/**
 * 将 epochMillis 转为中文日期格式：yyyy年MM月dd日。
 */
private val chineseDateFormatter: JavaDateTimeFormatter =
    JavaDateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.CHINA)

private fun formatChineseDate(epochMillis: Long): String {
    val instant = Instant.ofEpochMilli(epochMillis)
    val zoned = instant.atZone(ZoneId.systemDefault())
    return chineseDateFormatter.format(zoned)
}
