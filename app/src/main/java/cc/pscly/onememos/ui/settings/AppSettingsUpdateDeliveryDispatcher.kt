package cc.pscly.onememos.ui.settings

import android.app.Activity
import android.content.Intent
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.domain.update.UpdateDeliveryResult
import cc.pscly.onememos.ui.feature.settings.common.SettingsUpdateDeliveryDispatcher
import cc.pscly.onememos.update.AppUpdateDeliveryLauncher

class AppSettingsUpdateDeliveryDispatcher private constructor(
    private val deliver: (UpdateDeliveryAction, (UpdateDeliveryResult) -> Unit) -> Unit,
    private val launcher: AppUpdateDeliveryLauncher?,
    private val activityProvider: () -> Activity?,
) : SettingsUpdateDeliveryDispatcher {
    constructor(
        deliver: (UpdateDeliveryAction, (UpdateDeliveryResult) -> Unit) -> Unit,
    ) : this(
        deliver = deliver,
        launcher = null,
        activityProvider = { null },
    )

    constructor(
        launcher: AppUpdateDeliveryLauncher,
        activityProvider: () -> Activity?,
    ) : this(
        deliver = { action, onResult ->
            launcher.dispatch(action = action, activity = activityProvider(), onResult = onResult)
        },
        launcher = launcher,
        activityProvider = activityProvider,
    )

    override fun dispatch(
        action: UpdateDeliveryAction,
        onResult: (UpdateDeliveryResult) -> Unit,
    ) {
        deliver(action, onResult)
    }

    fun bindUnknownSourcesLauncher(activityLauncher: (Intent) -> Unit) {
        launcher?.bindUnknownSourcesLauncher(activityLauncher)
    }

    fun bindInstallerLauncher(activityLauncher: (Intent) -> Unit) {
        launcher?.bindInstallerLauncher(activityLauncher)
    }

    fun clearActivityLaunchers() {
        launcher?.clearActivityLaunchers()
    }

    fun onUnknownSourcesReturned() {
        launcher?.onUnknownSourcesReturned(activityProvider())
    }

    fun onInstallerReturned() {
        launcher?.onInstallerReturned(activityProvider())
    }
}
