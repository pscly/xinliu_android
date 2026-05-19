package cc.pscly.onememos.core.network.dto

import com.google.gson.annotations.SerializedName

data class MemosUserDto(
    val name: String? = null,
    @SerializedName("display_name")
    val displayName: String? = null,
)
