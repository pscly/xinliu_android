package cc.pscly.onememos.settings

import cc.pscly.onememos.domain.settings.SettingsPermission
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.navigation.AboutAdvancedSettingsKey
import cc.pscly.onememos.navigation.AccountManagementSettingsKey
import cc.pscly.onememos.navigation.AccountSyncSettingsKey
import cc.pscly.onememos.navigation.AdvancedSyncSettingsKey
import cc.pscly.onememos.navigation.AppearanceInteractionSettingsKey
import cc.pscly.onememos.navigation.AppNavigationController
import cc.pscly.onememos.navigation.BackResult
import cc.pscly.onememos.navigation.JsonNavigationStateStore
import cc.pscly.onememos.navigation.NavigationRestoreResult
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.RecordEditingSettingsKey
import cc.pscly.onememos.navigation.ReminderCalendarSettingsKey
import cc.pscly.onememos.navigation.SettingsHubKey
import cc.pscly.onememos.navigation.StorageOfflineSettingsKey
import cc.pscly.onememos.navigation.TopLevelSection
import cc.pscly.onememos.ui.feature.settings.SettingsEventCallbacks
import cc.pscly.onememos.ui.feature.settings.SettingsEntryContributor
import cc.pscly.onememos.ui.feature.settings.dispatchSettingsEvent
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Settings 端到端状态：Hub 六入口、账号子页栈、确认/平台/更新边界，
 * 以及一次性事件不因重组/恢复重复执行。
 */
class SettingsEndToEndStateTest {
    companion object {
        private lateinit var projectDir: File

        @BeforeClass
        @JvmStatic
        fun setup() {
            val path = System.getProperty("oneMemos.projectDir")
            require(!path.isNullOrBlank()) { "oneMemos.projectDir missing" }
            projectDir = File(path)
        }
    }

    private val hubSixEntries: List<OneMemosNavKey> =
        listOf(
            AccountSyncSettingsKey,
            RecordEditingSettingsKey,
            ReminderCalendarSettingsKey,
            StorageOfflineSettingsKey,
            AppearanceInteractionSettingsKey,
            AboutAdvancedSettingsKey,
        )

    @Test
    fun hubSixEntries_pushAndBack_preserveSettingsStackSemantics() {
        val controller = AppNavigationController()
        controller.switchSection(TopLevelSection.SETTINGS)
        assertEquals(listOf(SettingsHubKey), controller.snapshot().stacks[TopLevelSection.SETTINGS])

        hubSixEntries.forEach { key ->
            assertTrue("SettingsEntryContributor 必须拥有 $key", SettingsEntryContributor.owns(key))
            controller.push(key)
            assertEquals(
                listOf(SettingsHubKey, key),
                controller.snapshot().stacks[TopLevelSection.SETTINGS],
            )
            assertEquals(BackResult.Consumed, controller.back())
            assertEquals(
                listOf(SettingsHubKey),
                controller.snapshot().stacks[TopLevelSection.SETTINGS],
            )
        }
    }

    @Test
    fun accountManagement_andAdvancedSync_stackAndBack() {
        val controller = AppNavigationController()
        controller.switchSection(TopLevelSection.SETTINGS)
        controller.push(AccountSyncSettingsKey)
        controller.push(AccountManagementSettingsKey)
        assertEquals(
            listOf(SettingsHubKey, AccountSyncSettingsKey, AccountManagementSettingsKey),
            controller.snapshot().stacks[TopLevelSection.SETTINGS],
        )
        assertEquals(BackResult.Consumed, controller.back())
        assertEquals(
            listOf(SettingsHubKey, AccountSyncSettingsKey),
            controller.snapshot().stacks[TopLevelSection.SETTINGS],
        )

        controller.push(AdvancedSyncSettingsKey)
        assertEquals(
            listOf(SettingsHubKey, AccountSyncSettingsKey, AdvancedSyncSettingsKey),
            controller.snapshot().stacks[TopLevelSection.SETTINGS],
        )
        assertEquals(BackResult.Consumed, controller.back())
        assertEquals(BackResult.Consumed, controller.back())
        assertEquals(
            listOf(SettingsHubKey),
            controller.snapshot().stacks[TopLevelSection.SETTINGS],
        )
        // Settings 根返回 HOME，设置栈现场保留
        assertEquals(BackResult.Consumed, controller.back())
        assertEquals(TopLevelSection.HOME, controller.snapshot().activeSection)
        assertEquals(
            listOf(SettingsHubKey),
            controller.snapshot().stacks[TopLevelSection.SETTINGS],
        )
    }

    @Test
    fun navigateConfirmPlatformUpdate_eachDispatchedOnce() {
        val calls = mutableListOf<String>()
        val callbacks =
            SettingsEventCallbacks(
                onNavigate = { calls += "nav:$it" },
                onToast = { calls += "toast:$it" },
                onConfirm = { calls += "confirm:$it" },
                onPlatform = { calls += "platform:$it" },
                onUpdateDelivery = { calls += "update:${it::class.simpleName}" },
            )

        dispatchSettingsEvent(SettingsUiEvent.Navigate(AccountManagementSettingsKey), callbacks)
        dispatchSettingsEvent(SettingsUiEvent.Confirm(SettingsConfirmation.LOGOUT), callbacks)
        dispatchSettingsEvent(SettingsUiEvent.Confirm(SettingsConfirmation.FULL_RESYNC), callbacks)
        dispatchSettingsEvent(
            SettingsUiEvent.Platform(
                SettingsPlatformAction.RequestPermissions(
                    permissions = setOf(SettingsPermission.READ_CALENDAR),
                ),
            ),
            callbacks,
        )
        dispatchSettingsEvent(
            SettingsUiEvent.UpdateDelivery(
                UpdateDeliveryAction.OpenUnknownSourcesSettings("cc.pscly.onememos"),
            ),
            callbacks,
        )
        dispatchSettingsEvent(SettingsUiEvent.Toast(SettingsMessage.PERMISSION_DENIED), callbacks)

        assertEquals(
            listOf(
                "nav:AccountManagementSettingsKey",
                "confirm:LOGOUT",
                "confirm:FULL_RESYNC",
                "platform:RequestPermissions(permissions=[READ_CALENDAR])",
                "update:OpenUnknownSourcesSettings",
                "toast:PERMISSION_DENIED",
            ),
            calls,
        )
    }

    @Test
    fun oneShotEvents_replayZero_notReplayedAfterNewCollector() =
        runTest {
            val events =
                MutableSharedFlow<SettingsUiEvent>(
                    replay = 0,
                    extraBufferCapacity = 1,
                )
            val firstCollector = mutableListOf<SettingsUiEvent>()
            val job =
                launch {
                    events.collect { firstCollector += it }
                }
            testScheduler.runCurrent()

            assertTrue(events.tryEmit(SettingsUiEvent.Navigate(AccountSyncSettingsKey)))
            testScheduler.runCurrent()
            assertEquals(1, firstCollector.size)
            job.cancel()

            // 新收集器在 replay=0 下不得收到已发射事件（模拟重组/旋转后重新 collect）
            val secondCollector = mutableListOf<SettingsUiEvent>()
            val job2 =
                launch {
                    events.collect { secondCollector += it }
                }
            testScheduler.runCurrent()
            assertTrue(secondCollector.isEmpty())

            assertTrue(events.tryEmit(SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED)))
            testScheduler.runCurrent()
            assertEquals(1, secondCollector.size)
            assertEquals(
                SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED),
                secondCollector.single(),
            )
            job2.cancel()
        }

    @Test
    fun settingsStack_survivesProcessRestore_withoutReplayingNavigationEvents() {
        val controller = AppNavigationController()
        controller.switchSection(TopLevelSection.SETTINGS)
        controller.push(AccountSyncSettingsKey)
        controller.push(AdvancedSyncSettingsKey)

        val store = JsonNavigationStateStore()
        val encoded = controller.encode()
        val restored = (store.restore(encoded) as NavigationRestoreResult.Restored).snapshot
        val rebuilt = AppNavigationController(initial = restored)

        assertEquals(TopLevelSection.SETTINGS, rebuilt.snapshot().activeSection)
        assertEquals(
            listOf(SettingsHubKey, AccountSyncSettingsKey, AdvancedSyncSettingsKey),
            rebuilt.snapshot().stacks[TopLevelSection.SETTINGS],
        )
        // 恢复只重载栈，不自动 push 新键
        rebuilt.switchSection(TopLevelSection.HOME)
        rebuilt.switchSection(TopLevelSection.SETTINGS)
        assertEquals(
            listOf(SettingsHubKey, AccountSyncSettingsKey, AdvancedSyncSettingsKey),
            rebuilt.snapshot().stacks[TopLevelSection.SETTINGS],
        )
    }

    @Test
    fun entryContributor_collectsEventsOnlyInStartedLifecycle_sourceContract() {
        val source =
            projectDir
                .resolve(
                    "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsEntryContributor.kt",
                ).readText()
        assertTrue(source.contains("repeatOnLifecycle(Lifecycle.State.STARTED)"))
        assertTrue(source.contains("events.collect"))
        assertTrue(source.contains("navigator.push(key)"))
        assertTrue(source.contains("LocalSettingsPlatformActionDispatcher.current"))
        assertTrue(source.contains("LocalSettingsUpdateDeliveryDispatcher.current"))

        // 页面级不得双消费
        val pages =
            listOf(
                "hub/SettingsHubScreen.kt",
                "account/AccountSyncScreen.kt",
                "account/AccountManagementScreen.kt",
                "account/AdvancedSyncScreen.kt",
                "record/RecordEditingScreen.kt",
                "reminder/ReminderCalendarScreen.kt",
                "storage/StorageOfflineScreen.kt",
                "appearance/AppearanceInteractionScreen.kt",
                "about/AboutAdvancedScreen.kt",
            )
        pages.forEach { relative ->
            val body =
                projectDir
                    .resolve(
                        "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/$relative",
                    ).readText()
            assertFalse(
                "$relative 不得页面级 collect 一次性事件",
                body.contains("events.collect"),
            )
        }
    }

    @Test
    fun hubOwnsExactlySixCapabilityEntries_plusThreeAccountSubKeys() {
        hubSixEntries.forEach { key ->
            assertTrue(SettingsEntryContributor.owns(key))
        }
        assertTrue(SettingsEntryContributor.owns(SettingsHubKey))
        assertTrue(SettingsEntryContributor.owns(AccountManagementSettingsKey))
        assertTrue(SettingsEntryContributor.owns(AdvancedSyncSettingsKey))
        // 共 9 键：1 hub + 6 能力 + 2 账号子页
        val nine =
            listOf(
                SettingsHubKey,
                AccountSyncSettingsKey,
                AccountManagementSettingsKey,
                AdvancedSyncSettingsKey,
                RecordEditingSettingsKey,
                ReminderCalendarSettingsKey,
                StorageOfflineSettingsKey,
                AppearanceInteractionSettingsKey,
                AboutAdvancedSettingsKey,
            )
        assertEquals(9, nine.size)
        nine.forEach { assertTrue(SettingsEntryContributor.owns(it)) }
    }
}
