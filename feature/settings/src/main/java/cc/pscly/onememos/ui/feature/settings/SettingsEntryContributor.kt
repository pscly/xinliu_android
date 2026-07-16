package cc.pscly.onememos.ui.feature.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavEntry
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.domain.update.UpdateDeliveryResult
import cc.pscly.onememos.navigation.AboutAdvancedSettingsKey
import cc.pscly.onememos.navigation.AccountManagementSettingsKey
import cc.pscly.onememos.navigation.AccountSyncSettingsKey
import cc.pscly.onememos.navigation.AdvancedSyncSettingsKey
import cc.pscly.onememos.navigation.AppearanceInteractionSettingsKey
import cc.pscly.onememos.navigation.FeatureEntryContributor
import cc.pscly.onememos.navigation.FeatureEntryHost
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.OneMemosNavigator
import cc.pscly.onememos.navigation.RecordEditingSettingsKey
import cc.pscly.onememos.navigation.ReminderCalendarSettingsKey
import cc.pscly.onememos.navigation.SettingsHubKey
import cc.pscly.onememos.navigation.StorageOfflineSettingsKey
import cc.pscly.onememos.ui.feature.settings.about.AboutAdvancedScreen
import cc.pscly.onememos.ui.feature.settings.about.AboutAdvancedViewModel
import cc.pscly.onememos.ui.feature.settings.account.AccountManagementScreen
import cc.pscly.onememos.ui.feature.settings.account.AccountSyncScreen
import cc.pscly.onememos.ui.feature.settings.account.AccountSyncViewModel
import cc.pscly.onememos.ui.feature.settings.account.AdvancedSyncScreen
import cc.pscly.onememos.ui.feature.settings.appearance.AppearanceInteractionScreen
import cc.pscly.onememos.ui.feature.settings.appearance.AppearanceInteractionUserIntent
import cc.pscly.onememos.ui.feature.settings.appearance.AppearanceInteractionViewModel
import cc.pscly.onememos.ui.feature.settings.common.LocalSettingsPlatformActionDispatcher
import cc.pscly.onememos.ui.feature.settings.common.LocalSettingsUpdateDeliveryDispatcher
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsPlatformResult
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import cc.pscly.onememos.ui.feature.settings.hub.SettingsHubScreen
import cc.pscly.onememos.ui.feature.settings.hub.SettingsHubViewModel
import cc.pscly.onememos.ui.feature.settings.record.RecordEditingScreen
import cc.pscly.onememos.ui.feature.settings.record.RecordEditingViewModel
import cc.pscly.onememos.ui.feature.settings.reminder.ReminderCalendarScreen
import cc.pscly.onememos.ui.feature.settings.reminder.ReminderCalendarUserIntent
import cc.pscly.onememos.ui.feature.settings.reminder.ReminderCalendarViewModel
import cc.pscly.onememos.ui.feature.settings.storage.StorageOfflineScreen
import cc.pscly.onememos.ui.feature.settings.storage.StorageOfflineViewModel
import kotlinx.coroutines.flow.SharedFlow

data class SettingsEventCallbacks(
    val onNavigate: (OneMemosNavKey) -> Unit = {},
    val onToast: (SettingsMessage) -> Unit = {},
    val onConfirm: (SettingsConfirmation) -> Unit = {},
    val onPlatform: (SettingsPlatformAction) -> Unit = {},
    val onUpdateDelivery: (UpdateDeliveryAction) -> Unit = {},
)

fun dispatchSettingsEvent(
    event: SettingsUiEvent,
    callbacks: SettingsEventCallbacks,
) {
    when (event) {
        is SettingsUiEvent.Navigate -> callbacks.onNavigate(event.key)
        is SettingsUiEvent.Toast -> callbacks.onToast(event.message)
        is SettingsUiEvent.Confirm -> callbacks.onConfirm(event.request)
        is SettingsUiEvent.Platform -> callbacks.onPlatform(event.action)
        is SettingsUiEvent.UpdateDelivery -> callbacks.onUpdateDelivery(event.action)
    }
}

object SettingsEntryContributor : FeatureEntryContributor {
    private val ownedKeys: Set<OneMemosNavKey> =
        setOf(
            SettingsHubKey,
            AccountSyncSettingsKey,
            AccountManagementSettingsKey,
            AdvancedSyncSettingsKey,
            RecordEditingSettingsKey,
            ReminderCalendarSettingsKey,
            StorageOfflineSettingsKey,
            AppearanceInteractionSettingsKey,
            AboutAdvancedSettingsKey,
        )

    override fun owns(key: OneMemosNavKey): Boolean = key in ownedKeys

    override fun entry(
        key: OneMemosNavKey,
        navigator: OneMemosNavigator,
        host: FeatureEntryHost,
    ): NavEntry<OneMemosNavKey> {
        require(owns(key)) { "SettingsEntryContributor does not own $key" }
        return when (key) {
            SettingsHubKey ->
                NavEntry(key) {
                    val viewModel = hiltViewModel<SettingsHubViewModel>()
                    SettingsHubScreen(
                        onOpen = { target -> navigator.push(target) },
                        viewModel = viewModel,
                    )
                }
            AccountSyncSettingsKey ->
                NavEntry(key) {
                    val viewModel = hiltViewModel<AccountSyncViewModel>()
                    CollectSettingsOneShotEvents(viewModel.events, navigator)
                    AccountSyncScreen(onBack = { navigator.back() }, viewModel = viewModel)
                }
            AccountManagementSettingsKey ->
                NavEntry(key) {
                    val viewModel = hiltViewModel<AccountSyncViewModel>()
                    var confirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }
                    CollectSettingsOneShotEvents(
                        events = viewModel.events,
                        navigator = navigator,
                        onConfirm = { confirmation = it },
                    )
                    AccountManagementScreen(
                        onBack = { navigator.back() },
                        confirmation = confirmation,
                        onDismissConfirmation = { confirmation = null },
                        viewModel = viewModel,
                    )
                }
            AdvancedSyncSettingsKey ->
                NavEntry(key) {
                    val viewModel = hiltViewModel<AccountSyncViewModel>()
                    var confirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }
                    CollectSettingsOneShotEvents(
                        events = viewModel.events,
                        navigator = navigator,
                        onConfirm = { confirmation = it },
                    )
                    AdvancedSyncScreen(
                        onBack = { navigator.back() },
                        confirmation = confirmation,
                        onDismissConfirmation = { confirmation = null },
                        viewModel = viewModel,
                    )
                }
            RecordEditingSettingsKey ->
                NavEntry(key) {
                    val viewModel = hiltViewModel<RecordEditingViewModel>()
                    CollectSettingsOneShotEvents(viewModel.events, navigator)
                    RecordEditingScreen(onBack = { navigator.back() }, viewModel = viewModel)
                }
            ReminderCalendarSettingsKey ->
                NavEntry(key) {
                    val viewModel = hiltViewModel<ReminderCalendarViewModel>()
                    CollectSettingsOneShotEvents(
                        viewModel.events,
                        navigator,
                        onPlatformResult = {
                            viewModel.onIntent(ReminderCalendarUserIntent.ApplyPlatformResult(it))
                        },
                    )
                    ReminderCalendarScreen(viewModel = viewModel)
                }
            StorageOfflineSettingsKey ->
                NavEntry(key) {
                    val viewModel = hiltViewModel<StorageOfflineViewModel>()
                    var confirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }
                    CollectSettingsOneShotEvents(
                        events = viewModel.events,
                        navigator = navigator,
                        onConfirm = { confirmation = it },
                    )
                    StorageOfflineScreen(
                        confirmation = confirmation,
                        onDismissConfirmation = { confirmation = null },
                        viewModel = viewModel,
                    )
                }
            AppearanceInteractionSettingsKey ->
                NavEntry(key) {
                    val viewModel = hiltViewModel<AppearanceInteractionViewModel>()
                    CollectSettingsOneShotEvents(
                        viewModel.events,
                        navigator,
                        onPlatformResult = {
                            viewModel.onIntent(
                                AppearanceInteractionUserIntent.ApplyPlatformResult(it),
                            )
                        },
                    )
                    AppearanceInteractionScreen(viewModel = viewModel)
                }
            AboutAdvancedSettingsKey ->
                NavEntry(key) {
                    val viewModel = hiltViewModel<AboutAdvancedViewModel>()
                    var confirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }
                    CollectSettingsOneShotEvents(
                        events = viewModel.events,
                        navigator = navigator,
                        onConfirm = { confirmation = it },
                    )
                    AboutAdvancedScreen(
                        confirmation = confirmation,
                        onDismissConfirmation = { confirmation = null },
                        viewModel = viewModel,
                    )
                }
            else -> error("SettingsEntryContributor does not own $key")
        }
    }
}

@Composable
private fun CollectSettingsOneShotEvents(
    events: SharedFlow<SettingsUiEvent>,
    navigator: OneMemosNavigator,
    onConfirm: (SettingsConfirmation) -> Unit = {},
    onPlatformResult: ((SettingsPlatformResult) -> Unit)? = null,
    onUpdateDeliveryResult: (UpdateDeliveryResult) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val platformDispatcher = LocalSettingsPlatformActionDispatcher.current
    val updateDispatcher = LocalSettingsUpdateDeliveryDispatcher.current
    val callbacks =
        remember(
            navigator,
            onConfirm,
            onPlatformResult,
            onUpdateDeliveryResult,
            platformDispatcher,
            updateDispatcher,
            context,
        ) {
            SettingsEventCallbacks(
                onNavigate = { key -> navigator.push(key) },
                onToast = { message ->
                    Toast.makeText(context, toastText(message), Toast.LENGTH_SHORT).show()
                },
                onConfirm = onConfirm,
                onPlatform = { action ->
                    platformDispatcher.dispatch(action) { result ->
                        onPlatformResult?.invoke(result)
                    }
                },
                onUpdateDelivery = { action ->
                    updateDispatcher.dispatch(action, onUpdateDeliveryResult)
                },
            )
        }
    LaunchedEffect(events, lifecycleOwner, callbacks) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect { event -> dispatchSettingsEvent(event, callbacks) }
        }
    }
}

private fun toastText(message: SettingsMessage): String =
    when (message) {
        SettingsMessage.COMMAND_SUCCEEDED -> "设置已更新"
        SettingsMessage.COMMAND_FAILED -> "操作失败，请查看页面提示"
        SettingsMessage.PERMISSION_DENIED -> "权限未授予"
    }
