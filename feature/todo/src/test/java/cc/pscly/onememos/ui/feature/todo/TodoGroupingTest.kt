package cc.pscly.onememos.ui.feature.todo

import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.domain.model.TodoStatuses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoGroupingTest {
    private val fixedNowLocal = "2026-02-06 09:00:00"
    private val nowProvider: (String) -> String = { fixedNowLocal }

    @Test
    fun `到期在今天 - 进入今日分组`() {
        val item =
            TodoItem(
                id = "1",
                listId = "l1",
                title = "写周报",
                dueAtLocal = "2026-02-06 18:00:00",
            )

        val sections = buildTodoSections(listOf(item), nowLocalProvider = nowProvider)
        assertEquals(listOf("today"), sections.map { it.key })
        assertEquals("1", sections.first().items.first().item.id)
    }

    @Test
    fun `到期在未来7天内 - 进入即将分组`() {
        val item =
            TodoItem(
                id = "1",
                listId = "l1",
                title = "交付版本",
                dueAtLocal = "2026-02-09 10:00:00",
            )

        val sections = buildTodoSections(listOf(item), nowLocalProvider = nowProvider)
        assertEquals(listOf("upcoming"), sections.map { it.key })
    }

    @Test
    fun `到期在未来7天外 - 进入其他分组`() {
        val item =
            TodoItem(
                id = "1",
                listId = "l1",
                title = "准备季度规划",
                dueAtLocal = "2026-02-20 10:00:00",
            )

        val sections = buildTodoSections(listOf(item), nowLocalProvider = nowProvider)
        assertEquals(listOf("other"), sections.map { it.key })
    }

    @Test
    fun `循环任务 - 能算出下次发生并参与分组`() {
        val item =
            TodoItem(
                id = "1",
                listId = "l1",
                title = "每天喝水",
                isRecurring = true,
                rrule = "FREQ=DAILY",
                dtstartLocal = "2026-02-06 08:00:00",
                dueAtLocal = null,
            )

        val sections = buildTodoSections(listOf(item), nowLocalProvider = nowProvider)
        assertTrue(sections.isNotEmpty())
        val p = sections.first().items.first()
        assertNotNull(p.effectiveDueAtLocal)
        // 下次发生一般会落在未来（因 fastForward），不强依赖精确秒值，只保证不为空且可解析。
        assertNotNull(p.effectiveDueLocal)
    }

    @Test
    fun `已完成 - 进入已完成分组`() {
        val item =
            TodoItem(
                id = "1",
                listId = "l1",
                title = "已完成事项",
                status = TodoStatuses.DONE,
                completedAtLocal = "2026-02-06 09:01:00",
            )

        val sections = buildTodoSections(listOf(item), nowLocalProvider = nowProvider)
        assertEquals(listOf("done"), sections.map { it.key })
    }

    @Test
    fun `提醒摘要 - before_due minutes`() {
        val raw =
            """[
              {"type":"before_due","minutes":0},
              {"type":"before_due","minutes":5},
              {"type":"before_due","minutes":30}
            ]""".trimIndent()

        assertEquals("提醒：准时、提前5分（+1）", reminderSummary(raw))
        assertEquals(null, reminderSummary("[]"))
        assertEquals(null, reminderSummary(""))
    }
}
