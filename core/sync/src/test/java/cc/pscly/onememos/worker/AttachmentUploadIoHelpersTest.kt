package cc.pscly.onememos.worker

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AttachmentUploadIoHelpersTest {
    @Test
    fun limitCountingInputStream_over_maxBytes_throws_with_seenBytes() {
        val maxBytes = 5L
        val bytes = ByteArray(6) { 1 }
        val limited = LimitCountingInputStream(ByteArrayInputStream(bytes), maxBytes)

        try {
            while (true) {
                val r = limited.read()
                if (r == -1) break
            }
            fail("期望抛出 AttachmentOversizeException")
        } catch (e: AttachmentOversizeException) {
            assertEquals(maxBytes, e.maxBytes)
            assertEquals(6L, e.seenBytes)
        }
    }

    @Test
    fun localReadWrappingInputStream_wraps_ioexception_as_localReadIOException() {
        val raw =
            object : InputStream() {
                override fun read(): Int = throw IOException("boom")
                override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("boom")
            }
        val wrapped = LocalReadWrappingInputStream(raw)

        try {
            wrapped.read()
            fail("期望抛出 LocalReadIOException")
        } catch (e: LocalReadIOException) {
            assertEquals("附件读取失败", e.message)
            assertEquals("boom", e.cause?.message)
        }

        try {
            wrapped.read(ByteArray(1), 0, 1)
            fail("期望抛出 LocalReadIOException")
        } catch (e: LocalReadIOException) {
            assertEquals("boom", e.cause?.message)
        }
    }

    @Test
    fun localReadWrappingInputStream_passes_through_attachmentOversizeException() {
        val oversize = AttachmentOversizeException(maxBytes = 5, seenBytes = 6)
        val raw =
            object : InputStream() {
                override fun read(): Int = throw oversize
                override fun read(b: ByteArray, off: Int, len: Int): Int = throw oversize
            }
        val wrapped = LocalReadWrappingInputStream(raw)

        try {
            wrapped.read()
            fail("期望抛出 AttachmentOversizeException")
        } catch (e: AttachmentOversizeException) {
            assertSame(oversize, e)
        }

        try {
            wrapped.read(ByteArray(1), 0, 1)
            fail("期望抛出 AttachmentOversizeException")
        } catch (e: AttachmentOversizeException) {
            assertSame(oversize, e)
        }
    }

    @Test
    fun resolveAttachmentSizeBytes_fileUri_returns_length() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("attach_size_", ".bin")
        file.writeBytes(ByteArray(12) { 7 })
        try {
            val uri = Uri.fromFile(file)
            assertEquals(12L, resolveAttachmentSizeBytes(context, uri))
        } finally {
            file.delete()
        }
    }
}
