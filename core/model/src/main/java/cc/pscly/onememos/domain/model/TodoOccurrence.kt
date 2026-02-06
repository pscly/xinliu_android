package cc.pscly.onememos.domain.model

import java.time.Instant

data class TodoOccurrence(
    val id: String,
    val itemId: String,
    val tzid: String,
    /**
     * recurrence_id_local：长度 19 的本地时间字符串（yyyy-MM-dd HH:mm:ss）。
     * 服务端会用 (item_id + tzid + recurrence_id_local) 做去重/唯一键。
     */
    val recurrenceIdLocal: String,
    val statusOverride: String? = null,
    val titleOverride: String? = null,
    val noteOverride: String? = null,
    val dueAtOverrideLocal: String? = null,
    val completedAtLocal: String? = null,
    val deletedAt: String? = null,
    val clientUpdatedAtMs: Long = 0L,
    val updatedAt: String = Instant.EPOCH.toString(),
)

