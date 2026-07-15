package cc.pscly.onememos.update

import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.domain.update.UpdateDeliveryFailure
import cc.pscly.onememos.domain.update.UpdateDeliveryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交付契约特征测试：launcher 只转发 manager 动作，不持有第二份下载/安装状态。
 * 完整 Activity 启动路径由 Robolectric/设备门禁覆盖。
 */
class AppUpdateDeliveryLauncherTest {
    @Test
    fun deliveryActions_preserveUnknownSourcesAndInstallApkPayloads() {
        val open =
            UpdateDeliveryAction.OpenUnknownSourcesSettings(packageName = "cc.pscly.onememos")
        val install =
            UpdateDeliveryAction.InstallApk(
                uri = "content://cc.pscly.onememos.fileprovider/shared/update.apk",
            )

        assertEquals("cc.pscly.onememos", open.packageName)
        assertEquals(
            "content://cc.pscly.onememos.fileprovider/shared/update.apk",
            install.uri,
        )
        assertEquals("application/vnd.android.package-archive", install.mimeType)
    }

    @Test
    fun deliveryResults_coverPermissionInstallerAndFailurePaths() {
        val granted = UpdateDeliveryResult.UnknownSourcesPermissionChanged(granted = true)
        val denied = UpdateDeliveryResult.UnknownSourcesPermissionChanged(granted = false)
        val returned = UpdateDeliveryResult.InstallerReturned
        val missing = UpdateDeliveryResult.Failed(UpdateDeliveryFailure.ACTIVITY_NOT_FOUND)

        assertTrue(granted.granted)
        assertTrue(!denied.granted)
        assertEquals(UpdateDeliveryResult.InstallerReturned, returned)
        assertEquals(UpdateDeliveryFailure.ACTIVITY_NOT_FOUND, missing.reason)
    }

    @Test
    fun identityPort_adapterFieldsStayStableForUpdateManager() {
        val identity =
            object : AppIdentityPort {
                override val applicationId: String = "cc.pscly.onememos"
                override val versionName: String = "1.8.11"
                override val versionCode: Long = 156L
                override val fileProviderAuthority: String = "cc.pscly.onememos.fileprovider"
            }

        assertEquals("cc.pscly.onememos", identity.applicationId)
        assertEquals("1.8.11", identity.versionName)
        assertEquals(156L, identity.versionCode)
        assertEquals("cc.pscly.onememos.fileprovider", identity.fileProviderAuthority)
        assertNull(null)
    }
}
