package cc.pscly.onememos.worker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream

internal class AttachmentOversizeException(
    val maxBytes: Long,
    val seenBytes: Long,
) : IOException("附件大小超限：$seenBytes > $maxBytes")

internal class LocalReadIOException(
    cause: IOException,
) : IOException("附件读取失败", cause)

internal class LimitCountingInputStream(
    private val delegate: InputStream,
    private val maxBytes: Long,
) : InputStream() {
    private var readTotal: Long = 0L

    override fun read(): Int {
        val r = delegate.read()
        if (r != -1) {
            readTotal += 1
            if (readTotal > maxBytes) {
                throw AttachmentOversizeException(maxBytes = maxBytes, seenBytes = readTotal)
            }
        }
        return r
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(b, off, len)
        if (n > 0) {
            readTotal += n.toLong()
            if (readTotal > maxBytes) {
                throw AttachmentOversizeException(maxBytes = maxBytes, seenBytes = readTotal)
            }
        }
        return n
    }

    override fun close() = delegate.close()
}

internal class LocalReadWrappingInputStream(
    private val delegate: InputStream,
) : InputStream() {
    override fun read(): Int {
        return try {
            delegate.read()
        } catch (e: AttachmentOversizeException) {
            throw e
        } catch (e: IOException) {
            throw LocalReadIOException(e)
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return try {
            delegate.read(b, off, len)
        } catch (e: AttachmentOversizeException) {
            throw e
        } catch (e: IOException) {
            throw LocalReadIOException(e)
        }
    }

    override fun close() = delegate.close()
}

internal fun resolveAttachmentSizeBytes(context: Context, uri: Uri): Long? {
    if (uri.scheme.equals("file", ignoreCase = true)) {
        val path = uri.path ?: return null
        return runCatching { File(path).length() }.getOrNull()?.takeIf { it >= 0L }
    }

    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index).takeIf { it >= 0L } else null
        }
    } catch (_: Exception) {
        null
    }
}
