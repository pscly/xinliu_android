package cc.pscly.onememos.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter as JavaDateTimeFormatter
import java.util.Locale

object DateTimeFormatter {
    @Volatile
    private var cachedLocale: Locale = Locale.getDefault()

    @Volatile
    private var formatter: JavaDateTimeFormatter = JavaDateTimeFormatter.ofPattern("yyyy-MM-dd/HH:mm", cachedLocale)

    @Volatile
    private var dateFormatter: JavaDateTimeFormatter = JavaDateTimeFormatter.ofPattern("yyyy-MM-dd", cachedLocale)

    @Volatile
    private var timeFormatter: JavaDateTimeFormatter = JavaDateTimeFormatter.ofPattern("HH:mm", cachedLocale)

    private fun ensureLocaleUpToDate() {
        val current = Locale.getDefault()
        if (current == cachedLocale) return

        cachedLocale = current
        formatter = JavaDateTimeFormatter.ofPattern("yyyy-MM-dd/HH:mm", current)
        dateFormatter = JavaDateTimeFormatter.ofPattern("yyyy-MM-dd", current)
        timeFormatter = JavaDateTimeFormatter.ofPattern("HH:mm", current)
    }

    fun formatYmdHm(epochMillis: Long): String =
        run {
            ensureLocaleUpToDate()
            formatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        }

    fun formatYmd(epochMillis: Long): String =
        run {
            ensureLocaleUpToDate()
            dateFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        }

    fun formatHm(epochMillis: Long): String =
        run {
            ensureLocaleUpToDate()
            timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        }
}
