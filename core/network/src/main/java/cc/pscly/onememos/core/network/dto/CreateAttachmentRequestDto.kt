package cc.pscly.onememos.core.network.dto

import com.google.gson.annotations.SerializedName

/**
 * 注意：memos v0.25.x 的 Attachment.content 在 JSON 中是 base64 字符串。
 * 这里用 String 显式承载，避免 Gson 把 ByteArray 序列化成数字数组。
 */
data class CreateAttachmentRequestDto(
    val filename: String,
    @SerializedName("content")
    val contentBase64: String,
    val type: String,
    val memo: String? = null,
    @SerializedName("externalLink")
    val externalLink: String? = null,
)
