package cc.pscly.onememos.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalNavigationMapperTest {
    private val mapper = ExternalNavigationMapper()

    @Test
    fun shareAndLegacyEditor_mapToHomeEditorPushAllow() {
        val shared = mapper.map(ExternalNavigationInput.SharedMemo("memos/1"))
        val legacy = mapper.map(ExternalNavigationInput.LegacyEditorExtra("uuid/中文"))
        assertAcceptedPush(shared, TopLevelSection.HOME, EditorKey("memos/1"), ExternalNavigationDuplicatePolicy.ALLOW)
        assertAcceptedPush(legacy, TopLevelSection.HOME, EditorKey("uuid/中文"), ExternalNavigationDuplicatePolicy.ALLOW)
    }

    @Test
    fun todoNotification_mapsToIgnoreIfTop_andRejectsBlank() {
        val ok =
            mapper.map(
                ExternalNavigationInput.TodoNotification(
                    itemId = " item-1 ",
                    expectedOwnerKey = " owner-1 ",
                ),
            )
        assertAcceptedPush(
            ok,
            TopLevelSection.TODO,
            TodoItemKey("item-1", "owner-1"),
            ExternalNavigationDuplicatePolicy.IGNORE_IF_TOP,
        )

        val blankItem =
            mapper.map(ExternalNavigationInput.TodoNotification(itemId = " ", expectedOwnerKey = "o"))
        assertEquals(
            ExternalNavigationRejection.INVALID_ARGUMENT,
            (blankItem as ExternalNavigationResult.Rejected).reason,
        )
        val blankOwner =
            mapper.map(ExternalNavigationInput.TodoNotification(itemId = "i", expectedOwnerKey = ""))
        assertEquals(
            ExternalNavigationRejection.INVALID_ARGUMENT,
            (blankOwner as ExternalNavigationResult.Rejected).reason,
        )
    }

    @Test
    fun openTodoRoot_resetsTodoStack() {
        val open = mapper.map(ExternalNavigationInput.OpenTodoRoot)
        assertAcceptedReset(open, TopLevelSection.TODO)
    }

    @Test
    fun emptyEditor_rejected() {
        val emptyEditor = mapper.map(ExternalNavigationInput.SharedMemo("  "))
        assertEquals(
            ExternalNavigationRejection.INVALID_ARGUMENT,
            (emptyEditor as ExternalNavigationResult.Rejected).reason,
        )
    }

    @Test
    fun applyToStateMachine_rejectedKeepsStacks_todoTopDuplicateIsIdempotent() {
        val machine = NavigationStateMachine()
        val before = machine.state.value
        machine.applyExternal(
            mapper.map(ExternalNavigationInput.TodoNotification(itemId = " ", expectedOwnerKey = "o")),
        )
        assertEquals(before, machine.state.value)

        val todoPush =
            mapper.map(
                ExternalNavigationInput.TodoNotification(
                    itemId = "i1",
                    expectedOwnerKey = "o1",
                ),
            )
        machine.applyExternal(todoPush)
        machine.applyExternal(todoPush)
        assertEquals(
            listOf(TodoKey, TodoItemKey("i1", "o1")),
            machine.state.value.stacks[TopLevelSection.TODO],
        )
        assertEquals(TopLevelSection.TODO, machine.state.value.activeSection)
    }

    private fun assertAcceptedPush(
        result: ExternalNavigationResult,
        section: TopLevelSection,
        key: OneMemosNavKey,
        policy: ExternalNavigationDuplicatePolicy,
    ) {
        assertTrue(result is ExternalNavigationResult.Accepted)
        val accepted = result as ExternalNavigationResult.Accepted
        assertEquals(section, accepted.section)
        val push = accepted.mutation as ExternalStackMutation.Push
        assertEquals(key, push.key)
        assertEquals(policy, push.duplicatePolicy)
    }

    private fun assertAcceptedReset(
        result: ExternalNavigationResult,
        section: TopLevelSection,
    ) {
        assertTrue(result is ExternalNavigationResult.Accepted)
        val accepted = result as ExternalNavigationResult.Accepted
        assertEquals(section, accepted.section)
        assertEquals(ExternalStackMutation.ResetToRoot, accepted.mutation)
    }
}
