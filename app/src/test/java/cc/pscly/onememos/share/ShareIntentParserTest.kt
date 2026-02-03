package cc.pscly.onememos.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ShareIntentParserTest {
    @Test
    fun parse_sendText_mergesSubjectAndText() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "主题")
                putExtra(Intent.EXTRA_TEXT, "正文")
            }

        val payload = ShareIntentParser.parse(intent)
        assertNotNull(payload)
        assertTrue(payload!!.text.contains("主题"))
        assertTrue(payload.text.contains("正文"))
    }

    @Test
    fun parse_sendSingleStream_collectsExtraStream() {
        val u1 = Uri.parse("content://example/1")
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, u1)
            }

        val payload = ShareIntentParser.parse(intent)
        assertNotNull(payload)
        assertEquals(listOf(u1), payload!!.streamUris)
    }

    @Test
    fun parse_collectsClipDataUris() {
        val u1 = Uri.parse("content://example/1")
        val u2 = Uri.parse("content://example/2")
        val clip = ClipData.newRawUri("a", u1).apply { addItem(ClipData.Item(u2)) }

        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                clipData = clip
            }

        val payload = ShareIntentParser.parse(intent)
        assertNotNull(payload)
        assertEquals(2, payload!!.streamUris.size)
        assertTrue(payload.streamUris.contains(u1))
        assertTrue(payload.streamUris.contains(u2))
    }
}
