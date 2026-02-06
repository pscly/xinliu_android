package cc.pscly.onememos.domain.model

import java.time.Instant

data class TodoItem(
    val id: String,
    val listId: String,
    val parentId: String? = null,
    val title: String,
    val note: String = "",
    val status: String = TodoStatuses.OPEN,
    val priority: Int = 0,
    val sortOrder: Int = 0,
    val tags: List<String> = emptyList(),
    /**
     * reminders 为“客户端自定义结构的数组”。为了避免把动态结构暴露到 UI/Domain，
     * 这里统一以 JSON 字符串存储（默认 "[]"）。
     */
    val remindersJson: String = "[]",
    val dueAtLocal: String? = null,
    val completedAtLocal: String? = null,
    val isRecurring: Boolean = false,
    val rrule: String? = null,
    val dtstartLocal: String? = null,
    /**
     * IANA TZID，例如 "Asia/Shanghai"。用于解释 *_at_local 与 rrule。
     */
    val tzid: String = "",
    val deletedAt: String? = null,
    val clientUpdatedAtMs: Long = 0L,
    val updatedAt: String = Instant.EPOCH.toString(),
)

