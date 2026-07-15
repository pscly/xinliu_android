package cc.pscly.onememos.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.domain.update.UpdateDeliveryFailure
import cc.pscly.onememos.domain.update.UpdateDeliveryResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * app 唯一更新交付入口：执行未知来源设置/安装器 Intent，并把结果回送唯一 [AppUpdateManager]。
 * 本类不保存下载、校验或安装状态。
 */
@Singleton
class AppUpdateDeliveryLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: AppUpdateManager,
) {
    fun requestInstall(activity: Activity?) {
        execute(manager.requestDelivery(), activity)
    }

    fun onHostResumed(activity: Activity?) {
        val packageManager = activity?.packageManager ?: context.packageManager
        val granted = packageManager.canRequestPackageInstalls()
        execute(
            manager.onDeliveryResult(UpdateDeliveryResult.UnknownSourcesPermissionChanged(granted)),
            activity,
        )
    }

    fun onInstallerReturned() {
        manager.onDeliveryResult(UpdateDeliveryResult.InstallerReturned)
    }

    private fun execute(
        action: UpdateDeliveryAction?,
        activity: Activity?,
    ) {
        when (action) {
            is UpdateDeliveryAction.OpenUnknownSourcesSettings -> {
                val intent =
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${action.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                start(intent, activity)
            }
            is UpdateDeliveryAction.InstallApk -> {
                val intent =
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.parse(action.uri), action.mimeType)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val started = start(intent, activity)
                if (started) {
                    manager.onDeliveryResult(UpdateDeliveryResult.InstallerReturned)
                }
            }
            null -> Unit
        }
    }

    private fun start(
        intent: Intent,
        activity: Activity?,
    ): Boolean {
        return try {
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            true
        } catch (_: ActivityNotFoundException) {
            manager.onDeliveryResult(UpdateDeliveryResult.Failed(UpdateDeliveryFailure.ACTIVITY_NOT_FOUND))
            false
        } catch (_: Exception) {
            manager.onDeliveryResult(UpdateDeliveryResult.Failed(UpdateDeliveryFailure.PLATFORM_FAILURE))
            false
        }
    }
}
