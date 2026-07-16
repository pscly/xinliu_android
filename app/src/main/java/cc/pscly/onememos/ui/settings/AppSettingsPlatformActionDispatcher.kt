package cc.pscly.onememos.ui.settings

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPermission
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.overlay.QuickCaptureOverlayService
import cc.pscly.onememos.screenshot.ScreenshotQuickCaptureActivity
import cc.pscly.onememos.ui.feature.quickcapture.QuickCaptureActivity
import cc.pscly.onememos.ui.feature.settings.common.SettingsPlatformActionDispatcher
import cc.pscly.onememos.ui.feature.settings.common.SettingsPlatformResult

class AppSettingsPlatformActionDispatcher(
    private val context: Context,
    private val requestPermissions: (Array<String>) -> Unit = {},
    private val openOverlayPermissionSettings: (Intent) -> Unit = { intent ->
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    },
    private val overlayPermissionGranted: () -> Boolean = { Settings.canDrawOverlays(context) },
    private val activityProvider: () -> Activity? = { null },
) : SettingsPlatformActionDispatcher {
    private var pendingPermissionResult: ((SettingsPlatformResult) -> Unit)? = null
    private var pendingRequestedPermissions: Set<SettingsPermission> = emptySet()
    private var pendingOverlayResult: ((SettingsPlatformResult) -> Unit)? = null
    private var permissionLauncherOverride: ((Array<String>) -> Unit)? = null
    private var overlayLauncherOverride: ((Intent) -> Unit)? = null

    fun bindPermissionLauncher(launcher: (Array<String>) -> Unit) {
        permissionLauncherOverride = launcher
    }

    fun bindOverlayPermissionLauncher(launcher: (Intent) -> Unit) {
        overlayLauncherOverride = launcher
    }

    fun clearActivityLaunchers() {
        permissionLauncherOverride = null
        overlayLauncherOverride = null
    }

    fun onPermissionsResult(grantMap: Map<String, Boolean>) {
        val callback = pendingPermissionResult ?: return
        pendingPermissionResult = null
        val requested = pendingRequestedPermissions
        pendingRequestedPermissions = emptySet()
        val granted = linkedSetOf<SettingsPermission>()
        val denied = linkedSetOf<SettingsPermission>()
        for (permission in requested) {
            if (grantMap[permission.toManifestName()] == true) {
                granted += permission
            } else {
                denied += permission
            }
        }
        callback(SettingsPlatformResult.Permissions(granted = granted, denied = denied))
    }

    fun onPermissionResult(grantMap: Map<String, Boolean>) = onPermissionsResult(grantMap)

    fun onOverlayPermissionResult() {
        val callback = pendingOverlayResult ?: return
        pendingOverlayResult = null
        callback(SettingsPlatformResult.OverlayPermissionChanged(granted = overlayPermissionGranted()))
    }

    fun onHostResumed() = onOverlayPermissionResult()

    override fun dispatch(
        action: SettingsPlatformAction,
        onResult: (SettingsPlatformResult) -> Unit,
    ) {
        when (action) {
            is SettingsPlatformAction.RequestPermissions -> {
                val names = action.permissions.map { it.toManifestName() }.toTypedArray()
                if (names.isEmpty()) {
                    onResult(SettingsPlatformResult.Permissions(emptySet(), emptySet()))
                    return
                }
                pendingRequestedPermissions = action.permissions
                pendingPermissionResult = onResult
                val launcher = permissionLauncherOverride ?: requestPermissions
                launcher(names)
            }
            is SettingsPlatformAction.OpenOverlayPermissionSettings -> {
                pendingOverlayResult = onResult
                val intent =
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${action.packageName}"),
                    )
                runCatching {
                    val launcher = overlayLauncherOverride ?: openOverlayPermissionSettings
                    launcher(intent)
                }
                    .onFailure {
                        pendingOverlayResult = null
                        onResult(SettingsPlatformResult.Failed(SettingsCapabilityError.PlatformUnavailable))
                    }
            }
            is SettingsPlatformAction.ShareFile -> {
                val shareIntent =
                    Intent(Intent.ACTION_SEND)
                        .setType(action.mimeType)
                        .putExtra(Intent.EXTRA_STREAM, Uri.parse(action.uri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val chooser = Intent.createChooser(shareIntent, "分享诊断文件")
                if (startActivity(chooser)) {
                    onResult(SettingsPlatformResult.Completed)
                } else {
                    onResult(SettingsPlatformResult.Failed(SettingsCapabilityError.PlatformUnavailable))
                }
            }
            SettingsPlatformAction.OpenQuickCapture -> {
                val intent =
                    Intent(context, QuickCaptureActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (startActivity(intent)) {
                    onResult(SettingsPlatformResult.Completed)
                } else {
                    onResult(SettingsPlatformResult.Failed(SettingsCapabilityError.PlatformUnavailable))
                }
            }
            SettingsPlatformAction.StartQuickCaptureOverlay -> {
                runCatching {
                    context.startService(Intent(context, QuickCaptureOverlayService::class.java))
                    onResult(SettingsPlatformResult.Completed)
                }.getOrElse {
                    onResult(SettingsPlatformResult.Failed(SettingsCapabilityError.PlatformUnavailable))
                }
            }
            SettingsPlatformAction.OpenScreenshotCapture -> {
                val intent =
                    Intent(context, ScreenshotQuickCaptureActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (startActivity(intent)) {
                    onResult(SettingsPlatformResult.Completed)
                } else {
                    onResult(SettingsPlatformResult.Failed(SettingsCapabilityError.PlatformUnavailable))
                }
            }
        }
    }

    private fun startActivity(intent: Intent): Boolean {
        return try {
            val activity = activityProvider()
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun SettingsPermission.toManifestName(): String =
        when (this) {
            SettingsPermission.READ_CALENDAR -> Manifest.permission.READ_CALENDAR
            SettingsPermission.WRITE_CALENDAR -> Manifest.permission.WRITE_CALENDAR
        }
}
