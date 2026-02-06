package cc.pscly.onememos.domain.model

import java.time.Instant

data class TodoList(
    val id: String,
    val name: String,
    val color: String? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    /**
     * 软删除时间（ISO-8601）。为 null 表示未删除。
     * 注意：服务端在 sync/pull 里会通过 deleted_at 下发软删除对象。
     */
    val deletedAt: String? = null,
    /**
     * 客户端更新时间戳（毫秒）。用于 sync 冲突判定（client_updated_at_ms）。
     */
    val clientUpdatedAtMs: Long = 0L,
    /**
     * 服务端更新时间（ISO-8601）。本地离线写入时用本机时间占位即可。
     */
    val updatedAt: String = Instant.EPOCH.toString(),
)

