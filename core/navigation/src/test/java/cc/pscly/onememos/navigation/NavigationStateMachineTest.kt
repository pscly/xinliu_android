package cc.pscly.onememos.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationStateMachineTest {
    @Test
    fun sixRootStacks_existAndStartAtHome() {
        val machine = NavigationStateMachine()
        val snap = machine.state.value
        assertEquals(TopLevelSection.HOME, snap.activeSection)
        TopLevelSection.entries.forEach { section ->
            assertEquals(listOf(section.root), snap.stacks[section])
        }
    }

    @Test
    fun switchSection_preservesStacks_andReselectIsNoOp() {
        val machine = NavigationStateMachine()
        machine.push(EditorKey("a"))
        machine.switchSection(TopLevelSection.TODO)
        machine.push(TodoItemKey("i1", "o1"))
        machine.switchSection(TopLevelSection.HOME)
        assertEquals(
            listOf(HomeKey, EditorKey("a")),
            machine.state.value.stacks[TopLevelSection.HOME],
        )
        assertEquals(
            listOf(TodoKey, TodoItemKey("i1", "o1")),
            machine.state.value.stacks[TopLevelSection.TODO],
        )
        val before = machine.state.value
        machine.switchSection(TopLevelSection.HOME)
        assertEquals(before, machine.state.value)
    }

    @Test
    fun push_onlyWritesActiveStack_andAllowsDuplicates() {
        val machine = NavigationStateMachine()
        machine.push(EditorKey("a"))
        machine.push(EditorKey("a"))
        assertEquals(
            listOf(HomeKey, EditorKey("a"), EditorKey("a")),
            machine.state.value.stacks[TopLevelSection.HOME],
        )
        TopLevelSection.entries.filter { it != TopLevelSection.HOME }.forEach {
            assertEquals(listOf(it.root), machine.state.value.stacks[it])
        }
    }

    @Test
    fun back_popsCurrentStack_nonHomeRootGoesHome_homeRootExits() {
        val machine = NavigationStateMachine()
        machine.push(EditorKey("a"))
        assertEquals(BackResult.Consumed, machine.back())
        assertEquals(listOf(HomeKey), machine.state.value.stacks[TopLevelSection.HOME])

        machine.switchSection(TopLevelSection.SETTINGS)
        assertEquals(BackResult.Consumed, machine.back())
        assertEquals(TopLevelSection.HOME, machine.state.value.activeSection)

        assertEquals(BackResult.ExitApplication, machine.back())
    }

    @Test
    fun detailKeysStayOnOriginatingStack_noTopLevelHistory() {
        val machine = NavigationStateMachine()
        machine.switchSection(TopLevelSection.COLLECTIONS)
        machine.push(ShareCardKey("c1"))
        machine.switchSection(TopLevelSection.PROFILE)
        machine.push(EditorKey("p1"))
        // 系统不维护顶层访问历史：从 PROFILE 根返回只回 HOME，不回 COLLECTIONS
        machine.back() // pop editor
        machine.back() // PROFILE root -> HOME
        assertEquals(TopLevelSection.HOME, machine.state.value.activeSection)
        assertEquals(
            listOf(CollectionsKey, ShareCardKey("c1")),
            machine.state.value.stacks[TopLevelSection.COLLECTIONS],
        )
        assertEquals(listOf(ProfileKey), machine.state.value.stacks[TopLevelSection.PROFILE])
    }

    @Test
    fun applyExternal_resetPushAllowIgnoreIfTop() {
        val machine = NavigationStateMachine()
        machine.switchSection(TopLevelSection.TODO)
        machine.push(TodoItemKey("deep", "o"))
        machine.push(TodoItemKey("top", "o"))

        // IGNORE_IF_TOP: same key at top is no-op
        machine.applyExternal(
            ExternalNavigationResult.Accepted(
                section = TopLevelSection.TODO,
                mutation =
                    ExternalStackMutation.Push(
                        key = TodoItemKey("top", "o"),
                        duplicatePolicy = ExternalNavigationDuplicatePolicy.IGNORE_IF_TOP,
                    ),
            ),
        )
        assertEquals(
            listOf(TodoKey, TodoItemKey("deep", "o"), TodoItemKey("top", "o")),
            machine.state.value.stacks[TopLevelSection.TODO],
        )

        // same key deeper still appends
        machine.applyExternal(
            ExternalNavigationResult.Accepted(
                section = TopLevelSection.TODO,
                mutation =
                    ExternalStackMutation.Push(
                        key = TodoItemKey("deep", "o"),
                        duplicatePolicy = ExternalNavigationDuplicatePolicy.IGNORE_IF_TOP,
                    ),
            ),
        )
        assertEquals(
            listOf(
                TodoKey,
                TodoItemKey("deep", "o"),
                TodoItemKey("top", "o"),
                TodoItemKey("deep", "o"),
            ),
            machine.state.value.stacks[TopLevelSection.TODO],
        )

        // ALLOW always appends
        machine.switchSection(TopLevelSection.HOME)
        machine.applyExternal(
            ExternalNavigationResult.Accepted(
                section = TopLevelSection.HOME,
                mutation =
                    ExternalStackMutation.Push(
                        key = EditorKey("x"),
                        duplicatePolicy = ExternalNavigationDuplicatePolicy.ALLOW,
                    ),
            ),
        )
        machine.applyExternal(
            ExternalNavigationResult.Accepted(
                section = TopLevelSection.HOME,
                mutation =
                    ExternalStackMutation.Push(
                        key = EditorKey("x"),
                        duplicatePolicy = ExternalNavigationDuplicatePolicy.ALLOW,
                    ),
            ),
        )
        assertEquals(
            listOf(HomeKey, EditorKey("x"), EditorKey("x")),
            machine.state.value.stacks[TopLevelSection.HOME],
        )

        // ResetToRoot only target stack; even from other active section checks TODO target
        machine.switchSection(TopLevelSection.PROFILE)
        machine.applyExternal(
            ExternalNavigationResult.Accepted(
                section = TopLevelSection.TODO,
                mutation = ExternalStackMutation.ResetToRoot,
            ),
        )
        assertEquals(TopLevelSection.TODO, machine.state.value.activeSection)
        assertEquals(listOf(TodoKey), machine.state.value.stacks[TopLevelSection.TODO])
        assertNotEquals(listOf(HomeKey), machine.state.value.stacks[TopLevelSection.HOME])

        // Rejected is no-op
        val before = machine.state.value
        machine.applyExternal(
            ExternalNavigationResult.Rejected(ExternalNavigationRejection.UNKNOWN_VALUE),
        )
        assertEquals(before, machine.state.value)
    }
}
