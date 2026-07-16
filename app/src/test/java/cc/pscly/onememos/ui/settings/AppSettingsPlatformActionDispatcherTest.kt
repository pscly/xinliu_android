package cc.pscly.onememos.ui.settings

import android.Manifest
import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.domain.settings.SettingsPermission
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.overlay.QuickCaptureOverlayService
import cc.pscly.onememos.screenshot.ScreenshotQuickCaptureActivity
import cc.pscly.onememos.ui.feature.quickcapture.QuickCaptureActivity
import cc.pscly.onememos.ui.feature.settings.common.SettingsPlatformResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSettingsPlatformActionDispatcherTest {
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

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun calendarPermissions_mapLauncherResultToDomainResult() {
        var launchedPermissions: Array<String>? = null
        var result: SettingsPlatformResult? = null
        val dispatcher =
            dispatcher(
                requestPermissions = { launchedPermissions = it },
            )

        dispatcher.dispatch(
            SettingsPlatformAction.RequestPermissions(
                setOf(SettingsPermission.READ_CALENDAR, SettingsPermission.WRITE_CALENDAR),
            ),
        ) { result = it }

        assertEquals(
            setOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            launchedPermissions?.toSet(),
        )
        dispatcher.onPermissionsResult(
            mapOf(
                Manifest.permission.READ_CALENDAR to true,
                Manifest.permission.WRITE_CALENDAR to false,
            ),
        )
        assertEquals(
            SettingsPlatformResult.Permissions(
                granted = setOf(SettingsPermission.READ_CALENDAR),
                denied = setOf(SettingsPermission.WRITE_CALENDAR),
            ),
            result,
        )
    }

    @Test
    fun overlaySettings_usesPackageUriAndReportsCurrentGrant() {
        var launchedIntent: Intent? = null
        var result: SettingsPlatformResult? = null
        val dispatcher =
            dispatcher(
                openOverlayPermissionSettings = { launchedIntent = it },
                overlayPermissionGranted = { true },
            )

        dispatcher.dispatch(
            SettingsPlatformAction.OpenOverlayPermissionSettings("cc.pscly.onememos"),
        ) { result = it }

        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, launchedIntent?.action)
        assertEquals("package:cc.pscly.onememos", launchedIntent?.dataString)
        dispatcher.onOverlayPermissionResult()
        assertEquals(SettingsPlatformResult.OverlayPermissionChanged(granted = true), result)
    }

    @Test
    fun diagnosticsShare_usesReadGrantAndCompletes() {
        drainStartedComponents()
        var result: SettingsPlatformResult? = null
        val dispatcher = dispatcher()

        dispatcher.dispatch(
            SettingsPlatformAction.ShareFile(
                uri = "content://cc.pscly.onememos.fileprovider/diagnostics.json",
                mimeType = "application/json",
            ),
        ) { result = it }

        val chooser = shadowOf(context).nextStartedActivity
        val share = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals(Intent.ACTION_SEND, share?.action)
        assertEquals("application/json", share?.type)
        assertTrue(requireNotNull(share).flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(SettingsPlatformResult.Completed, result)
    }

    @Test
    fun quickCaptureTargets_startOnlyOwnedServiceAndActivities() {
        drainStartedComponents()
        val dispatcher = dispatcher()
        val results = mutableListOf<SettingsPlatformResult>()

        dispatcher.dispatch(SettingsPlatformAction.StartQuickCaptureOverlay, results::add)
        dispatcher.dispatch(SettingsPlatformAction.OpenQuickCapture, results::add)
        dispatcher.dispatch(SettingsPlatformAction.OpenScreenshotCapture, results::add)

        assertEquals(
            QuickCaptureOverlayService::class.java.name,
            shadowOf(context).nextStartedService.component?.className,
        )
        val startedActivities =
            buildList {
                while (true) {
                    val intent = shadowOf(context).nextStartedActivity ?: break
                    add(intent.component?.className)
                }
            }
        assertEquals(
            setOf(
                QuickCaptureActivity::class.java.name,
                ScreenshotQuickCaptureActivity::class.java.name,
            ),
            startedActivities.toSet(),
        )
        assertEquals(2, startedActivities.size)
        assertEquals(List(3) { SettingsPlatformResult.Completed }, results)
    }

    @Test
    fun source_excludesUnknownSourcesAndApkInstallation() {
        val source =
            projectDir
                .resolve(
                    "app/src/main/java/cc/pscly/onememos/ui/settings/AppSettingsPlatformActionDispatcher.kt",
                ).readText()

        assertFalse(source.contains("UpdateDeliveryAction"))
        assertFalse(source.contains("ACTION_MANAGE_UNKNOWN_APP_SOURCES"))
        assertFalse(source.contains("InstallApk"))
        assertFalse(source.contains("application/vnd.android.package-archive"))
    }

    private fun dispatcher(
        requestPermissions: (Array<String>) -> Unit = {},
        openOverlayPermissionSettings: (Intent) -> Unit = {},
        overlayPermissionGranted: () -> Boolean = { false },
    ): AppSettingsPlatformActionDispatcher =
        AppSettingsPlatformActionDispatcher(
            context = context,
            requestPermissions = requestPermissions,
            openOverlayPermissionSettings = openOverlayPermissionSettings,
            overlayPermissionGranted = overlayPermissionGranted,
        )

    private fun drainStartedComponents() {
        val shadow = shadowOf(context)
        while (shadow.nextStartedActivity != null) Unit
        while (shadow.nextStartedService != null) Unit
    }
}
