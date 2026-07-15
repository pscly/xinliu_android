package cc.pscly.onememos.ui.feature.settings.common

import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPermission
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.domain.update.UpdateDeliveryResult
import cc.pscly.onememos.navigation.AccountSyncSettingsKey
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共享事件与双 dispatcher 契约：
 * 平台只收 SettingsPlatformAction；更新只收 UpdateDeliveryAction；
 * callback 回传结果，不持有页面状态持有者 / 更新管理器 / 宿主页面。
 */
class SettingsUiContractsTest {
    @Test
    fun settingsUiEvent_variants_coverNavigateToastConfirmPlatformUpdate() {
        val navigate = SettingsUiEvent.Navigate(AccountSyncSettingsKey)
        val toast = SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED)
        val confirm = SettingsUiEvent.Confirm(SettingsConfirmation.LOGOUT)
        val platform =
            SettingsUiEvent.Platform(
                SettingsPlatformAction.RequestPermissions(
                    setOf(SettingsPermission.READ_CALENDAR),
                ),
            )
        val update =
            SettingsUiEvent.UpdateDelivery(
                UpdateDeliveryAction.OpenUnknownSourcesSettings("cc.pscly.onememos"),
            )
        assertEquals(AccountSyncSettingsKey, navigate.key)
        assertEquals(SettingsMessage.COMMAND_SUCCEEDED, toast.message)
        assertEquals(SettingsConfirmation.LOGOUT, confirm.request)
        assertTrue(platform.action is SettingsPlatformAction.RequestPermissions)
        assertTrue(update.action is UpdateDeliveryAction.OpenUnknownSourcesSettings)
    }

    @Test
    fun platformDispatcher_acceptsOnlyPlatformAction_andCallbacksResult() {
        val received = AtomicReference<SettingsPlatformAction?>(null)
        val result = AtomicReference<SettingsPlatformResult?>(null)
        val dispatcher =
            object : SettingsPlatformActionDispatcher {
                override fun dispatch(
                    action: SettingsPlatformAction,
                    onResult: (SettingsPlatformResult) -> Unit,
                ) {
                    received.set(action)
                    onResult(
                        SettingsPlatformResult.Permissions(
                            granted = setOf(SettingsPermission.READ_CALENDAR),
                            denied = emptySet(),
                        ),
                    )
                }
            }
        val action =
            SettingsPlatformAction.RequestPermissions(
                setOf(SettingsPermission.READ_CALENDAR, SettingsPermission.WRITE_CALENDAR),
            )
        dispatcher.dispatch(action) { result.set(it) }
        assertEquals(action, received.get())
        val permissions = result.get() as SettingsPlatformResult.Permissions
        assertEquals(setOf(SettingsPermission.READ_CALENDAR), permissions.granted)
    }

    @Test
    fun updateDispatcher_acceptsOnlyUpdateDeliveryAction_andCallbacksResult() {
        val received = AtomicReference<UpdateDeliveryAction?>(null)
        val result = AtomicReference<UpdateDeliveryResult?>(null)
        val dispatcher =
            object : SettingsUpdateDeliveryDispatcher {
                override fun dispatch(
                    action: UpdateDeliveryAction,
                    onResult: (UpdateDeliveryResult) -> Unit,
                ) {
                    received.set(action)
                    onResult(UpdateDeliveryResult.InstallerReturned)
                }
            }
        val action = UpdateDeliveryAction.InstallApk(uri = "content://apk")
        dispatcher.dispatch(action) { result.set(it) }
        assertEquals(action, received.get())
        assertEquals(UpdateDeliveryResult.InstallerReturned, result.get())
    }

    @Test
    fun platformResult_variants_includeCompletedOverlayFailed() {
        assertNotNull(SettingsPlatformResult.Completed)
        val overlay = SettingsPlatformResult.OverlayPermissionChanged(true)
        assertTrue(overlay.granted)
        val failed = SettingsPlatformResult.Failed(SettingsCapabilityError.PlatformUnavailable)
        assertEquals(SettingsCapabilityError.PlatformUnavailable, failed.error)
    }

    @Test
    fun compositionLocals_exist_andDispatchers_haveNoActivityOrManagerInSignatures() {
        assertNotNull(LocalSettingsPlatformActionDispatcher)
        assertNotNull(LocalSettingsUpdateDeliveryDispatcher)
        val projectDir =
            System.getProperty("oneMemos.projectDir")
                ?: error("oneMemos.projectDir 未设置")
        val platformFile =
            File(
                projectDir,
                "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/common/SettingsPlatformActionDispatcher.kt",
            )
        val updateFile =
            File(
                projectDir,
                "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/common/SettingsUpdateDeliveryDispatcher.kt",
            )
        assertTrue(platformFile.exists())
        assertTrue(updateFile.exists())
        val platformBody = stripComments(platformFile.readText())
        val updateBody = stripComments(updateFile.readText())
        assertFalse(platformBody.contains("android.app.Activity"))
        assertFalse(platformBody.contains("AppUpdateManager"))
        assertFalse(platformBody.contains("ViewModel"))
        assertFalse(updateBody.contains("android.app.Activity"))
        assertFalse(updateBody.contains("AppUpdateManager"))
        assertFalse(updateBody.contains("ViewModel"))
        assertTrue(platformBody.contains("SettingsPlatformAction"))
        assertTrue(platformBody.contains("SettingsPlatformResult"))
        assertTrue(updateBody.contains("UpdateDeliveryAction"))
        assertTrue(updateBody.contains("UpdateDeliveryResult"))
    }

    private fun stripComments(source: String): String {
        val noBlock = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock
            .lineSequence()
            .map { line -> line.substringBefore("//") }
            .joinToString("\n")
    }
}
