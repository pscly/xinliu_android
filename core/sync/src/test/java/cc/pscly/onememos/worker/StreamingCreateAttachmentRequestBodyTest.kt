package cc.pscly.onememos.worker

import android.app.Application
import android.util.Base64
import java.io.ByteArrayInputStream
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StreamingCreateAttachmentRequestBodyTest {

    @Test
    fun writeTo_outputsValidJson_andBase64IsNoWrap() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val expectedBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val body =
            StreamingCreateAttachmentRequestBody(
                filename = "a-\"b.txt",
                type = "text/plain",
                memo = "memo-1",
                externalLink = null,
                knownContentSizeBytes = bytes.size.toLong(),
                openInputStream = { ByteArrayInputStream(bytes) },
            )

        val buffer = Buffer()
        body.writeTo(buffer)
        val json = buffer.readUtf8()

        assertFalse(json.contains('\n'))
        assertFalse(json.contains('\r'))

        val obj = JSONObject(json)
        assertEquals("a-\"b.txt", obj.getString("filename"))
        assertEquals("text/plain", obj.getString("type"))
        assertEquals("memo-1", obj.getString("memo"))
        assertEquals(expectedBase64, obj.getString("content"))
    }

    @Test
    fun contentLength_knownSize_matchesActualBytesWritten() {
        val bytes = "hello-world".toByteArray(Charsets.UTF_8)
        val body =
            StreamingCreateAttachmentRequestBody(
                filename = "f.txt",
                type = "text/plain",
                memo = null,
                externalLink = "https://example.com/a?b=1",
                knownContentSizeBytes = bytes.size.toLong(),
                openInputStream = { ByteArrayInputStream(bytes) },
            )

        val buffer = Buffer()
        body.writeTo(buffer)
        val writtenSize = buffer.size

        assertEquals(writtenSize, body.contentLength())
    }

    @Test
    fun contentLength_unknownSize_returnsMinus1() {
        val body =
            StreamingCreateAttachmentRequestBody(
                filename = "f.txt",
                type = "text/plain",
                memo = null,
                externalLink = null,
                knownContentSizeBytes = null,
                openInputStream = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
            )

        assertEquals(-1L, body.contentLength())
    }
}
