package cc.pscly.onememos.worker

import android.util.Base64
import android.util.Base64OutputStream
import java.io.InputStream
import java.io.OutputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject

/**
 * 以流式方式生成 CreateAttachment 的 JSON body（content 为 base64 且 NO_WRAP），避免 readBytes() 造成 OOM。
 */
internal class StreamingCreateAttachmentRequestBody(
    private val filename: String,
    private val type: String,
    private val memo: String?,
    private val externalLink: String?,
    private val knownContentSizeBytes: Long?,
    private val openInputStream: () -> InputStream,
) : RequestBody() {

    override fun contentType() = JSON_MEDIA_TYPE

    override fun contentLength(): Long {
        val contentSize = knownContentSizeBytes ?: return -1L

        val base64Len = ((contentSize + 2L) / 3L) * 4L
        val prefix = prefixJsonBytes()
        val suffix = suffixJsonBytes()
        return prefix.size.toLong() + base64Len + suffix.size.toLong()
    }

    override fun writeTo(sink: BufferedSink) {
        sink.write(prefixJsonBytes())

        // Base64OutputStream.close() 会关闭底层 OutputStream，这里必须避免把 Okio sink 意外关闭。
        val nonClosingSinkOut = NonClosingOutputStream(sink.outputStream())
        val base64Out = Base64OutputStream(nonClosingSinkOut, Base64.NO_WRAP)

        openInputStream().use { input ->
            input.copyTo(base64Out)
        }
        base64Out.close()

        sink.write(suffixJsonBytes())
    }

    private fun prefixJsonBytes(): ByteArray {
        val prefix =
            buildString {
                append("{\"filename\":")
                append(JSONObject.quote(filename))
                append(",\"content\":\"")
            }
        return prefix.toByteArray(Charsets.UTF_8)
    }

    private fun suffixJsonBytes(): ByteArray {
        val suffix =
            buildString {
                append("\",\"type\":")
                append(JSONObject.quote(type))
                append(",\"memo\":")
                append(jsonStringOrNullLiteral(memo))
                append(",\"externalLink\":")
                append(jsonStringOrNullLiteral(externalLink))
                append("}")
            }
        return suffix.toByteArray(Charsets.UTF_8)
    }

    private fun jsonStringOrNullLiteral(value: String?): String {
        if (value == null) return "null"
        return JSONObject.quote(value)
    }

    private class NonClosingOutputStream(
        private val delegate: OutputStream,
    ) : OutputStream() {
        override fun write(b: Int) = delegate.write(b)

        override fun write(b: ByteArray) = delegate.write(b)

        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)

        override fun flush() = delegate.flush()

        override fun close() {
        }
    }

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
