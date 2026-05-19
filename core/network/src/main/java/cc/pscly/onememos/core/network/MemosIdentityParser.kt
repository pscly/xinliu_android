package cc.pscly.onememos.core.network

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import cc.pscly.onememos.core.network.dto.GetCurrentUserResponseDto

object MemosIdentityParser {
    fun extractCreatorName(response: GetCurrentUserResponseDto?): String? {
        val direct = response?.user?.name?.trim()?.takeIf { it.isNotBlank() }
        if (!direct.isNullOrBlank()) return direct

        val nested = response?.data?.user?.name?.trim()?.takeIf { it.isNotBlank() }
        if (!nested.isNullOrBlank()) return nested

        return null
    }

    /**
     * 尝试从 memos 的“当前用户”响应中提取用户资源名（例如 users/1）。
     *
     * 注意：不同 memos 版本/部署返回结构可能不同，这里做最小兼容：
     * - { user: { name: "users/1" } }
     * - { data: { user: { name: "users/1" } } }
     * - { name: "users/1" }（直接返回 user 对象）
     */
    fun extractCreatorName(payload: JsonObject?): String? {
        if (payload == null) return null

        fun str(e: JsonElement?): String? = e?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotBlank() }

        val direct = str(payload.get("name"))
        if (!direct.isNullOrBlank()) return direct

        val user = payload.getAsJsonObject("user")
        val userName = str(user?.get("name"))
        if (!userName.isNullOrBlank()) return userName

        val dataUser = payload.getAsJsonObject("data")?.getAsJsonObject("user")
        val dataUserName = str(dataUser?.get("name"))
        if (!dataUserName.isNullOrBlank()) return dataUserName

        return null
    }
}
