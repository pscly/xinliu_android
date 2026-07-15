package cc.pscly.onememos.ui.feature.settings.storage

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.domain.model.CacheStats
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsSnapshot
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
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
class StorageOfflineScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun storagePage_showsFourUsageItemsAndExplanation_withoutAutomaticRefresh() {
        val actions = mutableListOf<StorageOfflineUiAction>()
        setPage(onAction = actions::add)

        listOf(
            "本地内容（数据库） 1.0 KB",
            "图片缓存 2.0 KB",
            "附件本地缓存 3.0 KB",
            "其它缓存 4.0 KB",
        ).forEach { text ->
            composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
        composeRule
            .onNodeWithText("缓存只会随浏览逐步写入；清理缓存不会删除服务器数据。")
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(actions.isEmpty())

        composeRule.onNodeWithTag("settings_storage_refresh").performScrollTo().performClick()
        assertEquals(listOf(StorageOfflineUiAction.RefreshStats), actions)
    }

    @Test
    fun storagePage_exposesPrefetchRangesAndAttachmentLimit() {
        val actions = mutableListOf<StorageOfflineUiAction>()
        setPage(onAction = actions::add)

        composeRule.onNodeWithText("自动预取图片").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("范围：最近 12 条随笔").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("上限：最多 36 张图片").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("附件缓存上限：256 MB").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("settings_storage_prefetch_switch").performScrollTo().performClick()
        composeRule
            .onNodeWithTag("settings_storage_memo_limit")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(25f) }
        composeRule
            .onNodeWithTag("settings_storage_image_limit")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(50f) }
        composeRule
            .onNodeWithTag("settings_storage_attachment_limit")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(512f) }

        assertTrue(actions.contains(StorageOfflineUiAction.SetImagePrefetchEnabled(false)))
        assertTrue(actions.contains(StorageOfflineUiAction.SetPrefetchMemoLimit(25)))
        assertTrue(actions.contains(StorageOfflineUiAction.SetPrefetchImageLimit(50)))
        assertTrue(actions.contains(StorageOfflineUiAction.SetAttachmentCacheLimitMb(512)))
    }

    @Test
    fun threeCleanupActions_requireConfirmationAndHaveMinHeight48() {
        var confirmation by mutableStateOf<SettingsConfirmation?>(null)
        val confirmed = mutableListOf<StorageOfflineUiAction>()
        composeRule.setContent {
            OneMemosTheme {
                StorageOfflineContent(
                    uiState = readyState,
                    confirmation = confirmation,
                    onAction = { action ->
                        confirmation = action.requestedConfirmation() ?: confirmation
                        if (action.isConfirmedCleanup()) {
                            confirmed += action
                            confirmation = null
                        }
                    },
                    onDismissConfirmation = { confirmation = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val cases =
            listOf(
                CleanupCase(
                    tag = "settings_storage_clear_images",
                    title = "清理图片缓存？",
                    detail = "这会删除本地图片缓存；再次查看时可能需要重新下载。",
                    confirmedAction = StorageOfflineUiAction.ConfirmClearImageCache,
                ),
                CleanupCase(
                    tag = "settings_storage_clear_attachments",
                    title = "清理附件缓存？",
                    detail = "这会删除离线附件缓存，不会删除服务器数据。",
                    confirmedAction = StorageOfflineUiAction.ConfirmClearAttachmentCache,
                ),
                CleanupCase(
                    tag = "settings_storage_clear_all",
                    title = "清理全部缓存？",
                    detail = "这会清理图片与附件缓存，不会删除数据库、设置或服务器数据。",
                    confirmedAction = StorageOfflineUiAction.ConfirmClearAllCache,
                ),
            )

        cases.forEach { case ->
            composeRule
                .onNodeWithTag(case.tag)
                .performScrollTo()
                .assertHeightIsAtLeast(48.dp)
                .performClick()
            composeRule.onNodeWithText(case.title).assertIsDisplayed()
            composeRule.onNodeWithText(case.detail).assertIsDisplayed()
            composeRule.onNodeWithTag("settings_storage_confirm_cleanup").performClick()
        }
        assertEquals(cases.map(CleanupCase::confirmedAction), confirmed)
    }

    @Test
    fun loadingErrorAndCleanupDisabled_haveTextAndSemantics() {
        var state by mutableStateOf(StorageOfflineUiState())
        composeRule.setContent {
            OneMemosTheme {
                StorageOfflineContent(
                    uiState = state,
                    confirmation = null,
                    onAction = {},
                    onDismissConfirmation = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeRule.onNodeWithText("正在读取存储设置").assertIsDisplayed()
        composeRule
            .onNodeWithTag("settings_storage_root")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "加载中"))

        state =
            readyState.copy(
                persistentError = SettingsCapabilityError.StorageFailure,
                cleanupSubmitting = true,
            )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("存储操作失败，已保留上次统计。", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("正在清理缓存，请稍候").performScrollTo().assertIsDisplayed()
        listOf(
            "settings_storage_clear_images",
            "settings_storage_clear_attachments",
            "settings_storage_clear_all",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled()
        }
    }

    @Test
    fun largeFont_keepsValuesUnitsAndConfirmationDetailVisible() {
        var confirmation by mutableStateOf<SettingsConfirmation?>(null)
        var refreshCount = 0
        composeRule.setContent {
            OneMemosTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                    StorageOfflineContent(
                        uiState = readyState,
                        confirmation = confirmation,
                        onAction = { action ->
                            if (action == StorageOfflineUiAction.RefreshStats) refreshCount++
                        },
                        onDismissConfirmation = { confirmation = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithText("附件缓存上限：256 MB").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("上限：最多 36 张图片").performScrollTo().assertIsDisplayed()
        assertEquals(0, refreshCount)

        confirmation = SettingsConfirmation.CLEAR_ALL_CACHE
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText("这会清理图片与附件缓存，不会删除数据库、设置或服务器数据。")
            .assertIsDisplayed()
    }

    private fun setPage(onAction: (StorageOfflineUiAction) -> Unit) {
        composeRule.setContent {
            OneMemosTheme {
                StorageOfflineContent(
                    uiState = readyState,
                    confirmation = null,
                    onAction = onAction,
                    onDismissConfirmation = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private data class CleanupCase(
        val tag: String,
        val title: String,
        val detail: String,
        val confirmedAction: StorageOfflineUiAction,
    )

    private companion object {
        val readyState =
            StorageOfflineUiState(
                loading = false,
                snapshot =
                    StorageOfflineSettingsSnapshot(
                        imagePrefetchEnabled = true,
                        prefetchMemoLimit = 12,
                        prefetchImageLimit = 36,
                        attachmentCacheLimitMb = 256,
                        cacheStats =
                            CacheStats(
                                databaseBytes = 1_024,
                                imageCacheBytes = 2_048,
                                attachmentCacheBytes = 3_072,
                                otherCacheBytes = 4_096,
                            ),
                    ),
            )

        fun StorageOfflineUiAction.requestedConfirmation(): SettingsConfirmation? =
            when (this) {
                StorageOfflineUiAction.RequestClearImageCache -> SettingsConfirmation.CLEAR_IMAGE_CACHE
                StorageOfflineUiAction.RequestClearAttachmentCache ->
                    SettingsConfirmation.CLEAR_ATTACHMENT_CACHE
                StorageOfflineUiAction.RequestClearAllCache -> SettingsConfirmation.CLEAR_ALL_CACHE
                else -> null
            }

        fun StorageOfflineUiAction.isConfirmedCleanup(): Boolean =
            this == StorageOfflineUiAction.ConfirmClearImageCache ||
                this == StorageOfflineUiAction.ConfirmClearAttachmentCache ||
                this == StorageOfflineUiAction.ConfirmClearAllCache
    }
}
