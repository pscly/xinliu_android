package cc.pscly.onememos.core.network.dto

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * 上游 memos v0.31.0 `Attachment`。
 *
 * 说明：
 * - `size` 在 proto 里是 int64，protojson 会编码成字符串；Gson 的 Long 适配器同时接受
 *   数字与数字字符串，所以这里保持 Long 类型不需要改动。
 * - `motionMedia` / `mediaMetadata` 结构复杂且当前 App 未消费，按 [JsonObject] 透传，
 *   避免臆造字段名。
 */
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
    val motionMedia: JsonObject? = null,
    val mediaMetadata: JsonObject? = null,
)
