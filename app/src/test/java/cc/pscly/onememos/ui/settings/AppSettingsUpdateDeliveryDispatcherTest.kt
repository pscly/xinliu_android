package cc.pscly.onememos.ui.settings

import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.domain.update.UpdateDeliveryResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class AppSettingsUpdateDeliveryDispatcherTest {
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

    @Test
    fun unknownSourcesActionAndPermissionResult_forwardThroughOneLauncherPath() {
        val action = UpdateDeliveryAction.OpenUnknownSourcesSettings("cc.pscly.onememos")
        val expected = UpdateDeliveryResult.UnknownSourcesPermissionChanged(granted = true)
        val forwarded = mutableListOf<UpdateDeliveryAction>()
        var result: UpdateDeliveryResult? = null
        val dispatcher =
            AppSettingsUpdateDeliveryDispatcher(
                deliver = { actual, onResult ->
                    forwarded += actual
                    onResult(expected)
                },
            )

        dispatcher.dispatch(action) { result = it }

        assertEquals(listOf(action), forwarded)
        assertEquals(expected, result)
    }

    @Test
    fun installActionAndInstallerResult_forwardThroughOneLauncherPath() {
        val action = UpdateDeliveryAction.InstallApk("content://updates/one-memos.apk")
        val forwarded = mutableListOf<UpdateDeliveryAction>()
        var result: UpdateDeliveryResult? = null
        val dispatcher =
            AppSettingsUpdateDeliveryDispatcher(
                deliver = { actual, onResult ->
                    forwarded += actual
                    onResult(UpdateDeliveryResult.InstallerReturned)
                },
            )

        dispatcher.dispatch(action) { result = it }

        assertEquals(listOf(action), forwarded)
        assertEquals(UpdateDeliveryResult.InstallerReturned, result)
    }

    @Test
    fun source_onlyDelegatesToAppUpdateDeliveryLauncher() {
        val source =
            projectDir
                .resolve(
                    "app/src/main/java/cc/pscly/onememos/ui/settings/AppSettingsUpdateDeliveryDispatcher.kt",
                ).readText()

        assertTrue(source.contains("AppUpdateDeliveryLauncher"))
        assertTrue(source.contains("launcher.dispatch"))
        assertFalse(source.contains("AppUpdateManager"))
        assertFalse(source.contains("Intent("))
        assertEquals(1, source.windowed("launcher.dispatch".length).count { it == "launcher.dispatch" })
    }
}
