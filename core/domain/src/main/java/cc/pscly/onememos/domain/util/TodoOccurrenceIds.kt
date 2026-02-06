package cc.pscly.onememos.domain.util

import java.util.UUID

object TodoOccurrenceIds {
    /**
     * 为 occurrence 生成稳定 id：
     * - 需求：离线也能创建；再次生成要一致（避免重复 upsert）。
     * - 约束：服务端 id 最大长度 36，因此使用 UUID（36 含短横线）。
     */
    fun stableId(
        itemId: String,
        tzid: String,
        recurrenceIdLocal: String,
    ): String {
        val key = "$itemId|$tzid|$recurrenceIdLocal"
        return UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
    }
}

