package cc.pscly.onememos.core.network.dto

/**
 * google.protobuf.Empty 的 JSON 形态通常是 `{}`。
 * 用一个空 DTO 来避免 Retrofit/Gson 对空响应体解析不一致的问题。
 */
data class EmptyDto(
    val _unused: String? = null,
)
