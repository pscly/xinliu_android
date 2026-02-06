package cc.pscly.onememos.ui.feature.todo

import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.model.TodoStatuses
import cc.pscly.onememos.domain.todo.TodoRecurrenceCalculator
import cc.pscly.onememos.domain.util.LocalDateTimes
import java.time.LocalDate
import java.time.LocalDateTime

internal const val TODO_UPCOMING_DAYS: Long = 7L

internal data class TodoItemPresentation(
    val item: TodoItem,
    val isDone: Boolean,
    val effectiveDueAtLocal: String?,
    val effectiveDueLocal: LocalDateTime?,
    val nowDate: LocalDate,
)

internal data class TodoSection(
    val key: String,
    val title: String,
    val items: List<TodoItemPresentation>,
)

internal fun TodoItem.isDone(): Boolean = status == TodoStatuses.DONE || !completedAtLocal.isNullOrBlank()

internal fun buildTodoSections(
    items: List<TodoItem>,
    upcomingDays: Long = TODO_UPCOMING_DAYS,
    nowLocalProvider: (tzid: String) -> String = { tzid -> LocalDateTimes.nowString(tzid) },
): List<TodoSection> {
    if (items.isEmpty()) return emptyList()

    // 以 tzid 缓存 nowLocal/nowDate，避免每条 item 重复计算。
    data class NowInfo(
        val nowLocal: String,
        val nowDate: LocalDate,
    )

    val nowByTzid = LinkedHashMap<String, NowInfo>()
    fun nowInfo(tzid: String): NowInfo {
        val key = tzid.trim()
        return nowByTzid.getOrPut(key) {
            val nowLocal = nowLocalProvider(key)
            val nowDate = LocalDateTimes.parseOrNull(nowLocal)?.toLocalDate() ?: LocalDate.now()
            NowInfo(nowLocal = nowLocal, nowDate = nowDate)
        }
    }

    fun effectiveDueAtLocal(item: TodoItem, nowLocal: String): String? {
        val due = item.dueAtLocal?.trim().orEmpty()
        if (due.isNotBlank()) return due
        if (!item.isRecurring) return null
        return TodoRecurrenceCalculator.nextRecurrenceIdLocal(
            rrule = item.rrule,
            dtstartLocal = item.dtstartLocal,
            nowLocal = nowLocal,
        )
    }

    val open = ArrayList<TodoItemPresentation>(items.size)
    val done = ArrayList<TodoItemPresentation>(items.size / 2)

    items.forEach { item ->
        val now = nowInfo(item.tzid)
        val isDone = item.isDone()
        val effDue = effectiveDueAtLocal(item, now.nowLocal)
        val effLocal = LocalDateTimes.parseOrNull(effDue)
        val p =
            TodoItemPresentation(
                item = item,
                isDone = isDone,
                effectiveDueAtLocal = effDue,
                effectiveDueLocal = effLocal,
                nowDate = now.nowDate,
            )
        if (isDone) done.add(p) else open.add(p)
    }

    val today = ArrayList<TodoItemPresentation>()
    val upcoming = ArrayList<TodoItemPresentation>()
    val other = ArrayList<TodoItemPresentation>()

    open.forEach { p ->
        val dueDate = p.effectiveDueLocal?.toLocalDate()
        if (dueDate == null) {
            other.add(p)
            return@forEach
        }

        val end = p.nowDate.plusDays(upcomingDays)
        when {
            dueDate.isAfter(end) -> other.add(p)
            dueDate.isAfter(p.nowDate) -> upcoming.add(p)
            else -> today.add(p) // 含逾期 + 今日
        }
    }

    val openComparator =
        compareBy<TodoItemPresentation>(
            { it.effectiveDueLocal ?: LocalDateTime.MAX },
            { it.item.title },
        )

    today.sortWith(openComparator)
    upcoming.sortWith(openComparator)
    other.sortWith(openComparator)

    done.sortWith(
        compareByDescending<TodoItemPresentation> { it.item.completedAtLocal.orEmpty() }
            .thenByDescending { it.item.clientUpdatedAtMs }
            .thenBy { it.item.title },
    )

    val sections = ArrayList<TodoSection>(4)
    if (today.isNotEmpty()) {
        sections.add(TodoSection(key = "today", title = "今日", items = today))
    }
    if (upcoming.isNotEmpty()) {
        sections.add(TodoSection(key = "upcoming", title = "即将（${upcomingDays}天）", items = upcoming))
    }
    if (other.isNotEmpty()) {
        sections.add(TodoSection(key = "other", title = "其他", items = other))
    }
    if (done.isNotEmpty()) {
        sections.add(TodoSection(key = "done", title = "已完成", items = done))
    }
    return sections
}
