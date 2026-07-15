package cc.pscly.onememos.navigation

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon as AndroidIcon
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import cc.pscly.onememos.BuildConfig
import cc.pscly.onememos.overlay.QuickCaptureOverlayService
import cc.pscly.onememos.qs.QuickCaptureTileService
import cc.pscly.onememos.qs.QuickScreenshotTileService
import cc.pscly.onememos.screenshot.ScreenshotQuickCaptureActivity
import cc.pscly.onememos.ui.AppViewModel
import cc.pscly.onememos.ui.feature.quickcapture.QuickCaptureActivity
import cc.pscly.onememos.ui.feature.settings.SettingsAppInfo
import cc.pscly.onememos.ui.feature.settings.SettingsScreen
import cc.pscly.onememos.ui.feature.settings.SettingsUpdateInfo
import cc.pscly.onememos.update.AppUpdatePhase
import cc.pscly.onememos.update.AppUpdateUiState

/**
 * 迁移期 Settings 唯一 entry owner：九个键暂时都构造既有 SettingsScreen。
 * Task 30 会替换为 Feature-owned contributor，Task 31 删除本 bridge。
 */
object LegacySettingsEntryContributor : FeatureEntryContributor {
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
        require(owns(key)) { "LegacySettingsEntryContributor does not own $key" }
        return NavEntry(key) {
            val context = LocalContext.current
            val appViewModel: AppViewModel = hiltViewModel()
            val updateUiState by appViewModel.updateUiState.collectAsStateWithLifecycle()
            SettingsScreen(
                onBack = { navigator.back() },
                onOpenAuth = { mode ->
                    val authMode =
                        if (mode.equals("custom", ignoreCase = true)) {
                            AuthMode.CUSTOM_TOKEN
                        } else {
                            null
                        }
                    navigator.push(AuthKey(mode = authMode))
                },
                appInfo =
                    SettingsAppInfo(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                        buildType = BuildConfig.BUILD_TYPE,
                        flowBackendBaseUrl = BuildConfig.FLOW_BACKEND_BASE_URL,
                    ),
                updateInfo = updateUiState.toSettingsUpdateInfo(),
                onCheckForUpdates = appViewModel::checkForUpdatesManually,
                onRunUpdateAction = {
                    when (updateUiState.phase) {
                        AppUpdatePhase.AVAILABLE -> appViewModel.startUpdateDownload()
                        AppUpdatePhase.READY_TO_INSTALL ->
                            appViewModel.installDownloadedUpdate(context as? Activity)
                        else -> Unit
                    }
                },
                onClearIgnoredVersion = appViewModel::clearIgnoredUpdate,
                onRequestAddQuickCaptureTile = {
                    val act = context as? Activity
                    if (act == null) {
                        Toast.makeText(context, "当前页面无法发起系统添加请求", Toast.LENGTH_SHORT).show()
                        return@SettingsScreen
                    }
                    val sbm = act.getSystemService(StatusBarManager::class.java)
                    if (sbm == null) {
                        Toast.makeText(context, "系统服务不可用，无法一键添加", Toast.LENGTH_SHORT).show()
                        return@SettingsScreen
                    }
                    sbm.requestAddTileService(
                        ComponentName(act, QuickCaptureTileService::class.java),
                        context.getString(cc.pscly.onememos.core.quicktiles.R.string.qs_quick_capture),
                        AndroidIcon.createWithResource(
                            act,
                            cc.pscly.onememos.core.quicktiles.R.drawable.ic_qs_quick_capture,
                        ),
                        act.mainExecutor,
                    ) { result ->
                        Toast.makeText(context, "系统返回：$result", Toast.LENGTH_SHORT).show()
                    }
                },
                onStartQuickCaptureOverlay = {
                    context.startService(Intent(context, QuickCaptureOverlayService::class.java))
                },
                onOpenQuickCaptureActivity = {
                    context.startActivity(
                        Intent(context, QuickCaptureActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                },
                onRequestAddQuickScreenshotTile = {
                    val act = context as? Activity
                    if (act == null) {
                        Toast.makeText(context, "当前页面无法发起系统添加请求", Toast.LENGTH_SHORT).show()
                        return@SettingsScreen
                    }
                    val sbm = act.getSystemService(StatusBarManager::class.java)
                    if (sbm == null) {
                        Toast.makeText(context, "系统服务不可用，无法一键添加", Toast.LENGTH_SHORT).show()
                        return@SettingsScreen
                    }
                    sbm.requestAddTileService(
                        ComponentName(act, QuickScreenshotTileService::class.java),
                        context.getString(cc.pscly.onememos.core.quicktiles.R.string.qs_quick_screenshot),
                        AndroidIcon.createWithResource(
                            act,
                            cc.pscly.onememos.core.quicktiles.R.drawable.ic_qs_quick_screenshot,
                        ),
                        act.mainExecutor,
                    ) { result ->
                        Toast.makeText(context, "系统返回：$result", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenScreenshotQuickCapture = {
                    context.startActivity(
                        Intent(context, ScreenshotQuickCaptureActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                },
            )
        }
    }

    private fun AppUpdateUiState.toSettingsUpdateInfo(): SettingsUpdateInfo =
        SettingsUpdateInfo(
            statusText = statusMessage,
            checking = phase == AppUpdatePhase.CHECKING,
            actionLabel =
                when (phase) {
                    AppUpdatePhase.AVAILABLE -> "下载更新"
                    AppUpdatePhase.READY_TO_INSTALL -> "安装更新"
                    else -> null
                },
            actionEnabled = phase == AppUpdatePhase.AVAILABLE || phase == AppUpdatePhase.READY_TO_INSTALL,
            downloadProgressPercent = downloadProgressPercent.takeIf { phase == AppUpdatePhase.DOWNLOADING },
            ignoredVersionTag = ignoredVersionTag,
        )
}
