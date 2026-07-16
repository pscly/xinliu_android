package cc.pscly.onememos.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 进程死亡 / 配置变更恢复：六栈、当前分区、类型化参数与已消费外部输入不得重放。
 */
class NavigationProcessDeathTest {
    private val store = JsonNavigationStateStore()

    @Test
    fun encodeRestore_preservesSixStacksActiveSectionAndTypedKeys() {
        val controller = AppNavigationController()
        controller.push(EditorKey("home/uuid-with-slash"))
        controller.switchSection(TopLevelSection.COLLECTIONS)
        controller.push(ShareCardKey("share/1"))
        controller.switchSection(TopLevelSection.TODO)
        controller.push(TodoItemKey(itemId = "item-1", expectedOwnerKey = "owner-1"))
        controller.switchSection(TopLevelSection.PROFILE)
        controller.push(EditorKey("profile-memo"))
        controller.switchSection(TopLevelSection.ARCHIVED)
        controller.switchSection(TopLevelSection.SETTINGS)
        controller.push(AccountSyncSettingsKey)
        controller.push(AccountManagementSettingsKey)

        val before = controller.snapshot()
        val encoded = controller.encode()

        // 模拟进程死亡：新 controller 从编码快照恢复
        val restoredResult = store.restore(encoded)
        assertTrue(restoredResult is NavigationRestoreResult.Restored)
        val restored = (restoredResult as NavigationRestoreResult.Restored).snapshot
        val rebuilt = AppNavigationController(initial = restored)

        assertEquals(before.activeSection, rebuilt.snapshot().activeSection)
        TopLevelSection.entries.forEach { section ->
            assertEquals(
                "section=$section",
                before.stacks[section],
                rebuilt.snapshot().stacks[section],
            )
        }
        assertEquals(
            listOf(TodoKey, TodoItemKey("item-1", "owner-1")),
            rebuilt.snapshot().stacks[TopLevelSection.TODO],
        )
        assertEquals(
            listOf(SettingsHubKey, AccountSyncSettingsKey, AccountManagementSettingsKey),
            rebuilt.snapshot().stacks[TopLevelSection.SETTINGS],
        )
        assertEquals(
            listOf(HomeKey, EditorKey("home/uuid-with-slash")),
            rebuilt.snapshot().stacks[TopLevelSection.HOME],
        )
    }

    @Test
    fun afterRestore_onlyReloadsEntries_doesNotReplayConsumedExternalInput() {
        val controller = AppNavigationController()
        val applied =
            controller.applyPendingExternal(
                ExternalNavigationInput.TodoNotification(
                    itemId = "item-restore",
                    expectedOwnerKey = "owner-r",
                ),
            )
        assertTrue(applied is ExternalNavigationResult.Accepted)
        assertEquals(
            listOf(TodoKey, TodoItemKey("item-restore", "owner-r")),
            controller.snapshot().stacks[TopLevelSection.TODO],
        )
        assertTrue(controller.initialIntentConsumed)

        val encoded = controller.encode()
        val restored = (store.restore(encoded) as NavigationRestoreResult.Restored).snapshot
        val rebuilt = AppNavigationController(initial = restored)
        // 恢复后初始标记为未消费会话，但 Host 层对同一 Intent 的已消费语义由 initialIntentConsumed 控制；
        // 新进程不会自动重放通知：只有显式 applyPendingExternal 才会改栈。
        assertFalseNotReplayed(rebuilt)

        // 若错误地再次投递同一初始 Intent 且 forceNewDelivery=false，应被消费门闩挡住
        rebuilt.applyPendingExternal(
            ExternalNavigationInput.TodoNotification(
                itemId = "item-restore",
                expectedOwnerKey = "owner-r",
            ),
        )
        // 首次在新进程上应用一次是合法的新会话输入；再以 forceNewDelivery=false 不得重放
        val second =
            rebuilt.applyPendingExternal(
                ExternalNavigationInput.TodoNotification(
                    itemId = "item-restore",
                    expectedOwnerKey = "owner-r",
                ),
                forceNewDelivery = false,
            )
        assertNull(second)
        assertEquals(
            listOf(TodoKey, TodoItemKey("item-restore", "owner-r")),
            rebuilt.snapshot().stacks[TopLevelSection.TODO],
        )
    }

    @Test
    fun restore_rejectsUnknownKey_doesNotInjectStringRoute() {
        val codec = JsonNavKeyCodec()
        val bad =
            """
            {"activeSection":"HOME","stacks":{
              "HOME":["{\"type\":\"cc.pscly.onememos.navigation.UnknownKey\"}"],
              "COLLECTIONS":["${escape(codec.encode(CollectionsKey))}"],
              "TODO":["${escape(codec.encode(TodoKey))}"],
              "PROFILE":["${escape(codec.encode(ProfileKey))}"],
              "ARCHIVED":["${escape(codec.encode(ArchivedKey))}"],
              "SETTINGS":["${escape(codec.encode(SettingsHubKey))}"]
            }}
            """.trimIndent()
        val result = store.restore(bad)
        assertTrue(result is NavigationRestoreResult.Rejected)
        assertEquals(
            NavigationRestoreRejection.UNKNOWN_KEY,
            (result as NavigationRestoreResult.Rejected).reason,
        )
    }

    @Test
    fun hostEncodePath_matchesControllerEncode() {
        // AppNavigationHost 使用同一 JsonNavigationStateStore；编码往返必须结构一致
        val snap =
            NavigationSnapshot(
                activeSection = TopLevelSection.SETTINGS,
                stacks =
                    mapOf(
                        TopLevelSection.HOME to listOf(HomeKey, WelcomeKey),
                        TopLevelSection.COLLECTIONS to listOf(CollectionsKey),
                        TopLevelSection.TODO to
                            listOf(TodoKey, TodoItemKey("i", "o")),
                        TopLevelSection.PROFILE to listOf(ProfileKey),
                        TopLevelSection.ARCHIVED to listOf(ArchivedKey),
                        TopLevelSection.SETTINGS to
                            listOf(SettingsHubKey, AdvancedSyncSettingsKey),
                    ),
            ).normalized()
        val encoded = store.save(snap)
        val restored = store.restore(encoded)
        assertTrue(restored is NavigationRestoreResult.Restored)
        assertEquals(snap, (restored as NavigationRestoreResult.Restored).snapshot)
    }

    private fun assertFalseNotReplayed(controller: AppNavigationController) {
        // 恢复后快照已含 Todo 项；未调用 apply 前栈不得再增长
        assertEquals(
            listOf(TodoKey, TodoItemKey("item-restore", "owner-r")),
            controller.snapshot().stacks[TopLevelSection.TODO],
        )
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
