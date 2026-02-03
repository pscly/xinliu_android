package cc.pscly.onememos.core.network.dto

import com.google.gson.annotations.SerializedName

data class AttachmentDto(
    val name: String? = null,
    @SerializedName("createTime")
    val createTime: String? = null,
    val filename: String? = null,
    @SerializedName("externalLink")
    val externalLink: String? = null,
    val type: String? = null,
    val size: Long? = null,
    val memo: String? = null,
)
