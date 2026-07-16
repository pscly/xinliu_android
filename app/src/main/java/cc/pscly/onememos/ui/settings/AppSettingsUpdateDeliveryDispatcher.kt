package cc.pscly.onememos.ui.settings

import android.app.Activity
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.domain.update.UpdateDeliveryResult
import cc.pscly.onememos.ui.feature.settings.common.SettingsUpdateDeliveryDispatcher
import cc.pscly.onememos.update.AppUpdateDeliveryLauncher

class AppSettingsUpdateDeliveryDispatcher(
    private val deliver: (UpdateDeliveryAction, (UpdateDeliveryResult) -> Unit) -> Unit,
) : SettingsUpdateDeliveryDispatcher {
    constructor(
        launcher: AppUpdateDeliveryLauncher,
        activityProvider: () -> Activity?,
    ) : this(
        deliver = { action, onResult ->
            launcher.dispatch(action = action, activity = activityProvider(), onResult = onResult)
        },
    )

    override fun dispatch(
        action: UpdateDeliveryAction,
        onResult: (UpdateDeliveryResult) -> Unit,
    ) {
        deliver(action, onResult)
    }
}
