package cc.pscly.onememos.core.network.dto

import com.google.gson.annotations.SerializedName

data class ListMemosResponseDto(
    val memos: List<MemoDto> = emptyList(),
    @SerializedName("nextPageToken")
    val nextPageToken: String? = null,
)
