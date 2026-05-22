package cc.pscly.onememos.ui.util

import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class QuickInsertTimeFormatterTest {
    private var originalTimeZone: TimeZone? = null

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        originalTimeZone?.let(TimeZone::setDefault)
    }

    @Test
    fun format_fullDateTime_usesExpectedPattern() {
        assertEquals(
            "2026-05-22 12:34:56",
            QuickInsertTimeFormatter.format(
                epochMillis = 1779424496000L,
                format = QuickInsertTimeFormat.FULL_DATETIME,
            ),
        )
    }

    @Test
    fun format_timeOnly_usesExpectedPattern() {
        assertEquals(
            "12:34:56",
            QuickInsertTimeFormatter.format(
                epochMillis = 1779424496000L,
                format = QuickInsertTimeFormat.TIME_ONLY,
            ),
        )
    }
}
