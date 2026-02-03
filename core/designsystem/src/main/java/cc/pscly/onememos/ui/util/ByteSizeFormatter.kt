package cc.pscly.onememos.ui.util

import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

object ByteSizeFormatter {
    fun format(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = floor(log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())

        // 0~9.9 显示 1 位小数；10 以上显示整数，避免界面太“花”。
        val text =
            if (digitGroups == 0) {
                bytes.toString()
            } else if (value < 10) {
                String.format(Locale.getDefault(), "%.1f", value)
            } else {
                String.format(Locale.getDefault(), "%.0f", value)
            }
        return "$text ${units[digitGroups]}"
    }
}
