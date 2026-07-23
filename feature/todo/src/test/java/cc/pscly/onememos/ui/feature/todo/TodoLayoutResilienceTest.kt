package cc.pscly.onememos.ui.feature.todo

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Todo 列表布局弹性：小视口 + 大字体下相邻行不得纵向重叠。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h250dp-xxhdpi")
class TodoLayoutResilienceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val f1 =
        TodoItemPresentation(
            item =
                TodoItem(
                    id = "todo-1",
                    listId = "list-1",
                    title = "事项一",
                    updatedAt = "1970-01-01T00:00:00Z",
                ),
            isDone = false,
            effectiveDueAtLocal = null,
            effectiveDueLocal = null,
            reminderSummary = null,
            nowDate = LocalDate.of(2026, 7, 22),
        )

    private val f2 =
        f1.copy(item = f1.item.copy(id = "todo-2", title = "事项二"))

    @Test
    fun todoRows_noVerticalOverlap_onNarrowTallSmallScreen() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 2f),
            ) {
                OneMemosTheme {
                    TodoItemsList(modifier = Modifier.fillMaxSize()) {
                        items(listOf(f1, f2), key = { it.item.id }) { p ->
                            TodoItemRow(
                                p = p,
                                listName = null,
                                onOpen = {},
                                onToggleDone = { _, _ -> },
                                onDelete = {},
                                onTagClick = {},
                                onStamp = {},
                                modifier = Modifier.testTag("todo_row_${p.item.id}"),
                            )
                        }
                    }
                }
            }
        }

        val row1 =
            composeRule
                .onNodeWithTag("todo_row_todo-1")
                .fetchSemanticsNode()
                .boundsInRoot
        val row2 =
            composeRule
                .onNodeWithTag("todo_row_todo-2")
                .fetchSemanticsNode()
                .boundsInRoot

        val noVerticalOverlap =
            row1.bottom <= row2.top || row2.bottom <= row1.top

        assertTrue(
            "todo rows must not vertically overlap: row1=$row1 row2=$row2",
            noVerticalOverlap,
        )
    }
}
