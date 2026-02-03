package cc.pscly.onememos.core.network.dto

import com.google.gson.annotations.SerializedName

data class MemoDto(
    val name: String? = null,
    val state: String? = null,
    val creator: String? = null,
    @SerializedName("createTime")
    val createTime: String? = null,
    @SerializedName("updateTime")
    val updateTime: String? = null,
    @SerializedName("displayTime")
    val displayTime: String? = null,
    val content: String? = null,
    val visibility: String? = null,
    val pinned: Boolean? = null,
    val attachments: List<AttachmentDto>? = null,
)
