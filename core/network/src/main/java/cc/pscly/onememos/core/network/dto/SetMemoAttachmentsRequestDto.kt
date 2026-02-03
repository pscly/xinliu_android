package cc.pscly.onememos.core.network.dto

/**
 * SetMemoAttachments 的 HTTP body 是 "*"，因此这里是完整 request 的 JSON 结构。
 */
data class SetMemoAttachmentsRequestDto(
    val name: String,
    val attachments: List<AttachmentRefDto>,
)

data class AttachmentRefDto(
    val name: String,
)
