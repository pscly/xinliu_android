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
class AppUpdateDeliveryLauncher private constructor(
    private val context: Context,
    private val requestDeliveryAction: () -> UpdateDeliveryAction?,
    private val consumeDeliveryResult: (UpdateDeliveryResult) -> UpdateDeliveryAction?,
    private val canRequestPackageInstalls: (Activity?) -> Boolean,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        manager: AppUpdateManager,
    ) : this(
        context = context,
        requestDeliveryAction = manager::requestDelivery,
        consumeDeliveryResult = manager::onDeliveryResult,
        canRequestPackageInstalls = { activity ->
            (activity?.packageManager ?: context.packageManager).canRequestPackageInstalls()
        },
    )

    internal constructor(
        context: Context,
        requestDeliveryAction: () -> UpdateDeliveryAction?,
        consumeDeliveryResult: (UpdateDeliveryResult) -> UpdateDeliveryAction?,
        canRequestPackageInstalls: () -> Boolean,
    ) : this(
        context = context,
        requestDeliveryAction = requestDeliveryAction,
        consumeDeliveryResult = consumeDeliveryResult,
        canRequestPackageInstalls = { _: Activity? -> canRequestPackageInstalls() },
    )

    private var pendingExternalAction: PendingExternalAction? = null
    private var pendingResultCallback: ((UpdateDeliveryResult) -> Unit)? = null
    private var unknownSourcesLauncher: ((Intent) -> Unit)? = null
    private var installerLauncher: ((Intent) -> Unit)? = null

    fun requestInstall(activity: Activity?) {
        pendingResultCallback = null
        execute(requestDeliveryAction(), activity)
    }

    fun dispatch(
        action: UpdateDeliveryAction,
        activity: Activity?,
        onResult: (UpdateDeliveryResult) -> Unit = {},
    ) {
        pendingResultCallback = onResult
        execute(action, activity)
    }

    fun bindUnknownSourcesLauncher(launcher: (Intent) -> Unit) {
        unknownSourcesLauncher = launcher
    }

    fun bindInstallerLauncher(launcher: (Intent) -> Unit) {
        installerLauncher = launcher
    }

    fun clearActivityLaunchers() {
        unknownSourcesLauncher = null
        installerLauncher = null
    }

    fun onHostResumed(activity: Activity?) {
        onUnknownSourcesReturned(activity)
    }

    fun onUnknownSourcesReturned(activity: Activity?) {
        if (pendingExternalAction != PendingExternalAction.UNKNOWN_SOURCES) return
        complete(
            result =
                UpdateDeliveryResult.UnknownSourcesPermissionChanged(
                    granted = canRequestPackageInstalls(activity),
                ),
            activity = activity,
        )
    }

    fun onInstallerReturned(activity: Activity? = null) {
        if (pendingExternalAction != PendingExternalAction.INSTALLER) return
        complete(UpdateDeliveryResult.InstallerReturned, activity)
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
                start(
                    intent = intent,
                    activity = activity,
                    externalAction = PendingExternalAction.UNKNOWN_SOURCES,
                    activityLauncher = unknownSourcesLauncher,
                )
            }
            is UpdateDeliveryAction.InstallApk -> {
                val intent =
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.parse(action.uri), action.mimeType)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                start(
                    intent = intent,
                    activity = activity,
                    externalAction = PendingExternalAction.INSTALLER,
                    activityLauncher = installerLauncher,
                )
            }
            null -> Unit
        }
    }

    private fun start(
        intent: Intent,
        activity: Activity?,
        externalAction: PendingExternalAction,
        activityLauncher: ((Intent) -> Unit)?,
    ) {
        pendingExternalAction = externalAction
        try {
            if (activityLauncher != null) {
                activityLauncher(intent)
            } else if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (_: ActivityNotFoundException) {
            complete(
                UpdateDeliveryResult.Failed(UpdateDeliveryFailure.ACTIVITY_NOT_FOUND),
                activity,
            )
        } catch (_: Exception) {
            complete(
                UpdateDeliveryResult.Failed(UpdateDeliveryFailure.PLATFORM_FAILURE),
                activity,
            )
        }
    }

    private fun complete(
        result: UpdateDeliveryResult,
        activity: Activity?,
    ) {
        pendingExternalAction = null
        val callback = pendingResultCallback
        pendingResultCallback = null
        val nextAction = consumeDeliveryResult(result)
        if (nextAction != null) {
            execute(nextAction, activity)
        }
        callback?.invoke(result)
    }

    private enum class PendingExternalAction {
        UNKNOWN_SOURCES,
        INSTALLER,
    }
}
