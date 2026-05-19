package cc.pscly.onememos.core.network

import android.net.Uri

object MemosUrls {
    // 默认 Memos 服务器（普通用户无感使用；仅开发者模式2允许自定义服务器）。
    const val DEFAULT_MEMOS_SERVER_URL: String = "https://me.pscly.cc/"

    /**
     * 将用户输入的 serverUrl 归一化为可拼接的 base（保证有 scheme，去掉尾部 /）。
     */
    fun normalizeServerBase(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val withScheme =
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }

        val normalized = withScheme.trimEnd('/')
        return if (normalized.isBlank()) null else normalized
    }

    fun apiV1(base: String, path: String): String {
        val baseNorm = base.trimEnd('/')
        val p = path.trimStart('/')
        return "$baseNorm/$p"
    }

    fun listMemos(base: String): String = apiV1(base, "api/v1/memos")
    fun createMemo(base: String): String = apiV1(base, "api/v1/memos")
    fun createAttachment(base: String): String = apiV1(base, "api/v1/attachments")
    fun authMe(base: String): String = apiV1(base, "api/v1/auth/me")
    fun authStatus(base: String): String = apiV1(base, "api/v1/auth/status")
    fun currentUser(base: String): String = apiV1(base, "api/v1/users/me")

    fun memo(base: String, memoName: String): String = apiV1(base, "api/v1/$memoName")
    fun memoAttachments(base: String, memoName: String): String = apiV1(base, "api/v1/$memoName/attachments")

    /**
     * 下载附件（图片）：
     * GET /file/{name=attachments/<wildcard>}/{filename}?thumbnail=true|false
     */
    fun attachmentFileUrl(
        base: String,
        attachmentName: String,
        filename: String,
        thumbnail: Boolean = false,
    ): String {
        val baseNorm = base.trimEnd('/')
        val encodedFilename = Uri.encode(filename)
        val url = "$baseNorm/file/$attachmentName/$encodedFilename"
        return if (thumbnail) "$url?thumbnail=true" else url
    }
}
