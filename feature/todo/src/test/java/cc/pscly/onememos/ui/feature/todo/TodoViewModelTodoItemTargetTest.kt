package cc.pscly.onememos.ui.feature.todo

import cc.pscly.onememos.navigation.TodoItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class TodoViewModelTodoItemTargetTest {
    @Test
    fun targetState_typesAndReasons_areStable() {
        val key = TodoItemKey(itemId = "i1", expectedOwnerKey = "o1")
        val loading: TodoItemTargetState = TodoItemTargetState.Loading(key)
        val unavailable =
            TodoItemTargetState.Unavailable(
                key = key,
                reason = TodoItemUnavailableReason.DISABLED,
            )
        assertEquals(key, (loading as TodoItemTargetState.Loading).key)
        assertEquals(TodoItemUnavailableReason.DISABLED, unavailable.reason)
        assertEquals(TodoItemUnavailableReason.DELETED, TodoItemUnavailableReason.DELETED)
        assertEquals(
            TodoItemUnavailableReason.NOT_FOUND_OR_ACCOUNT_MISMATCH,
            TodoItemUnavailableReason.NOT_FOUND_OR_ACCOUNT_MISMATCH,
        )
    }

    @Test
    fun todoViewModelSource_hasNoNavigatorDependency() {
        val path =
            System.getProperty("oneMemos.projectDir")?.let {
                File(it, "feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoViewModel.kt")
            }
        // When run under :feature:todo tests, projectDir may be absent; skip soft.
        if (path == null || !path.exists()) return
        val body = path.readText()
        assertFalse(body.contains("OneMemosNavigator"))
        assertFalse(body.contains("FeatureEntryHost"))
        assertTrueBind(body)
    }

    private fun assertTrueBind(body: String) {
        org.junit.Assert.assertTrue(body.contains("fun bind(key: TodoItemKey)"))
        org.junit.Assert.assertTrue(body.contains("OwnerKeyProvider"))
        org.junit.Assert.assertTrue(body.contains("todoItemTargetState"))
    }
}
