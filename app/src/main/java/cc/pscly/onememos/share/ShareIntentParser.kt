package cc.pscly.onememos.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * 分享 Intent 解析（纯逻辑，便于单元测试）。
 */
internal object ShareIntentParser {
    data class SharePayload(
        val text: String,
        val streamUris: List<Uri>,
    )

    fun parse(intent: Intent?): SharePayload? {
        if (intent == null) return null

        val action = intent.action.orEmpty()
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().trim()
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()

        val streams = mutableListOf<Uri>()
        if (action == Intent.ACTION_SEND_MULTIPLE) {
            val list = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            streams += list
            streams += extractClipDataUris(intent.clipData)
        } else {
            val single = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (single != null) streams += single
            streams += extractClipDataUris(intent.clipData)
        }

        val mergedText =
            buildString {
                if (subject.isNotBlank()) {
                    append(subject)
                    append('\n')
                }
                if (text.isNotBlank()) {
                    append(text)
                }
            }.trim()

        // 没有文字也没有附件就不处理
        if (mergedText.isBlank() && streams.isEmpty()) return null

        return SharePayload(
            text = mergedText.ifBlank { "(分享内容为空)" },
            streamUris = streams.distinct(),
        )
    }

    private fun extractClipDataUris(clipData: ClipData?): List<Uri> {
        if (clipData == null) return emptyList()
        val out = ArrayList<Uri>(clipData.itemCount)
        for (i in 0 until clipData.itemCount) {
            val uri = clipData.getItemAt(i).uri ?: continue
            out += uri
        }
        return out
    }
}

