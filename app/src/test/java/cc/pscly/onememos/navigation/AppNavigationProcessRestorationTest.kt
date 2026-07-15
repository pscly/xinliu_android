package cc.pscly.onememos.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationProcessRestorationTest {
    @Test
    fun externalTodoNotification_appendsScopedItem_andIgnoresTopDuplicate() {
        val controller = AppNavigationController()
        // 先推进六栈到不同页面
        controller.push(EditorKey("home-draft"))
        controller.switchSection(TopLevelSection.COLLECTIONS)
        controller.push(ShareCardKey("c1"))
        controller.switchSection(TopLevelSection.PROFILE)
        controller.push(EditorKey("profile-memo"))
        controller.switchSection(TopLevelSection.SETTINGS)
        controller.push(AccountSyncSettingsKey)
        controller.switchSection(TopLevelSection.ARCHIVED)
        controller.switchSection(TopLevelSection.TODO)
        controller.push(TodoItemKey("deep", "owner-a"))

        val first =
            controller.applyPendingExternal(
                ExternalNavigationInput.TodoNotification(
                    itemId = "item-top",
                    expectedOwnerKey = "owner-a",
                ),
            )
        assertTrue(first is ExternalNavigationResult.Accepted)
        assertEquals(
            listOf(TodoKey, TodoItemKey("deep", "owner-a"), TodoItemKey("item-top", "owner-a")),
            controller.snapshot().stacks[TopLevelSection.TODO],
        )
        // 其他分区现场保留
        assertEquals(
            listOf(HomeKey, EditorKey("home-draft")),
            controller.snapshot().stacks[TopLevelSection.HOME],
        )
        assertEquals(
            listOf(CollectionsKey, ShareCardKey("c1")),
            controller.snapshot().stacks[TopLevelSection.COLLECTIONS],
        )

        // 栈顶重复投递不追加
        val dup =
            controller.applyPendingExternal(
                ExternalNavigationInput.TodoNotification(
                    itemId = "item-top",
                    expectedOwnerKey = "owner-a",
                ),
                forceNewDelivery = true,
            )
        assertTrue(dup is ExternalNavigationResult.Accepted)
        assertEquals(
            listOf(TodoKey, TodoItemKey("deep", "owner-a"), TodoItemKey("item-top", "owner-a")),
            controller.snapshot().stacks[TopLevelSection.TODO],
        )
    }

    @Test
    fun afterPop_newIntentCanAppendAgain() {
        val controller = AppNavigationController()
        controller.applyPendingExternal(
            ExternalNavigationInput.TodoNotification("item-1", "owner-1"),
            forceNewDelivery = true,
        )
        assertEquals(
            listOf(TodoKey, TodoItemKey("item-1", "owner-1")),
            controller.snapshot().stacks[TopLevelSection.TODO],
        )
        controller.back()
        assertEquals(listOf(TodoKey), controller.snapshot().stacks[TopLevelSection.TODO])

        controller.markNewIntentDelivery()
        controller.applyPendingExternal(
            ExternalNavigationInput.TodoNotification("item-1", "owner-1"),
            forceNewDelivery = true,
        )
        assertEquals(
            listOf(TodoKey, TodoItemKey("item-1", "owner-1")),
            controller.snapshot().stacks[TopLevelSection.TODO],
        )
    }

    @Test
    fun openTodoRoot_onlyResetsTodoStack() {
        val controller = AppNavigationController()
        controller.push(EditorKey("keep-home"))
        controller.switchSection(TopLevelSection.TODO)
        controller.push(TodoItemKey("old", "owner"))
        controller.switchSection(TopLevelSection.PROFILE)
        controller.push(EditorKey("keep-profile"))

        controller.applyPendingExternal(ExternalNavigationInput.OpenTodoRoot, forceNewDelivery = true)

        assertEquals(TopLevelSection.TODO, controller.snapshot().activeSection)
        assertEquals(listOf(TodoKey), controller.snapshot().stacks[TopLevelSection.TODO])
        assertEquals(
            listOf(HomeKey, EditorKey("keep-home")),
            controller.snapshot().stacks[TopLevelSection.HOME],
        )
        assertEquals(
            listOf(ProfileKey, EditorKey("keep-profile")),
            controller.snapshot().stacks[TopLevelSection.PROFILE],
        )
    }

    @Test
    fun unknownInput_doesNotChangeStacks() {
        val controller = AppNavigationController()
        controller.push(EditorKey("x"))
        val before = controller.snapshot()
        val result =
            controller.applyPendingExternal(
                ExternalNavigationInput.LegacyRouteExtra("nope"),
                forceNewDelivery = true,
            )
        assertTrue(result is ExternalNavigationResult.Rejected)
        assertEquals(before, controller.snapshot())
    }

    @Test
    fun consumedInitialIntent_isNotReplayedAfterRestore() {
        val controller = AppNavigationController()
        val applied =
            controller.applyPendingExternal(
                ExternalNavigationInput.LegacyEditorExtra("share-uuid"),
            )
        assertTrue(applied is ExternalNavigationResult.Accepted)
        assertEquals(
            listOf(HomeKey, EditorKey("share-uuid")),
            controller.snapshot().stacks[TopLevelSection.HOME],
        )

        // 模拟旋转/进程恢复：已消费标记阻止重放
        val replay =
            controller.applyPendingExternal(
                ExternalNavigationInput.LegacyEditorExtra("share-uuid"),
                forceNewDelivery = false,
            )
        assertNull(replay)
        assertEquals(
            listOf(HomeKey, EditorKey("share-uuid")),
            controller.snapshot().stacks[TopLevelSection.HOME],
        )

        // onNewIntent 新投递可再次应用
        controller.markNewIntentDelivery()
        val again =
            controller.applyPendingExternal(
                ExternalNavigationInput.LegacyEditorExtra("share-uuid-2"),
                forceNewDelivery = true,
            )
        assertTrue(again is ExternalNavigationResult.Accepted)
        assertEquals(
            listOf(HomeKey, EditorKey("share-uuid"), EditorKey("share-uuid-2")),
            controller.snapshot().stacks[TopLevelSection.HOME],
        )
    }

    @Test
    fun encodeRestore_preservesTodoItemKeyOnly() {
        val controller = AppNavigationController()
        controller.switchSection(TopLevelSection.TODO)
        controller.push(TodoItemKey("item-1", "owner-1"))
        val encoded = controller.encode()
        val store = JsonNavigationStateStore()
        val restored = store.restore(encoded)
        assertTrue(restored is NavigationRestoreResult.Restored)
        val snap = (restored as NavigationRestoreResult.Restored).snapshot
        assertEquals(
            listOf(TodoKey, TodoItemKey("item-1", "owner-1")),
            snap.stacks[TopLevelSection.TODO],
        )
    }

    @Test
    fun welcomeOnlyWhenNoPendingExternal() {
        val controller = AppNavigationController()
        controller.maybePushWelcome(showWelcome = true, hasPendingExternal = true)
        assertEquals(listOf(HomeKey), controller.snapshot().stacks[TopLevelSection.HOME])

        controller.maybePushWelcome(showWelcome = true, hasPendingExternal = false)
        assertEquals(
            listOf(HomeKey, WelcomeKey),
            controller.snapshot().stacks[TopLevelSection.HOME],
        )
    }
}
