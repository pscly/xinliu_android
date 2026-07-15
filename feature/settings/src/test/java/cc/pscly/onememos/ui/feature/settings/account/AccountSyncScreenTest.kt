package cc.pscly.onememos.ui.feature.settings.account

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.settings.AccountSyncHealth
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCommand
import cc.pscly.onememos.domain.settings.AccountSyncSettingsSnapshot
import cc.pscly.onememos.domain.settings.FullResyncProgress
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AccountSyncScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allTenHealthVariants_followUniquePrimaryActionMatrix() {
        val cases =
            listOf(
                MatrixCase(AccountSyncHealth.Unbound, "登录", enabled = true, advancedVisible = false),
                MatrixCase(
                    AccountSyncHealth.ConfiguredSignedOut,
                    "登录",
                    enabled = true,
                    advancedVisible = false,
                ),
                MatrixCase(
                    AccountSyncHealth.Healthy(lastSuccessAtEpochMs = 100L),
                    "立即同步",
                    enabled = true,
                    advancedVisible = true,
                ),
                MatrixCase(AccountSyncHealth.Syncing, "同步进行中", enabled = false, advancedVisible = true),
                MatrixCase(AccountSyncHealth.Queued, "等待同步", enabled = false, advancedVisible = true),
                MatrixCase(
                    AccountSyncHealth.Failed(SettingsCapabilityError.NetworkUnavailable),
                    "立即同步",
                    enabled = true,
                    advancedVisible = true,
                ),
                MatrixCase(
                    AccountSyncHealth.AuthenticationExpired,
                    "重新登录",
                    enabled = true,
                    advancedVisible = false,
                ),
                MatrixCase(
                    AccountSyncHealth.FullResyncRunning(
                        FullResyncProgress(
                            stage = FullSyncStage.NORMAL,
                            pagesFetched = 2,
                            itemsFetched = 9,
                        ),
                    ),
                    "重同步进行中",
                    enabled = false,
                    advancedVisible = true,
                ),
                MatrixCase(
                    AccountSyncHealth.FullResyncFailed(SettingsCapabilityError.StorageFailure),
                    "查看故障处理",
                    enabled = true,
                    advancedVisible = true,
                ),
                MatrixCase(
                    AccountSyncHealth.FullResyncCompleted(
                        completionId = "run-200",
                        completedAtEpochMs = 200L,
                    ),
                    primaryText = null,
                    enabled = false,
                    advancedVisible = true,
                ),
            )
        var snapshot by mutableStateOf(snapshot(cases.first().health))
        composeRule.setContent {
            OneMemosTheme {
                AccountSyncContent(
                    snapshot = snapshot,
                    onBack = {},
                    onOpenLogin = {},
                    onSyncNow = {},
                    onOpenAccountManagement = {},
                    onOpenAdvancedSync = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        cases.forEach { case ->
            snapshot = snapshot(case.health)
            composeRule.waitForIdle()
            val primaryNodes = composeRule.onAllNodesWithTag("settings_account_primary").fetchSemanticsNodes()
            if (case.primaryText == null) {
                assertEquals(0, primaryNodes.size)
            } else {
                assertEquals(1, primaryNodes.size)
                val primary = composeRule.onNodeWithTag("settings_account_primary")
                primary.performScrollTo()
                composeRule.waitForIdle()
                composeRule.onNodeWithText(case.primaryText).assertIsDisplayed()
                primary.assertHeightIsAtLeast(48.dp)
                if (case.enabled) primary.assertIsEnabled() else primary.assertIsNotEnabled()
            }
            val advancedCount =
                composeRule.onAllNodesWithTag("settings_account_advanced").fetchSemanticsNodes().size
            assertEquals(if (case.advancedVisible) 1 else 0, advancedCount)
        }

        snapshot = snapshot(AccountSyncHealth.Failed(SettingsCapabilityError.NetworkUnavailable))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("网络不可用", substring = true).assertExists()
        snapshot = snapshot(AccountSyncHealth.AuthenticationExpired)
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText("立即同步").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("网络不可用", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("全量重同步").fetchSemanticsNodes().size)
    }

    @Test
    fun rootOrder_isTitleHealthPrimaryLastSuccessAccountSummaryManagementAdvanced() {
        setAccountRoot(snapshot(AccountSyncHealth.Healthy(lastSuccessAtEpochMs = 100L)))
        val orderedTags =
            listOf(
                "settings_account_header",
                "settings_account_health",
                "settings_account_primary",
                "settings_account_last_success",
                "settings_account_summary",
                "settings_account_management",
                "settings_account_advanced",
            )
        val orderedNodes =
            composeRule
                .onAllNodes(
                    SemanticsMatcher("账号与同步页面固定语义顺序") { node ->
                        node.config.getOrNull(SemanticsProperties.TestTag) in orderedTags
                    },
                ).fetchSemanticsNodes()
                .map { it.config.getOrNull(SemanticsProperties.TestTag) }
        assertEquals(orderedTags, orderedNodes)
        composeRule.onNodeWithText("当前账号：已连接账号").assertExists()
        composeRule.onNodeWithTag("settings_account_back").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("settings_account_management").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("settings_account_advanced").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun secondaryViews_ownSensitiveActions_withoutLeakingThemToRoot() {
        var surface by mutableStateOf(AccountSurface.ROOT)
        composeRule.setContent {
            OneMemosTheme {
                when (surface) {
                    AccountSurface.ROOT ->
                        AccountSyncContent(
                            snapshot = snapshot(AccountSyncHealth.Healthy(lastSuccessAtEpochMs = 100L)),
                            onBack = {},
                            onOpenLogin = {},
                            onSyncNow = {},
                            onOpenAccountManagement = {},
                            onOpenAdvancedSync = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    AccountSurface.MANAGEMENT ->
                        AccountManagementContent(
                            snapshot = snapshot(AccountSyncHealth.Healthy(lastSuccessAtEpochMs = 100L)),
                            onBack = {},
                            onChangePassword = { _, _, _ -> },
                            onConfirmLogout = {},
                        )
                    AccountSurface.ADVANCED ->
                        AdvancedSyncContent(
                            snapshot = snapshot(AccountSyncHealth.Healthy(lastSuccessAtEpochMs = 100L)),
                            onBack = {},
                            onConfirmFullResync = {},
                        )
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText("修改密码").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("退出登录").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("全量重同步").fetchSemanticsNodes().size)

        surface = AccountSurface.MANAGEMENT
        composeRule.waitForIdle()
        composeRule.onNodeWithText("修改密码").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_account_logout").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("全量重同步", substring = true).fetchSemanticsNodes().size)

        surface = AccountSurface.ADVANCED
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_account_full_resync_action").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("修改密码").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("退出登录").fetchSemanticsNodes().size)
    }

    @Test
    fun passwordAction_rejectsNewPasswordAboveSeventyOneUtf8Bytes() {
        composeRule.setContent {
            OneMemosTheme {
                AccountManagementContent(
                    snapshot = snapshot(AccountSyncHealth.Healthy(lastSuccessAtEpochMs = 100L)),
                    onBack = {},
                    onChangePassword = { _, _, _ -> },
                    onConfirmLogout = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings_account_password_current").performTextReplacement("current")
        val tooLongPassword = "密".repeat(24)
        composeRule.onNodeWithTag("settings_account_password_new").performTextReplacement(tooLongPassword)
        composeRule.onNodeWithTag("settings_account_password_repeat").performTextReplacement(tooLongPassword)
        composeRule.onNodeWithTag("settings_account_password_save").performScrollTo().assertIsNotEnabled()

        val validPassword = "密".repeat(23)
        composeRule.onNodeWithTag("settings_account_password_new").performTextReplacement(validPassword)
        composeRule.onNodeWithTag("settings_account_password_repeat").performTextReplacement(validPassword)
        composeRule.onNodeWithTag("settings_account_password_save").assertIsEnabled()
    }

    @Test
    fun fullResync_retryRequiresImpactConfirmation_andReturnsFocus() {
        var confirmed = 0
        composeRule.setContent {
            OneMemosTheme {
                AdvancedSyncContent(
                    snapshot = snapshot(AccountSyncHealth.FullResyncFailed(SettingsCapabilityError.StorageFailure)),
                    onBack = {},
                    onConfirmFullResync = { confirmed += 1 },
                )
            }
        }

        composeRule.onNodeWithText("全量重同步会重新获取全部远端内容，本地同步数据将重新核对。").assertIsDisplayed()
        val trigger = composeRule.onNodeWithTag("settings_account_full_resync_action")
        trigger.performClick()
        composeRule.onNodeWithTag("settings_account_full_resync_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("确认重试").performClick()
        composeRule.waitForIdle()
        assertEquals(1, confirmed)
        trigger.assertIsFocused()
    }

    @Test
    fun logoutRequiresConfirmation_andReturnsFocusToTrigger() {
        var confirmed = 0
        composeRule.setContent {
            OneMemosTheme {
                AccountManagementContent(
                    snapshot = snapshot(AccountSyncHealth.Healthy(lastSuccessAtEpochMs = 100L)),
                    onBack = {},
                    onChangePassword = { _, _, _ -> },
                    onConfirmLogout = { confirmed += 1 },
                )
            }
        }

        val trigger = composeRule.onNodeWithTag("settings_account_logout")
        trigger.performScrollTo()
        trigger.performClick()
        composeRule.onNodeWithTag("settings_account_logout_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("确认退出").performClick()
        composeRule.waitForIdle()
        assertEquals(1, confirmed)
        trigger.assertIsFocused()
    }

    @Test
    fun advancedSync_disablesDangerousActionWhileRunningCompletedOrInFlight() {
        var snapshot by
            mutableStateOf(
                snapshot(
                    AccountSyncHealth.FullResyncRunning(
                        FullResyncProgress(FullSyncStage.NORMAL, pagesFetched = 1, itemsFetched = 4),
                    ),
                ),
            )
        composeRule.setContent {
            OneMemosTheme {
                AdvancedSyncContent(
                    snapshot = snapshot,
                    onBack = {},
                    onConfirmFullResync = {},
                )
            }
        }
        composeRule.onNodeWithTag("settings_account_full_resync_action").assertIsNotEnabled()
        assertEquals(0, composeRule.onAllNodesWithText("取消").fetchSemanticsNodes().size)

        snapshot = snapshot(
            AccountSyncHealth.FullResyncCompleted(
                completionId = "run-200",
                completedAtEpochMs = 200L,
            ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("全量重同步已完成").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_account_full_resync_action").assertIsNotEnabled()

        snapshot =
            snapshot(
                AccountSyncHealth.Healthy(lastSuccessAtEpochMs = 100L),
                commandInFlight = AccountSyncSettingsCommand.FullResync,
            )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_account_full_resync_action").assertIsNotEnabled()
    }

    private fun setAccountRoot(snapshot: AccountSyncSettingsSnapshot) {
        composeRule.setContent {
            OneMemosTheme {
                AccountSyncContent(
                    snapshot = snapshot,
                    onBack = {},
                    onOpenLogin = {},
                    onSyncNow = {},
                    onOpenAccountManagement = {},
                    onOpenAdvancedSync = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun snapshot(
        health: AccountSyncHealth,
        commandInFlight: AccountSyncSettingsCommand? = null,
    ) =
        AccountSyncSettingsSnapshot(
            health = health,
            accountLabel = "已连接账号",
            lastSuccessAtEpochMs = 100L,
            commandInFlight = commandInFlight,
        )

    private data class MatrixCase(
        val health: AccountSyncHealth,
        val primaryText: String?,
        val enabled: Boolean,
        val advancedVisible: Boolean,
    )

    private enum class AccountSurface {
        ROOT,
        MANAGEMENT,
        ADVANCED,
    }
}
