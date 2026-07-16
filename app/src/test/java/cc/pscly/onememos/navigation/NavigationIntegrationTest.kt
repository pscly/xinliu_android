package cc.pscly.onememos.navigation

import cc.pscly.onememos.ui.feature.home.HomeEntryContributor
import cc.pscly.onememos.ui.feature.home.HomeScreenMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host 层六栈集成回归：分区切换保现场、返回语义、归档归属、外部输入与唯一 entry。
 * 通过 [AppNavigationController] 覆盖 Host 接线逻辑，不依赖 Compose 运行时。
 */
class NavigationIntegrationTest {
    @Test
    fun sixSections_pushDetail_switchPreservesStacks_reselectIsNoOp() {
        val controller = AppNavigationController()

        val detailBySection =
            mapOf(
                TopLevelSection.HOME to EditorKey("home-detail"),
                TopLevelSection.COLLECTIONS to ShareCardKey("collections-card"),
                TopLevelSection.TODO to TodoItemKey("todo-detail", "owner-1"),
                TopLevelSection.PROFILE to EditorKey("profile-detail"),
                TopLevelSection.ARCHIVED to EditorKey("archived-detail"),
                TopLevelSection.SETTINGS to AccountSyncSettingsKey,
            )

        detailBySection.forEach { (section, detail) ->
            controller.switchSection(section)
            controller.push(detail)
        }

        // 再切换一轮：各分区现场必须保留
        TopLevelSection.entries.forEach { section ->
            controller.switchSection(section)
            val expected = listOf(section.root, detailBySection.getValue(section))
            assertEquals(
                "section=$section 现场应保留",
                expected,
                controller.snapshot().stacks[section],
            )
        }

        // 当前分区重复选择：不清栈、不回根
        controller.switchSection(TopLevelSection.SETTINGS)
        val before = controller.snapshot()
        controller.switchSection(TopLevelSection.SETTINGS)
        assertEquals(before, controller.snapshot())
        assertEquals(
            listOf(SettingsHubKey, AccountSyncSettingsKey),
            controller.snapshot().stacks[TopLevelSection.SETTINGS],
        )
    }

    @Test
    fun nonHomeRoot_backToHome_homeRoot_exits() {
        val controller = AppNavigationController()

        TopLevelSection.entries
            .filter { it != TopLevelSection.HOME }
            .forEach { section ->
                controller.switchSection(section)
                assertEquals(section, controller.snapshot().activeSection)
                assertEquals(BackResult.Consumed, controller.back())
                assertEquals(TopLevelSection.HOME, controller.snapshot().activeSection)
                // 非 HOME 根返回只切换分区，不弹栈
                assertEquals(listOf(section.root), controller.snapshot().stacks[section])
            }

        assertEquals(TopLevelSection.HOME, controller.snapshot().activeSection)
        assertEquals(listOf(HomeKey), controller.snapshot().stacks[TopLevelSection.HOME])
        assertEquals(BackResult.ExitApplication, controller.back())
    }

    @Test
    fun archivedKey_isOwnedByHome_withArchivedMode() {
        assertTrue(HomeEntryContributor.owns(ArchivedKey))
        assertTrue(HomeEntryContributor.owns(HomeKey))
        assertEquals(TopLevelSection.ARCHIVED.root, ArchivedKey)

        assertEquals(ArchivedKey, TopLevelSection.ARCHIVED.root)
        assertEquals(HomeScreenMode.ARCHIVED.name, "ARCHIVED")

        val controller = AppNavigationController()
        controller.switchSection(TopLevelSection.ARCHIVED)
        assertEquals(listOf(ArchivedKey), controller.snapshot().stacks[TopLevelSection.ARCHIVED])
        assertEquals(TopLevelSection.ARCHIVED, controller.snapshot().activeSection)
    }

    @Test
    fun shareInput_entersHomeEditor_preservesOtherStacks() {
        val controller = AppNavigationController()
        controller.switchSection(TopLevelSection.SETTINGS)
        controller.push(AccountSyncSettingsKey)
        controller.switchSection(TopLevelSection.TODO)
        controller.push(TodoItemKey("keep", "owner-a"))

        val result =
            controller.applyPendingExternal(
                ExternalNavigationInput.SharedMemo("share/uuid-1"),
                forceNewDelivery = true,
            )
        assertTrue(result is ExternalNavigationResult.Accepted)
        assertEquals(TopLevelSection.HOME, controller.snapshot().activeSection)
        assertEquals(
            listOf(HomeKey, EditorKey("share/uuid-1")),
            controller.snapshot().stacks[TopLevelSection.HOME],
        )
        assertEquals(
            listOf(SettingsHubKey, AccountSyncSettingsKey),
            controller.snapshot().stacks[TopLevelSection.SETTINGS],
        )
        assertEquals(
            listOf(TodoKey, TodoItemKey("keep", "owner-a")),
            controller.snapshot().stacks[TopLevelSection.TODO],
        )
    }

    @Test
    fun todoNotification_preservesFiveStacks_pushesScopedItem_ignoresTopDuplicate() {
        val controller = AppNavigationController()
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
                forceNewDelivery = true,
            )
        assertTrue(first is ExternalNavigationResult.Accepted)
        assertEquals(TopLevelSection.TODO, controller.snapshot().activeSection)
        assertEquals(
            listOf(TodoKey, TodoItemKey("deep", "owner-a"), TodoItemKey("item-top", "owner-a")),
            controller.snapshot().stacks[TopLevelSection.TODO],
        )
        assertEquals(
            listOf(HomeKey, EditorKey("home-draft")),
            controller.snapshot().stacks[TopLevelSection.HOME],
        )
        assertEquals(
            listOf(CollectionsKey, ShareCardKey("c1")),
            controller.snapshot().stacks[TopLevelSection.COLLECTIONS],
        )
        assertEquals(
            listOf(ProfileKey, EditorKey("profile-memo")),
            controller.snapshot().stacks[TopLevelSection.PROFILE],
        )
        assertEquals(
            listOf(SettingsHubKey, AccountSyncSettingsKey),
            controller.snapshot().stacks[TopLevelSection.SETTINGS],
        )
        assertEquals(listOf(ArchivedKey), controller.snapshot().stacks[TopLevelSection.ARCHIVED])

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

        controller.back()
        assertEquals(
            listOf(TodoKey, TodoItemKey("deep", "owner-a")),
            controller.snapshot().stacks[TopLevelSection.TODO],
        )
        controller.markNewIntentDelivery()
        controller.applyPendingExternal(
            ExternalNavigationInput.TodoNotification(
                itemId = "item-top",
                expectedOwnerKey = "owner-a",
            ),
            forceNewDelivery = true,
        )
        assertEquals(
            listOf(TodoKey, TodoItemKey("deep", "owner-a"), TodoItemKey("item-top", "owner-a")),
            controller.snapshot().stacks[TopLevelSection.TODO],
        )
    }

    @Test
    fun openTodoRoot_onlyResetsTodoStack_unknownInput_noChange() {
        val controller = AppNavigationController()
        controller.push(EditorKey("keep-home"))
        controller.switchSection(TopLevelSection.TODO)
        controller.push(TodoItemKey("old", "owner"))
        controller.switchSection(TopLevelSection.PROFILE)
        controller.push(EditorKey("keep-profile"))

        controller.applyPendingExternal(
            ExternalNavigationInput.OpenTodoRoot,
            forceNewDelivery = true,
        )
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

        val before = controller.snapshot()
        val rejected =
            controller.applyPendingExternal(
                ExternalNavigationInput.TodoNotification(itemId = " ", expectedOwnerKey = "o"),
                forceNewDelivery = true,
            )
        assertTrue(rejected is ExternalNavigationResult.Rejected)
        assertEquals(before, controller.snapshot())
    }

    @Test
    fun everyKnownKey_hasExactlyOneEntryOwner() {
        val keys =
            listOf(
                HomeKey,
                CollectionsKey,
                TodoKey,
                TodoItemKey(itemId = "item-1", expectedOwnerKey = "owner-1"),
                ProfileKey,
                ArchivedKey,
                SettingsHubKey,
                WelcomeKey,
                EditorKey(uuid = null),
                EditorKey(uuid = "memos/123"),
                ShareCardKey(uuid = "share/1"),
                AuthKey(mode = null),
                AuthKey(mode = AuthMode.CUSTOM_TOKEN),
                AccountSyncSettingsKey,
                AccountManagementSettingsKey,
                AdvancedSyncSettingsKey,
                RecordEditingSettingsKey,
                ReminderCalendarSettingsKey,
                StorageOfflineSettingsKey,
                AppearanceInteractionSettingsKey,
                AboutAdvancedSettingsKey,
            )
        keys.forEach { key ->
            val owners = appEntryContributors.filter { it.owns(key) }
            assertEquals("key=$key owners=$owners", 1, owners.size)
        }
        assertFalse(appEntryContributors.any { it.javaClass.simpleName.contains("Legacy") })
    }
}
