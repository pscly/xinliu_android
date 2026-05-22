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

    @Volatile
    private var timeHmsFormatter: JavaDateTimeFormatter = JavaDateTimeFormatter.ofPattern("HH:mm:ss", cachedLocale)

    @Volatile
    private var dateTimeHmsFormatter: JavaDateTimeFormatter = JavaDateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", cachedLocale)

    private fun ensureLocaleUpToDate() {
        val current = Locale.getDefault()
        if (current == cachedLocale) return

        cachedLocale = current
        formatter = JavaDateTimeFormatter.ofPattern("yyyy-MM-dd/HH:mm", current)
        dateFormatter = JavaDateTimeFormatter.ofPattern("yyyy-MM-dd", current)
        timeFormatter = JavaDateTimeFormatter.ofPattern("HH:mm", current)
        timeHmsFormatter = JavaDateTimeFormatter.ofPattern("HH:mm:ss", current)
        dateTimeHmsFormatter = JavaDateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", current)
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

    fun formatHms(epochMillis: Long): String =
        run {
            ensureLocaleUpToDate()
            timeHmsFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        }

    fun formatYmdHms(epochMillis: Long): String =
        run {
            ensureLocaleUpToDate()
            dateTimeHmsFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        }
}
