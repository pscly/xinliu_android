package cc.pscly.onememos.domain.model

data class MemoAttachment(
    val id: Long,
    /** 本地附件路径（例如 app 内部文件 file:// 或 content://）。远端附件可能为空。 */
    val localUri: String?,
    /** 远端附件下载后的本地缓存路径（file://...）。 */
    val cacheUri: String?,
    /** 远端附件资源名，例如 attachments/{id}。本地未上传附件可能为空。 */
    val remoteName: String?,
    /** 文件名（用于拼接 /file/{name}/{filename} 的下载 URL）。 */
    val filename: String?,
    val mimeType: String?,
    val createdAt: Long,
)
